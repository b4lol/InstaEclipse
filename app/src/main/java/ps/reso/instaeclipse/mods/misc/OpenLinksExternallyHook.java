package ps.reso.instaeclipse.mods.misc;

import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import ps.reso.instaeclipse.hook.HookBridge;
import ps.reso.instaeclipse.hook.MethodHook;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.feature.FeatureStatusTracker;
import ps.reso.instaeclipse.utils.log.ModuleLog;

/**
 * Opens web links in the user's default browser instead of Instagram's in-app browser.
 *
 * <p>Every activity launch goes through {@link Instrumentation#execStartActivity}, so hooking
 * it catches the in-app browser whatever IG code path starts it (no obfuscated names). The
 * browser intent is swapped for a plain {@code ACTION_VIEW}; Instagram's {@code l.instagram.com}
 * click-tracking redirect is unwrapped first. Links to Meta's own domains stay in-app, since
 * login / account-center / payment flows rely on the in-app browser's callbacks.
 */
public class OpenLinksExternallyHook {

    private static final String[] IAB_ACTIVITIES = {
            "com.instagram.inappbrowser.fragments.BrowserLiteInMainProcessIGActivity",
            "com.facebook.browser.lite.BrowserLiteActivity",
    };

    private static final String[] META_HOSTS = {
            "instagram.com", "facebook.com", "meta.com", "threads.net", "threads.com",
            "fbsbx.com", "whatsapp.com",
    };

    private static final MethodHook HOOK = new MethodHook() {
        @Override
        protected boolean isActive() {
            return FeatureFlags.openLinksExternally;
        }

        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            for (int i = 0; i < param.args.length; i++) {
                if (param.args[i] instanceof Intent intent) {
                    Intent external = toExternal(intent);
                    if (external != null) param.args[i] = external;
                    return;
                }
            }
        }
    };

    public void install() {
        HookBridge.hookAllMethods(Instrumentation.class, "execStartActivity", HOOK);
        FeatureStatusTracker.setHooked("OpenLinksExternally");
    }

    static Intent toExternal(Intent intent) {
        ComponentName cn = intent.getComponent();
        if (cn == null || !isInAppBrowser(cn.getClassName())) return null;

        Uri uri = unwrapRedirect(findUrl(intent));
        if (uri == null || isMetaHost(uri.getHost())) return null;

        ModuleLog.probe("(IE|ExtLinks) redirecting " + uri.getHost());
        return new Intent(Intent.ACTION_VIEW, uri)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    private static boolean isInAppBrowser(String className) {
        for (String s : IAB_ACTIVITIES) if (s.equals(className)) return true;
        return false;
    }

    /** The IAB takes its URL as intent data; older builds passed it in an extra instead. */
    private static Uri findUrl(Intent intent) {
        Uri data = intent.getData();
        if (isWeb(data)) return data;
        Bundle extras = intent.getExtras();
        if (extras == null) return null;
        try {
            for (String k : extras.keySet()) {
                Object v = extras.get(k);
                if (v instanceof String s && (s.startsWith("https://") || s.startsWith("http://"))) {
                    return Uri.parse(s);
                }
            }
        } catch (Throwable ignored) {
            // BadParcelableException on an extra of an unknown class — leave the launch alone.
        }
        return null;
    }

    /** {@code https://l.instagram.com/?u=<encoded target>&e=…} → the target itself. */
    static Uri unwrapRedirect(Uri uri) {
        if (uri == null) return null;
        String host = uri.getHost();
        if (host != null && (host.equals("l.instagram.com") || host.equals("l.facebook.com"))) {
            try {
                String target = uri.getQueryParameter("u");
                if (target != null) {
                    Uri t = Uri.parse(target);
                    if (isWeb(t)) return t;
                }
            } catch (Throwable ignored) {
                // opaque / malformed URI — keep the original
            }
        }
        return uri;
    }

    private static boolean isWeb(Uri uri) {
        if (uri == null) return false;
        String scheme = uri.getScheme();
        return "https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme);
    }

    static boolean isMetaHost(String host) {
        if (host == null) return true;
        host = host.toLowerCase(java.util.Locale.ROOT);
        for (String d : META_HOSTS) {
            if (host.equals(d) || host.endsWith("." + d)) return true;
        }
        return false;
    }
}
