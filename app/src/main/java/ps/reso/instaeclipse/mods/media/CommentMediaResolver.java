package ps.reso.instaeclipse.mods.media;

import java.lang.reflect.*;
import java.util.*;

/** Bounded walk of one comment's media holders; never follows authors, replies or sessions. */
public final class CommentMediaResolver {
    private CommentMediaResolver() {}
    public static List<String> urls(Object comment) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        walk(comment, 0, Collections.newSetFromMap(new IdentityHashMap<>()), result);
        return new ArrayList<>(result);
    }
    private static void walk(Object value, int depth, Set<Object> seen, Set<String> urls) {
        if (value == null || depth > 4 || seen.size() >= 128 || !seen.add(value)) return;
        String name = value.getClass().getName();
        if (name.startsWith("com.instagram.user.") || name.contains("Session")
                || name.startsWith("android.") || value instanceof Collection<?> || value instanceof Map<?, ?>) return;
        if (value instanceof String s) {
            if (depth > 1 && DownloadRequestValidator.isAllowedMediaUrl(s)) urls.add(s);
            return;
        }
        if (!name.startsWith("X.") && !name.startsWith("com.instagram.")) return;
        for (Class<?> c = value.getClass(); c != Object.class && c != null; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                try { f.setAccessible(true); walk(f.get(value), depth + 1, seen, urls); }
                catch (ReflectiveOperationException | RuntimeException ignored) {}
            }
        }
    }
}
