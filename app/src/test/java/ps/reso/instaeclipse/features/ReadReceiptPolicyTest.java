package ps.reso.instaeclipse.features;
import org.junit.Test;
import static org.junit.Assert.*;
public class ReadReceiptPolicyTest {
    @Test public void requiresExactThreadAndRejectsMissingIds() {
        assertTrue(ReadReceiptPolicy.allows("123456,654321", "123456"));
        assertFalse(ReadReceiptPolicy.allows("1234567", "123456"));
        assertFalse(ReadReceiptPolicy.allows("", null));
        assertFalse(ReadReceiptPolicy.allows("bad", "bad"));
    }
    @Test public void changesOnlySelectedThread() {
        assertEquals("123456,654321", ReadReceiptPolicy.setAllowed("654321,654321", "123456", true));
        assertEquals("654321", ReadReceiptPolicy.setAllowed("123456,654321", "123456", false));
    }
    @Test public void resolvesOnlyAThreadPathSegment() {
        assertEquals("123456", ReadReceiptPolicy.threadFromPath("/api/v1/direct_v2/threads/123456/items/44/opened/"));
        assertNull(ReadReceiptPolicy.threadFromPath("/api/v1/direct_v2/threads/inbox/opened/"));
        assertNull(ReadReceiptPolicy.threadFromPath("/media/123456/"));
    }
}
