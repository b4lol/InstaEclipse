package ps.reso.instaeclipse.features;
import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
public class MediaFilePolicyTest {
    @Test public void keepsGifAndPngRatherThanJpeg() {
        assertEquals("image/gif", MediaFilePolicy.mime("GIF89aXX".getBytes(StandardCharsets.US_ASCII), "image/jpeg"));
        assertEquals(".gif", MediaFilePolicy.extension("image/gif"));
        assertEquals("image/png", MediaFilePolicy.mime(new byte[]{(byte)137,80,78,71,13,10,26,10}, null));
    }
    @Test public void distinguishesAudioFromVideo() {
        assertEquals("audio/mp4", MediaFilePolicy.mime(new byte[]{0,0,0,20,'f','t','y','p','M','4','A',' '}, null));
        assertEquals(".m4a", MediaFilePolicy.extension("audio/mp4"));
    }
    @Test public void refusesHtmlEvenWithAnImageHeader() {
        assertNull(MediaFilePolicy.mime("<html>error".getBytes(StandardCharsets.US_ASCII), "image/jpeg"));
        assertNull(MediaFilePolicy.mime(new byte[]{}, null));
    }
}
