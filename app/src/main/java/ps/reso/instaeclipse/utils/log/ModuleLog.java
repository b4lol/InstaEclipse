package ps.reso.instaeclipse.utils.log;

import android.util.Log;

/**
 * Logging used across the hooks: prefixes the caller's class/method/line, logs to logcat, and
 * appends to Logging's in-memory ring buffer so the message shows up in InstaEclipse's in-app
 * log viewer, not just adb logcat.
 *
 * Uses android.util.Log rather than the framework log ({@code HookBridge.log}) so it works
 * identically in the companion app's own (un-hooked) process.
 */
public final class ModuleLog {

    private static final String TAG = "InstaEclipse";

    /** Verbose/debug logging. Off by default so per-event PROBE/DEBUG chatter doesn't ship in
     *  release. Normal status lines (line()) always log. Also enables the
     *  {@code [Class.method:line]} caller prefix, which needs a stack walk per line. */
    public static volatile boolean verbose = false;

    private ModuleLog() {}

    /** Debug-only line: logs nothing unless {@link #verbose} is on. Use for high-frequency or
     *  sensitive PROBE/DEBUG output so it stays out of release logs by default. */
    public static void probe(String msg) {
        if (verbose) line(msg);
    }

    private static String getCallerInfo() {
        // Thread.getStackTrace() is expensive on ART and line() runs inside hooks; almost every
        // message already carries its own "(IE|Tag)" prefix, so only resolve the caller when
        // debugging.
        if (!verbose) return "";
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (int i = 2; i < stack.length; i++) {
            String cn = stack[i].getClassName();
            if (!cn.equals(ModuleLog.class.getName()) && !cn.equals(Logging.class.getName())
                    && !cn.equals(Thread.class.getName()) && !cn.startsWith("ps.reso.instaeclipse.hook.")) {
                String simpleName = cn.substring(cn.lastIndexOf('.') + 1);
                return "[" + simpleName + "." + stack[i].getMethodName() + ":" + stack[i].getLineNumber() + "] ";
            }
        }
        return "";
    }

    public static void line(String msg) {
        String formatted = getCallerInfo() + msg;
        Logging.append(formatted);
        Log.i(TAG, formatted);
    }

    public static void line(String msg, Throwable t) {
        String stackTraceStr = t != null ? Log.getStackTraceString(t) : "";
        String fullMsg = msg + (stackTraceStr.isEmpty() ? "" : "\n" + stackTraceStr);
        String formatted = getCallerInfo() + fullMsg;
        Logging.append(formatted);
        Log.i(TAG, formatted);
    }
}
