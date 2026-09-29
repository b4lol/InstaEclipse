package ps.reso.instaeclipse.hook;

import android.annotation.SuppressLint;
import android.app.Application;

/**
 * The hooked app's {@link Application}. Replaces the legacy-only
 * {@code AndroidAppHelper.currentApplication()}.
 */
public final class HostApp {

    private static volatile Application application;

    private HostApp() {}

    /** Set from the {@code Application.attach} hook in the module entry point. */
    public static void set(Application app) {
        application = app;
    }

    /** The current application, or {@code null} before {@code Application.attach} ran. */
    @SuppressLint({"PrivateApi", "DiscouragedPrivateApi"})
    public static Application get() {
        Application app = application;
        if (app != null) return app;
        try {
            // Same source AndroidAppHelper used; only reached if called before attach.
            app = (Application) Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication").invoke(null);
            if (app != null) application = app;
            return app;
        } catch (Throwable t) {
            return null;
        }
    }
}
