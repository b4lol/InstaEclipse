package ps.reso.instaeclipse.mods.extras;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import ps.reso.instaeclipse.hook.HookBridge;
import ps.reso.instaeclipse.hook.HookHelpers;
import ps.reso.instaeclipse.hook.MethodHook;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.feature.FeatureStatusTracker;
import ps.reso.instaeclipse.utils.log.ModuleLog;

/**
 * "Airplane mode": keeps Instagram's realtime (MQTT) connection down while the feed, profiles and
 * other REST browsing keep working. With MQTT down there is no presence ("active now"), no typing
 * or live seen events and no in-app incoming calls; DMs are neither pushed in nor sent live.
 *
 * <p>{@code RealtimeClientManager} keeps its method names: {@code startMqttClient} is skipped
 * while the mode is on, and {@link #apply} stops or restarts every live manager the moment the
 * toggle changes, so no restart is needed. Incoming realtime events are dropped too.
 */
public class AirplaneModeHook {

    private static final String MANAGER = "com.instagram.realtimeclient.RealtimeClientManager";
    private static final List<WeakReference<Object>> MANAGERS = new ArrayList<>();

    private static Method start;
    private static volatile boolean loggedBlock;
    private static Method stop;

    public void install(ClassLoader cl) {
        Class<?> manager = HookHelpers.findClass(MANAGER, cl);
        try {
            start = manager.getDeclaredMethod("startMqttClient");
            stop = manager.getDeclaredMethod("stopMqttClient");
            start.setAccessible(true);
            stop.setAccessible(true);
        } catch (NoSuchMethodException e) {
            ModuleLog.line("(InstaEclipse | Airplane): MQTT start/stop methods missing");
            return;
        }

        MethodHook track = new MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                synchronized (MANAGERS) {
                    MANAGERS.add(new WeakReference<>(param.thisObject));
                }
            }
        };
        for (java.lang.reflect.Constructor<?> c : manager.getDeclaredConstructors()) {
            HookBridge.hookMethod(c, track);
        }

        HookBridge.hookMethod(start, new MethodHook() {
            @Override
            protected boolean isActive() {
                return FeatureFlags.airplaneMode;
            }

            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                param.setResult(null);
                if (!loggedBlock) {
                    loggedBlock = true;
                    ModuleLog.line("(InstaEclipse | Airplane): MQTT start blocked");
                }
            }
        });

        try {
            Class<?> handler = HookHelpers.findClass("com.instagram.realtimeclient.MainRealtimeEventHandler", cl);
            HookBridge.hookAllMethods(handler, "onRealtimeEvent", new MethodHook() {
                @Override
                protected boolean isActive() {
                    return FeatureFlags.airplaneMode;
                }

                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    param.setResult(null);
                }
            });
        } catch (Throwable t) {
            ModuleLog.line("(InstaEclipse | Airplane): event handler hook failed: " + t);
        }

        FeatureStatusTracker.setHooked("AirplaneMode");
        ModuleLog.line("(InstaEclipse | Airplane): hooked");
    }

    /** Applies the current {@link FeatureFlags#airplaneMode} to every live realtime manager. */
    public static void apply() {
        Method m = FeatureFlags.airplaneMode ? stop : start;
        if (m == null) return;
        List<Object> live = new ArrayList<>();
        synchronized (MANAGERS) {
            for (int i = MANAGERS.size() - 1; i >= 0; i--) {
                Object o = MANAGERS.get(i).get();
                if (o == null) MANAGERS.remove(i);
                else live.add(o);
            }
        }
        for (Object o : live) {
            try {
                m.invoke(o);
            } catch (Throwable t) {
                ModuleLog.line("(InstaEclipse | Airplane): " + m.getName() + " failed: " + t);
            }
        }
        ModuleLog.line("(InstaEclipse | Airplane): " + m.getName() + " on " + live.size() + " manager(s)");
    }
}
