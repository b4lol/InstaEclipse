package ps.reso.instaeclipse;

import android.app.Application;

import ps.reso.instaeclipse.utils.core.RemotePrefs;

/** Companion-app process setup. Not used inside Instagram (the module has its own entry). */
public class InstaEclipseApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // The framework hands us its service binder through libxposed's XposedProvider.
        RemotePrefs.init(this);
    }
}
