package ps.reso.instaeclipse.mods.misc;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CaptionHashtagStripTest {

    @Test
    public void removesInlineAndTrailingTags() {
        assertEquals("Sunset at the\n\nSee you soon",
                CaptionCopyContextMenuHook.stripHashtags(
                        "Sunset at the #beach\n\nSee you soon\n\n#travel #sun #summer2026"));
    }

    @Test
    public void keepsNonTagHashes() {
        assertEquals("Order C# book, issue &#35; and a#b",
                CaptionCopyContextMenuHook.stripHashtags("Order C# book, issue &#35; and a#b #dev"));
    }

    @Test
    public void handlesUnicodeTags() {
        assertEquals("İstanbul’da akşam",
                CaptionCopyContextMenuHook.stripHashtags("İstanbul’da akşam #gün_batımı #şehir"));
    }

    @Test
    public void unchangedWithoutTags() {
        assertEquals("plain caption", CaptionCopyContextMenuHook.stripHashtags("plain caption"));
    }
}
