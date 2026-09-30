package ps.reso.instaeclipse.mods.ghost;

import ps.reso.instaeclipse.utils.core.LazyDexKit;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.ClassDataList;
import org.luckypray.dexkit.result.MethodData;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

import ps.reso.instaeclipse.hook.MethodHook;
import ps.reso.instaeclipse.hook.HookBridge;
import ps.reso.instaeclipse.Xposed.Module;
import ps.reso.instaeclipse.utils.core.DexKitCache;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.feature.FeatureStatusTracker;
import ps.reso.instaeclipse.utils.log.ModuleLog;

/**
 * Handles Ghost Mode for Direct Messages (DM) in Instagram.
 */
public class GhostDMSeenHook {
    public void handleSeenBlock(LazyDexKit bridge) {
        boolean verified = false;
        try {
            android.content.Context ctx = ps.reso.instaeclipse.hook.HostApp.get();
            verified = ctx != null && "447.0.0.21.81".equals(ctx.getPackageManager()
                    .getPackageInfo(ctx.getPackageName(), 0).versionName);
        } catch (Exception ignored) {}
        final boolean verifiedThreadArgument = verified;
        MethodHook hook = new MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!FeatureFlags.isGhostSeen) return;
                // IG 447's mark_thread_seen- sender takes (UserSession, callback, threadId, itemId, token).
                // Unknown shapes remain blocked, never infer the current thread for a background request.
                Class<?>[] types = ((Method) param.method).getParameterTypes();
                boolean knownShape = verifiedThreadArgument && types.length == 5 && types[2] == String.class
                        && types[3] == String.class && types[4] == String.class
                        && types[0].getName().equals("com.instagram.common.session.UserSession");
                if (knownShape && param.args[2] instanceof String id && ReadReceiptExceptions.allows(id)) return;
                param.setResult(null);
            }
        };

        // Cache hit — skip DexKit
        if (DexKitCache.isCacheValid()) {
            Method cached = DexKitCache.loadMethod("GhostSeen", Module.hostClassLoader);
            if (cached != null) {
                HookBridge.hookMethod(cached, hook);
                ModuleLog.line("(InstaEclipse | GhostModeSeen): ✅ Hooked: " + cached.getDeclaringClass().getName() + "." + cached.getName());
                FeatureStatusTracker.setHooked("GhostSeen");
                return;
            }
        }

        try {
            // Step 1: Find all methods containing "mark_thread_seen-"
            List<MethodData> methods = bridge.findMethod(FindMethod.create()
                    .matcher(MethodMatcher.create().usingStrings("mark_thread_seen-")));

            if (methods.isEmpty()) {
                ModuleLog.line("(InstaEclipse | GhostModeSeen): ❌ No methods found using 'mark_thread_seen-'");
                return;
            }

            for (MethodData method : methods) {
                Method reflectMethod;
                try {
                    reflectMethod = method.getMethodInstance(Module.hostClassLoader);
                } catch (Throwable e) {
                    continue; // Skip methods that can't be resolved
                }

                int modifiers = reflectMethod.getModifiers();
                String returnType = String.valueOf(method.getReturnType());
                ClassDataList paramTypes = method.getParamTypes();

                // Step 2: Match: static final void method(?, ?, ?, ...)
                if (Modifier.isStatic(modifiers)
                        && Modifier.isFinal(modifiers)
                        && returnType.contains("void")
                        && paramTypes.size() >= 3) {

                    try {
                        DexKitCache.saveMethod("GhostSeen", reflectMethod);
                        HookBridge.hookMethod(reflectMethod, hook);

                        ModuleLog.line("(InstaEclipse | GhostModeSeen): ✅ Hooked: " +
                                method.getClassName() + "." + method.getName());
                        FeatureStatusTracker.setHooked("GhostSeen");
                        return;

                    } catch (Throwable e) {
                        ModuleLog.line("(InstaEclipse | GhostModeSeen): ❌ Hook error: " + e.getMessage());
                    }
                }
            }

        } catch (Throwable e) {
            ModuleLog.line("(InstaEclipse | GhostModeSeen): ❌ DexKit exception: " + e.getMessage());
        }
    }


}
