package ps.reso.instaeclipse.hook;

import android.util.Log;

import java.lang.reflect.Executable;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.libxposed.api.XposedInterface;

/**
 * Entry point for installing hooks through the libxposed (API 101+) framework interface.
 * {@link ps.reso.instaeclipse.Xposed.Module} attaches the framework once per process.
 *
 * <p>On an API 102 framework every hook gets an ID made of its callback's class and identity.
 * The framework scopes IDs per module and per hooked method, so installing the same callback on
 * the same method twice (e.g. once from the DexKit cache and once from a fresh search) replaces the
 * first hook instead of stacking a second one, while distinct callbacks never replace each other.</p>
 */
public final class HookBridge {

    private static final String TAG = "InstaEclipse";

    private static volatile XposedInterface xposed;
    private static volatile int apiVersion;
    private static final AtomicInteger installed = new AtomicInteger();

    private HookBridge() {}

    /** Called once from the module entry point. */
    public static void attach(XposedInterface framework) {
        xposed = framework;
        // The API classes come from the framework at runtime (compileOnly), so this reports the
        // framework's own API level. Never read XposedInterface.LIB_API: javac inlines it.
        apiVersion = framework.getApiVersion();
    }

    /** Runtime libxposed API level of the attached framework (0 before attach). */
    public static int apiVersion() {
        return apiVersion;
    }


    /** "Vector 1.9 (API 102)" style description of the attached framework, for logs. */
    public static String describeFramework() {
        XposedInterface x = xposed;
        if (x == null) return "not attached";
        return x.getFrameworkName() + " " + x.getFrameworkVersion() + " (API " + apiVersion + ")";
    }

    static String hookId(MethodHook callback) {
        return callback.getClass().getName() + "#" + Integer.toHexString(System.identityHashCode(callback));
    }

    /** Number of hooks installed through this bridge in this process. */
    public static int installedHookCount() {
        return installed.get();
    }

    public static boolean isAttached() {
        return xposed != null;
    }

    /** The framework interface; throws if the module entry point hasn't run in this process. */
    public static XposedInterface framework() {
        XposedInterface x = xposed;
        if (x == null) throw new IllegalStateException("Xposed framework not attached");
        return x;
    }

    /** Hooks a single method or constructor. */
    public static XposedInterface.HookHandle hookMethod(Member member, MethodHook callback) {
        if (!(member instanceof Executable)) {
            throw new IllegalArgumentException("Only methods and constructors can be hooked: " + member);
        }
        XposedInterface.HookBuilder builder = framework().hook((Executable) member)
                .setPriority(callback.priority);
        if (apiVersion >= XposedInterface.API_102) builder.setId(hookId(callback));
        XposedInterface.HookHandle handle = builder.intercept(callback::intercept);
        installed.incrementAndGet();
        return handle;
    }

    /** Hooks every method declared by {@code clazz} (not inherited) with the given name. */
    public static Set<XposedInterface.HookHandle> hookAllMethods(Class<?> clazz, String methodName,
                                                                  MethodHook callback) {
        Set<XposedInterface.HookHandle> handles = new HashSet<>();
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.getName().equals(methodName)) handles.add(hookMethod(m, callback));
        }
        return handles;
    }

    /** Writes to the framework log (falls back to logcat outside a hooked process). */
    public static void log(String message) {
        XposedInterface x = xposed;
        if (x != null) {
            x.log(Log.INFO, TAG, message);
        } else {
            Log.i(TAG, message);
        }
    }

    public static void log(Throwable t) {
        XposedInterface x = xposed;
        String msg = String.valueOf(t.getMessage());
        if (x != null) {
            x.log(Log.ERROR, TAG, msg, t);
        } else {
            Log.e(TAG, msg, t);
        }
    }

    static void logCallbackError(MethodHook hook, String phase, Throwable t) {
        XposedInterface x = xposed;
        String msg = "Hook " + hook.getClass().getName() + " threw in " + phase + " callback";
        if (x != null) {
            x.log(Log.ERROR, TAG, msg, t);
        } else {
            Log.e(TAG, msg, t);
        }
    }
}
