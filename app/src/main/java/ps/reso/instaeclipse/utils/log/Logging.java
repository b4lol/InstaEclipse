package ps.reso.instaeclipse.utils.log;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Persistent, size-bounded ring buffer of log lines, used to power the in-app log viewer
 * (LoggingFragment). Runs independently in whichever process calls init() — Instagram's
 * hooked process and the companion app each keep their own buffer/file, since a hooked
 * process can't directly read another process's memory.
 *
 * Consecutive identical lines are collapsed into a single "(×N)" entry to avoid the buffer
 * filling up with noise from a hook that logs the same thing on every call.
 *
 * Threading: {@link #LOCK} only guards the in-memory state and is never held during disk I/O,
 * so callers on Instagram's UI thread never wait for the file. All file access happens on a
 * single background thread, which owns the writer.
 */
public final class Logging {

    private static final long FLUSH_DELAY_MS = 750;
    // Broadcast extras ride the Binder transaction buffer (~1MB total, shared across all
    // in-flight transactions in the process), and Java strings are UTF-16 (2 bytes/char), so
    // this must stay well under half a million chars or the reply broadcast triggers
    // TransactionTooLargeException -> the receiving process gets killed with
    // CannotDeliverBroadcastException instead of ever seeing the intent.
    private static final int IPC_MAX_CHARS = 150000;
    private static final long MAX_FILE_BYTES = 4 * 1024 * 1024;
    private static final int MAX_LINES = 10000;

    private static final Object LOCK = new Object();
    private static final ArrayDeque<String> LINES = new ArrayDeque<>(MAX_LINES + 16);
    private static final ThreadLocal<SimpleDateFormat> TS =
            ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US));

    // In-memory state, guarded by LOCK.
    private static boolean initialized;
    private static String lastBody;
    private static String lastBodyTime;
    private static int lastBodyRepeat = 1;
    private static boolean rewritePending;

    // I/O state, only touched on the I/O thread.
    private static File logFile;
    private static BufferedWriter writer;
    private static long fileBytes;

    private static HandlerThread ioThread;
    private static volatile Handler ioHandler;

    private static final Runnable REWRITE_RUNNABLE = Logging::rewriteFile;

    private Logging() {}

    public static void init(Context context) {
        init(context, "instaeclipse_logging.log");
    }

    public static void init(Context context, String filename) {
        final File file = new File(context.getFilesDir(), filename);
        synchronized (LOCK) {
            if (initialized) return;
            initialized = true;
            ioThread = new HandlerThread("InstaEclipse-Logging-IO");
            ioThread.start();
            ioHandler = new Handler(ioThread.getLooper());
        }
        // Load previous lines off the caller's thread (this runs during Instagram's
        // Application.attach). Queued first, so it completes before any append is written.
        ioHandler.post(() -> {
            logFile = file;
            loadFromFile();
        });
    }

    private static void loadFromFile() {
        if (!logFile.exists()) return;
        List<String> loaded = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(logFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                loaded.add(line);
                if (loaded.size() > MAX_LINES * 2) loaded.subList(0, MAX_LINES).clear();
            }
        } catch (Throwable ignored) {}
        fileBytes = logFile.length();
        synchronized (LOCK) {
            // Lines appended while we were loading are newer: keep them after the old ones.
            for (int i = loaded.size() - 1; i >= 0 && LINES.size() < MAX_LINES; i--) {
                LINES.addFirst(loaded.get(i));
            }
        }
    }

    public static void append(String line) {
        if (line == null || line.isEmpty()) return;
        String time = TS.get().format(new Date());
        final String entry;
        synchronized (LOCK) {
            if (!initialized) return;
            if (line.equals(lastBody) && !LINES.isEmpty()) {
                lastBodyRepeat++;
                LINES.pollLast();
                LINES.addLast(lastBodyTime + " " + line + " (×" + lastBodyRepeat + ")");
                // Collapsing edits the last line in place: rewrite the file, debounced.
                if (!rewritePending) {
                    rewritePending = true;
                    ioHandler.postDelayed(REWRITE_RUNNABLE, FLUSH_DELAY_MS);
                }
                return;
            }
            entry = time + " " + line;
            lastBody = line;
            lastBodyRepeat = 1;
            lastBodyTime = time;
            while (LINES.size() >= MAX_LINES) LINES.pollFirst();
            LINES.addLast(entry);
            if (rewritePending) return; // the pending rewrite will include this line
        }
        ioHandler.post(() -> appendToFile(entry));
    }

    private static void appendToFile(String entry) {
        if (logFile == null) return;
        if (fileBytes > MAX_FILE_BYTES) {
            rewriteFile();
            return;
        }
        try {
            if (writer == null) {
                writer = new BufferedWriter(new OutputStreamWriter(
                        new FileOutputStream(logFile, true), StandardCharsets.UTF_8));
            }
            writer.write(entry);
            writer.newLine();
            writer.flush();
            fileBytes += entry.length() + 1;
        } catch (Throwable t) {
            closeWriter();
        }
    }

    /** Replaces the file with the current buffer. The snapshot is taken under the lock; the
     *  write happens outside it, to a temp file that is renamed over the log. */
    private static void rewriteFile() {
        List<String> snapshot;
        synchronized (LOCK) {
            rewritePending = false;
            snapshot = new ArrayList<>(LINES);
        }
        if (logFile == null) return;
        closeWriter();
        File tmp = new File(logFile.getPath() + ".tmp");
        long bytes = 0;
        try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(tmp, false), StandardCharsets.UTF_8))) {
            for (String s : snapshot) {
                bw.write(s);
                bw.newLine();
                bytes += s.length() + 1;
            }
        } catch (Throwable t) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return;
        }
        if (tmp.renameTo(logFile)) fileBytes = bytes;
    }

    private static void closeWriter() {
        if (writer == null) return;
        try { writer.close(); } catch (Throwable ignored) {}
        writer = null;
    }

    public static void clear() {
        Handler io;
        synchronized (LOCK) {
            LINES.clear();
            lastBody = null;
            lastBodyRepeat = 1;
            lastBodyTime = null;
            rewritePending = false;
            io = ioHandler;
        }
        if (io != null) {
            io.removeCallbacks(REWRITE_RUNNABLE);
            io.post(() -> {
                closeWriter();
                fileBytes = 0;
                if (logFile != null && logFile.exists()) {
                    try { //noinspection ResultOfMethodCallIgnored
                        logFile.delete();
                    } catch (Throwable ignored) {}
                }
            });
        }
    }

    public static String getSnapshot() {
        synchronized (LOCK) {
            return buildSnapshot(false);
        }
    }

    public static String getSnapshotForIpc() {
        synchronized (LOCK) {
            return buildSnapshot(true);
        }
    }

    private static String buildSnapshot(boolean forIpc) {
        if (LINES.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(LINES.size() * 96);
        for (String s : LINES) sb.append(s).append('\n');
        String snap = sb.toString();
        if (!forIpc || snap.length() <= IPC_MAX_CHARS) return snap;
        return "(truncated — older lines omitted)\n\n" + snap.substring(snap.length() - IPC_MAX_CHARS + 40);
    }
}
