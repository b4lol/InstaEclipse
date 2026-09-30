package ps.reso.instaeclipse.features

/** Signature takes precedence over server/file-name claims; never label GIF bytes as JPEG. */
object MediaFilePolicy {
    @JvmStatic
    fun mime(header: ByteArray, reported: String?): String? {
        fun starts(vararg bytes: Int) = header.size >= bytes.size && bytes.indices.all { (header[it].toInt() and 255) == bytes[it] }
        if (starts(0xff, 0xd8, 0xff)) return "image/jpeg"
        if (starts(137, 80, 78, 71, 13, 10, 26, 10)) return "image/png"
        if (header.size >= 6 && String(header, 0, 6, Charsets.US_ASCII) in setOf("GIF87a", "GIF89a")) return "image/gif"
        if (header.size >= 12 && String(header, 0, 4, Charsets.US_ASCII) == "RIFF" && String(header, 8, 4, Charsets.US_ASCII) == "WEBP") return "image/webp"
        if (header.size >= 12 && String(header, 4, 4, Charsets.US_ASCII) == "ftyp") {
            return when (String(header, 8, 4, Charsets.US_ASCII).trim().lowercase(java.util.Locale.ROOT)) {
                "m4a", "m4b" -> "audio/mp4"
                "avif", "avis" -> "image/avif"
                "heic", "heix", "mif1", "msf1", "hevc", "hevx" -> "image/heif"
                else -> if (reported?.startsWith("audio/") == true) "audio/mp4" else "video/mp4"
            }
        }
        if (starts(79, 103, 103, 83)) return "audio/ogg"
        if (starts(73, 68, 51)) return "audio/mpeg"
        return null
    }

    @JvmStatic
    fun extension(mime: String): String = when (mime) {
        "image/jpeg" -> ".jpg"
        "image/png" -> ".png"
        "image/gif" -> ".gif"
        "image/webp" -> ".webp"
        "image/avif" -> ".avif"
        "image/heif" -> ".heic"
        "audio/mp4" -> ".m4a"
        "audio/ogg" -> ".ogg"
        "audio/mpeg" -> ".mp3"
        else -> ".mp4"
    }
}
