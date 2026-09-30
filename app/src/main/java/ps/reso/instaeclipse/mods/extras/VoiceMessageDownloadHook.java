package ps.reso.instaeclipse.mods.extras;

import android.app.Activity;
import android.app.AlertDialog;
import java.lang.reflect.*;
import java.util.*;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;
import ps.reso.instaeclipse.R;
import ps.reso.instaeclipse.hook.*;
import ps.reso.instaeclipse.mods.media.*;
import ps.reso.instaeclipse.utils.core.*;
import ps.reso.instaeclipse.utils.feature.*;
import ps.reso.instaeclipse.utils.i18n.I18n;

/** Resolves the exact long-pressed message and its audio path, never a recent/global URL. */
public final class VoiceMessageDownloadHook {
    private static final String CACHE = "VoiceMessagePath_v1";
    private final ThreadLocal<Frame> active = new ThreadLocal<>();
    private record Path(Method caller, Method resolver, Field audio, Method media, Field source, Method url) {}
    private static final class Frame {
        final Frame previous; final Activity activity;
        String url; boolean ambiguous;
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
            @Override protected boolean isActive() { return FeatureFlags.downloadVoiceMessages && active.get() != null; }
            @Override protected void afterHookedMethod(MethodHookParam p) throws Throwable {
                Frame frame = active.get();
                Object message = p.getResult();
                if (frame == null || message == null || !resolved.audio().getDeclaringClass().isInstance(message)) return;
                Object audio = resolved.audio().get(message);
                if (audio == null) return; // a non-voice message
                Object media = resolved.media().invoke(audio);
                Object source = media == null ? null : resolved.source().get(media);
                Object url = source == null ? null : resolved.url().invoke(source);
                if (!(url instanceof String value) || !DownloadRequestValidator.isAllowedMediaUrl(value)) return;
                if (frame.url != null && !frame.url.equals(value)) frame.ambiguous = true;
                frame.url = value;
            }
        });
        HookBridge.hookMethod(path.caller(), new MethodHook() {
            @Override protected boolean isActive() { return FeatureFlags.downloadVoiceMessages; }
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                active.set(new Frame(active.get(), activity(p.thisObject)));
            }
            @Override protected void afterHookedMethod(MethodHookParam p) {
                Frame frame = active.get();
                if (frame == null) return;
                if (frame.previous == null) active.remove(); else active.set(frame.previous);
                if (p.hasThrowable() || frame.ambiguous || frame.url == null || frame.activity == null) return;
                Activity ctx = frame.activity;
                ctx.runOnUiThread(() -> {
                    if (ctx.isFinishing() || ctx.isDestroyed()) return;
                    new AlertDialog.Builder(ctx).setTitle(I18n.t(ctx, R.string.ie_voice_download_title))
                            .setPositiveButton(I18n.t(ctx, R.string.ie_download), (d, w) -> MediaActions.saveAudio(ctx, frame.url))
                            .setNegativeButton(I18n.t(ctx, R.string.ie_instagram_options), null).show();
                });
            }
        });
        FeatureStatusTracker.setHooked("DownloadVoiceMessages");
    }

    static Activity activity(Object caller) {
        if (caller == null) return null;
        for (Field f : caller.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || !Activity.class.isAssignableFrom(f.getType())) continue;
            try { f.setAccessible(true); Object value = f.get(caller); if (value instanceof Activity a) return a; }
            catch (ReflectiveOperationException | RuntimeException ignored) {}
        }
        return null;
    }

    private static Path discover(LazyDexKit dex, ClassLoader cl) throws Throwable {
        List<MethodData> callers = dex.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                .usingStrings("DirectThreadFragment.showMessageActionDialog", "userSession").returnType("void")));
        List<MethodData> sources = dex.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                .usingStrings("prepare audio source link")));
        Set<Path> paths = new LinkedHashSet<>();
        for (MethodData caller : callers) for (MethodData invoke : caller.getInvokes()) {
            if (!invoke.isMethod()) continue;
            Method resolver;
            try { resolver = invoke.getMethodInstance(cl); } catch (Throwable missing) { continue; }
            Class<?>[] args = resolver.getParameterTypes();
            if (!Modifier.isStatic(resolver.getModifiers()) || args.length != 4 || args[1] != String.class
                    || args[2] != String.class || args[3] != boolean.class || resolver.getReturnType().isPrimitive()) continue;
            for (MethodData source : sources) for (var usedAudio : source.getUsingFields()) {
                Field audio;
                try { audio = usedAudio.getField().getFieldInstance(cl); } catch (Throwable missing) { continue; }
                if (audio.getDeclaringClass() != resolver.getReturnType() || Modifier.isStatic(audio.getModifiers())
                        || audio.getType().isPrimitive() || audio.getType() == Object.class || audio.getType() == String.class) continue;
                for (MethodData getMedia : source.getInvokes()) {
                    if (!getMedia.isMethod() || getMedia.getParamCount() != 0
                            || !getMedia.getDeclaredClassName().equals(audio.getType().getName())) continue;
                    Method media = getMedia.getMethodInstance(cl);
                    if (Modifier.isStatic(media.getModifiers()) || media.getReturnType().isPrimitive()) continue;
                    for (var usedUrl : source.getUsingFields()) {
                        Field urlField;
                        try { urlField = usedUrl.getField().getFieldInstance(cl); } catch (Throwable missing) { continue; }
                        if (urlField.getDeclaringClass() != media.getReturnType() || Modifier.isStatic(urlField.getModifiers())) continue;
                        for (MethodData getUrl : source.getInvokes()) {
                            if (!getUrl.isMethod() || getUrl.getParamCount() != 0
                                    || !"java.lang.String".equals(getUrl.getReturnTypeName())
                                    || !getUrl.getDeclaredClassName().equals(urlField.getType().getName())) continue;
                            Method url = getUrl.getMethodInstance(cl);
                            if (Modifier.isStatic(url.getModifiers())) continue;
                            audio.setAccessible(true); media.setAccessible(true); urlField.setAccessible(true); url.setAccessible(true);
                            paths.add(new Path(caller.getMethodInstance(cl), resolver, audio, media, urlField, url));
                        }
                    }
                }
            }
        }
        return paths.size() == 1 ? paths.iterator().next() : null;
    }

    private static void save(Path p) {
        DexKitCache.saveMethod(CACHE + "Caller", p.caller());
        DexKitCache.saveMethod(CACHE + "Resolver", p.resolver());
        DexKitCache.saveMethod(CACHE + "Media", p.media());
        DexKitCache.saveMethod(CACHE + "Url", p.url());
        DexKitCache.saveString(CACHE + "AudioField", p.audio().getDeclaringClass().getName() + "#" + p.audio().getName());
        DexKitCache.saveString(CACHE + "UrlField", p.source().getDeclaringClass().getName() + "#" + p.source().getName());
        DexKitCache.saveString(CACHE, "found");
    }
    private static Path load(ClassLoader cl) {
        if (!DexKitCache.isCacheValid() || !"found".equals(DexKitCache.loadString(CACHE))) return null;
        try {
            Method caller = DexKitCache.loadMethod(CACHE + "Caller", cl), resolver = DexKitCache.loadMethod(CACHE + "Resolver", cl);
            Method media = DexKitCache.loadMethod(CACHE + "Media", cl), url = DexKitCache.loadMethod(CACHE + "Url", cl);
            Field audio = field(DexKitCache.loadString(CACHE + "AudioField"), cl);
            Field source = field(DexKitCache.loadString(CACHE + "UrlField"), cl);
            if (caller == null || resolver == null || media == null || url == null) return null;
            media.setAccessible(true); url.setAccessible(true);
            return new Path(caller, resolver, audio, media, source, url);
        } catch (ReflectiveOperationException | RuntimeException missing) { return null; }
    }
    static Field field(String value, ClassLoader cl) throws ReflectiveOperationException {
        String[] parts = value.split("#", 2);
        Field field = cl.loadClass(parts[0]).getDeclaredField(parts[1]); field.setAccessible(true); return field;
    }
}
