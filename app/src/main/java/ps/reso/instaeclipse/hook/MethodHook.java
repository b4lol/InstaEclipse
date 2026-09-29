package ps.reso.instaeclipse.hook;

import androidx.annotation.NonNull;

import java.lang.reflect.Member;

import io.github.libxposed.api.XposedInterface;

/**
 * Before/after hook callback on top of libxposed's interceptor chain (API 101+).
 *
 * <p>Keeps the semantics every hook in this code base was written against:
 * <ul>
 *   <li>{@link #beforeHookedMethod} may change {@code param.args} or short-circuit the call
 *   with {@link MethodHookParam#setResult} / {@link MethodHookParam#setThrowable};</li>
 *   <li>{@link #afterHookedMethod} sees the result or throwable and may replace it;</li>
 *   <li>an exception thrown by a callback is logged and ignored, exactly like the legacy
 *   bridge did, so a broken hook never crashes Instagram.</li>
 * </ul>
 */
public abstract class MethodHook {

    final int priority;

    protected MethodHook() {
        this(XposedInterface.PRIORITY_DEFAULT);
    }

    protected MethodHook(int priority) {
        this.priority = priority;
    }

    /**
     * Fast-path gate, checked before any per-call allocation. Hooks on hot methods (getColor,
     * getDrawable, onAttachedToWindow, …) override this with their feature flag so that, while
     * the feature is off, the hook costs one call and a field read instead of building a
     * {@link MethodHookParam} and copying the arguments.
     */
    protected boolean isActive() {
        return true;
    }

    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
    }

    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
    }

    final Object intercept(@NonNull XposedInterface.Chain chain) throws Throwable {
        boolean active;
        try {
            active = isActive();
        } catch (Throwable t) {
            HookBridge.logCallbackError(this, "isActive", t);
            active = false;
        }
        if (!active) return chain.proceed();

        Object originalThis = chain.getThisObject();
        MethodHookParam param = new MethodHookParam(
                chain.getExecutable(), originalThis, chain.getArgs().toArray());

        try {
            beforeHookedMethod(param);
        } catch (Throwable t) {
            HookBridge.logCallbackError(this, "before", t);
            param.reset();
        }

        if (!param.returnEarly) {
            try {
                Object result;
                if (param.thisObject != originalThis && param.thisObject != null) {
                    result = chain.proceedWith(param.thisObject, param.args);
                } else {
                    result = chain.proceed(param.args);
                }
                param.result = result;
                param.throwable = null;
            } catch (Throwable t) {
                param.result = null;
                param.throwable = t;
            }
        }

        Object lastResult = param.result;
        Throwable lastThrowable = param.throwable;
        try {
            afterHookedMethod(param);
        } catch (Throwable t) {
            HookBridge.logCallbackError(this, "after", t);
            param.result = lastResult;
            param.throwable = lastThrowable;
        }

        if (param.throwable != null) throw param.throwable;
        return param.result;
    }

    /** State of one hooked call, shared by the before and after callbacks. */
    public static final class MethodHookParam {
        /** The hooked method or constructor. */
        public final Member method;
        /** {@code this} for instance calls, {@code null} for static ones. */
        public Object thisObject;
        /** Arguments of the call; changes made in the before callback are passed on. */
        public Object[] args;

        private Object result;
        private Throwable throwable;
        boolean returnEarly;

        MethodHookParam(Member method, Object thisObject, Object[] args) {
            this.method = method;
            this.thisObject = thisObject;
            this.args = args;
        }

        public Object getResult() {
            return result;
        }

        /** Sets the result; in the before callback this skips the original method. */
        public void setResult(Object result) {
            this.result = result;
            this.throwable = null;
            this.returnEarly = true;
        }

        public Throwable getThrowable() {
            return throwable;
        }

        public boolean hasThrowable() {
            return throwable != null;
        }

        /** Makes the call throw; in the before callback this skips the original method. */
        public void setThrowable(Throwable throwable) {
            this.throwable = throwable;
            this.result = null;
            this.returnEarly = true;
        }

        public Object getResultOrThrowable() throws Throwable {
            if (throwable != null) throw throwable;
            return result;
        }

        void reset() {
            result = null;
            throwable = null;
            returnEarly = false;
        }
    }
}
