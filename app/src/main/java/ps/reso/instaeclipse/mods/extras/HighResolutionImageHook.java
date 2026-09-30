package ps.reso.instaeclipse.mods.extras;

import java.lang.reflect.Method;
import java.util.List;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import ps.reso.instaeclipse.hook.*;
import ps.reso.instaeclipse.utils.core.LazyDexKit;
import ps.reso.instaeclipse.utils.feature.*;

/** Selects a real host image candidate; never fabricates CDN URLs or changes global DPI. */
public final class HighResolutionImageHook {
    public void install(LazyDexKit dex, ClassLoader cl) {
        for (Method m : dex.findMethodsCached("HighResolutionImage_v1", cl, FindMethod.create()
                .matcher(MethodMatcher.create().returnType("com.instagram.model.mediasize.ExtendedImageUrl")
                        .paramTypes("java.lang.Integer", "java.util.List", "int")))) {
            Class<?> resultType = m.getReturnType();
            Method width, height;
            try { width = resultType.getMethod("getWidth"); height = resultType.getMethod("getHeight"); }
            catch (NoSuchMethodException missing) { continue; }
            HookBridge.hookMethod(m, new MethodHook() {
                @Override protected boolean isActive() { return FeatureFlags.highResolutionImages; }
                @Override protected void afterHookedMethod(MethodHookParam p) throws Throwable {
                    if (p.hasThrowable() || !(p.args[1] instanceof List<?> candidates)) return;
                    Object best = null; long area = 0;
                    for (Object candidate : candidates) {
                        if (!resultType.isInstance(candidate)) continue;
                        Object w = width.invoke(candidate), h = height.invoke(candidate);
                        if (!(w instanceof Number) || !(h instanceof Number)) continue;
                        long x = ((Number) w).longValue(), y = ((Number) h).longValue();
                        if (x <= 0 || y <= 0 || x > 65536 || y > 65536) continue;
                        if (x * y > area) { area = x * y; best = candidate; }
                    }
                    if (best != null) { p.setResult(best); FeatureStatusTracker.setHooked("HighResolutionImages"); }
                }
            });
        }
    }
}
