package ps.reso.instaeclipse.mods.extras;

import android.app.Activity;
import android.content.Intent;

import java.lang.reflect.Method;

import ps.reso.instaeclipse.hook.HookBridge;
import ps.reso.instaeclipse.hook.MethodHook;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.feature.FeatureStatusTracker;
import ps.reso.instaeclipse.utils.log.ModuleLog;

/**
 * Opens Instagram on the chosen tab. The main activity picks its first tab from intent extras
 * holding a tab enum name (FEED, CLIPS, DIRECT, SEARCH, PROFILE, NEWS): the entrance-module extra
 * on a normal launch, {@code MainActivityAccountHelper.STARTUP_TAB} after an account switch. Both
 * are set on a fresh launch unless IG set one itself; IG falls back to its default tab when the
 * chosen one is not on the bottom bar.
 */
public class StartupTabHook {

    /** Read only on the account-switch path. */
    static final String EXTRA = "MainActivityAccountHelper.STARTUP_TAB";
    /** Read on a normal launch (X.00VB.A06 in IG 447) when no cold-start flag picks a tab. */
    static final String ENTRANCE_EXTRA = "INSTAGRAM_MAIN_ACTIVITY_INTENT_ENTRANCE_MODULE_EXTRA_FIELD";
    /** Marks a launch intent this hook rewrote. */
    static final String MARK = "ie_startup_tab";
    private static final String APPLIED = "ie_startup_tab_applied";
    static final String[] TABS = {"", "FEED", "CLIPS", "DIRECT", "SEARCH", "PROFILE", "NEWS"};

    /** Launcher alias; the activity behind it is {@link #MAIN_CLASS}. */
    private static final String MAIN = "com.instagram.android.activity.MainTabActivity";
    private static final String MAIN_CLASS = "com.instagram.mainactivity.InstagramMainActivity";

    private static boolean isMainActivity(Activity activity) {
        String cls = activity.getClass().getName();
        if (MAIN_CLASS.equals(cls) || MAIN.equals(cls)) return true;
        android.content.ComponentName cn = activity.getComponentName();
        return cn != null && MAIN.equals(cn.getClassName());
    }

    public void install(ClassLoader cl) {
        // The main activity lives in a secondary dex that isn't loaded yet when hooks install,
        // so hook the framework's activity-creation entry point and match it by name.
        MethodHook hook = new MethodHook() {
            @Override
            protected boolean isActive() {
                return !FeatureFlags.startupTab.isEmpty();
            }

            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!(param.args[0] instanceof Activity activity)) return;
                if (param.args.length > 1 && param.args[1] != null) return; // recreated: keep the user's tab
                if (!isMainActivity(activity)) return;
                Intent intent = activity.getIntent();
                if (intent == null || intent.hasExtra(EXTRA) || intent.hasExtra(ENTRANCE_EXTRA)) return;
                if (!Intent.ACTION_MAIN.equals(intent.getAction())) return; // deep links pick their own tab
                intent.putExtra(EXTRA, FeatureFlags.startupTab);
                intent.putExtra(ENTRANCE_EXTRA, FeatureFlags.startupTab);
                intent.putExtra(MARK, true);
            }
        };
        int hooked = 0;
        for (Method m : android.app.Instrumentation.class.getDeclaredMethods()) {
            if (m.getName().equals("callActivityOnCreate")) {
                HookBridge.hookMethod(m, hook);
                hooked++;
            }
        }
        if (hooked == 0) {
            ModuleLog.line("(InstaEclipse | StartupTab): callActivityOnCreate not found");
            return;
        }
        // During onCreate IG puts "is_cold_start_feed"/"is_cold_start_reel_tab" on the launch
        // intent, and those win over the entrance extra; hide them on intents we rewrote.
        try {
            java.util.Set<String> coldStart = new java.util.HashSet<>(
                    java.util.Arrays.asList("is_cold_start_feed", "is_cold_start_reel_tab"));
            HookBridge.hookMethod(Intent.class.getDeclaredMethod("getBooleanExtra", String.class, boolean.class),
                    new MethodHook() {
                        @Override
                        protected boolean isActive() {
                            return !FeatureFlags.startupTab.isEmpty();
                        }

                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (coldStart.contains(param.args[0]) && ((Intent) param.thisObject).hasExtra(MARK)) {
                                param.setResult(false);
                            }
                        }
                    });
        } catch (NoSuchMethodException e) {
            ModuleLog.line("(InstaEclipse | StartupTab): Intent.getBooleanExtra missing");
        }
        // On IG 447 the extras are read while the tab controller is still null, so the pick is
        // dropped; switch tabs through the controller once the activity first resumes.
        for (Method m : android.app.Instrumentation.class.getDeclaredMethods()) {
            if (!m.getName().equals("callActivityOnResume")) continue;
            HookBridge.hookMethod(m, new MethodHook() {
                @Override
                protected boolean isActive() {
                    return !FeatureFlags.startupTab.isEmpty();
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!(param.args[0] instanceof Activity activity) || !isMainActivity(activity)) return;
                    Intent intent = activity.getIntent();
                    if (intent == null || !intent.hasExtra(MARK) || intent.hasExtra(APPLIED)) return;
                    intent.putExtra(APPLIED, true);
                    String tab = FeatureFlags.startupTab;
                    activity.getWindow().getDecorView().post(() -> switchTab(activity, tab));
                }
            });
        }
        FeatureStatusTracker.setHooked("StartupTab");
        ModuleLog.line("(InstaEclipse | StartupTab): hooked Instrumentation.callActivityOnCreate x" + hooked);
    }

    /**
     * Calls the main activity's tab controller: a field whose type declares
     * {@code void (TabEnum, String, boolean)} (IG 447: {@code X.00ZO.A0R}), with the tab enum
     * resolved from that parameter type by name.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void switchTab(Activity activity, String tab) {
        for (Class<?> c = activity.getClass(); c != null && c != Activity.class; c = c.getSuperclass()) {
            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                Class<?> type = f.getType();
                if (type.isPrimitive() || type.getName().startsWith("android.") || type.getName().startsWith("java.")) continue;
                for (Method m : type.getDeclaredMethods()) {
                    Class<?>[] p = m.getParameterTypes();
                    if (m.getReturnType() != void.class || p.length != 3 || !p[0].isEnum()
                            || p[1] != String.class || p[2] != boolean.class) continue;
                    try {
                        Object value = Enum.valueOf((Class<? extends Enum>) p[0], tab);
                        f.setAccessible(true);
                        Object controller = f.get(activity);
                        if (controller == null) continue;
                        m.setAccessible(true);
                        m.invoke(controller, value, null, false);
                        ModuleLog.line("(InstaEclipse | StartupTab): switched to " + tab);
                        return;
                    } catch (IllegalArgumentException notThisEnum) {
                        // enum without that constant: not the tab enum
                    } catch (Throwable t) {
                        ModuleLog.line("(InstaEclipse | StartupTab): switch failed: " + t);
                        return;
                    }
                }
            }
        }
        ModuleLog.line("(InstaEclipse | StartupTab): tab controller not found");
    }
}
