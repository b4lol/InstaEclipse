package ps.reso.instaeclipse.mods.media

import android.app.AlertDialog
import android.content.*
import android.graphics.BitmapFactory
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import ps.reso.instaeclipse.R
import ps.reso.instaeclipse.features.MediaFilePolicy
import ps.reso.instaeclipse.utils.feature.FeatureFlags
import ps.reso.instaeclipse.utils.feature.FeatureStatusTracker
import ps.reso.instaeclipse.utils.i18n.I18n
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** User-initiated media operations, with bounded workers and no host objects held in the queue. */
object MediaActions {
    private val main = Handler(Looper.getMainLooper())
    private val workers = ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS, ArrayBlockingQueue(8),
        { work -> Thread(work, "IE-MediaActions").apply { isDaemon = true } })
    private const val MAX_BYTES = 256L * 1024 * 1024

    @JvmStatic
    fun offer(ctx: Context, urls: List<String>, download: Runnable): Boolean {
        if (!FeatureFlags.mediaActions || urls.isEmpty()) return false
        val valid = urls.filter { DownloadRequestValidator.isAllowedMediaUrl(it) }.distinct()
        if (valid.isEmpty()) return false
        FeatureStatusTracker.setHooked("MediaActions")
        if (valid.size == 1) show(ctx, valid.first(), download)
        else AlertDialog.Builder(ctx).setTitle(I18n.t(ctx, R.string.ie_media_item))
            .setItems(valid.indices.map { (it + 1).toString() }.toTypedArray()) { _, i -> show(ctx, valid[i], download) }
            .setNegativeButton(android.R.string.cancel, null).show()
        return true
    }

    private fun show(ctx: Context, url: String, download: Runnable) {
        val labels = intArrayOf(R.string.ie_download, R.string.ie_audio_only, R.string.ie_media_info,
            R.string.ie_external_media, R.string.ie_copy_image)
        AlertDialog.Builder(ctx).setItems(labels.map { I18n.t(ctx, it) }.toTypedArray()) { _, index ->
            when (index) {
                0 -> download.run()
                3 -> try {
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW, Uri.parse(url)),
                        I18n.t(ctx, R.string.ie_external_media)))
                } catch (_: Exception) { toast(ctx, R.string.ie_media_failed) }
                else -> run(ctx, url, index)
            }
        }.show()
    }

    @JvmStatic
    fun saveAudio(ctx: Context, url: String) { run(ctx, url, 1) }

    private fun run(ctx: Context, url: String, action: Int) {
        if (action == 1 && FeatureFlags.downloaderCustomUri.isNotEmpty()) {
            try {
                val request = Intent().setComponent(ComponentName("ps.reso.instaeclipse",
                    "ps.reso.instaeclipse.mods.media.DownloadSaveService"))
                    .putExtra("url", url).putExtra("filename", "InstaEclipse_${System.currentTimeMillis()}.m4a")
                    .putExtra("mimeType", "audio/mp4").putExtra("extractAudio", true)
                ctx.startForegroundService(request)
            } catch (_: Exception) { toast(ctx, R.string.ie_media_failed) }
            return
        }
        toast(ctx, R.string.ie_working)
        try { workers.execute {
            var input: File? = null
            var output: File? = null
            try {
                input = File.createTempFile("ie-media-", ".tmp", ctx.cacheDir)
                val reported = fetch(url, input)
                val bytes = ByteArray(16)
                input.inputStream().use { stream ->
                    var offset = 0
                    while (offset < bytes.size) {
                        val n = stream.read(bytes, offset, bytes.size - offset)
                        if (n < 0) break
                        offset += n
                    }
                }
                val mime = MediaFilePolicy.mime(bytes, reported) ?: error("Unsupported media")
                when (action) {
                    1 -> {
                        output = File.createTempFile("ie-audio-", ".m4a", ctx.cacheDir)
                        extractAudio(input, output)
                        save(ctx, output, "audio/mp4")
                        toast(ctx, R.string.ie_saved)
                    }
                    2 -> {
                        val details = information(input, mime)
                        main.post { if (ctx !is android.app.Activity || !ctx.isFinishing) {
                            AlertDialog.Builder(ctx).setTitle(I18n.t(ctx, R.string.ie_media_info))
                                .setMessage(details).setPositiveButton(android.R.string.ok, null).show()
                        } }
                    }
                    4 -> {
                        require(mime.startsWith("image/"))
                        val uri = save(ctx, input, mime)
                        main.post {
                            val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newUri(ctx.contentResolver, "Image", uri))
                            toast(ctx, R.string.ie_image_copied)
                        }
                    }
                }
            } catch (_: Exception) { toast(ctx, R.string.ie_media_failed) }
            finally { input?.delete(); output?.delete() }
        } } catch (_: java.util.concurrent.RejectedExecutionException) { toast(ctx, R.string.ie_media_busy) }
    }

    /** Shared with the companion service; every redirect is revalidated. */
    @JvmStatic
    fun fetch(url: String, target: File): String? {
        var current = url
        repeat(6) {
            require(DownloadRequestValidator.isAllowedMediaUrl(current))
            val conn = URI(current).toURL().openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 15_000; conn.readTimeout = 30_000
                conn.instanceFollowRedirects = false
                val status = conn.responseCode
                if (status in 300..399) {
                    current = URI(current).resolve(conn.getHeaderField("Location") ?: error("Missing redirect")).toString()
                } else {
                    require(status in 200..299)
                    require(conn.contentLengthLong <= MAX_BYTES)
                    conn.inputStream.use { source -> target.outputStream().use { dest ->
                        val buffer = ByteArray(32768); var total = 0L
                        val deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(5)
                        while (true) {
                            check(!Thread.currentThread().isInterrupted && System.nanoTime() < deadline)
                            val count = source.read(buffer)
                            if (count < 0) break
                            total += count; require(total <= MAX_BYTES)
                            dest.write(buffer, 0, count)
                        }
                    } }
                    return conn.contentType
                }
            } finally { conn.disconnect() }
        }
        error("Too many redirects")
    }

    @JvmStatic
    fun extractAudio(input: File, output: File) {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var started = false
        try {
            extractor.setDataSource(input.absolutePath)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("No audio track")
            val format = extractor.getTrackFormat(track)
            // MP4 supports AAC without re-encoding. Refuse formats that need a transcoder.
            require(format.getString(MediaFormat.KEY_MIME) == "audio/mp4a-latm")
            extractor.selectTrack(track)
            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val out = muxer.addTrack(format); muxer.start(); started = true
            val capacity = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else 1024 * 1024
            require(capacity in 1..(16 * 1024 * 1024))
            val buffer = ByteBuffer.allocate(capacity.coerceAtLeast(1024 * 1024))
            val info = MediaCodec.BufferInfo()
            while (true) {
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                require(extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED == 0)
                val flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0)
                    MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                info.set(0, size, extractor.sampleTime, flags)
                muxer.writeSampleData(out, buffer, info)
                extractor.advance()
            }
            muxer.stop(); started = false
        } finally {
            extractor.release()
            if (started) try { muxer?.stop() } catch (_: Exception) {}
            muxer?.release()
        }
    }

    private fun information(file: File, mime: String): String {
        val size = android.text.format.Formatter.formatFileSize(ps.reso.instaeclipse.hook.HostApp.get(), file.length())
        if (mime.startsWith("image/")) {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            return "$mime\n${options.outWidth} × ${options.outHeight}\n$size"
        }
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
            "$mime\n${width ?: "—"} × ${height ?: "—"}\n${duration / 1000} s\n$size"
        } finally { retriever.release() }
    }

    private fun save(ctx: Context, file: File, mime: String): Uri {
        // MediaStore creates a URI owned by the host, which can be granted through the clipboard.
        // Android 9 cannot insert without legacy storage permission; report failure rather than broadening permissions.
        val collection = if (mime.startsWith("image/")) MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "InstaEclipse_${System.currentTimeMillis()}${MediaFilePolicy.extension(mime)}")
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.MediaColumns.IS_PENDING, 1)
                put(MediaStore.MediaColumns.RELATIVE_PATH, if (mime.startsWith("image/")) "Pictures/InstaEclipse" else "Music/InstaEclipse")
            }
        }
        val resolver = ctx.contentResolver
        val uri = resolver.insert(collection, values) ?: error("Storage unavailable")
        try {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: error("Storage unavailable")
            if (Build.VERSION.SDK_INT >= 29) resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            return uri
        } catch (e: Exception) { resolver.delete(uri, null, null); throw e }
    }

    private fun toast(ctx: Context, id: Int) { main.post { Toast.makeText(ctx, I18n.t(ctx, id), Toast.LENGTH_SHORT).show() } }
}
