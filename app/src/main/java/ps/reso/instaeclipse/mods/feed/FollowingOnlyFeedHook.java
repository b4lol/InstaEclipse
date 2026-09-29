package ps.reso.instaeclipse.mods.feed;

import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import ps.reso.instaeclipse.hook.HookBridge;
import ps.reso.instaeclipse.hook.MethodHook;
import ps.reso.instaeclipse.utils.core.DexKitCache;
import ps.reso.instaeclipse.utils.core.LazyDexKit;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.feature.FeatureStatusTracker;
import ps.reso.instaeclipse.utils.log.ModuleLog;

/**
 * Limits the home feed to accounts the user follows by forcing the feed/timeline request's
 * {@code pagination_source} parameter to {@code "following"} (same server-side switch JTInstagram's
 * "Limit following feed" uses).
 *
 * <p>The feed request builder method (IG 447: {@code X.02yk.A01}) is found by its
 * "homecoming_all" + "X-IG-PRIMED-FEED-REQUEST" strings. It fills a generic request builder
 * (its 2nd parameter) through {@code (String, String)} adders — some add query/body params,
 * others headers. Every such adder is hooked, but only acts while the feed builder is on the
 * stack (ThreadLocal), so no other API request is touched. If IG skipped the parameter on this
 * code path, it is appended after the builder returns through an adder seen adding a param.
 */
public class FollowingOnlyFeedHook {

    private static final String KEY = "pagination_source";
    private static final String VALUE = "following";

    private static final String CACHE_BUILD = "FollowingFeed_build";
    private static final String CACHE_ADDERS = "FollowingFeed_adders";

    /** Per-call state; non-null only while the feed request builder is running. */
    private static final class CallState {
        boolean paramSeen;
        Method paramAdder;
    }

    private static final ThreadLocal<CallState> STATE = new ThreadLocal<>();

    private static final MethodHook BUILD_HOOK = new MethodHook() {
        @Override
        protected boolean isActive() {
            return FeatureFlags.followingOnlyFeed;
        }

        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            STATE.set(new CallState());
        }

        @Override
        protected void afterHookedMethod(MethodHookParam param) {
            CallState st = STATE.get();
            STATE.remove();
            if (st == null || st.paramSeen || st.paramAdder == null) return;
            if (param.args.length < 2 || param.args[1] == null) return;
            try {
                st.paramAdder.setAccessible(true);
                st.paramAdder.invoke(param.args[1], KEY, VALUE);
                ModuleLog.probe("(IE|FollowingFeed) pagination_source appended");
            } catch (Throwable t) {
                ModuleLog.line("(InstaEclipse | FollowingFeed): append failed: " + t);
            }
        }
    };

    private static final MethodHook ADDER_HOOK = new MethodHook() {
        @Override
        protected boolean isActive() {
            return FeatureFlags.followingOnlyFeed;
        }

        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            CallState st = STATE.get();
            if (st == null || !(param.args[0] instanceof String key)) return;
            if (KEY.equals(key)) {
                st.paramSeen = true;
                param.args[1] = VALUE;
                ModuleLog.probe("(IE|FollowingFeed) pagination_source forced");
            } else if (st.paramAdder == null && isParamKey(key)) {
                // Headers are "X-IG-…"-style; body/query params are lower_snake_case.
                st.paramAdder = (Method) param.method;
            }
        }
    };

    private static boolean isParamKey(String key) {
        if (key.isEmpty()) return false;
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (!(c == '_' || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9'))) return false;
        }
        return true;
    }

    public void install(LazyDexKit bridge, ClassLoader classLoader) {
        Method build = null;
        List<Method> adders = null;
        if (DexKitCache.isCacheValid()) {
            build = DexKitCache.loadMethod(CACHE_BUILD, classLoader);
            adders = DexKitCache.loadMethods(CACHE_ADDERS, classLoader);
            if ("missing".equals(DexKitCache.loadString(CACHE_BUILD))) {
                ModuleLog.line("(InstaEclipse | FollowingFeed): not available on this version (cached)");
                return;
            }
        }

        if (build == null || adders == null || adders.isEmpty()) {
            build = findBuildMethod(bridge, classLoader);
            if (build == null) {
                DexKitCache.saveString(CACHE_BUILD, "missing");
                ModuleLog.line("(InstaEclipse | FollowingFeed): feed request builder not found");
                return;
            }
            adders = findAdders(build);
            if (adders.isEmpty()) {
                ModuleLog.line("(InstaEclipse | FollowingFeed): no param adders on " + build.getParameterTypes()[1].getName());
                return;
            }
            DexKitCache.saveMethod(CACHE_BUILD, build);
            DexKitCache.saveMethods(CACHE_ADDERS, adders);
        }

        HookBridge.hookMethod(build, BUILD_HOOK);
        for (Method m : adders) HookBridge.hookMethod(m, ADDER_HOOK);
        FeatureStatusTracker.setHooked("FollowingOnlyFeed");
        ModuleLog.line("(InstaEclipse | FollowingFeed): hooked " + build.getDeclaringClass().getName()
                + "." + build.getName() + " + " + adders.size() + " adders");
    }

    private static Method findBuildMethod(LazyDexKit bridge, ClassLoader classLoader) {
        for (MethodData md : bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                .usingStrings("homecoming_all", "X-IG-PRIMED-FEED-REQUEST")))) {
            try {
                if (md.isConstructor() || md.isStaticInitializer()) continue;
                Method m = md.getMethodInstance(classLoader);
                if (m.getParameterCount() >= 2) return m;
            } catch (Throwable t) {
                ModuleLog.line("(InstaEclipse | FollowingFeed): " + t.getMessage());
            }
        }
        return null;
    }

    /** Every non-static {@code void (String, String)} method declared on the request builder. */
    private static List<Method> findAdders(Method build) {
        List<Method> out = new ArrayList<>();
        Class<?> builder = build.getParameterTypes()[1];
        for (Method m : builder.getDeclaredMethods()) {
            Class<?>[] p = m.getParameterTypes();
            if (m.getReturnType() == void.class && !Modifier.isStatic(m.getModifiers())
                    && p.length == 2 && p[0] == String.class && p[1] == String.class) {
                out.add(m);
            }
        }
        return out;
    }
}
