package ps.reso.instaeclipse.mods.ghost;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import ps.reso.instaeclipse.hook.ViewAttachDispatcher;
import ps.reso.instaeclipse.utils.ui.ResIds;
import ps.reso.instaeclipse.hook.MethodHook;
import ps.reso.instaeclipse.hook.HookBridge;
import ps.reso.instaeclipse.hook.HookHelpers;
import ps.reso.instaeclipse.R;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.i18n.I18n;
import ps.reso.instaeclipse.utils.log.ModuleLog;

public class GhostChannelMarkAsReadHook {

    private static final String CHANNEL_TAG = "ie_channel_seen";

    public void install(ClassLoader classLoader) {
        try {
            ViewAttachDispatcher.register(new ViewAttachDispatcher.Listener() {
                // Shared View.onAttachedToWindow hook: runs for every attached view, so only
                // compare ids here; nothing runs at all while the feature is off.
                @Override
                public boolean isActive() {
                    return FeatureFlags.isGhostSeen;
                }

                @Override
                public void onAttached(View view) {
                    Context context = view.getContext();

                    int seenStateId = ResIds.id(context, "seen_state_text");
                    if (seenStateId == 0 || view.getId() != seenStateId) return;
                    if (!(view instanceof TextView seenTextView)) return;

                    int headerButtonsId = ResIds.id(context, "header_right_buttons");
                    if (headerButtonsId != 0) {
                        View container = view.getRootView().findViewById(headerButtonsId);
                        if (container instanceof ViewGroup viewGroup) {
                            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                                CharSequence description = viewGroup.getChildAt(i).getContentDescription();
                                if (description != null) {
                                    String descStr = description.toString().toLowerCase();
                                    if (descStr.contains("audio call") ||
                                            descStr.contains("video call") ||
                                            descStr.contains("blend")) {
                                        return;
                                    }
                                }
                            }
                        }
                    }
                    updateChannelSeen(seenTextView);
                }
            });
        } catch (Throwable t) {
            ModuleLog.line("(InstaEclipse): Channel seen hook failed: " + t.getMessage());
        }
    }

    private void updateChannelSeen(TextView textView) {
        // Prevent multiple listeners/updates
        if (textView.getTag() != null && textView.getTag().equals(CHANNEL_TAG)) return;
        textView.setTag(CHANNEL_TAG);

        // Make it look interactive
        textView.setTextColor(Color.CYAN); // Distinguish it as a "modded" element

        textView.setOnClickListener(v -> {
            triggerChannelSeen(textView);
        });

        // Optional: Append a ghost emoji to indicate it's modded
        String currentText = textView.getText().toString();
        if (!currentText.contains("👻")) {
            textView.setText(currentText + " 👻");
        }
    }

    private void triggerChannelSeen(View view) {
        try {
            Context ctx = view.getContext();
            @SuppressLint("DiscouragedApi")
            int messageListId = ctx.getResources().getIdentifier("message_list", "id", ctx.getPackageName());

            View root = view.getRootView();
            View messageList = root.findViewById(messageListId);

            if (messageList instanceof ViewGroup group) {
                group.scrollBy(0, 100_000);

                FeatureFlags.isGhostSeen = false;
                group.scrollBy(0, -300);

                view.postDelayed(() -> {
                    group.scrollBy(0, 300);
                    FeatureFlags.isGhostSeen = true;
                    Toast.makeText(ctx, I18n.t(ctx, R.string.ig_toast_channel_seen_sent), Toast.LENGTH_SHORT).show();
                }, 400);
            }
        } catch (Exception e) {
            FeatureFlags.isGhostSeen = true;
        }
    }
}