package ps.reso.instaeclipse.hook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import org.junit.Test;

public class HookHelpersTest {

    @SuppressWarnings({"unused", "FieldMayBeFinal"})
    private static class Base {
        private String secret = "base";

        private int add(int a, Integer b) {
            return a + b;
        }

        String describe(CharSequence s) {
            return "cs:" + s;
        }
    }

    private static class Child extends Base {
    }

    @Test
    public void readsAndWritesInheritedPrivateFields() {
        Child c = new Child();
        assertEquals("base", HookHelpers.getObjectField(c, "secret"));
        HookHelpers.setObjectField(c, "secret", "changed");
        assertEquals("changed", HookHelpers.getObjectField(c, "secret"));
    }

    @Test(expected = NoSuchFieldError.class)
    public void missingFieldThrows() {
        HookHelpers.getObjectField(new Child(), "nope");
    }

    @Test
    public void callsInheritedMethodsWithBoxingAndSubtypes() {
        Child c = new Child();
        assertEquals(5, HookHelpers.callMethod(c, "add", 2, 3));
        assertEquals("cs:x", HookHelpers.callMethod(c, "describe", "x"));
    }

    @Test
    public void unwrapsTargetExceptions() {
        Object o = new Object() {
            @SuppressWarnings("unused")
            void explode() {
                throw new IllegalStateException("inner");
            }
        };
        try {
            HookHelpers.callMethod(o, "explode");
            fail("expected exception");
        } catch (RuntimeException e) {
            assertEquals(IllegalStateException.class, e.getCause().getClass());
        }
    }

    @Test
    public void findClassWrapsMissingClass() {
        assertSame(String.class, HookHelpers.findClass("java.lang.String", getClass().getClassLoader()));
        try {
            HookHelpers.findClass("does.not.Exist", getClass().getClassLoader());
            fail("expected error");
        } catch (NoClassDefFoundError expected) {
            assertEquals(ClassNotFoundException.class, expected.getCause().getClass());
        }
    }

    @Test
    public void additionalInstanceFieldsArePerObject() {
        Object a = new Object();
        Object b = new Object();
        HookHelpers.setAdditionalInstanceField(a, "k", 1);
        assertEquals(1, HookHelpers.getAdditionalInstanceField(a, "k"));
        assertNull(HookHelpers.getAdditionalInstanceField(b, "k"));
    }
}
