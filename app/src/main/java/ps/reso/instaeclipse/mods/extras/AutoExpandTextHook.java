package ps.reso.instaeclipse.mods.extras;

import java.lang.reflect.Method;
import ps.reso.instaeclipse.hook.*;
import ps.reso.instaeclipse.utils.feature.*;

/** Uses the host's explicit expanded state; never clicks links or changes arbitrary TextViews. */
public final class AutoExpandTextHook {
    public void install(ClassLoader cl) throws Throwable {
        Class<?> view = cl.loadClass("com.instagram.ui.widget.expandingtextview.ExpandingTextView");
        for (Method setter : view.getDeclaredMethods()) {
            if (!setter.getName().equals("setExpandState") || setter.getParameterCount() != 1) continue;
            Class<?> state = setter.getParameterTypes()[0];
            if (!state.isEnum()) continue;
            Object expanded = null;
            for (Object candidate : state.getEnumConstants())
                if (((Enum<?>) candidate).name().equals("EXPANDED")) expanded = candidate;
            if (expanded == null) continue;
            Object value = expanded;
            setter.setAccessible(true);
            HookBridge.hookMethod(setter, new MethodHook() {
                @Override protected boolean isActive() { return FeatureFlags.autoExpandText; }
                @Override protected void beforeHookedMethod(MethodHookParam p) { p.args[0] = value; }
            });
            HookHelpers.findAndHookMethod(view, "onMeasure", int.class, int.class, new MethodHook() {
                @Override protected boolean isActive() { return FeatureFlags.autoExpandText; }
                @Override protected void beforeHookedMethod(MethodHookParam p) throws Throwable { setter.invoke(p.thisObject, value); }
            });
            FeatureStatusTracker.setHooked("AutoExpandText");
            return;
        }
    }
}
