package ps.reso.instaeclipse.features

import com.google.gson.JsonParser
import java.net.URLEncoder
import java.util.Locale

/** Language codes, request shapes and response parsing for the Google/Yandex translation actions. */
object TranslationPolicy {
    const val GOOGLE_APP = "com.google.android.apps.translate"
    const val YANDEX_APP = "ru.yandex.translate"

    /** Longest text sent to the in-app Google request; a comment or message rarely comes close. */
    const val MAX_REQUEST_CHARS = 5000
    /** Text carried in a web URL is shorter, so the URL stays within browser limits. */
    const val MAX_URL_CHARS = 1500

    class Result(@JvmField val text: String, @JvmField val source: String?)

    /** Google code for the reader's language: ISO 639-1, with the two Chinese scripts told apart. */
    @JvmStatic
    fun targetLanguage(locale: Locale): String {
        val language = when (locale.language) {
            "", "und" -> "en"
            "iw" -> "he"
            "in" -> "id"
            "ji" -> "yi"
            else -> locale.language
        }
        if (language != "zh") return language
        val traditional = locale.script == "Hant" || locale.country in setOf("TW", "HK", "MO")
        return if (traditional) "zh-TW" else "zh-CN"
    }

    /** Yandex has a single Chinese code. */
    @JvmStatic
    fun yandexLanguage(target: String): String = target.substringBefore('-')

    /** Cuts [text] to [max] chars without splitting a surrogate pair. */
    @JvmStatic
    fun clip(text: String, max: Int): String {
        if (text.length <= max) return text
        val end = if (Character.isHighSurrogate(text[max - 1])) max - 1 else max
        return text.substring(0, end)
    }

    /**
     * Keyless Google endpoints, tried in order: each is rate-limited per IP on its own, so one can
     * answer while the other returns "Sorry…" (HTTP 429).
     */
    @JvmStatic
    fun googleApiUrls(target: String): List<String> = listOf(
        "https://clients5.google.com/translate_a/t?client=dict-chrome-ex&sl=auto&tl=${encode(target)}",
        "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=${encode(target)}&dt=t&dj=1",
    )

    /** Used when the WebView user agent is unavailable; Google answers the default Dalvik agent with a captcha. */
    const val FALLBACK_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36"

    /** The text goes in a POST body so long comments are not limited by URL length. */
    @JvmStatic
    fun googleRequestBody(text: String): String = "q=" + encode(clip(text, MAX_REQUEST_CHARS))

    @JvmStatic
    fun googleWebUrl(text: String, target: String): String =
        "https://translate.google.com/?sl=auto&tl=${encode(target)}&op=translate&text=${encode(clip(text, MAX_URL_CHARS))}"

    @JvmStatic
    fun yandexWebUrl(text: String, target: String): String =
        "https://translate.yandex.com/?source_lang=auto&target_lang=${encode(yandexLanguage(target))}" +
            "&text=${encode(clip(text, MAX_URL_CHARS))}"

    /**
     * Reads either response shape: `[["…","en"]]` (dict-chrome-ex) or
     * `{"sentences":[{"trans":…},…],"src":"en"}` (gtx, `dj=1`). Null when nothing was translated.
     */
    @JvmStatic
    fun parseGoogle(json: String?): Result? {
        val root = runCatching { JsonParser.parseString(json.orEmpty()) }.getOrNull() ?: return null
        val text = StringBuilder()
        var source: String? = null
        if (root.isJsonObject) {
            val sentences = root.asJsonObject.get("sentences")?.takeIf { it.isJsonArray }?.asJsonArray ?: return null
            for (sentence in sentences) {
                val trans = sentence.takeIf { it.isJsonObject }?.asJsonObject?.get("trans")
                if (trans != null && trans.isJsonPrimitive) text.append(trans.asString)
            }
            source = root.asJsonObject.get("src")?.takeIf { it.isJsonPrimitive }?.asString
        } else if (root.isJsonArray) {
            for (item in root.asJsonArray) {
                val parts = if (item.isJsonArray) item.asJsonArray else null
                val trans = parts?.takeIf { it.size() > 0 }?.get(0) ?: item
                if (trans.isJsonPrimitive) text.append(trans.asString)
                if (source == null && parts != null && parts.size() > 1 && parts[1].isJsonPrimitive) source = parts[1].asString
            }
        }
        if (text.isBlank()) return null
        return Result(text.toString(), source?.takeIf { it.isNotBlank() })
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
