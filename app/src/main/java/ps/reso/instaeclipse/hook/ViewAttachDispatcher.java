package ps.reso.instaeclipse.hook;

import android.view.View;

import java.util.concurrent.CopyOnWriteArrayList;

import ps.reso.instaeclipse.utils.log.ModuleLog;

/**
 * One shared hook on {@code View.onAttachedToWindow} for every feature that needs to react to
 * specific views appearing. That method runs for every view Instagram attaches, so features
 * register a {@link Listener} here instead of installing their own hook: one trampoline per
 * attach instead of one per feature, and nothing at all is allocated while every listener is
 * inactive.
 */
public final class ViewAttachDispatcher {

    /** Callback for attached views. Keep {@link #onAttached} cheap: compare ids first. */
    public interface Listener {
        /** Usually the feature flag; checked before {@link #onAttached}. */
        boolean isActive();

        /** Called on the UI thread after {@code view} was attached. */
        void onAttached(View view);
    }

    private static final CopyOnWriteArrayList<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static boolean installed;

    private ViewAttachDispatcher() {}

    /** Registers {@code listener}; the shared hook is installed on first use. */
    public static synchronized void register(Listener listener) {
        LISTENERS.add(listener);
        if (installed) return;
        HookHelpers.findAndHookMethod(View.class, "onAttachedToWindow", new MethodHook() {
            @Override
            protected boolean isActive() {
                for (Listener l : LISTENERS) {
                    if (l.isActive()) return true;
                }
                return false;
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                View view = (View) param.thisObject;
                for (Listener l : LISTENERS) {
                    if (!l.isActive()) continue;
                    try {
                        l.onAttached(view);
                    } catch (Throwable t) {
                        // One broken feature must not stop the others.
                        ModuleLog.line("(IE|ViewAttach) " + l.getClass().getName() + ": " + t);
                    }
                }
            }
        });
        installed = true;
    }
}
