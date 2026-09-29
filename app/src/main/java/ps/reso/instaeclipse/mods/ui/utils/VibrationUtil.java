package ps.reso.instaeclipse.mods.ui.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.VibrationEffect;
import android.os.Vibrator;

import ps.reso.instaeclipse.hook.HookBridge;
import ps.reso.instaeclipse.utils.log.ModuleLog;

public class VibrationUtil {
    // Runs inside Instagram's process, so Instagram's VIBRATE permission applies,
    // not the companion's manifest.
    @SuppressLint({"ObsoleteSdkInt", "MissingPermission"})
    public static void vibrate(Context context) {
        try {
            Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    v.vibrate(50); // For older Android versions
                }
            }
        } catch (Exception e) {
            ModuleLog.line("InstaEclipse (vibrate error): " + e.getMessage());
        }
    }
}
