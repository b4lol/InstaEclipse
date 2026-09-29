package ps.reso.instaeclipse.hook;

import android.util.Log;

import java.lang.reflect.Executable;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import io.github.libxposed.api.XposedInterface;

/**
 * Entry point for installing hooks through the libxposed (API 101+) framework interface.
 * {@link ps.reso.instaeclipse.Xposed.Module} attaches the framework once per process.
 */
public final class HookBridge {

    private static final String TAG = "InstaEclipse";

    private static volatile XposedInterface xposed;

    private HookBridge() {}

    /** Called once from the module entry point. */
    public static void attach(XposedInterface framework) {
        xposed = framework;
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
        return framework().hook((Executable) member)
                .setPriority(callback.priority)
                .intercept(callback::intercept);
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
