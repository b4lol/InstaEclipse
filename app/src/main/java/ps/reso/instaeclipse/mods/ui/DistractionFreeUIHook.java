package ps.reso.instaeclipse.mods.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.res.Resources;
import android.view.View;
import android.view.ViewGroup;

import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.feature.FeatureStatusTracker;
import ps.reso.instaeclipse.utils.log.ModuleLog;

/**
 * Distraction Free — view-level removal of the story tray and the Reels tab.
 *
 * The network interceptor already drops the story/reels metadata requests, but IG 447.0.0.39+ falls
 * back to its locally-cached stories/reels (media streams from the CDN, which distraction-free does
 * not block), so they stay visible. To make them actually disappear we collapse the real Views by
 * their stable public resource names (verified from the live view tree, never obfuscated X.* ids):
 *   - story tray  -> com.instagram.android:id/reels_tray_container   (disableStories or hideStoriesTray)
 *   - Reels tab   -> com.instagram.android:id/clips_tab              (gated on disableReels)
 *
 * Armed from {@link UIHookManager#setupHooks} on every resume; a one-time global-layout listener per
 * window re-applies the collapse because IG rebuilds these views as you navigate. Everything is
 * gated per-flag and wrapped in try/catch, so nothing changes unless the toggle is on and a miss can
 * never crash Instagram.
 */
public class DistractionFreeUIHook {

    private static volatile boolean idsResolved = false;
    private static int trayId = 0;      // reels_tray_container (story tray)
    private static int notesTrayId = 0;
    private static int clipsTabId = 0;  // clips_tab (Reels bottom-nav tab)

    private static final java.util.Set<View> watchedDecors =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    private static boolean anyFlag() {
        return FeatureFlags.disableStories || FeatureFlags.hideStoriesTray
                || FeatureFlags.disableReels || FeatureFlags.hideNotesTray;
    }

    @SuppressLint("DiscouragedApi")
    private static void ensureIds(Activity a) {
        if (idsResolved) return;
        try {
            Resources res = a.getResources();
            String pkg = a.getPackageName();
            trayId = res.getIdentifier("reels_tray_container", "id", pkg);
            clipsTabId = res.getIdentifier("clips_tab", "id", pkg);
            notesTrayId = ps.reso.instaeclipse.utils.ui.ResIds.id(a, "cf_hub_recycler_view");
            if (notesTrayId != 0) ps.reso.instaeclipse.utils.feature.FeatureStatusTracker.setHooked("HideNotesTray");
            idsResolved = true;
            ModuleLog.line("(IE|DistractUI) resolved: tray=" + trayId + " clipsTab=" + clipsTabId);
        } catch (Throwable ignored) {}
    }

    /** Collapse the enabled targets in this activity, and keep them collapsed via a one-time
     *  global-layout listener on the window decor. Called on every main/modal resume. */
    public static void watchActivity(final Activity a) {
        if (a == null || !anyFlag()) return;
        try {
            ensureIds(a);
            sweep(a);
            final View decor = a.getWindow() != null ? a.getWindow().getDecorView() : null;
            if (decor == null || !watchedDecors.add(decor)) return;
            decor.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
                if (anyFlag() || !collapsed.isEmpty()) sweep(a);
            });
        } catch (Throwable ignored) {}
    }

    private static void sweep(Activity a) {
        try {
            View root = a.getWindow() != null ? a.getWindow().getDecorView() : null;
            if (!(root instanceof ViewGroup)) return;
            if (trayId != 0) {
                updateAllById((ViewGroup) root, trayId, FeatureFlags.disableStories || FeatureFlags.hideStoriesTray);
                if (FeatureFlags.hideStoriesTray) FeatureStatusTracker.setHooked("HideStoriesTray");
            }
            if (clipsTabId != 0) updateAllById((ViewGroup) root, clipsTabId, FeatureFlags.disableReels);
            if (notesTrayId != 0) updateAllById((ViewGroup) root, notesTrayId, FeatureFlags.hideNotesTray);
        } catch (Throwable ignored) {}
    }

    private static void updateAllById(ViewGroup root, int id, boolean hide) {
        java.util.ArrayDeque<View> stack = new java.util.ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            View v = stack.pop();
            if (v.getId() == id) { if (hide) collapse(v); else restore(v); }
            if (v instanceof ViewGroup vg)
                for (int i = 0; i < vg.getChildCount(); i++) stack.push(vg.getChildAt(i));
        }
    }

    private record ViewState(int visibility, int width, int height) {}
    private static final java.util.Map<View, ViewState> collapsed = new java.util.WeakHashMap<>();

    private static void collapse(View v) {
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (!collapsed.containsKey(v)) collapsed.put(v, new ViewState(v.getVisibility(),
                lp == null ? -2 : lp.width, lp == null ? -2 : lp.height));
        if (v.getVisibility() != View.GONE) v.setVisibility(View.GONE);
        if (lp != null && (lp.width != 0 || lp.height != 0)) {
            lp.height = 0; lp.width = 0; v.setLayoutParams(lp);
        }
    }

    private static void restore(View v) {
        ViewState old = collapsed.remove(v);
        if (old == null) return;
        v.setVisibility(old.visibility());
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp != null) { lp.width = old.width(); lp.height = old.height(); v.setLayoutParams(lp); }
    }
}
