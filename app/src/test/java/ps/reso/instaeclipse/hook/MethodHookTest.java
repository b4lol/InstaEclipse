package ps.reso.instaeclipse.hook;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.annotation.NonNull;

import org.junit.Test;

import java.lang.reflect.Executable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import io.github.libxposed.api.XposedInterface;

/** Checks that MethodHook keeps legacy before/after semantics on the interceptor chain. */
public class MethodHookTest {

    /** Minimal chain: the "original method" is a function of the arguments. */
    private static final class FakeChain implements XposedInterface.Chain {
        final Object thisObject;
        final Object[] args;
        final Function<Object[], Object> original;
        int proceedCount;
        Object[] proceededArgs;
        Object proceededThis;

        FakeChain(Object thisObject, Object[] args, Function<Object[], Object> original) {
            this.thisObject = thisObject;
            this.args = args;
            this.original = original;
        }

        @NonNull
        @Override
        public Executable getExecutable() {
            try {
                return String.class.getMethod("length");
            } catch (NoSuchMethodException e) {
                throw new AssertionError(e);
            }
        }

        @Override
        public Object getThisObject() {
            return thisObject;
        }

        @NonNull
        @Override
        public List<Object> getArgs() {
            return Collections.unmodifiableList(Arrays.asList(args));
        }

        @Override
        public Object getArg(int index) {
            return args[index];
        }

        @Override
        public Object proceed() {
            return proceed(args);
        }

        @Override
        public Object proceed(@NonNull Object[] a) {
            return proceedWith(thisObject, a);
        }

        @Override
        public Object proceedWith(@NonNull Object thisObj) {
            return proceedWith(thisObj, args);
        }

        @Override
        public Object proceedWith(@NonNull Object thisObj, @NonNull Object[] a) {
            proceedCount++;
            proceededArgs = a.clone();
            proceededThis = thisObj;
            return original.apply(a);
        }
    }

    @Test
    public void passesThroughWhenCallbacksDoNothing() throws Throwable {
        FakeChain chain = new FakeChain("self", new Object[]{2, 3}, a -> (int) a[0] + (int) a[1]);
        Object result = new MethodHook() {}.intercept(chain);
        assertEquals(5, result);
        assertEquals(1, chain.proceedCount);
    }

    @Test
    public void beforeCanChangeArguments() throws Throwable {
        FakeChain chain = new FakeChain("self", new Object[]{2, 3}, a -> (int) a[0] + (int) a[1]);
        Object result = new MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                param.args[1] = 10;
            }
        }.intercept(chain);
        assertEquals(12, result);
        assertArrayEquals(new Object[]{2, 10}, chain.proceededArgs);
    }

    @Test
    public void setResultInBeforeSkipsOriginal() throws Throwable {
        FakeChain chain = new FakeChain("self", new Object[]{}, a -> "original");
        final boolean[] afterRan = {false};
        Object result = new MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                param.setResult("replaced");
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                afterRan[0] = true;
                assertEquals("replaced", param.getResult());
            }
        }.intercept(chain);
        assertEquals("replaced", result);
        assertEquals(0, chain.proceedCount);
        assertTrue(afterRan[0]);
    }

    @Test
    public void afterSeesAndReplacesResult() throws Throwable {
        FakeChain chain = new FakeChain("self", new Object[]{}, a -> 1);
        Object result = new MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                assertEquals(1, param.getResult());
                assertSame("self", param.thisObject);
                param.setResult(2);
            }
        }.intercept(chain);
        assertEquals(2, result);
    }

    @Test
    public void originalExceptionPropagatesUnlessAfterSwallowsIt() throws Throwable {
        IllegalStateException boom = new IllegalStateException("boom");
        FakeChain chain = new FakeChain("self", new Object[]{}, a -> { throw boom; });
        try {
            new MethodHook() {}.intercept(chain);
            fail("expected exception");
        } catch (IllegalStateException e) {
            assertSame(boom, e);
        }

        Object result = new MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                assertTrue(param.hasThrowable());
                param.setResult("recovered");
            }
        }.intercept(new FakeChain("self", new Object[]{}, a -> { throw boom; }));
        assertEquals("recovered", result);
    }

    @Test
    public void setThrowableInBeforeThrowsWithoutCallingOriginal() throws Throwable {
        FakeChain chain = new FakeChain("self", new Object[]{}, a -> "original");
        RuntimeException denied = new RuntimeException("denied");
        try {
            new MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    param.setThrowable(denied);
                }
            }.intercept(chain);
            fail("expected exception");
        } catch (RuntimeException e) {
            assertSame(denied, e);
        }
        assertEquals(0, chain.proceedCount);
    }

    @Test
    public void crashingBeforeIsIgnoredAndOriginalRuns() throws Throwable {
        FakeChain chain = new FakeChain("self", new Object[]{}, a -> "original");
        Object result = new MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                param.setResult("half-done");
                throw new NullPointerException("bug in hook");
            }
        }.intercept(chain);
        assertEquals("original", result);
        assertEquals(1, chain.proceedCount);
    }

    @Test
    public void crashingAfterKeepsOriginalResult() throws Throwable {
        FakeChain chain = new FakeChain("self", new Object[]{}, a -> "original");
        Object result = new MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                param.setResult("half-done");
                throw new NullPointerException("bug in hook");
            }
        }.intercept(chain);
        assertEquals("original", result);
    }

    @Test
    public void inactiveHookSkipsCallbacksAndPassesThrough() throws Throwable {
        FakeChain chain = new FakeChain("self", new Object[]{2, 3}, a -> (int) a[0] + (int) a[1]);
        final boolean[] ran = {false};
        Object result = new MethodHook() {
            @Override
            protected boolean isActive() {
                return false;
            }

            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                ran[0] = true;
                param.setResult(-1);
            }
        }.intercept(chain);
        assertEquals(5, result);
        assertFalse(ran[0]);
        assertEquals(1, chain.proceedCount);
    }

    @Test
    public void crashingIsActiveCountsAsInactive() throws Throwable {
        FakeChain chain = new FakeChain("self", new Object[]{}, a -> "original");
        Object result = new MethodHook() {
            @Override
            protected boolean isActive() {
                throw new IllegalStateException("bug");
            }

            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                param.setResult("hooked");
            }
        }.intercept(chain);
        assertEquals("original", result);
    }

    @Test
    public void staticCallsHaveNullThis() throws Throwable {
        FakeChain chain = new FakeChain(null, new Object[]{}, a -> null);
        final boolean[] ran = {false};
        new MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                ran[0] = true;
                assertNull(param.thisObject);
                assertFalse(param.hasThrowable());
            }
        }.intercept(chain);
        assertTrue(ran[0]);
        assertNull(chain.proceededThis);
    }
}
