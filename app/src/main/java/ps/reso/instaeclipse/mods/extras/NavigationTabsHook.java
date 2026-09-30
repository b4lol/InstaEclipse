package ps.reso.instaeclipse.mods.extras;

import android.app.AlertDialog;
import android.content.Context;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import java.lang.reflect.Method;
import java.util.*;
import ps.reso.instaeclipse.R;
import ps.reso.instaeclipse.features.NavigationPolicy;
import ps.reso.instaeclipse.hook.*;
import ps.reso.instaeclipse.utils.core.*;
import ps.reso.instaeclipse.utils.feature.*;
import ps.reso.instaeclipse.utils.i18n.I18n;

/** Filters the host's tab model, so visual positions and click targets remain in sync. */
public final class NavigationTabsHook {
    public void install(LazyDexKit dex, ClassLoader cl) {
        for (Method m : dex.findMethodsCached("NavigationTabs_v1", cl, FindMethod.create()
                .matcher(MethodMatcher.create().paramTypes("com.instagram.common.session.UserSession", "boolean")
                        .returnType("java.util.List")))) {
            HookBridge.hookMethod(m, new MethodHook() {
                @Override protected boolean isActive() {
                    return FeatureFlags.hideNavigationSearch || FeatureFlags.hideNavigationReels
                            || FeatureFlags.hideNavigationCreate || FeatureFlags.hideNavigationDirect
                            || FeatureFlags.hideNavigationNews || !FeatureFlags.navigationOrder.isEmpty();
                }
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (!(p.getResult() instanceof List<?> tabs) || tabs.size() < 2 || tabs.size() > 12) return;
                    Map<String, Object> byName = new LinkedHashMap<>();
                    Class<?> type = null;
                    for (Object tab : tabs) {
                        if (!(tab instanceof Enum<?> e)) return;
                        if (type != null && type != e.getDeclaringClass()) return;
                        type = e.getDeclaringClass();
                        if (byName.put(e.name(), tab) != null) return;
                    }
                    // Do not touch unrelated enum lists sharing the method signature.
                    if (!byName.containsKey("FEED") || !byName.containsKey("PROFILE")) return;
                    Set<String> hidden = new HashSet<>();
                    if (FeatureFlags.hideNavigationSearch) hidden.add("SEARCH");
                    if (FeatureFlags.hideNavigationReels) hidden.add("CLIPS");
                    if (FeatureFlags.hideNavigationCreate) hidden.add("SHARE");
                    if (FeatureFlags.hideNavigationDirect) hidden.add("DIRECT");
                    if (FeatureFlags.hideNavigationNews) hidden.add("NEWS");
                    List<Object> result = new ArrayList<>();
                    for (String name : NavigationPolicy.arrange(new ArrayList<>(byName.keySet()), hidden,
                            FeatureFlags.navigationOrder)) result.add(byName.get(name));
                    p.setResult(result);
                    FeatureStatusTracker.setHooked("NavigationTabs");
                }
            });
        }
    }

    public static void showOrderPicker(Context ctx) {
        new AlertDialog.Builder(ctx).setTitle(I18n.t(ctx, R.string.ie_navigation_order))
                .setMessage(I18n.t(ctx, R.string.ie_navigation_order_help))
                .setPositiveButton(android.R.string.ok, (d, w) -> pickNext(ctx, new ArrayList<>()))
                .setNegativeButton(android.R.string.cancel, null).show();
    }
    private static void pickNext(Context ctx, List<String> chosen) {
        String[] values = {"FEED", "SEARCH", "CLIPS", "DIRECT", "PROFILE", "NEWS", "SHARE"};
        int[] labels = {R.string.ig_tab_feed, R.string.ig_tab_search, R.string.ig_tab_reels,
                R.string.ig_tab_direct, R.string.ig_tab_profile, R.string.ig_tab_notifications,
                R.string.ie_create_tab};
        List<String> remaining = new ArrayList<>(), display = new ArrayList<>();
        for (int i = 0; i < values.length; i++) if (!chosen.contains(values[i])) {
            remaining.add(values[i]); display.add(I18n.t(ctx, labels[i]));
        }
        new AlertDialog.Builder(ctx).setTitle(I18n.t(ctx, R.string.ie_navigation_order) + " · " + (chosen.size() + 1))
                .setItems(display.toArray(new String[0]), (d, which) -> {
                    chosen.add(remaining.get(which));
                    if (chosen.size() == values.length) save(chosen); else pickNext(ctx, chosen);
                })
                .setPositiveButton(I18n.t(ctx, R.string.ie_save), (d, w) -> save(chosen))
                .setNeutralButton(I18n.t(ctx, R.string.ie_reset), (d, w) -> save(List.of()))
                .setNegativeButton(android.R.string.cancel, null).show();
    }
    private static void save(List<String> order) {
        FeatureFlags.navigationOrder = String.join(",", order);
        SettingsManager.saveAllFlags();
    }
}
