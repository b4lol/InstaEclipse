package ps.reso.instaeclipse.utils.core;

import android.annotation.SuppressLint;
import android.os.SystemClock;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.BatchFindMethodUsingStrings;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.result.ClassDataList;
import org.luckypray.dexkit.result.MethodData;
import org.luckypray.dexkit.result.MethodDataList;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ps.reso.instaeclipse.utils.log.ModuleLog;

/**
 * DexKit bridge that is only opened when a query actually runs, and can be closed once hooks
 * are installed. With {@link DexKitCache} warm (every launch after the first for a given
 * Instagram version) most launches never load libdexkit.so or index Instagram's dex files.
 *
 * <p>All calls are serialized; a query after {@link #close()} transparently reopens the bridge.
 */
public final class LazyDexKit {

    private final String apkPath;
    private final String nativeLibPath;

    private DexKitBridge bridge;
    private boolean nativeLoaded;
    private int opens;

    public LazyDexKit(String apkPath, String nativeLibPath) {
        this.apkPath = apkPath;
        this.nativeLibPath = nativeLibPath;
    }

    /** The underlying bridge, opened on first use. */
    @SuppressLint("UnsafeDynamicallyLoadedCode")
    public synchronized DexKitBridge get() {
        if (bridge == null) {
            long start = SystemClock.elapsedRealtime();
            if (!nativeLoaded) {
                System.load(nativeLibPath);
                nativeLoaded = true;
            }
            bridge = DexKitBridge.create(apkPath);
            opens++;
            ModuleLog.line("(IE|DexKit) bridge opened in " + (SystemClock.elapsedRealtime() - start)
                    + " ms, first needed by " + firstCaller());
        }
        return bridge;
    }

    /** The hook that caused the bridge to open: tells which lookup missed the cache. */
    private static String firstCaller() {
        for (StackTraceElement e : new Throwable().getStackTrace()) {
            String c = e.getClassName();
            if (!c.equals(LazyDexKit.class.getName())) {
                return c.substring(c.lastIndexOf('.') + 1) + "." + e.getMethodName() + ":" + e.getLineNumber();
            }
        }
        return "?";
    }

    public synchronized MethodDataList findMethod(FindMethod query) {
        return get().findMethod(query);
    }

    public synchronized ClassDataList findClass(FindClass query) {
        return get().findClass(query);
    }

    /** True if the bridge was opened at least once in this process. */
    public synchronized boolean wasUsed() {
        return opens > 0;
    }

    /** Releases the native index. Safe to call repeatedly. */
    public synchronized void close() {
        if (bridge == null) return;
        try {
            bridge.close();
        } catch (Throwable t) {
            ModuleLog.line("(IE|DexKit) close failed: " + t.getMessage());
        }
        bridge = null;
    }

    // ── Cached queries ───────────────────────────────────────────────────────

    /**
     * Methods matching {@code query}, cached in {@link DexKitCache} per Instagram version under
     * {@code cacheKey}. An empty result is cached too, so an anchor that doesn't exist in this
     * Instagram build isn't searched again on every launch.
     */
    public List<Method> findMethodsCached(String cacheKey, ClassLoader cl, FindMethod query) {
        List<Method> cached = loadCached(cacheKey, cl);
        if (cached != null) return cached;
        List<Method> out = new ArrayList<>();
        for (MethodData md : findMethod(query)) addInstance(md, cl, out);
        DexKitCache.saveMethods(cacheKey, out);
        return out;
    }

    /**
     * Methods whose code references any of {@code anchors} (each matched like
     * {@code MethodMatcher.usingStrings(anchor)}), de-duplicated, cached under {@code cacheKey}.
     * All anchors are resolved in a single DexKit pass.
     */
    public List<Method> findMethodsUsingAnyStringCached(String cacheKey, ClassLoader cl, String... anchors) {
        List<Method> cached = loadCached(cacheKey, cl);
        if (cached != null) return cached;
        Map<String, List<String>> groups = new LinkedHashMap<>();
        for (String a : anchors) groups.put(a, Collections.singletonList(a));
        Map<String, MethodDataList> found;
        synchronized (this) {
            found = get().batchFindMethodUsingStrings(BatchFindMethodUsingStrings.create().groups(groups));
        }
        Map<String, Method> unique = new LinkedHashMap<>();
        for (String a : anchors) {
            MethodDataList list = found.get(a);
            if (list == null) continue;
            for (MethodData md : list) {
                if (unique.containsKey(md.getDescriptor())) continue;
                List<Method> one = new ArrayList<>(1);
                addInstance(md, cl, one);
                if (!one.isEmpty()) unique.put(md.getDescriptor(), one.get(0));
            }
        }
        List<Method> out = new ArrayList<>(unique.values());
        DexKitCache.saveMethods(cacheKey, out);
        return out;
    }

    /** Convenience for a single anchor string. */
    public List<Method> findMethodsUsingStringCached(String cacheKey, ClassLoader cl, String anchor) {
        return findMethodsUsingAnyStringCached(cacheKey, cl, anchor);
    }

    private static List<Method> loadCached(String cacheKey, ClassLoader cl) {
        if (!DexKitCache.isCacheValid()) return null;
        return DexKitCache.loadMethods(cacheKey, cl);
    }

    private static void addInstance(MethodData md, ClassLoader cl, List<Method> out) {
        if (md.isConstructor()) return;
        try {
            out.add(md.getMethodInstance(cl));
        } catch (Throwable ignored) {
            // Class not loadable in this process (e.g. a split that isn't installed).
        }
    }
}
