package ps.reso.instaeclipse.utils.feature;

/**
 * Feature switches. Written on the main thread (sync receiver, in-app menu) and read from hooks
 * on any thread (network, decoding, UI), so every field is volatile to make changes visible.
 */
public class FeatureFlags {

    // Dev Options
    public static volatile boolean isDevEnabled = false;

    // Ghost Mode
    public static volatile boolean isGhostModeEnabled = false;
    public static volatile boolean isGhostSeen = false;
    public static volatile boolean isGhostTyping = false;
    public static volatile boolean isGhostScreenshot = false;
    public static volatile boolean isGhostViewOnce = false;
    public static volatile boolean isGhostStory = false;
    public static volatile boolean isGhostLive = false;
    public static volatile boolean allowScreenshots = false;
    public static volatile boolean keepEphemeralMessages = false;
    public static volatile boolean permanentViewMode = false;
    public static volatile boolean keepUnsentMessages = false;

    // Auto-clear cache
    public static volatile boolean autoClearCache = false;
    public static volatile int autoClearCacheSizeMb = 100;

    // Remove Meta AI (#179)
    public static volatile boolean removeMetaAI = false;

    // Lock DMs (#182) — passcode stored as salted SHA-256 hash (never plaintext)
    public static volatile boolean lockDirectMessages = false;
    public static volatile String lockDirectPasscode = "";
    public static volatile String lockDirectSalt = ""; // per-install random salt; "" = legacy unsalted
    public static volatile boolean lockDirectAlways = false; // re-lock whenever leaving the inbox (not just on app close)
    public static volatile boolean lockWholeApp = false; // lock the ENTIRE app on launch/return (same passcode as Lock DMs)
    public static volatile boolean lockUseFingerprint = true; // offer biometric unlock when the device has one enrolled
    public static volatile boolean hideSpecificChats = false; // hide chosen DM threads from the inbox (per-thread)

    // Which ghost mode features the quick toggle will control
    public static volatile boolean quickToggleSeen = false;
    public static volatile boolean quickToggleTyping = false;
    public static volatile boolean quickToggleScreenshot = false;
    public static volatile boolean quickToggleViewOnce = false;
    public static volatile boolean quickToggleStory = false;
    public static volatile boolean quickToggleLive = false;
    public static volatile boolean quickToggleEphemeral = false;
    public static volatile boolean quickTogglePermanentView = false;
    public static volatile boolean quickToggleAllowScreenshots = false;


    // Distraction Free
    public static volatile boolean isExtremeMode = false; // Extreme Mode
    public static volatile boolean isDistractionFree = false;
    public static volatile boolean disableStories = false;
    public static volatile boolean disableFeed = false;
    public static volatile boolean disableReels = false;
    public static volatile boolean disableReelsExceptDM = false;
    public static volatile boolean disableExplore = false;
    public static volatile boolean disableComments = false;

    // Ads and Analytics
    public static volatile boolean isAdBlockEnabled = false;
    public static volatile boolean isAnalyticsBlocked = false;
    public static volatile boolean disableTrackingLinks = false;

    // Misc Options
    public static volatile boolean isMiscEnabled = false;
    public static volatile boolean disableStoryFlipping = false;
    public static volatile boolean disableVideoAutoPlay = false;
    public static volatile boolean spoofLastSeen = false;
    public static volatile boolean showFollowerToast = false;
    public static volatile boolean showFeatureToasts = false;
    public static volatile boolean disableRepost = false;


    public static volatile boolean enableStoryMentions = false;
    public static volatile boolean disableDiscoverPeople = false;
    public static volatile boolean removeBuildExpiredPopup = false;
    public static volatile boolean enableCopyComment = false;
    public static volatile boolean enableCaptionCopy = false;
    public static volatile boolean openLinksExternally = false;

    // Extras (ported from a JTInstagram review)
    public static volatile boolean unlimitedAccounts = false;
    public static volatile boolean storyExactTime = false;
    public static volatile boolean reelsDisableTapPause = false;
    public static volatile boolean reelsAutoScroll = false;
    public static volatile boolean reelsLockScroll = false;
    public static volatile boolean airplaneMode = false;
    public static volatile boolean hideShareSheetGroup = false;
    public static volatile boolean disableSwipeToCamera = false;
    public static volatile String startupTab = "";
    public static volatile String customShareDomain = "";
    public static volatile boolean disableDoubleTapLike = false;
    public static volatile boolean enablePhotoZoom = false;

    // Location Spoof
    public static volatile boolean spoofLocation = false;
    public static volatile double spoofLat = 0.0;
    public static volatile double spoofLng = 0.0;
    public static volatile String spoofLabel = "";   // place name for spoofLat/spoofLng
    public static volatile String spoofRecent = "";  // JSON history, see LocationPresets

    // Video Quality (0 = auto/off, else desired height in px, or Integer.MAX_VALUE for max available)
    public static volatile int forceReelQuality = 0;

    // Custom Theme (themePresetId: 0 = custom palette from themePaletteJson, else a built-in preset id)
    public static volatile boolean customThemeEnabled = false;
    public static volatile int themePresetId = 1;
    public static volatile String themePaletteJson = "";

    // Clean Feed
    public static volatile boolean hideSuggestionsInFeed = false;
    public static volatile boolean hideThreadsSuggestions = false;
    public static volatile boolean followingOnlyFeed = false;

    // Downloader
    public static volatile boolean enablePostDownload = false;
    public static volatile boolean enableStoryDownload = false;
    public static volatile boolean enableReelDownload = false;
    public static volatile boolean enableProfileDownload = false;
    public static volatile boolean downloaderUsernameFolder = false;
    public static volatile boolean downloaderAddTimestamp = false;
    public static volatile boolean copyMediaLink = false;      // #117 — inject "Copy Media Link" (direct CDN url) into the post ⋮ menu
    public static volatile boolean saveInstants = false;        // #184 — long-press a received Instant (quicksnap) to save it
    public static volatile boolean uploadInstants = false;      // #199 — send an Instant from gallery (bitmap-swap into quicksnap send)
    public static volatile boolean cacheStories = false;        // cache viewed stories locally for 24h (survive expiry/deletion)
    public static volatile boolean customFontEnabled = false;   // replace IG's UI text font with a user .ttf/.otf
    public static volatile String  customFontPath = "";         // path to the user-picked font in the module's filesDir
    public static volatile boolean customEmojiEnabled = false;  // replace IG's emoji font (needs an EmojiCompat-format .ttf)
    public static volatile String  customEmojiPath = "";        // path to the user-picked EmojiCompat emoji font
    public static volatile String  downloaderCustomPath = "";   // human-readable display path
    public static volatile String  downloaderCustomUri  = "";   // SAF tree URI string for actual writes

    // Section 17 features: opt-in, preserving existing defaults.
    public static volatile boolean hideNotesTray = false;
    public static volatile boolean hideNavigationSearch = false;
    public static volatile boolean hideNavigationReels = false;
    public static volatile boolean hideNavigationCreate = false;
    public static volatile boolean hideNavigationDirect = false;
    public static volatile boolean hideNavigationNews = false;
    public static volatile boolean exactTimestamps = false;
    public static volatile boolean hideChatButton = false;
    public static volatile boolean mediaActions = false;
    public static volatile boolean saveCommentMedia = false;
    public static volatile boolean translateText = false;
    public static volatile boolean highResolutionImages = false;
    public static volatile String navigationOrder = "";
    public static volatile boolean profileFollowLabel = false;
    public static volatile boolean separateThemeProfiles = false;
    public static volatile String themeLightPaletteJson = "";
    public static volatile String themeDarkPaletteJson = "";
    public static volatile boolean searchComments = false;
    public static volatile boolean autoExpandText = false;
    public static volatile boolean hideOnboardingPrompts = false;
    public static volatile boolean fixNotificationRegistration = false;
    public static volatile boolean readReceiptExceptions = false;
    public static volatile String readReceiptThreadIds = "";
    public static volatile boolean hideLikedPosts = false;
    public static volatile boolean downloadVoiceMessages = false;
    public static volatile boolean hideStoriesTray = false;
}
