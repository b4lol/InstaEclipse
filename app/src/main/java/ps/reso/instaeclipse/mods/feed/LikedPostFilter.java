package ps.reso.instaeclipse.mods.feed;

import java.lang.reflect.*;
import java.util.*;
import ps.reso.instaeclipse.utils.core.LazyDexKit;

/** Reads only exact has_liked accessors on the item or its immediate media dictionary. */
final class LikedPostFilter {
    private static volatile List<Method> getters = List.of();
    static void resolve(LazyDexKit dex, ClassLoader cl) {
        List<Method> found = new ArrayList<>();
        for (Method m : dex.findMethodsUsingStringCached("FeedHasLiked_v1", cl, "has_liked")) {
            if (m.getParameterCount() != 0 || Modifier.isStatic(m.getModifiers())
                    || (m.getReturnType() != Boolean.class && m.getReturnType() != boolean.class)) continue;
            m.setAccessible(true); found.add(m);
        }
        getters = List.copyOf(found);
    }
    static boolean isAvailable() { return !getters.isEmpty(); }
    static boolean isLiked(Object item) { return read(item, 2); }
    private static boolean read(Object obj, int depth) {
        if (obj == null) return false;
        for (Method getter : getters) {
            if (!getter.getDeclaringClass().isInstance(obj)) continue;
            try { if (Boolean.TRUE.equals(getter.invoke(obj))) return true; }
            catch (ReflectiveOperationException | RuntimeException ignored) {}
        }
        if (depth == 0) return false;
        String name = obj.getClass().getName();
        if (!name.startsWith("X.") && !name.startsWith("com.instagram.feed.")) return false;
        for (Field field : obj.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()
                    || field.getType() == String.class) continue;
            try { field.setAccessible(true); if (read(field.get(obj), depth - 1)) return true; }
            catch (ReflectiveOperationException | RuntimeException ignored) {}
        }
        return false;
    }
}
