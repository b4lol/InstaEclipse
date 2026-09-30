package ps.reso.instaeclipse.mods.extras;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import ps.reso.instaeclipse.hook.*;
import ps.reso.instaeclipse.utils.feature.*;

public final class NotificationRegistrationHook {
    public void install() {
        if (Build.VERSION.SDK_INT < 31) return;
        HookHelpers.findAndHookMethod(PendingIntent.class, "getBroadcast", Context.class, int.class,
                Intent.class, int.class, new MethodHook() {
                    @Override protected boolean isActive() { return FeatureFlags.fixNotificationRegistration; }
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        if (!(p.args[2] instanceof Intent intent)
                                || !"com.google.example.invalidpackage".equals(intent.getPackage())) return;
                        int flags = (int) p.args[3];
                        if ((flags & (PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_MUTABLE)) == 0)
                            p.args[3] = flags | PendingIntent.FLAG_IMMUTABLE;
                    }
                });
        FeatureStatusTracker.setHooked("FixNotificationRegistration");
    }
}
