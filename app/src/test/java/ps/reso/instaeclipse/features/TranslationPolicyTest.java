package ps.reso.instaeclipse.features;

import org.junit.Test;
import java.util.Locale;
import static org.junit.Assert.*;

public class TranslationPolicyTest {
    @Test public void mapsDeviceLocalesToServiceCodes() {
        assertEquals("tr", TranslationPolicy.targetLanguage(Locale.forLanguageTag("tr-TR")));
        assertEquals("he", TranslationPolicy.targetLanguage(new Locale("iw")));
        assertEquals("id", TranslationPolicy.targetLanguage(new Locale("in")));
        assertEquals("zh-CN", TranslationPolicy.targetLanguage(Locale.SIMPLIFIED_CHINESE));
        assertEquals("zh-TW", TranslationPolicy.targetLanguage(Locale.forLanguageTag("zh-Hant")));
        assertEquals("zh-TW", TranslationPolicy.targetLanguage(Locale.forLanguageTag("zh-HK")));
        assertEquals("en", TranslationPolicy.targetLanguage(Locale.ROOT));
        assertEquals("zh", TranslationPolicy.yandexLanguage("zh-TW"));
    }

    @Test public void joinsSentencesAndKeepsDetectedSource() {
        TranslationPolicy.Result result = TranslationPolicy.parseGoogle(
                "{\"sentences\":[{\"trans\":\"Merhaba. \",\"orig\":\"Hello. \"},{\"trans\":\"\\\"Nasılsın?\\\"\"},"
                        + "{\"translit\":\"x\"}],\"src\":\"en\"}");
        assertNotNull(result);
        assertEquals("Merhaba. \"Nasılsın?\"", result.text);
        assertEquals("en", result.source);
    }

    @Test public void readsCompactChromeExtensionResponses() {
        TranslationPolicy.Result result = TranslationPolicy.parseGoogle("[[\"Selam\\n\\nD\u00fcnya\",\"en\"]]");
        assertNotNull(result);
        assertEquals("Selam\n\nDünya", result.text);
        assertEquals("en", result.source);
        TranslationPolicy.Result sourceless = TranslationPolicy.parseGoogle("[\"Günaydın\"]");
        assertNotNull(sourceless);
        assertEquals("Günaydın", sourceless.text);
        assertNull(sourceless.source);
    }

    @Test public void rejectsMalformedOrEmptyResponses() {
        assertNull(TranslationPolicy.parseGoogle(null));
        assertNull(TranslationPolicy.parseGoogle("<html>429</html>"));
        assertNull(TranslationPolicy.parseGoogle("[[[\"a\",\"b\"]]]"));
        assertNull(TranslationPolicy.parseGoogle("[[\" \",\"en\"]]"));
        assertNull(TranslationPolicy.parseGoogle("{\"sentences\":[{\"trans\":\"  \"}]}"));
        assertNull(TranslationPolicy.parseGoogle("{\"sentences\":[{\"trans\":\"ok\"}],\"src\":\"\"}").source);
    }

    @Test public void clipsWithoutSplittingSurrogatePairs() {
        String text = "ab😀c";
        assertEquals("ab", TranslationPolicy.clip(text, 3));
        assertEquals("ab😀", TranslationPolicy.clip(text, 4));
        assertEquals(text, TranslationPolicy.clip(text, 10));
    }

    @Test public void encodesTextIntoRequestsAndWebUrls() {
        assertEquals("q=a+%26+b%3Dc", TranslationPolicy.googleRequestBody("a & b=c"));
        for (String api : TranslationPolicy.googleApiUrls("zh-TW")) assertTrue(api.contains("&tl=zh-TW"));
        assertEquals(2, TranslationPolicy.googleApiUrls("tr").size());
        assertEquals("https://translate.google.com/?sl=auto&tl=tr&op=translate&text=%C3%A7ay+%3F",
                TranslationPolicy.googleWebUrl("çay ?", "tr"));
        assertEquals("https://translate.yandex.com/?source_lang=auto&target_lang=zh&text=hi",
                TranslationPolicy.yandexWebUrl("hi", "zh-CN"));
        String url = TranslationPolicy.googleWebUrl("x".repeat(TranslationPolicy.MAX_URL_CHARS + 50), "tr");
        assertEquals(TranslationPolicy.MAX_URL_CHARS, url.substring(url.indexOf("&text=") + 6).length());
    }
}
