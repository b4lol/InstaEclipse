package ps.reso.instaeclipse.mods.translate

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.WebSettings
import android.widget.TextView
import android.widget.Toast
import ps.reso.instaeclipse.R
import ps.reso.instaeclipse.features.TranslationPolicy
import ps.reso.instaeclipse.utils.i18n.I18n
import ps.reso.instaeclipse.utils.log.ModuleLog
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Translation actions for comments, captions and DMs. Google translates in place through its
 * keyless web endpoints (unofficial: they can be rate-limited or change); Yandex, and Google on
 * request, open the installed app or else the translation website. Only the text the user chose
 * is sent, and only after they pick a service.
 */
object TextTranslator {
    private const val MAX_RESPONSE_BYTES = 1024 * 1024
    private val main = Handler(Looper.getMainLooper())
    private val worker = ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, ArrayBlockingQueue(4),
        { work -> Thread(work, "IE-Translate").apply { isDaemon = true } }).apply { allowCoreThreadTimeOut(true) }

    /** Lets the user pick a service for [text]; [dismissLabel] names the button that returns to Instagram. */
    @JvmStatic
    @JvmOverloads
    fun show(ctx: Context, text: String?, dismissLabel: String? = null) {
        val value = text?.trim().orEmpty()
        if (value.isEmpty() || !usable(ctx)) return
        val items = arrayOf(t(ctx, R.string.ie_translate_google), t(ctx, R.string.ie_translate_yandex),
            t(ctx, R.string.ie_translate_other))
        AlertDialog.Builder(ctx).setTitle(t(ctx, R.string.ie_translate_title))
            .setItems(items) { _, which ->
                when (which) {
                    0 -> translateWithGoogle(ctx, value)
                    1 -> openYandex(ctx, value)
                    else -> openTextApps(ctx, value)
                }
            }
            .setNegativeButton(dismissLabel ?: ctx.getString(android.R.string.cancel), null)
            .show()
    }

    private fun translateWithGoogle(ctx: Context, text: String) {
        val target = TranslationPolicy.targetLanguage(ctx.resources.configuration.locales[0])
        val dialog = AlertDialog.Builder(ctx).setTitle(t(ctx, R.string.ie_translate_google))
            .setMessage(t(ctx, R.string.ie_translating))
            .setPositiveButton(t(ctx, R.string.ie_translate_copy), null)
            .setNeutralButton(t(ctx, R.string.ie_translate_open_google)) { _, _ -> openGoogle(ctx, text, target) }
            .setNegativeButton(ctx.getString(android.R.string.cancel), null)
            .show()
        val copy = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        copy.visibility = View.GONE
        try {
            worker.execute {
                val agent = userAgent(ctx)
                val result = TranslationPolicy.googleApiUrls(target).firstNotNullOfOrNull { url ->
                    runCatching { requestGoogle(url, text, agent) }
                        .onFailure { ModuleLog.line("(IE|Translate) ${Uri.parse(url).host}: $it") }.getOrNull()
                }
                main.post {
                    if (!dialog.isShowing || !usable(ctx)) return@post
                    if (result == null) {
                        dialog.setMessage(t(ctx, R.string.ie_translate_failed))
                        return@post
                    }
                    dialog.setTitle(t(ctx, R.string.ie_translate_result_title,
                        (result.source ?: "?").uppercase(), target.uppercase()))
                    dialog.setMessage(result.text)
                    dialog.findViewById<TextView>(android.R.id.message)?.setTextIsSelectable(true)
                    copy.visibility = View.VISIBLE
                    copy.setOnClickListener {
                        val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Translation", result.text))
                        Toast.makeText(ctx, t(ctx, R.string.ie_translate_copied), Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                }
            }
        } catch (_: RejectedExecutionException) {
            dialog.setMessage(t(ctx, R.string.ie_translate_failed))
        }
    }

    private fun requestGoogle(url: String, text: String, agent: String): TranslationPolicy.Result? {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.setRequestProperty("User-Agent", agent)
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.instanceFollowRedirects = false
            conn.useCaches = false
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
            conn.outputStream.use { it.write(TranslationPolicy.googleRequestBody(text).toByteArray()) }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                ModuleLog.line("(IE|Translate) ${conn.url.host}: HTTP ${conn.responseCode}")
                return null
            }
            val body = ByteArrayOutputStream()
            conn.inputStream.use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    if (body.size() + n > MAX_RESPONSE_BYTES) return null
                    body.write(buffer, 0, n)
                }
            }
            return TranslationPolicy.parseGoogle(body.toString("UTF-8"))
        } finally {
            conn.disconnect()
        }
    }

    @Volatile private var cachedUserAgent: String? = null

    /** The device's own browser agent; Google answers the default Dalvik agent with a captcha redirect. */
    private fun userAgent(ctx: Context): String = cachedUserAgent
        ?: (runCatching { WebSettings.getDefaultUserAgent(ctx.applicationContext) }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: TranslationPolicy.FALLBACK_USER_AGENT).also { cachedUserAgent = it }

    private fun openGoogle(ctx: Context, text: String, target: String) {
        if (launch(ctx, processText(text).setPackage(TranslationPolicy.GOOGLE_APP))) return
        if (!launch(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(TranslationPolicy.googleWebUrl(text, target)))))
            toast(ctx, R.string.ie_no_text_app)
    }

    private fun openYandex(ctx: Context, text: String) {
        if (launch(ctx, processText(text).setPackage(TranslationPolicy.YANDEX_APP))) return
        if (launch(ctx, Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                .setPackage(TranslationPolicy.YANDEX_APP))) return
        val target = TranslationPolicy.targetLanguage(ctx.resources.configuration.locales[0])
        if (!launch(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(TranslationPolicy.yandexWebUrl(text, target)))))
            toast(ctx, R.string.ie_no_text_app)
    }

    private fun openTextApps(ctx: Context, text: String) {
        if (!launch(ctx, Intent.createChooser(processText(text), t(ctx, R.string.ie_translate_other))))
            toast(ctx, R.string.ie_no_text_app)
    }

    private fun processText(text: String): Intent = Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain")
        .putExtra(Intent.EXTRA_PROCESS_TEXT, text)
        .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)

    private fun launch(ctx: Context, intent: Intent): Boolean = try {
        if (ctx !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    private fun usable(ctx: Context): Boolean = ctx !is Activity || (!ctx.isFinishing && !ctx.isDestroyed)

    private fun toast(ctx: Context, id: Int) = Toast.makeText(ctx, t(ctx, id), Toast.LENGTH_SHORT).show()

    private fun t(ctx: Context, id: Int, vararg args: Any): String = I18n.t(ctx, id, *args)
}
