package ps.reso.instaeclipse.mods.extras;

import android.text.format.DateUtils;
import java.lang.reflect.Method;
import java.time.ZoneId;
import java.util.Locale;
import ps.reso.instaeclipse.features.ExactTimePolicy;
import ps.reso.instaeclipse.hook.*;
import ps.reso.instaeclipse.utils.feature.*;

/** Platform relative timestamps used by host post/comment/message surfaces. */
public final class ExactTimestampHook {
    public void install() {
        for (Method m : DateUtils.class.getDeclaredMethods()) {
            if (!m.getName().equals("getRelativeTimeSpanString") || m.getParameterCount() == 0
                    || m.getParameterTypes()[0] != long.class) continue;
            HookBridge.hookMethod(m, new MethodHook() {
                @Override protected boolean isActive() { return FeatureFlags.exactTimestamps; }
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (p.hasThrowable() || !(p.getResult() instanceof CharSequence)) return;
                    String value = ExactTimePolicy.format((long) p.args[0], System.currentTimeMillis(),
                            Locale.getDefault(), ZoneId.systemDefault());
                    if (value != null) p.setResult(value);
                }
            });
        }
        FeatureStatusTracker.setHooked("ExactTimestamps");
    }
}
