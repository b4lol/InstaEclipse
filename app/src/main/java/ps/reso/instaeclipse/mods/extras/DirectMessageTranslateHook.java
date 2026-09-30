package ps.reso.instaeclipse.mods.extras;

import android.app.Activity;
import java.lang.reflect.*;
import java.util.*;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;
import ps.reso.instaeclipse.R;
import ps.reso.instaeclipse.hook.*;
import ps.reso.instaeclipse.mods.translate.TextTranslator;
import ps.reso.instaeclipse.utils.core.*;
import ps.reso.instaeclipse.utils.feature.*;
import ps.reso.instaeclipse.utils.i18n.I18n;

/**
 * Offers translation when a text DM is long-pressed. Uses the same message-action entry point as
 * {@link VoiceMessageDownloadHook}, so the text always belongs to the pressed message. The text
 * field is the one DirectMessage reads to decide whether a TEXT message is only an emoji.
 */
public final class DirectMessageTranslateHook {
    private static final String CACHE = "DirectMessageText_v1";
    private final ThreadLocal<Frame> active = new ThreadLocal<>();
    private record Path(Method caller, Method resolver, Field type, Field text) {}
    private static final class Frame {
        final Frame previous; final Activity activity;
        String text; boolean ambiguous;
        Frame(Frame previous, Activity activity) { this.previous = previous; this.activity = activity; }
    }

    public void install(LazyDexKit dex, ClassLoader cl) throws Throwable {
        Path path = load(cl);
        if (path == null) {
            if (DexKitCache.isCacheValid() && "missing".equals(DexKitCache.loadString(CACHE))) return;
            path = discover(dex, cl);
            if (path == null) { DexKitCache.saveString(CACHE, "missing"); return; }
            save(path);
        }
        Path resolved = path;
        HookBridge.hookMethod(path.resolver(), new MethodHook() {
            @Override protected boolean isActive() { return FeatureFlags.translateText && active.get() != null; }
            @Override protected void afterHookedMethod(MethodHookParam p) throws Throwable {
                Frame frame = active.get();
                Object message = p.getResult();
                if (frame == null || message == null || !resolved.text().getDeclaringClass().isInstance(message)) return;
                if (!(resolved.type().get(message) instanceof Enum<?> type) || !"TEXT".equals(type.name())) return;
                if (!(resolved.text().get(message) instanceof String value) || value.isBlank()) return;
                if (frame.text != null && !frame.text.equals(value)) frame.ambiguous = true;
                frame.text = value;
            }
        });
        HookBridge.hookMethod(path.caller(), new MethodHook() {
            @Override protected boolean isActive() { return FeatureFlags.translateText; }
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                active.set(new Frame(active.get(), VoiceMessageDownloadHook.activity(p.thisObject)));
            }
            @Override protected void afterHookedMethod(MethodHookParam p) {
                Frame frame = active.get();
                if (frame == null) return;
                if (frame.previous == null) active.remove(); else active.set(frame.previous);
                if (p.hasThrowable() || frame.ambiguous || frame.text == null || frame.activity == null) return;
                Activity ctx = frame.activity;
                ctx.runOnUiThread(() -> {
                    if (ctx.isFinishing() || ctx.isDestroyed()) return;
                    TextTranslator.show(ctx, frame.text, I18n.t(ctx, R.string.ie_instagram_options));
                });
            }
        });
        FeatureStatusTracker.setHooked("TranslateText");
    }

    private static Path discover(LazyDexKit dex, ClassLoader cl) throws Throwable {
        List<MethodData> callers = dex.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                .usingStrings("DirectThreadFragment.showMessageActionDialog", "userSession").returnType("void")));
        List<MethodData> emoji = dex.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                .usingStrings("DirectMessage.updateIsMessageEmoji")));
        if (emoji.size() != 1) return null;
        Set<Path> paths = new LinkedHashSet<>();
        for (MethodData caller : callers) for (MethodData invoke : caller.getInvokes()) {
            if (!invoke.isMethod()) continue;
            Method resolver;
            try { resolver = invoke.getMethodInstance(cl); } catch (Throwable missing) { continue; }
            Class<?>[] args = resolver.getParameterTypes();
            if (!Modifier.isStatic(resolver.getModifiers()) || args.length != 4 || args[1] != String.class
                    || args[2] != String.class || args[3] != boolean.class || resolver.getReturnType().isPrimitive()) continue;
            Field type = null, text = null;
            boolean unique = true;
            for (var used : emoji.get(0).getUsingFields()) {
                Field field;
                try { field = used.getField().getFieldInstance(cl); } catch (Throwable missing) { continue; }
                if (Modifier.isStatic(field.getModifiers())
                        || !field.getDeclaringClass().isAssignableFrom(resolver.getReturnType())) continue;
                if (field.getType() == String.class) {
                    unique &= text == null || text.equals(field);
                    text = field;
                } else if (field.getType().isEnum() && hasConstant(field.getType(), "TEXT")) {
                    unique &= type == null || type.equals(field);
                    type = field;
                }
            }
            if (!unique || type == null || text == null) continue;
            type.setAccessible(true); text.setAccessible(true);
            paths.add(new Path(caller.getMethodInstance(cl), resolver, type, text));
        }
        return paths.size() == 1 ? paths.iterator().next() : null;
    }

    private static boolean hasConstant(Class<?> enumType, String name) {
        for (Object constant : enumType.getEnumConstants()) if (((Enum<?>) constant).name().equals(name)) return true;
        return false;
    }

    private static void save(Path p) {
        DexKitCache.saveMethod(CACHE + "Caller", p.caller());
        DexKitCache.saveMethod(CACHE + "Resolver", p.resolver());
        DexKitCache.saveString(CACHE + "TypeField", p.type().getDeclaringClass().getName() + "#" + p.type().getName());
        DexKitCache.saveString(CACHE + "TextField", p.text().getDeclaringClass().getName() + "#" + p.text().getName());
        DexKitCache.saveString(CACHE, "found");
    }

    private static Path load(ClassLoader cl) {
        if (!DexKitCache.isCacheValid() || !"found".equals(DexKitCache.loadString(CACHE))) return null;
        try {
            Method caller = DexKitCache.loadMethod(CACHE + "Caller", cl), resolver = DexKitCache.loadMethod(CACHE + "Resolver", cl);
            if (caller == null || resolver == null) return null;
            Field type = VoiceMessageDownloadHook.field(DexKitCache.loadString(CACHE + "TypeField"), cl);
            Field text = VoiceMessageDownloadHook.field(DexKitCache.loadString(CACHE + "TextField"), cl);
            return new Path(caller, resolver, type, text);
        } catch (ReflectiveOperationException | RuntimeException missing) { return null; }
    }
}
