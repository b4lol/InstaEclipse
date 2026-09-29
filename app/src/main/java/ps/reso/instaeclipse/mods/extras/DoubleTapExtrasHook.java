package ps.reso.instaeclipse.mods.extras;

import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import ps.reso.instaeclipse.hook.HookBridge;
import ps.reso.instaeclipse.hook.MethodHook;
import ps.reso.instaeclipse.utils.core.DexKitCache;
import ps.reso.instaeclipse.utils.core.LazyDexKit;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.log.ModuleLog;

/**
 * Extends "Disable double-tap to like" to comments and DMs.
 * <ul>
 *   <li>Comments: the comment rows' {@code onDoubleTap} listeners log "fb_comment_double_tap";
 *   they are made to consume the gesture without liking.</li>
 *   <li>DMs: every path that sends the double-tap reaction reads the configured double-tap emoji
 *   through one {@code String (UserSession, int)} getter (it falls back to "❤"). Its void callers
 *   are skipped when they run under an {@code onDoubleTap} frame, so reactions picked from the
 *   long-press menu still go through.</li>
 * </ul>
 */
public class DoubleTapExtrasHook {

    private static final MethodHook CONSUME = new MethodHook() {
        @Override
        protected boolean isActive() {
            return FeatureFlags.disableDoubleTapLike;
        }

        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            param.setResult(true);
        }
    };

    private static final MethodHook SKIP_UNDER_DOUBLE_TAP = new MethodHook() {
        @Override
        protected boolean isActive() {
            return FeatureFlags.disableDoubleTapLike;
        }

        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            for (StackTraceElement e : Thread.currentThread().getStackTrace()) {
                if ("onDoubleTap".equals(e.getMethodName()) || "onDoubleTapEvent".equals(e.getMethodName())) {
                    param.setResult(null);
                    return;
                }
            }
        }
    };

    public void install(LazyDexKit bridge, ClassLoader cl) {
        List<Method> comments = cached("Extras_DoubleTapComments", cl, () -> {
            List<Method> out = new ArrayList<>();
            for (MethodData md : bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                    .name("onDoubleTap").returnType("boolean").usingStrings("fb_comment_double_tap")))) {
                out.add(md.getMethodInstance(cl));
            }
            return out;
        });
        for (Method m : comments) HookBridge.hookMethod(m, CONSUME);

        List<Method> direct = cached("Extras_DoubleTapDirect", cl, () -> {
            List<Method> out = new ArrayList<>();
            for (MethodData getter : bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                    .returnType("java.lang.String").paramTypes("com.instagram.common.session.UserSession", "int")
                    .usingStrings("❤")))) {
                for (MethodData caller : getter.getCallers()) {
                    if (caller.isMethod() && "void".equals(caller.getReturnTypeName())) {
                        out.add(caller.getMethodInstance(cl));
                    }
                }
            }
            return out;
        });
        for (Method m : direct) HookBridge.hookMethod(m, SKIP_UNDER_DOUBLE_TAP);

        ModuleLog.line("(InstaEclipse | DoubleTapLike): comments " + comments.size() + ", DM senders " + direct.size());
    }

    private interface ListFinder {
        List<Method> find() throws Throwable;
    }

    private static List<Method> cached(String key, ClassLoader cl, ListFinder finder) {
        if (DexKitCache.isCacheValid()) {
            List<Method> hit = DexKitCache.loadMethods(key, cl);
            if (hit != null && !hit.isEmpty()) return hit;
            if ("missing".equals(DexKitCache.loadString(key))) return new ArrayList<>();
        }
        List<Method> found = new ArrayList<>();
        try {
            found = finder.find();
        } catch (Throwable t) {
            ModuleLog.line("(InstaEclipse | DoubleTapLike): " + key + " lookup failed: " + t);
        }
        if (found.isEmpty()) DexKitCache.saveString(key, "missing");
        else DexKitCache.saveMethods(key, found);
        return found;
    }
}
