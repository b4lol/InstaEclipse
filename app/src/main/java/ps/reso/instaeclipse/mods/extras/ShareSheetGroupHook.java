package ps.reso.instaeclipse.mods.extras;

import android.view.View;

import java.lang.reflect.Method;
import java.util.List;

import ps.reso.instaeclipse.hook.HookBridge;
import ps.reso.instaeclipse.hook.HookHelpers;
import ps.reso.instaeclipse.hook.MethodHook;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.feature.FeatureStatusTracker;
import ps.reso.instaeclipse.utils.log.ModuleLog;

/**
 * Hides the share sheet's "create group" button that appears once several recipients are picked,
 * so a multi-recipient share can't turn into a new group chat by accident. {@code GroupSendButton}
 * keeps its class name; its bind method (the one taking the recipient {@link List}) is skipped and
 * the view is kept gone.
 */
public class ShareSheetGroupHook {

    private static final String BUTTON =
            "com.instagram.direct.fragment.sharesheet.groupsendbutton.shared.GroupSendButton";

    public void install(ClassLoader cl) {
        Class<?> button = HookHelpers.findClass(BUTTON, cl);
        MethodHook hide = new MethodHook() {
            @Override
            protected boolean isActive() {
                return FeatureFlags.hideShareSheetGroup;
            }

            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.thisObject instanceof View v) v.setVisibility(View.GONE);
                param.setResult(null);
            }
        };
        int hooked = 0;
        for (Method m : button.getDeclaredMethods()) {
            if (m.getReturnType() != void.class) continue;
            for (Class<?> p : m.getParameterTypes()) {
                if (p == List.class) {
                    HookBridge.hookMethod(m, hide);
                    hooked++;
                    break;
                }
            }
        }
        if (hooked == 0) {
            ModuleLog.line("(InstaEclipse | ShareSheetGroup): bind method not found");
            return;
        }
        FeatureStatusTracker.setHooked("HideShareSheetGroup");
        ModuleLog.line("(InstaEclipse | ShareSheetGroup): hooked " + hooked);
    }
}
