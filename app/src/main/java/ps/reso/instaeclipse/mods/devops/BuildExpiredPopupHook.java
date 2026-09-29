package ps.reso.instaeclipse.mods.devops;

import ps.reso.instaeclipse.utils.core.LazyDexKit;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;

import java.lang.reflect.Method;
import java.util.List;

import ps.reso.instaeclipse.hook.MethodHook;
import ps.reso.instaeclipse.hook.HookBridge;
import ps.reso.instaeclipse.utils.core.DexKitCache;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.feature.FeatureStatusTracker;
import ps.reso.instaeclipse.utils.log.ModuleLog;

public class BuildExpiredPopupHook {

    private static final String CACHE_SHOW  = "BuildExpiredShow";
    private static final String CACHE_CHECK = "BuildExpiredCheck";

    public void install(LazyDexKit bridge, ClassLoader classLoader) {

        // No-op the method that shows the popup — blocks all three internal paths:
        //   1. lockout_active pref = true  → shows immediately
        //   2. snooze expired              → shows via snooze dialog
        //   3. age threshold exceeded      → shows force-update dialog
        MethodHook noOpHook = new MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (FeatureFlags.removeBuildExpiredPopup) {
                    param.setResult(null); // void return → method becomes no-op
                }
            }
        };

        // Secondary defence: hook the snooze-expired boolean check.
        // Returns false so even if the show method is not found, the snooze
        // check keeps reporting "not expired".
        MethodHook falseHook = new MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (FeatureFlags.removeBuildExpiredPopup) {
                    param.setResult(false);
                }
            }
        };

        boolean hookedMain = false;

        // ── Cache path ────────────────────────────────────────────────────────
        if (DexKitCache.isCacheValid()) {
            Method show = DexKitCache.loadMethod(CACHE_SHOW, classLoader);
            if (show != null) {
                HookBridge.hookMethod(show, noOpHook);
                FeatureStatusTracker.setHooked("RemoveBuildExpiredPopup");
                hookedMain = true;
            }
            Method check = DexKitCache.loadMethod(CACHE_CHECK, classLoader);
            if (check != null) {
                HookBridge.hookMethod(check, falseHook);
            }
            if (hookedMain) return;
        }

        // ── DexKit path ───────────────────────────────────────────────────────
        try {
            // Primary: find the show-popup method via "lockout_active" string.
            List<MethodData> showMethods = bridge.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create()
                            .usingStrings("lockout_active")
                            .returnType("void")));

            for (MethodData md : showMethods) {
                Method method;
                try { method = md.getMethodInstance(classLoader); } catch (Throwable e) { continue; }

                Class<?>[] params = method.getParameterTypes();
                if (params.length < 1) continue;
                // Must take FragmentActivity as first arg; skip boolean-only variants
                if (!params[0].getName().contains("FragmentActivity")) continue;

                HookBridge.hookMethod(method, noOpHook);
                DexKitCache.saveMethod(CACHE_SHOW, method);
                ModuleLog.line("(IE|BuildExpired) ✅ hooked show-popup → "
                        + md.getClassName() + "." + md.getName());
                FeatureStatusTracker.setHooked("RemoveBuildExpiredPopup");
                hookedMain = true;
                break;
            }

            if (!hookedMain) {
                ModuleLog.line("(IE|BuildExpired) ⚠️ show-popup method not found, falling back to boolean hook only");
            }

            // Secondary: hook the snooze-expired boolean check
            List<MethodData> checkMethods = bridge.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create()
                            .usingStrings("snooze_expiration_lockout_manager")
                            .returnType("boolean")));

            for (MethodData md : checkMethods) {
                Method method;
                try { method = md.getMethodInstance(classLoader); } catch (Throwable e) { continue; }

                HookBridge.hookMethod(method, falseHook);
                DexKitCache.saveMethod(CACHE_CHECK, method);
                ModuleLog.line("(IE|BuildExpired) ✅ hooked snooze-check → "
                        + md.getClassName() + "." + md.getName());
                if (!hookedMain) {
                    FeatureStatusTracker.setHooked("RemoveBuildExpiredPopup");
                }
                break;
            }

        } catch (Throwable t) {
            ModuleLog.line("(IE|BuildExpired) ❌ install: " + t);
        }
    }
}
