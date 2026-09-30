package ps.reso.instaeclipse.mods.ghost;

import android.app.AlertDialog;
import android.content.Context;
import android.widget.Toast;
import ps.reso.instaeclipse.R;
import ps.reso.instaeclipse.features.ReadReceiptPolicy;
import ps.reso.instaeclipse.utils.core.SettingsManager;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.feature.FeatureStatusTracker;
import ps.reso.instaeclipse.utils.i18n.I18n;

public final class ReadReceiptExceptions {
    private ReadReceiptExceptions() {}
    public static boolean allows(String threadId) {
        return FeatureFlags.readReceiptExceptions && ReadReceiptPolicy.allows(FeatureFlags.readReceiptThreadIds, threadId);
    }
    public static boolean allowsPath(String path) { return allows(ReadReceiptPolicy.threadFromPath(path)); }

    public static boolean prompt(Context ctx) {
        if (!FeatureFlags.readReceiptExceptions) return false;
        String id = KeepUnsentMessagesHook.currentThreadId;
        if (id == null || !id.matches("[0-9]{6,64}")) {
            Toast.makeText(ctx, I18n.t(ctx, R.string.ig_hide_chat_no_thread), Toast.LENGTH_SHORT).show();
            return true;
        }
        boolean allow = !allows(id);
        new AlertDialog.Builder(ctx).setTitle(I18n.t(ctx, allow ? R.string.ie_allow_chat_seen : R.string.ie_block_chat_seen))
                .setMessage(I18n.t(ctx, R.string.ie_chat_seen_help))
                .setPositiveButton(android.R.string.ok, (d, which) -> {
                    if (!id.equals(KeepUnsentMessagesHook.currentThreadId)) return;
                    try {
                        FeatureFlags.readReceiptThreadIds = ReadReceiptPolicy.setAllowed(FeatureFlags.readReceiptThreadIds, id, allow);
                        SettingsManager.saveAllFlags();
                        FeatureStatusTracker.setHooked("ReadReceiptExceptions");
                    } catch (IllegalArgumentException ignored) {
                        Toast.makeText(ctx, I18n.t(ctx, R.string.ie_media_failed), Toast.LENGTH_SHORT).show();
                    }
                }).setNegativeButton(android.R.string.cancel, null).show();
        return true;
    }
}
