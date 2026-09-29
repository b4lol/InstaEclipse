package ps.reso.instaeclipse.utils.core;

import android.content.Context;
import android.content.SharedPreferences;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;
import ps.reso.instaeclipse.utils.log.ModuleLog;

/**
 * Framework-backed preferences shared between the companion app (writer) and the module
 * running inside Instagram (read-only). Replaces the legacy XSharedPreferences +
 * world-readable prefs file trick, which doesn't exist in libxposed API 101+.
 *
 * <p>Only what the module needs on a cold start, before any sync broadcast could reach it,
 * lives here: the SAF download folder.
 */
public final class RemotePrefs {

    /** Remote preference group name, used by both sides. */
    public static final String GROUP = "instaeclipse_shared";
    public static final String KEY_DOWNLOADER_URI = "downloaderCustomUri";
    public static final String KEY_DOWNLOADER_PATH = "downloaderCustomPath";

    private static final String CACHE_PREFS = "instaeclipse_cache";

    private static volatile XposedService service;
    private static Context appContext;

    private RemotePrefs() {}

    /** Companion side: call once from {@code Application.onCreate}. */
    public static void init(Context context) {
        appContext = context.getApplicationContext();
        XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
            @Override
            public void onServiceBind(XposedService s) {
                service = s;
                syncDownloaderFolder();
            }

            @Override
            public void onServiceDied(XposedService s) {
                if (service == s) service = null;
            }
        });
    }

    /** True when an Xposed framework service is connected to the companion app. */
    public static boolean isConnected() {
        return service != null;
    }

    /** Companion side: copies the download folder from the local cache to the framework. */
    public static void syncDownloaderFolder() {
        XposedService s = service;
        Context ctx = appContext;
        if (s == null || ctx == null) return;
        SharedPreferences cache = ctx.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE);
        try {
            s.getRemotePreferences(GROUP).edit()
                    .putString(KEY_DOWNLOADER_URI, cache.getString(KEY_DOWNLOADER_URI, ""))
                    .putString(KEY_DOWNLOADER_PATH, cache.getString(KEY_DOWNLOADER_PATH, ""))
                    .apply();
        } catch (Throwable t) {
            // UnsupportedOperationException: framework without remote-preference support.
            ModuleLog.line("(InstaEclipse | RemotePrefs): sync failed: " + t.getMessage());
        }
    }
}
