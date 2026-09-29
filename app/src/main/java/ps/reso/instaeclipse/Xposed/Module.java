package ps.reso.instaeclipse.Xposed;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;



import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;

import io.github.libxposed.api.XposedModule;
import ps.reso.instaeclipse.hook.HookBridge;
import ps.reso.instaeclipse.hook.HookHelpers;
import ps.reso.instaeclipse.hook.HostApp;
import ps.reso.instaeclipse.hook.MethodHook;
import ps.reso.instaeclipse.hook.ModuleResources;
import ps.reso.instaeclipse.mods.ads.AdBlocker;
import ps.reso.instaeclipse.mods.feed.FeedPhotoZoomHook;
import ps.reso.instaeclipse.mods.location.LocationSpoofHook;
import ps.reso.instaeclipse.utils.log.Logging;
import ps.reso.instaeclipse.mods.media.ForceReelQualityHook;
import ps.reso.instaeclipse.mods.feed.HideSuggestedFeedItemsHook;
import ps.reso.instaeclipse.mods.ads.TrackingLinkDisable;
import ps.reso.instaeclipse.mods.devops.BuildExpiredPopupHook;
import ps.reso.instaeclipse.mods.devops.DevOptionsUnlockHook;
import ps.reso.instaeclipse.mods.ghost.GhostChannelMarkAsReadHook;
import ps.reso.instaeclipse.mods.ghost.GhostDMMarkAsReadHook;
import ps.reso.instaeclipse.mods.ghost.GhostDMSeenHook;
import ps.reso.instaeclipse.mods.ghost.GhostEphemeralKeepHook;
import ps.reso.instaeclipse.mods.ghost.GhostPermanentViewHook;
import ps.reso.instaeclipse.mods.ghost.ViewOnceBadgeHook;
import ps.reso.instaeclipse.mods.ghost.KeepUnsentMessagesHook;
import ps.reso.instaeclipse.mods.ghost.GhostScreenshotDetectionHook;
import ps.reso.instaeclipse.mods.ghost.GhostStorySeenHook;
import ps.reso.instaeclipse.mods.ghost.GhostTypingIndicatorHook;
import ps.reso.instaeclipse.mods.ghost.GhostViewOnceHook;
import ps.reso.instaeclipse.mods.ghost.ScreenshotPermissionHook;
import ps.reso.instaeclipse.mods.media.FeedVideoDownloadHook;
import ps.reso.instaeclipse.mods.media.PostDownloadContextMenuHook;
import ps.reso.instaeclipse.mods.media.ProfilePicDownloadHook;
import ps.reso.instaeclipse.mods.media.ReelDownloadHook;
import ps.reso.instaeclipse.mods.media.StoryDownloadHook;
import ps.reso.instaeclipse.mods.misc.CommentCopyHook;
import ps.reso.instaeclipse.mods.misc.CaptionCopyContextMenuHook;
import ps.reso.instaeclipse.mods.misc.DisableDoubleTapLikeHook;
import ps.reso.instaeclipse.mods.misc.DisableStoryFlippingHook;
import ps.reso.instaeclipse.mods.misc.DisableVideoAutoPlayHook;
import ps.reso.instaeclipse.mods.misc.StoryMentionHook;
import ps.reso.instaeclipse.mods.network.IGNetworkInterceptor;
import ps.reso.instaeclipse.mods.ui.UIHookManager;
import ps.reso.instaeclipse.mods.ui.theme.IgThemeEngine;
import ps.reso.instaeclipse.mods.ui.theme.IgThemeHook;
import ps.reso.instaeclipse.utils.core.CommonUtils;
import ps.reso.instaeclipse.utils.core.IpcSecurity;
import ps.reso.instaeclipse.utils.core.LazyDexKit;
import ps.reso.instaeclipse.utils.core.RemotePrefs;
import ps.reso.instaeclipse.utils.core.DexKitCache;
import ps.reso.instaeclipse.utils.core.SettingsManager;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.feature.FeatureManager;
import ps.reso.instaeclipse.utils.log.ModuleLog;


/**
 * Module entry point for the modern libxposed API (API 101), registered in
 * {@code META-INF/xposed/java_init.list}. The framework creates one instance per hooked process.
 */
@SuppressLint("UnsafeDynamicallyLoadedCode")
public class Module extends XposedModule {
    // List of supported Instagram package names (maintained in CommonUtils)
    private static final List<String> SUPPORTED_PACKAGES = CommonUtils.SUPPORTED_PACKAGES;
    /** Opened only on a DexKitCache miss; closed once all hooks are installed. */
    public static LazyDexKit dexKitBridge;
    public static ClassLoader hostClassLoader;
    public static String moduleSourceDir;
    private static String moduleLibDir;
    private static String processName;
    /** Set once all hooks were installed for the current Instagram version (see installFeatureHooks). */
    private static final String CACHE_COMPLETE_KEY = "_install_complete";

    @Override
    public void onModuleLoaded(@NonNull ModuleLoadedParam param) {
        HookBridge.attach(this);
        processName = param.getProcessName();

        android.content.pm.ApplicationInfo moduleInfo = getModuleApplicationInfo();
        moduleSourceDir = moduleInfo.sourceDir;
        moduleLibDir = moduleInfo.nativeLibraryDir;
        if (moduleLibDir == null || !new java.io.File(moduleLibDir, "libdexkit.so").exists()) {
            moduleLibDir = legacyLibDir(moduleSourceDir);
        }
    }

    /** Fallback for frameworks that don't fill nativeLibraryDir: {@code <apk dir>/lib/<abi>}. */
    private static String legacyLibDir(String apkPath) {
        String abi = Build.SUPPORTED_ABIS[0];
        String abiFolder;
        if (abi.equalsIgnoreCase("arm64-v8a")) abiFolder = "arm64";
        else if (abi.equalsIgnoreCase("armeabi-v7a") || abi.equalsIgnoreCase("armeabi") || abi.equalsIgnoreCase("armv8i"))
            abiFolder = "arm";
        else if (abi.equalsIgnoreCase("x86")) abiFolder = "x86";
        else if (abi.equalsIgnoreCase("x86_64")) abiFolder = "x86_64";
        else abiFolder = abi;
        return apkPath.substring(0, apkPath.lastIndexOf("/")) + "/lib/" + abiFolder;
    }

    @Override
    public void onPackageReady(@NonNull PackageReadyParam param) {
        // A process can load extra packages (createPackageContext with code); only the
        // app's own first package is Instagram.
        if (!param.isFirstPackage()) return;
        String packageName = param.getPackageName();

        // Only Instagram's main process: secondary processes such as ":fbns" (push) never show
        // UI, yet installing every hook there cost ~2 s of CPU, a DexKit index and memory.
        if (processName != null && !processName.equals(packageName)) return;

        // Hook into Instagram and its clones
        if (SUPPORTED_PACKAGES.contains(packageName)) {
            try {
                if (dexKitBridge == null) {
                    // Nothing is loaded or indexed yet: libdexkit.so and the APK index are only
                    // opened if a hook misses DexKitCache.
                    dexKitBridge = new LazyDexKit(param.getApplicationInfo().sourceDir,
                            moduleLibDir + "/libdexkit.so");
                }

                // Use the target app's ClassLoader
                hostClassLoader = param.getClassLoader();

                // Call the method to hook the target app
                hookInstagram(packageName, hostClassLoader);

            } catch (Throwable e) {
                ModuleLog.line("(InstaEclipse): Failed to initialize hooks for " + packageName + ": " + e.getMessage());
            }
        }
    }

    /** Download folder chosen in the companion app, shared through framework remote prefs. */
    private void loadSharedDownloaderFolder() {
        try {
            android.content.SharedPreferences rp = getRemotePreferences(RemotePrefs.GROUP);
            String path = rp.getString(RemotePrefs.KEY_DOWNLOADER_PATH, "");
            String uri  = rp.getString(RemotePrefs.KEY_DOWNLOADER_URI,  "");
            if (!path.isEmpty()) FeatureFlags.downloaderCustomPath = path;
            if (!uri.isEmpty())  FeatureFlags.downloaderCustomUri  = uri;
        } catch (Throwable ignored) {
            // Framework without remote preferences (e.g. embedded mode): rely on the sync broadcast.
        }
    }

    private void hookInstagram(String packageName, ClassLoader classLoader) {
        try {
            // Application.attach(Context) is the earliest point with a usable Context, before
            // Instagram's own attachBaseContext work (e.g. ViewBinding pre-inflation).
            HookHelpers.findAndHookMethod("android.app.Application", classLoader, "attach", Context.class, new MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    HostApp.set((android.app.Application) param.thisObject);
                    onBeforeApplicationAttach((Context) param.args[0]);
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    onAfterApplicationAttach((Context) param.args[0], classLoader);
                }
            });
        } catch (Throwable attachError) {
            // attach() is a hidden API. If the framework doesn't exempt the module from hidden-API
            // checks, fall back to the public Instrumentation hook, which runs right after
            // attach() and before Application.onCreate().
            ModuleLog.line("(InstaEclipse): Application.attach hook unavailable ("
                    + attachError.getMessage() + "), using Instrumentation fallback");
            try {
                HookHelpers.findAndHookMethod(android.app.Instrumentation.class, "callApplicationOnCreate",
                        android.app.Application.class, new MethodHook() {
                            private boolean done;

                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                if (done) return;
                                done = true;
                                android.app.Application app = (android.app.Application) param.args[0];
                                HostApp.set(app);
                                onBeforeApplicationAttach(app);
                                onAfterApplicationAttach(app, classLoader);
                            }
                        });
            } catch (Throwable e) {
                ModuleLog.line("(InstaEclipse): Failed to hook " + packageName + ": " + e.getMessage());
            }
        }
    }

    /** Runs before Instagram's Application is attached: settings and DexKit cache. */
    private void onBeforeApplicationAttach(Context context) {
        // Install CommentCopyButtonHook BEFORE Instagram's Application.attach() runs
        // so we catch any ViewBinding pre-inflation that happens during attach()
        SettingsManager.init(context);
        SettingsManager.loadAllFlags(context);

        // Init DexKit cache — checks IG version to decide if saved descriptors are valid.
        // Must run before any hook that calls DexKitCache.isCacheValid().
        try {
            android.content.pm.PackageInfo pi =
                    context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            long vc = pi.getLongVersionCode();
            DexKitCache.init(context, String.valueOf(vc));
        } catch (Throwable e) {
            ModuleLog.line("(DexKitCache) ❌ init failed: " + e.getMessage());
        }
    }

    /** Runs once Instagram's Application has a base context: installs every feature hook. */
    private void onAfterApplicationAttach(Context context, ClassLoader classLoader) {
        // Setup context, preferences
        SettingsManager.init(context);
        SettingsManager.loadAllFlags(context);
        ModuleResources.init(context);

        // In-app log viewer: every ModuleLog.line(...) call across the hook codebase
        // appends to this buffer, which the companion app can read via IPC.
        Logging.init(context, "instaeclipse_module.log");

        // Pull the downloader folder chosen in the companion app so it's available
        // even when Instagram was started without ever receiving the sync broadcast.
        loadSharedDownloaderFolder();

        FeatureManager.refreshFeatureStatus(); // Update internal feature states

        // Activate the LSPosed Sync Bridge to listen to FeaturesFragment updates
        registerSyncReceiver(context);

        try {
            UIHookManager.registerConfigImportReceiver(context);
        } catch (Throwable e) {
            ModuleLog.line("(InstaEclipse | ImportReceiver): ❌ " + e.getMessage());
        }
        try {
            UIHookManager.registerSettingsRestoreReceiver(context);
        } catch (Throwable e) {
            ModuleLog.line("(InstaEclipse | RestoreReceiver): ❌ " + e.getMessage());
        }
        if (DexKitCache.isCacheValid() && "1".equals(DexKitCache.loadString(CACHE_COMPLETE_KEY))) {
            // Warm cache: installation is quick (reflection only), so hooks are in place before
            // Instagram's own startup code runs.
            installFeatureHooks(classLoader);
        } else {
            // First launch for this Instagram version: DexKit has to scan the APK, which takes
            // well over 10 s. Doing that inside Application.attach made Android 16 kill the app
            // ("failed to complete startup") before the cache was even written, so the next
            // launch repeated it. Resolve in the background instead; features become active a
            // few seconds after startup, and from the next launch on everything is cached.
            // (Also taken when a previous installation was interrupted and the cache is partial.)
            ModuleLog.line("(InstaEclipse): first launch for this Instagram version — installing hooks in background");
            Thread t = new Thread(() -> {
                long start = android.os.SystemClock.elapsedRealtime();
                installFeatureHooks(classLoader);
                ModuleLog.line("(InstaEclipse): background hook installation took "
                        + (android.os.SystemClock.elapsedRealtime() - start) + " ms");
            }, "InstaEclipse-Init");
            t.setPriority(Thread.NORM_PRIORITY - 1);
            t.start();
        }
    }

    /** Installs every feature hook (DexKit lookups included) and releases DexKit afterwards. */
    private interface HookInstaller {
        void install() throws Throwable;
    }

    private static void installExtra(String tag, HookInstaller installer) {
        try {
            installer.install();
        } catch (Throwable t) {
            ModuleLog.line("(InstaEclipse | " + tag + "): ❌ Failed to hook: " + t);
        }
    }

    private void installFeatureHooks(ClassLoader classLoader) {
        UIHookManager instagramUI = new UIHookManager();
        instagramUI.mainActivity(hostClassLoader);

        IGNetworkInterceptor interceptor = new IGNetworkInterceptor();

        // --- Feature Hooks ---

        // Developer Options
        try {
            new DevOptionsUnlockHook().handleDevOptions(dexKitBridge);
        } catch (Throwable ignored) {
            ModuleLog.line("(InstaEclipse | DevOptions): ❌ Failed to hook");
        }

        // Ghost Mode
        try {
            new GhostDMSeenHook().handleSeenBlock(dexKitBridge); // DM Seen
            new GhostDMMarkAsReadHook().install(classLoader); // Mark as Read Button
            new GhostChannelMarkAsReadHook().install(classLoader); // Channel Mark as Read Button
        } catch (Throwable ignored) {
            ModuleLog.line("(InstaEclipse | GhostSeen): ❌ Failed to hook");
        }

        try {
            new GhostTypingIndicatorHook().handleTypingBlock(dexKitBridge); // DM Typing
        } catch (Throwable ignored) {
            ModuleLog.line("(InstaEclipse | GhostTyping): ❌ Failed to hook");
        }

        try {
            new GhostScreenshotDetectionHook().handleScreenshotBlock(dexKitBridge); // Screenshot
        } catch (Throwable ignored) {
            ModuleLog.line("(InstaEclipse | GhostScreenshot): ❌ Failed to hook");
        }

        try {
            new ScreenshotPermissionHook().install(classLoader); // Allow Screenshots
        } catch (Throwable ignored) {
            ModuleLog.line("(InstaEclipse | ScreenshotPermission): ❌ Failed to hook");
        }

        try {
            new GhostViewOnceHook().handleViewOnceBlock(dexKitBridge); // View Once
        } catch (Throwable ignored) {
            ModuleLog.line("(InstaEclipse | GhostViewOnce): ❌ Failed to hook");
        }

        try {
            new GhostStorySeenHook().handleStorySeenBlock(dexKitBridge); // Story Seen
        } catch (Throwable ignored) {
            ModuleLog.line("(InstaEclipse | GhostStorySeen): ❌ Failed to hook");
        }

        try {
            new KeepUnsentMessagesHook().install(dexKitBridge, classLoader); // Keep Unsent
        } catch (Throwable ignored) {
            ModuleLog.line("(InstaEclipse | KeepUnsent): ❌ Failed to hook");
        }

        try {
            new ps.reso.instaeclipse.mods.ghost.UnsentThreadButtonHook().install(classLoader); // per-thread unsent button
        } catch (Throwable ignored) {
            ModuleLog.line("(InstaEclipse | UnsentBtn): ❌ Failed to hook");
        }

        try {
            new ps.reso.instaeclipse.mods.ghost.HideChatsHook().install(dexKitBridge, classLoader); // Hide Specific Chats
        } catch (Throwable ignored) {
            ModuleLog.line("(InstaEclipse | HideChats): ❌ Failed to hook");
        }

        try {
            new ps.reso.instaeclipse.mods.ui.CustomFontHook().install(classLoader); // Custom UI font
        } catch (Throwable ignored) {
            ModuleLog.line("(InstaEclipse | CustomFont): ❌ Failed to hook");
        }

        try {
            ps.reso.instaeclipse.mods.ui.RemoveMetaAIHook metaAi = new ps.reso.instaeclipse.mods.ui.RemoveMetaAIHook();
            metaAi.install(classLoader);              // composer/search XML layouts
            metaAi.installReels(dexKitBridge, classLoader); // reels Litho unit (#179)
        } catch (Throwable ignored) {
            ModuleLog.line("(InstaEclipse | RemoveMetaAI): ❌ Failed to hook");
        }

        // Disable Repost (feed + reels) — UI/action level; network drop is ineffective
        try {
            new ps.reso.instaeclipse.mods.ui.DisableRepostHook().install(dexKitBridge, classLoader);
        } catch (Throwable ignored) {
            ModuleLog.line("(InstaEclipse | DisableRepost): ❌ Failed to hook");
        }

        try {
            new ps.reso.instaeclipse.mods.ui.LockDirectMessagesHook().install(classLoader); // Lock DMs (#182)
        } catch (Throwable ignored) {
            ModuleLog.line("(InstaEclipse | LockDMs): ❌ Failed to hook");
        }

        // Hide in-feed widget units (suggested users panels, surveys, carousels, etc.)
        try {
            new HideSuggestedFeedItemsHook().install(dexKitBridge, hostClassLoader);
        } catch (Throwable ignored) {
            ModuleLog.line("(InstaEclipse | HideSuggested): ❌ Failed to hook");
        }

        // Following-only home feed
        try {
            new ps.reso.instaeclipse.mods.feed.FollowingOnlyFeedHook().install(dexKitBridge, classLoader);
        } catch (Throwable ignored) {
            ModuleLog.line("(InstaEclipse | FollowingFeed): ❌ Failed to hook");
        }

        // Ads Blocker
        try {
            new AdBlocker().disableSponsoredContent(dexKitBridge, hostClassLoader);
        } catch (Throwable ignored) {
            ModuleLog.line("(InstaEclipse | AdBlocker): ❌ Failed to hook");
        }

        // tracking link disable
        try {
            new TrackingLinkDisable().disableTrackingLinks(hostClassLoader);
        } catch (Throwable ignored) {
            ModuleLog.line("(InstaEclipse | TrackingLinkDisable): ❌ Failed to hook");
        }

        // Extras (ported from a JTInstagram review)
        installExtra("UnlimitedAccounts", () -> new ps.reso.instaeclipse.mods.extras.AccountLimitHook().install(dexKitBridge, classLoader));
        installExtra("StoryTime", () -> new ps.reso.instaeclipse.mods.extras.StoryTimestampHook().install(dexKitBridge, classLoader));
        installExtra("Reels", () -> new ps.reso.instaeclipse.mods.extras.ReelsControlsHook().install(dexKitBridge, classLoader));
        installExtra("Airplane", () -> new ps.reso.instaeclipse.mods.extras.AirplaneModeHook().install(classLoader));
        installExtra("ShareSheetGroup", () -> new ps.reso.instaeclipse.mods.extras.ShareSheetGroupHook().install(classLoader));
        installExtra("StartupTab", () -> new ps.reso.instaeclipse.mods.extras.StartupTabHook().install(classLoader));
        installExtra("SwipeCamera", () -> new ps.reso.instaeclipse.mods.extras.SwipeToCameraHook().install(classLoader));
        installExtra("DoubleTapExtras", () -> new ps.reso.instaeclipse.mods.extras.DoubleTapExtrasHook().install(dexKitBridge, classLoader));

        // Open links in the external browser
        try {
            new ps.reso.instaeclipse.mods.misc.OpenLinksExternallyHook().install();
        } catch (Throwable ignored) {
            ModuleLog.line("(InstaEclipse | ExtLinks): ❌ Failed to hook");
        }

        // Miscellaneous
        try {
            new DisableStoryFlippingHook().handleStoryFlippingDisable(dexKitBridge); // Story Flipping
        } catch (Throwable ignored) {
            ModuleLog.line("(InstaEclipse | StoryFlipping): ❌ Failed to hook");
        }

        // Story Mentions
        try {
            new StoryMentionHook().install(dexKitBridge, classLoader);
        } catch (Throwable ignored) {
            ModuleLog.line("(InstaEclipse | StoryMentions): ❌ Failed to hook");
        }

        // Comment Copy
        try {
            new CommentCopyHook().install(dexKitBridge, classLoader);
        } catch (Throwable ignored) {
            ModuleLog.line("(InstaEclipse | CopyComment): ❌ Failed to hook");
        }

        // Caption Copy
        try {
            new CaptionCopyContextMenuHook().install(dexKitBridge, classLoader);
        } catch (Throwable ignored) {
            ModuleLog.line("(InstaEclipse | Caption): ❌ Failed to hook");
        }

        // Disable Double Tap to Like
        try {
            new DisableDoubleTapLikeHook().install(dexKitBridge, classLoader);
        } catch (Throwable ignored) {
            ModuleLog.line("(InstaEclipse | DoubleTapLike): ❌ Failed to hook");
        }

        // Photo Zoom (long-press)
        try {
            new FeedPhotoZoomHook().install(classLoader);
        } catch (Throwable ignored) {
            ModuleLog.line("(InstaEclipse | PhotoZoom): ❌ Failed to hook");
        }

        // Location Spoof
        try {
            new LocationSpoofHook().install(classLoader);
        } catch (Throwable ignored) {
            ModuleLog.line("(InstaEclipse | SpoofLocation): ❌ Failed to hook");
        }

        // Custom Theme
        try {
            new IgThemeHook().install(hostClassLoader);
        } catch (Throwable ignored) {
            ModuleLog.line("(InstaEclipse | Theme): ❌ Failed to hook");
        }

        // Force Reel Quality
        try {
            new ForceReelQualityHook().install(dexKitBridge, classLoader);
        } catch (Throwable ignored) {
            ModuleLog.line("(InstaEclipse | ForceReelQuality): ❌ Failed to hook");
        }

        try {
            new DisableVideoAutoPlayHook().handleAutoPlayDisable(dexKitBridge); // Video Autoplay
        } catch (Throwable ignored) {
            ModuleLog.line("(InstaEclipse | AutoPlayDisable): ❌ Failed to hook");
        }

        // Build Expired Popup
        try {
            new BuildExpiredPopupHook().install(dexKitBridge, classLoader);
        } catch (Throwable ignored) {
            ModuleLog.line("(InstaEclipse | BuildExpired): ❌ Failed to hook");
        }

        // Media Download (feed)
        try {
            new FeedVideoDownloadHook().install(classLoader);
            FeedVideoDownloadHook.installVideoUrlCaptureHook(dexKitBridge, classLoader);
        } catch (Throwable ignored) {
            ModuleLog.line("(InstaEclipse | MediaDownload): ❌ Failed to hook");
        }

        // Post Download — three-dots menu (replaces floating button + long-press)
        try {
            new PostDownloadContextMenuHook().install(dexKitBridge, classLoader);
        } catch (Throwable ignored) {
            ModuleLog.line("(InstaEclipse | PostDownload): ❌ Failed to hook");
        }

        // Save Instants (#184) — long-press a received Instant (quicksnap) to save it
        try {
            new ps.reso.instaeclipse.mods.media.InstantSaveHook().install(classLoader);
        } catch (Throwable ignored) {
            ModuleLog.line("(InstaEclipse | InstantSave): ❌ Failed to hook");
        }

        // Upload Instants from gallery (#199) — swap gallery bitmap into quicksnap send
        try {
            new ps.reso.instaeclipse.mods.media.InstantUploadHook().install(classLoader);
        } catch (Throwable ignored) {
            ModuleLog.line("(InstaEclipse | InstantUpload): ❌ Failed to hook");
        }

        // Keep Ephemeral Messages
        try {
            new GhostEphemeralKeepHook().install(dexKitBridge, classLoader);
        } catch (Throwable ignored) {
            ModuleLog.line("(InstaEclipse | EphemeralHook): ❌ Failed to hook");
        }

        // Permanent View Mode (view-once / view-twice → permanent)
        try {
            new GhostPermanentViewHook().install(dexKitBridge, classLoader);
        } catch (Throwable ignored) {
            ModuleLog.line("(InstaEclipse | ViewOnceMedia): ❌ Failed to hook");
        }

        // Restore IG's native view-once/twice corner icon when Permanent View is on
        try {
            new ViewOnceBadgeHook().install(dexKitBridge, classLoader);
        } catch (Throwable ignored) {
            ModuleLog.line("(InstaEclipse | VOBadge): ❌ Failed to hook");
        }

        // Story Download
        try {
            new StoryDownloadHook().install(dexKitBridge, classLoader);
        } catch (Throwable ignored) {
            ModuleLog.line("(InstaEclipse | StoryDownload): ❌ Failed to hook");
        }

        // Reel Download
        try {
            new ReelDownloadHook().install(dexKitBridge, classLoader);
        } catch (Throwable ignored) {
            ModuleLog.line("(InstaEclipse | ReelDownload): ❌ Failed to hook");
        }

        // Profile Picture Download
        try {
            ProfilePicDownloadHook.install();
        } catch (Throwable ignored) {
            ModuleLog.line("(InstaEclipse | ProfileDownload): ❌ Failed to hook");
        }

        // Network Interceptor
        try {
            interceptor.handleInterceptor(classLoader);
        } catch (Throwable ignored) {
            ModuleLog.line("(InstaEclipse | Interceptor): ❌ Failed to hook");
        }

        // Crash guard: drop tasks rejected by already-shut-down executors (carousel/
        // realtime teardown race on IG 446+/447.0.0.39+) instead of letting AbortPolicy
        // throw and hard-crash the app.
        try {
            new ps.reso.instaeclipse.mods.core.TerminatedExecutorGuard().install(classLoader);
        } catch (Throwable ignored) {
            ModuleLog.line("(InstaEclipse | ExecGuard): ❌ Failed to hook");
        }

        // All hooks are installed: release DexKit's native index of Instagram's dex files.
        // (A later query would transparently reopen it.)
        ModuleLog.line("(IE|DexKit) startup " + (dexKitBridge.wasUsed() ? "used DexKit" : "served entirely from cache"));
        dexKitBridge.close();
        // Every installer ran to completion, so the cache holds all lookups for this version.
        DexKitCache.saveString(CACHE_COMPLETE_KEY, "1");
    }

    /**
     * Injects a dynamic receiver into Instagram to listen for settings changes
     * sent from the InstaEclipse companion app (FeaturesFragment staging system).
     */
    private static final java.util.Set<String> IPC_PRIVATE_PREFS =
            new java.util.HashSet<>(java.util.Arrays.asList("lockDirectPasscode", "lockDirectSalt"));

    private void registerSyncReceiver(Context context) {
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                String action = intent.getAction();
                if ("ps.reso.instaeclipse.ACTION_UPDATE_PREF".equals(action)) {
                    String key = intent.getStringExtra("key");
                    boolean value = intent.getBooleanExtra("value", false);

                    ModuleLog.line("(InstaEclipse) Sync: Updating " + key + " to " + value);

                    android.content.SharedPreferences prefs = ctx.getSharedPreferences("instaeclipse_prefs", Context.MODE_PRIVATE);
                    prefs.edit().putBoolean(key, value).apply();

                    SettingsManager.loadAllFlags(ctx);
                    FeatureManager.refreshFeatureStatus();
                    IgThemeEngine.invalidate();
                    IgThemeHook.refreshCurrentActivity();
                    if ("airplaneMode".equals(key)) ps.reso.instaeclipse.mods.extras.AirplaneModeHook.apply();

                } else if ("ps.reso.instaeclipse.ACTION_UPDATE_PREF_STRING".equals(action)) {
                    String key = intent.getStringExtra("key");
                    String value = intent.getStringExtra("value");

                    if (key == null || IPC_PRIVATE_PREFS.contains(key)) return;
                    ModuleLog.line("(InstaEclipse) Sync: Updating string pref " + key);

                    android.content.SharedPreferences prefs = ctx.getSharedPreferences("instaeclipse_prefs", Context.MODE_PRIVATE);
                    prefs.edit().putString(key, value).apply();

                    SettingsManager.loadAllFlags(ctx);
                    IgThemeEngine.invalidate();
                    IgThemeHook.refreshCurrentActivity();

                } else if ("ps.reso.instaeclipse.ACTION_UPDATE_PREF_INT".equals(action)) {
                    String key = intent.getStringExtra("key");
                    int value = intent.getIntExtra("value", 0);

                    ModuleLog.line("(InstaEclipse) Sync: Updating int pref " + key + " to " + value);

                    android.content.SharedPreferences prefs = ctx.getSharedPreferences("instaeclipse_prefs", Context.MODE_PRIVATE);
                    prefs.edit().putInt(key, value).apply();

                    SettingsManager.loadAllFlags(ctx);
                    FeatureManager.refreshFeatureStatus();
                    IgThemeEngine.invalidate();
                    IgThemeHook.refreshCurrentActivity();

                } else if (CommonUtils.ACTION_REQUEST_LOGS.equals(action)) {
                    try {
                        Intent reply = new Intent(CommonUtils.ACTION_LOGS_REPLY);
                        reply.setPackage(CommonUtils.MY_PACKAGE_NAME);
                        reply.putExtra(CommonUtils.EXTRA_LOG_TEXT, Logging.getSnapshotForIpc());
                        reply.putExtra(CommonUtils.EXTRA_LOG_SOURCE, ctx.getPackageName());
                        IpcSecurity.echoNonce(intent, reply);
                        ctx.sendBroadcast(reply);
                    } catch (Throwable t) {
                        Intent reply = new Intent(CommonUtils.ACTION_LOGS_REPLY);
                        reply.setPackage(CommonUtils.MY_PACKAGE_NAME);
                        reply.putExtra(CommonUtils.EXTRA_LOG_ERROR, String.valueOf(t.getMessage()));
                        reply.putExtra(CommonUtils.EXTRA_LOG_SOURCE, ctx.getPackageName());
                        IpcSecurity.echoNonce(intent, reply);
                        ctx.sendBroadcast(reply);
                    }

                } else if (CommonUtils.ACTION_CLEAR_LOGS.equals(action)) {
                    Logging.clear();

                } else if ("ps.reso.instaeclipse.ACTION_REQUEST_PREFS".equals(action)) {
                    ModuleLog.line("(InstaEclipse) Sync: Companion app requested current preferences.");

                    android.content.SharedPreferences prefs = ctx.getSharedPreferences("instaeclipse_prefs", Context.MODE_PRIVATE);
                    Intent reply = new Intent("ps.reso.instaeclipse.ACTION_SEND_PREFS");
                    reply.setPackage("ps.reso.instaeclipse");

                    Bundle bundle = new Bundle();
                    for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
                        // The DM-lock hash + salt never leave Instagram's process.
                        if (IPC_PRIVATE_PREFS.contains(entry.getKey())) continue;
                        if (entry.getValue() instanceof Boolean) {
                            bundle.putBoolean(entry.getKey(), (Boolean) entry.getValue());
                        } else if (entry.getValue() instanceof String) {
                            bundle.putString(entry.getKey(), (String) entry.getValue());
                        } else if (entry.getValue() instanceof Integer) {
                            bundle.putInt(entry.getKey(), (Integer) entry.getValue());
                        }
                    }
                    reply.putExtras(bundle);
                    IpcSecurity.echoNonce(intent, reply);
                    ctx.sendBroadcast(reply);

                } else if ("ps.reso.instaeclipse.ACTION_EXPORT_CONFIG".equals(action)) {
                    ModuleLog.line("(InstaEclipse) Sync: Companion app requested Dev Config export.");
                    try {
                        java.io.File source = new java.io.File(ctx.getFilesDir(), "mobileconfig/mc_overrides.json");
                        if (!source.exists()) {
                            ModuleLog.line("(InstaEclipse) Export: mc_overrides.json not found.");
                            Intent reply = new Intent("ps.reso.instaeclipse.ACTION_SEND_CONFIG");
                            reply.setPackage("ps.reso.instaeclipse");
                            reply.putExtra("error", "mc_overrides.json not found.");
                            IpcSecurity.echoNonce(intent, reply);
                            ctx.sendBroadcast(reply);
                            return;
                        }
                        StringBuilder sb = new StringBuilder();
                        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(source))) {
                            String line;
                            while ((line = reader.readLine()) != null) sb.append(line).append("\n");
                        }
                        Intent reply = new Intent("ps.reso.instaeclipse.ACTION_SEND_CONFIG");
                        reply.setPackage("ps.reso.instaeclipse");
                        reply.putExtra("json_content", sb.toString().trim());
                        IpcSecurity.echoNonce(intent, reply);
                        ctx.sendBroadcast(reply);
                        ModuleLog.line("(InstaEclipse) Export: config reply sent to companion.");
                    } catch (Exception e) {
                        ModuleLog.line("(InstaEclipse) Export: failed: " + e.getMessage());
                    }

                } else if ("ps.reso.instaeclipse.ACTION_BACKUP_SETTINGS".equals(action)) {
                    ModuleLog.line("(InstaEclipse) Sync: Companion app requested Settings backup.");
                    try {
                        String json = ps.reso.instaeclipse.utils.backup.SettingsBackupManager.toJson();
                        Intent exportIntent = new Intent();
                        exportIntent.setComponent(new android.content.ComponentName("ps.reso.instaeclipse", "ps.reso.instaeclipse.mods.devops.config.JsonExportActivity"));
                        exportIntent.putExtra("json_content", json);
                        exportIntent.putExtra("file_name", "instaeclipse_settings.json");
                        exportIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        ctx.startActivity(exportIntent);
                    } catch (Exception e) {
                        ModuleLog.line("(InstaEclipse) Failed to create backup: " + e.getMessage());
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction("ps.reso.instaeclipse.ACTION_UPDATE_PREF");
        filter.addAction("ps.reso.instaeclipse.ACTION_UPDATE_PREF_STRING");
        filter.addAction("ps.reso.instaeclipse.ACTION_UPDATE_PREF_INT");
        filter.addAction(CommonUtils.ACTION_REQUEST_LOGS);
        filter.addAction(CommonUtils.ACTION_CLEAR_LOGS);
        filter.addAction("ps.reso.instaeclipse.ACTION_REQUEST_PREFS");
        filter.addAction("ps.reso.instaeclipse.ACTION_EXPORT_CONFIG");
        filter.addAction("ps.reso.instaeclipse.ACTION_BACKUP_SETTINGS");

        // Only the companion (holder of our signature permission) may drive these actions.
        IpcSecurity.registerCompanionOnlyReceiver(context, receiver, filter);
    }
}
