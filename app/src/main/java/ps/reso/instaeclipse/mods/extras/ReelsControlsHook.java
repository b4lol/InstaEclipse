package ps.reso.instaeclipse.mods.extras;

import org.luckypray.dexkit.result.MethodData;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import ps.reso.instaeclipse.hook.HookBridge;
import ps.reso.instaeclipse.hook.HookHelpers;
import ps.reso.instaeclipse.hook.MethodHook;
import ps.reso.instaeclipse.utils.core.LazyDexKit;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.feature.FeatureStatusTracker;
import ps.reso.instaeclipse.utils.log.ModuleLog;

/**
 * Reels viewer controls, all keyed on IG's own "android_purge_…" markers (stable across builds):
 * <ul>
 *   <li>Disable tap-to-pause: the tap-to-pause gate {@code (UserSession) boolean} is the first
 *   static session check in {@code AudioIconUtilPluginImpl.configureMuteOrPauseIconImageView}
 *   (IG 447: {@code X.04dj.A08}); forced false, a tap mutes instead of pausing.</li>
 *   <li>Auto-scroll: IG's opt-in auto-scroll gate
 *   {@code ClipsViewerExperimentUtil.isOptInAutoscrollEnabled} (IG 447: {@code X.0AEd.A04}) is
 *   forced true, which unlocks IG's native "Auto scroll" option in the Reels ⋯ menu.</li>
 *   <li>Lock scrolling: {@code ClipsViewPagerImpl.getViewAtIndex} runs on every page bind; it
 *   toggles the pager's ViewPager2 user input, and pull-to-refresh is disabled with it.</li>
 * </ul>
 */
public class ReelsControlsHook {

    public void install(LazyDexKit bridge, ClassLoader cl) {
        installTapPause(bridge, cl);
        installAutoScroll(bridge, cl);
        installScrollLock(bridge, cl);
    }

    private void installTapPause(LazyDexKit bridge, ClassLoader cl) {
        Method gate = ExtrasLookup.method("Extras_ReelsTapPause", cl, () -> {
            for (MethodData owner : ExtrasLookup.byStrings(bridge,
                    "android_purge_26_q2_AudioIconUtilPluginImpl_configureMuteOrPauseIconImageView")) {
                Method m = ExtrasLookup.firstStaticInvoke(owner, cl,
                        c -> c.getReturnType() == boolean.class && ExtrasLookup.takesSession(c));
                if (m != null) return m;
            }
            return null;
        });
        if (gate == null) return;
        HookBridge.hookMethod(gate, new MethodHook() {
            @Override
            protected boolean isActive() {
                return FeatureFlags.reelsDisableTapPause;
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                param.setResult(false);
            }
        });
        FeatureStatusTracker.setHooked("ReelsDisableTapPause");
        ModuleLog.line("(InstaEclipse | Reels): tap-pause gate " + gate.getDeclaringClass().getName() + "." + gate.getName());
    }

    private void installAutoScroll(LazyDexKit bridge, ClassLoader cl) {
        Method gate = ExtrasLookup.method("Extras_ReelsAutoScroll", cl, () -> {
            for (MethodData md : ExtrasLookup.byStrings(bridge,
                    "android_purge_26_q2_ClipsViewerExperimentUtil_isOptInAutoscrollEnabled")) {
                if ("boolean".equals(md.getReturnTypeName())) return md.getMethodInstance(cl);
            }
            return null;
        });
        if (gate == null) return;
        HookBridge.hookMethod(gate, new MethodHook() {
            @Override
            protected boolean isActive() {
                return FeatureFlags.reelsAutoScroll;
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                param.setResult(true);
            }
        });
        FeatureStatusTracker.setHooked("ReelsAutoScroll");
        ModuleLog.line("(InstaEclipse | Reels): auto-scroll gate " + gate.getDeclaringClass().getName() + "." + gate.getName());
    }

    private void installScrollLock(LazyDexKit bridge, ClassLoader cl) {
        Method bind = ExtrasLookup.method("Extras_ReelsPagerBind", cl, () -> {
            for (MethodData md : ExtrasLookup.byStrings(bridge, "android_purge_26_q3_ClipsViewPagerImpl_getViewAtIndex")) {
                if (!md.isConstructor()) return md.getMethodInstance(cl);
            }
            return null;
        });
        Field pager = bind == null ? null : viewPagerField(bind.getDeclaringClass());
        if (pager != null) {
            Method setInput;
            try {
                setInput = pager.getType().getMethod("setUserInputEnabled", boolean.class);
            } catch (NoSuchMethodException e) {
                ModuleLog.line("(InstaEclipse | Reels): ViewPager2.setUserInputEnabled missing");
                return;
            }
            HookBridge.hookMethod(bind, new MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Object vp = pager.get(param.thisObject);
                    if (vp != null) setInput.invoke(vp, !FeatureFlags.reelsLockScroll);
                }
            });
            FeatureStatusTracker.setHooked("ReelsLockScroll");
            ModuleLog.line("(InstaEclipse | Reels): pager " + bind.getDeclaringClass().getName() + "." + pager.getName());
        }

        // Pull-to-refresh on top of the Reels pager would still reload the feed.
        try {
            Class<?> refresh = HookHelpers.findClass("instagram.features.clips.viewer.ui.ClipsSwipeRefreshLayout", cl);
            HookHelpers.findAndHookMethod(refresh, "onInterceptTouchEvent", android.view.MotionEvent.class, new MethodHook() {
                @Override
                protected boolean isActive() {
                    return FeatureFlags.reelsLockScroll;
                }

                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    param.setResult(false);
                }
            });
        } catch (Throwable t) {
            ModuleLog.line("(InstaEclipse | Reels): refresh layout hook failed: " + t);
        }
    }

    private static Field viewPagerField(Class<?> c) {
        for (Field f : c.getDeclaredFields()) {
            if (f.getType().getName().equals("androidx.viewpager2.widget.ViewPager2")) {
                f.setAccessible(true);
                return f;
            }
        }
        return null;
    }
}
