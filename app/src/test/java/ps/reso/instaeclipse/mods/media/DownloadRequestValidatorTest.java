package ps.reso.instaeclipse.mods.media;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DownloadRequestValidatorTest {

    @Test
    public void acceptsInstagramCdnHosts() {
        assertTrue(DownloadRequestValidator.isAllowedMediaUrl(
                "https://scontent-ist1-1.cdninstagram.com/v/t51.2885-15/a.jpg?x=1"));
        assertTrue(DownloadRequestValidator.isAllowedMediaUrl(
                "https://instagram.fist6-1.fna.fbcdn.net/o1/v/t16/f2/m86/a.mp4"));
    }

    @Test
    public void rejectsOtherSchemesAndHosts() {
        assertFalse(DownloadRequestValidator.isAllowedMediaUrl(null));
        assertFalse(DownloadRequestValidator.isAllowedMediaUrl(""));
        assertFalse(DownloadRequestValidator.isAllowedMediaUrl("http://scontent.cdninstagram.com/a.jpg"));
        assertFalse(DownloadRequestValidator.isAllowedMediaUrl("file:///data/data/x/shared_prefs/a.xml"));
        assertFalse(DownloadRequestValidator.isAllowedMediaUrl("https://evil.example/?cdninstagram.com"));
        assertFalse(DownloadRequestValidator.isAllowedMediaUrl("https://evilcdninstagram.com/a.jpg"));
        assertFalse(DownloadRequestValidator.isAllowedMediaUrl("https://cdninstagram.com.evil.example/a.jpg"));
        assertFalse(DownloadRequestValidator.isAllowedMediaUrl("https://user@scontent.cdninstagram.com/a.jpg"));
    }

    @Test
    public void sanitizesFileNames() {
        assertEquals("a.jpg", DownloadRequestValidator.sanitizeFileName("a.jpg", "x"));
        assertEquals("_.._evil.mp4", DownloadRequestValidator.sanitizeFileName("../../evil.mp4", "x"));
        assertEquals("a_b_c", DownloadRequestValidator.sanitizeFileName("a/b\\c", "x"));
        assertEquals("hidden", DownloadRequestValidator.sanitizeFileName("..hidden", "x"));
        assertEquals("x", DownloadRequestValidator.sanitizeFileName("...", "x"));
        assertEquals("x", DownloadRequestValidator.sanitizeFileName(null, "x"));
        String longName = "a".repeat(300) + ".mp4";
        String out = DownloadRequestValidator.sanitizeFileName(longName, "x");
        assertEquals(DownloadRequestValidator.MAX_NAME_LENGTH, out.length());
        assertTrue(out.endsWith(".mp4"));
    }
}
