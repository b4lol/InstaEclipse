package ps.reso.instaeclipse.hook;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import io.github.libxposed.api.XposedInterface;

/**
 * Reflection helpers used by the hooks (the libxposed API ships none). Lookups are cached;
 * failures are reported as unchecked errors, mirroring what the hooks already expect.
 */
public final class HookHelpers {

    /** Per-class field cache; two-level so a lookup allocates nothing. */
    private static final ConcurrentHashMap<Class<?>, ConcurrentHashMap<String, Field>> FIELD_CACHE =
            new ConcurrentHashMap<>();
    private static final Map<Object, Map<String, Object>> ADDITIONAL_FIELDS = new WeakHashMap<>();

    private HookHelpers() {}

    // ── Classes ────────────────────────────────────────────────────────────────

    public static Class<?> findClass(String className, ClassLoader classLoader) {
        try {
            return Class.forName(className, false, classLoader);
        } catch (ClassNotFoundException e) {
            NoClassDefFoundError err = new NoClassDefFoundError(className);
            err.initCause(e);
            throw err;
        }
    }

    // ── Hooking by signature ───────────────────────────────────────────────────

    /**
     * Hooks the method named {@code methodName} declared by {@code className}.
     * The trailing vararg must be the {@link MethodHook}; the ones before it are the
     * parameter types, as {@link Class} objects or fully-qualified class names.
     */
    public static XposedInterface.HookHandle findAndHookMethod(String className, ClassLoader classLoader,
                                                               String methodName,
                                                               Object... parameterTypesAndCallback) {
        return findAndHookMethod(findClass(className, classLoader), methodName, parameterTypesAndCallback);
    }

    public static XposedInterface.HookHandle findAndHookMethod(Class<?> clazz, String methodName,
                                                               Object... parameterTypesAndCallback) {
        if (parameterTypesAndCallback.length == 0
                || !(parameterTypesAndCallback[parameterTypesAndCallback.length - 1] instanceof MethodHook)) {
            throw new IllegalArgumentException("No MethodHook passed for " + clazz.getName() + "#" + methodName);
        }
        MethodHook callback = (MethodHook) parameterTypesAndCallback[parameterTypesAndCallback.length - 1];
        Class<?>[] types = new Class<?>[parameterTypesAndCallback.length - 1];
        for (int i = 0; i < types.length; i++) {
            Object t = parameterTypesAndCallback[i];
            if (t instanceof Class<?>) {
                types[i] = (Class<?>) t;
            } else if (t instanceof String) {
                types[i] = findClass((String) t, clazz.getClassLoader());
            } else {
                throw new IllegalArgumentException("Parameter type must be a Class or String: " + t);
            }
        }
        Method m;
        try {
            m = clazz.getDeclaredMethod(methodName, types);
        } catch (NoSuchMethodException e) {
            NoSuchMethodError err = new NoSuchMethodError(clazz.getName() + "#" + methodName);
            err.initCause(e);
            throw err;
        }
        return HookBridge.hookMethod(m, callback);
    }

    // ── Fields ─────────────────────────────────────────────────────────────────

    /** Finds a field in {@code clazz} or its superclasses and makes it accessible. */
    public static Field findField(Class<?> clazz, String fieldName) {
        ConcurrentHashMap<String, Field> byName =
                FIELD_CACHE.computeIfAbsent(clazz, k -> new ConcurrentHashMap<>());
        Field cached = byName.get(fieldName);
        if (cached != null) return cached;
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(fieldName);
                f.setAccessible(true);
                byName.put(fieldName, f);
                return f;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldError(clazz.getName() + "#" + fieldName);
    }

    public static Object getObjectField(Object obj, String fieldName) {
        try {
            return findField(obj.getClass(), fieldName).get(obj);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void setObjectField(Object obj, String fieldName, Object value) {
        try {
            findField(obj.getClass(), fieldName).set(obj, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    // ── Methods ────────────────────────────────────────────────────────────────

    /**
     * Calls the instance method {@code methodName} on {@code obj}, picking the first method in
     * the class hierarchy whose parameters accept {@code args}. Exceptions thrown by the target
     * are rethrown unchecked with the original as cause.
     */
    public static Object callMethod(Object obj, String methodName, Object... args) {
        Method m = findCompatibleMethod(obj.getClass(), methodName, args);
        try {
            return m.invoke(obj, args);
        } catch (InvocationTargetException e) {
            throw new RuntimeException("Invocation of " + methodName + " failed", e.getCause());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Method findCompatibleMethod(Class<?> clazz, String name, Object[] args) {
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals(name) || Modifier.isStatic(m.getModifiers())) continue;
                if (isApplicable(m.getParameterTypes(), args)) {
                    m.setAccessible(true);
                    return m;
                }
            }
        }
        throw new NoSuchMethodError(clazz.getName() + "#" + name);
    }

    private static boolean isApplicable(Class<?>[] params, Object[] args) {
        if (params.length != args.length) return false;
        for (int i = 0; i < params.length; i++) {
            Object a = args[i];
            Class<?> p = params[i];
            if (a == null) {
                if (p.isPrimitive()) return false;
            } else if (!box(p).isInstance(a)) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> box(Class<?> c) {
        if (!c.isPrimitive()) return c;
        if (c == int.class) return Integer.class;
        if (c == boolean.class) return Boolean.class;
        if (c == long.class) return Long.class;
        if (c == float.class) return Float.class;
        if (c == double.class) return Double.class;
        if (c == byte.class) return Byte.class;
        if (c == short.class) return Short.class;
        if (c == char.class) return Character.class;
        return Void.class;
    }

    // ── Extra per-instance state ───────────────────────────────────────────────

    /** Attaches a value to {@code obj} without modifying its class; released with the object. */
    public static void setAdditionalInstanceField(Object obj, String key, Object value) {
        synchronized (ADDITIONAL_FIELDS) {
            Map<String, Object> fields = ADDITIONAL_FIELDS.get(obj);
            if (fields == null) {
                fields = new HashMap<>();
                ADDITIONAL_FIELDS.put(obj, fields);
            }
            fields.put(key, value);
        }
    }

    public static Object getAdditionalInstanceField(Object obj, String key) {
        synchronized (ADDITIONAL_FIELDS) {
            Map<String, Object> fields = ADDITIONAL_FIELDS.get(obj);
            return fields == null ? null : fields.get(key);
        }
    }
}
