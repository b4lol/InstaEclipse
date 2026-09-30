package ps.reso.instaeclipse.features;

import org.junit.Test;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.Locale;
import static org.junit.Assert.*;

public class FeaturePoliciesTest {
    @Test public void keepsHomeProfileAndUnknownTabs() {
        assertEquals(List.of("PROFILE", "FEED", "FUTURE"), NavigationPolicy.arrange(
                List.of("FEED", "CLIPS", "FUTURE", "PROFILE"),
                Set.of("FEED", "PROFILE", "CLIPS"), "PROFILE,CLIPS"));
    }
    @Test public void normalizesAndDeduplicatesOrder() {
        assertEquals(List.of("DIRECT", "FEED"), NavigationPolicy.normalizeOrder(" direct,INVALID,feed,DIRECT "));
        assertTrue(NavigationPolicy.normalizeOrder(null).isEmpty());
    }
    @Test public void defaultOrderDoesNotMutateHostList() {
        List<String> input = List.of("FEED", "CLIPS", "PROFILE");
        assertEquals(input, NavigationPolicy.arrange(input, Set.of(), ""));
        assertEquals(3, input.size());
    }
    @Test public void rejectsDurationsAndFutureDates() {
        assertNull(ExactTimePolicy.format(3000, 1800000000000L, Locale.US, ZoneId.of("UTC")));
        assertNull(ExactTimePolicy.format(1800000000001L, 1800000000000L, Locale.US, ZoneId.of("UTC")));
    }
    @Test public void timestampsUseSuppliedTimezone() {
        long time = 1704067200000L;
        String utc = ExactTimePolicy.format(time, time, Locale.US, ZoneId.of("UTC"));
        String istanbul = ExactTimePolicy.format(time, time, Locale.US, ZoneId.of("Europe/Istanbul"));
        assertNotNull(utc);
        assertNotEquals(utc, istanbul);
    }
    @Test public void searchesSnapshotWithoutEmptyQueryOrDuplicates() {
        assertTrue(CommentSearchPolicy.search(List.of("One"), " ").isEmpty());
        assertEquals(List.of("One", "Another one"), CommentSearchPolicy.search(List.of("One", "Two", "One", "Another one"), "ONE"));
    }
}
