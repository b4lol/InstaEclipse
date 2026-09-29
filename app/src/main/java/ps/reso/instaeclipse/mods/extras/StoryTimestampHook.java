package ps.reso.instaeclipse.mods.extras;

import android.content.Context;
import android.text.format.DateFormat;
import android.text.format.DateUtils;

import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Date;

import ps.reso.instaeclipse.hook.HookBridge;
import ps.reso.instaeclipse.hook.MethodHook;
import ps.reso.instaeclipse.utils.core.DexKitCache;
import ps.reso.instaeclipse.utils.core.LazyDexKit;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.feature.FeatureStatusTracker;
import ps.reso.instaeclipse.utils.log.ModuleLog;

/**
 * Story header shows when the story was posted ("14:32 · 3h") instead of only the relative age.
 *
 * <p>{@code ReelItem}'s only {@code String (Context)} method formats the header age from the
 * item's taken-at seconds, read through a no-arg {@code long} getter on ReelItem (IG 447:
 * {@code ReelItem.A0j} calling {@code ReelItem.A06}). Both are resolved with DexKit; the hook
 * prefixes the original text with the local posting time.
 */
public class StoryTimestampHook {

    private static final String REEL_ITEM = "com.instagram.model.reels.ReelItem";
    private static final String CACHE_TAKEN_AT = "Extras_StoryTakenAt";

    public void install(LazyDexKit bridge, ClassLoader cl) {
        Method[] takenAt = new Method[1];
        Method header = ExtrasLookup.method("Extras_StoryHeader", cl, () -> {
            for (MethodData md : bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                    .declaredClass(REEL_ITEM).paramTypes("android.content.Context").returnType("java.lang.String")))) {
                for (MethodData inv : md.getInvokes()) {
                    if (REEL_ITEM.equals(inv.getDeclaredClassName()) && inv.getParamCount() == 0
                            && "long".equals(inv.getReturnTypeName())) {
                        takenAt[0] = inv.getMethodInstance(cl);
                        DexKitCache.saveMethod(CACHE_TAKEN_AT, takenAt[0]);
                        return md.getMethodInstance(cl);
                    }
                }
            }
            return null;
        });
        if (header == null) return;
        Method getter = takenAt[0] != null ? takenAt[0] : DexKitCache.loadMethod(CACHE_TAKEN_AT, cl);
        if (getter == null || Modifier.isStatic(getter.getModifiers())) return;
        getter.setAccessible(true);

        HookBridge.hookMethod(header, new MethodHook() {
            @Override
            protected boolean isActive() {
                return FeatureFlags.storyExactTime;
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Object original = param.getResult();
                if (!(original instanceof String) || !(param.args[0] instanceof Context ctx)) return;
                long seconds = (long) getter.invoke(param.thisObject);
                if (seconds <= 0) return;
                param.setResult(format(ctx, seconds * 1000L) + " · " + original);
            }
        });
        FeatureStatusTracker.setHooked("StoryExactTime");
        ModuleLog.line("(InstaEclipse | StoryTime): hooked " + header.getName() + " / " + getter.getName());
    }

    /** "14:32" for today, "12 Sep 14:32" otherwise; 12/24h follows the system setting. */
    static String format(Context ctx, long millis) {
        String time = DateFormat.getTimeFormat(ctx).format(new Date(millis));
        if (DateUtils.isToday(millis)) return time;
        return DateUtils.formatDateTime(ctx, millis, DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_NO_YEAR
                | DateUtils.FORMAT_ABBREV_MONTH) + " " + time;
    }
}
