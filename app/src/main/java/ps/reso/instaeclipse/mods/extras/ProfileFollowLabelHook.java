package ps.reso.instaeclipse.mods.extras;

import android.view.*;
import android.widget.TextView;
import java.lang.reflect.*;
import java.util.*;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import ps.reso.instaeclipse.R;
import ps.reso.instaeclipse.hook.*;
import ps.reso.instaeclipse.utils.core.LazyDexKit;
import ps.reso.instaeclipse.utils.feature.*;
import ps.reso.instaeclipse.utils.i18n.I18n;
import ps.reso.instaeclipse.utils.ui.ResIds;

/** Reads the profile binder's friendship model, avoiding network callbacks and cross-profile races. */
public final class ProfileFollowLabelHook {
    private static final String TAG = "ie_profile_follow_status";
    public void install(LazyDexKit dex, ClassLoader cl) {
        List<Method> maps = dex.findMethodsCached("ProfileFriendshipMap_v2", cl, FindMethod.create()
                .matcher(MethodMatcher.create().paramCount(1).usingStrings("followed_by", "following")
                        .returnType("java.util.Map")));
        maps.removeIf(m -> !Modifier.isStatic(m.getModifiers()));
        if (maps.size() != 1) return;
        Method mapper = maps.get(0); mapper.setAccessible(true);
        for (Method m : dex.findMethodsUsingStringCached("ProfileBadgeBinder_v1", cl, "bindInternalBadges")) {
            if (m.getReturnType() != void.class || !Arrays.asList(m.getParameterTypes()).contains(View.class)) continue;
            HookBridge.hookMethod(m, new MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) throws Throwable {
                    View root = null;
                    for (Object arg : p.args) if (arg instanceof View v) { root = v; break; }
                    if (root == null) return;
                    View previous = root.findViewWithTag(TAG);
                    if (previous != null && previous.getParent() instanceof ViewGroup group) group.removeView(previous);
                    if (!FeatureFlags.profileFollowLabel) return;
                    Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
                    Set<Object> statuses = Collections.newSetFromMap(new IdentityHashMap<>());
                    for (Object arg : p.args) find(arg, mapper.getParameterTypes()[0], 0, seen, statuses);
                    if (statuses.size() != 1) return;
                    Object value = mapper.invoke(null, statuses.iterator().next());
                    if (!(value instanceof Map<?, ?> map) || !(map.get("followed_by") instanceof Boolean followed)) return;
                    int id = ResIds.id(root.getContext(), "profile_header_full_name");
                    View anchor = id == 0 ? null : root.findViewById(id);
                    if (!(anchor instanceof TextView title) || !(anchor.getParent() instanceof android.widget.LinearLayout parent)
                            || parent.getOrientation() != android.widget.LinearLayout.VERTICAL) return;
                    TextView label = new TextView(root.getContext());
                    label.setTag(TAG);
                    label.setText(I18n.t(root.getContext(), followed ? R.string.ig_toast_follows_you : R.string.ig_toast_not_follows_you));
                    label.setTextColor(title.getCurrentTextColor()); label.setTextSize(13);
                    parent.addView(label, parent.indexOfChild(anchor) + 1,
                            new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    FeatureStatusTracker.setHooked("ProfileFollowLabel");
                }
            });
        }
    }
    private static void find(Object obj, Class<?> target, int depth, Set<Object> seen, Set<Object> out) {
        if (obj == null || depth > 3 || seen.size() >= 128 || !seen.add(obj)) return;
        if (target.isInstance(obj)) { out.add(obj); return; }
        String name = obj.getClass().getName();
        if (!name.startsWith("X.") && !name.startsWith("com.instagram.")) return;
        if (obj instanceof View || obj instanceof android.content.Context) return;
        for (Field f : obj.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
            try { f.setAccessible(true); find(f.get(obj), target, depth + 1, seen, out); }
            catch (ReflectiveOperationException | RuntimeException ignored) {}
        }
    }
}
