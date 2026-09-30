package ps.reso.instaeclipse;

import android.content.Context;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import org.junit.Test;
import org.junit.runner.RunWith;
import ps.reso.instaeclipse.mods.media.MediaActions;
import static org.junit.Assert.*;

/** Synthetic fixtures only; never opens Instagram, the network, or user media. */
@RunWith(AndroidJUnit4.class)
public class AudioExtractionTest {
    @Test public void extractsPlayableAacWithoutVideo() throws Exception {
        File input = fixture("audio-extraction.mp4");
        File output = File.createTempFile("audio-result", ".m4a", input.getParentFile());
        MediaExtractor result = new MediaExtractor();
        try {
            MediaActions.extractAudio(input, output);
            result.setDataSource(output.getAbsolutePath());
            assertEquals(1, result.getTrackCount());
            MediaFormat format = result.getTrackFormat(0);
            assertEquals("audio/mp4a-latm", format.getString(MediaFormat.KEY_MIME));
            assertEquals(44100, format.getInteger(MediaFormat.KEY_SAMPLE_RATE));
            result.selectTrack(0);
            ByteBuffer buffer = ByteBuffer.allocate(1024 * 1024);
            int samples = 0;
            long previous = -1;
            while (result.readSampleData(buffer, 0) >= 0) {
                assertTrue(result.getSampleTime() >= previous);
                previous = result.getSampleTime();
                samples++;
                result.advance();
                buffer.clear();
            }
            assertTrue(samples > 1);
            assertTrue(previous > 0);
        } finally {
            result.release();
            input.delete();
            output.delete();
        }
    }

    @Test public void rejectsVideoWithoutAudio() throws Exception {
        File input = fixture("video-only.mp4");
        File output = File.createTempFile("audio-result", ".m4a", input.getParentFile());
        try {
            assertThrows(IllegalStateException.class, () -> MediaActions.extractAudio(input, output));
        } finally {
            input.delete();
            output.delete();
        }
    }

    private static File fixture(String name) throws Exception {
        // Assets live in the test APK, but the test process runs as the target app and can only
        // write to the target's cache directory.
        Context test = InstrumentationRegistry.getInstrumentation().getContext();
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File file = File.createTempFile("audio-fixture", ".mp4", target.getCacheDir());
        try (InputStream in = test.getAssets().open(name); FileOutputStream out = new FileOutputStream(file)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
        }
        return file;
    }
}
