package ps.reso.instaeclipse.hook;

import android.content.Context;
import android.content.res.Resources;

import ps.reso.instaeclipse.utils.log.ModuleLog;

/**
 * The module APK's own resources, usable from inside Instagram's process. Resource IDs in
 * {@code ps.reso.instaeclipse.R} only make sense against this table, not Instagram's.
 * Replaces the legacy-only {@code XModuleResources}.
 */
public final class ModuleResources {

    private static volatile Resources resources;

    private ModuleResources() {}

    /** Loads the table once; call with any Instagram context after the framework attached. */
    public static void init(Context context) {
        if (resources != null) return;
        try {
            resources = context.getPackageManager()
                    .getResourcesForApplication(HookBridge.framework().getModuleApplicationInfo());
        } catch (Throwable t) {
            ModuleLog.line("(InstaEclipse | Resources): cannot load module resources: " + t.getMessage());
        }
    }

    /** Module resources, or {@code null} if {@link #init} hasn't succeeded. */
    public static Resources get() {
        return resources;
    }
}
