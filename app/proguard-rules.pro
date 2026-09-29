# Keep everything — no obfuscation, no shrinking
-dontobfuscate
-dontoptimize
-keepattributes *Annotation*, SourceFile, LineNumberTable

# Keep ALL classes & members (methods, fields)
-keep class ps.reso.instaeclipse.** { *; }

# Xposed API (libxposed 101) is provided by the framework at runtime; its AAR ships the
# consumer rule that keeps the XposedModule entry class. Keep our hook layer intact.
-keep class ps.reso.instaeclipse.hook.** { *; }

# Keep reflection / DexKit-accessed symbols
-keep class * {
    public protected *;
}

# Keep any dynamically called methods (like URI matchers)
-keepclassmembers class * {
    *** get*();
    void set*(***);
}

# Avoid warnings from missing Android APIs
-dontwarn android.support.**
-dontwarn androidx.**
-dontwarn com.android.**
-dontwarn org.lsposed.**
-dontwarn io.github.libxposed.api.**
# Suppress missing javax.lang.model warnings
-dontwarn javax.lang.model.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn org.checkerframework.**


# keep GSON serialized classes
-keep class * implements com.google.gson.JsonDeserializer { *; }
-keep class * implements com.google.gson.JsonSerializer { *; }
