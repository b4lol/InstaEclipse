package ps.reso.instaeclipse.mods.extras;

import java.lang.reflect.*;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import ps.reso.instaeclipse.hook.*;
import ps.reso.instaeclipse.utils.core.LazyDexKit;
import ps.reso.instaeclipse.utils.feature.*;

/** Matches only the two optional Bloks onboarding pages, never Android permission APIs. */
public final class OnboardingPromptHook {
    public void install(LazyDexKit dex, ClassLoader cl) {
        for (Method m : dex.findMethodsCached("OnboardingPrompt_v1", cl, FindMethod.create()
                .matcher(MethodMatcher.create().usingStrings("BKDataFetcher.fetch")
                        .paramTypes("android.content.Context", "com.instagram.bloks.hosting.IgBloksScreenConfig")
                        .returnType("void")))) {
            if (Modifier.isStatic(m.getModifiers())) continue;
            java.util.List<Field> fields = new java.util.ArrayList<>();
            for (Field f : m.getDeclaringClass().getDeclaredFields())
                if (f.getType() == String.class && !Modifier.isStatic(f.getModifiers())) { f.setAccessible(true); fields.add(f); }
            HookBridge.hookMethod(m, new MethodHook() {
                @Override protected boolean isActive() { return FeatureFlags.hideOnboardingPrompts; }
                @Override protected void beforeHookedMethod(MethodHookParam p) throws Throwable {
                    for (Field field : fields) {
                        Object id = field.get(p.thisObject);
                        if ("com.bloks.www.bloks.ig.ndx.ci.entry.screen".equals(id)
                                || "com.bloks.www.bloks.ig.ndx.ls.entry.screen".equals(id)) {
                            p.setResult(null); return;
                        }
                    }
                }
            });
            FeatureStatusTracker.setHooked("HideOnboardingPrompts");
        }
    }
}
