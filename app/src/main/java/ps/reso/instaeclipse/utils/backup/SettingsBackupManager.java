package ps.reso.instaeclipse.utils.backup;

import org.json.JSONException;
import org.json.JSONObject;

import ps.reso.instaeclipse.utils.feature.FeatureFlags;

public class SettingsBackupManager {

    private static final int VERSION = 1;

    /** Serialises every known FeatureFlag into a versioned JSON string. */
    public static String toJson() throws JSONException {
        JSONObject s = new JSONObject();

        // Developer
        s.put("isDevEnabled",            FeatureFlags.isDevEnabled);
        s.put("removeBuildExpiredPopup", FeatureFlags.removeBuildExpiredPopup);

        // Ghost Mode
        s.put("isGhostSeen",             FeatureFlags.isGhostSeen);
        s.put("isGhostTyping",           FeatureFlags.isGhostTyping);
        s.put("isGhostScreenshot",       FeatureFlags.isGhostScreenshot);
        s.put("isGhostViewOnce",         FeatureFlags.isGhostViewOnce);
        s.put("isGhostStory",            FeatureFlags.isGhostStory);
        s.put("isGhostLive",             FeatureFlags.isGhostLive);
        s.put("allowScreenshots",        FeatureFlags.allowScreenshots);
        s.put("keepEphemeralMessages",   FeatureFlags.keepEphemeralMessages);

        s.put("permanentViewMode",       FeatureFlags.permanentViewMode);
        s.put("keepUnsentMessages",      FeatureFlags.keepUnsentMessages);

        // Quick Toggles
        s.put("quickToggleSeen",         FeatureFlags.quickToggleSeen);
        s.put("quickToggleTyping",       FeatureFlags.quickToggleTyping);
        s.put("quickToggleScreenshot",   FeatureFlags.quickToggleScreenshot);
        s.put("quickToggleViewOnce",     FeatureFlags.quickToggleViewOnce);
        s.put("quickToggleStory",        FeatureFlags.quickToggleStory);
        s.put("quickToggleLive",         FeatureFlags.quickToggleLive);
        s.put("quickToggleEphemeral",    FeatureFlags.quickToggleEphemeral);
        s.put("quickTogglePermanentView",FeatureFlags.quickTogglePermanentView);
        s.put("quickToggleAllowScreenshots", FeatureFlags.quickToggleAllowScreenshots);

        // Clean Feed
        s.put("hideSuggestionsInFeed",      FeatureFlags.hideSuggestionsInFeed);
        s.put("hideThreadsSuggestions",     FeatureFlags.hideThreadsSuggestions);
        s.put("followingOnlyFeed",          FeatureFlags.followingOnlyFeed);

        // Ads
        s.put("isAdBlockEnabled",        FeatureFlags.isAdBlockEnabled);
        s.put("isAnalyticsBlocked",      FeatureFlags.isAnalyticsBlocked);
        s.put("disableTrackingLinks",    FeatureFlags.disableTrackingLinks);

        // Distraction Free
        s.put("isExtremeMode",           FeatureFlags.isExtremeMode);
        s.put("disableStories",          FeatureFlags.disableStories);
        s.put("disableFeed",             FeatureFlags.disableFeed);
        s.put("disableReels",            FeatureFlags.disableReels);
        s.put("disableReelsExceptDM",    FeatureFlags.disableReelsExceptDM);
        s.put("disableExplore",          FeatureFlags.disableExplore);
        s.put("disableComments",         FeatureFlags.disableComments);
        s.put("disableDiscoverPeople",   FeatureFlags.disableDiscoverPeople);

        // Miscellaneous
        s.put("disableStoryFlipping",    FeatureFlags.disableStoryFlipping);
        s.put("disableVideoAutoPlay",    FeatureFlags.disableVideoAutoPlay);
        s.put("spoofLastSeen",           FeatureFlags.spoofLastSeen);
        s.put("spoofLocation",           FeatureFlags.spoofLocation);
        s.put("spoofLat",                String.valueOf(FeatureFlags.spoofLat));
        s.put("spoofLng",                String.valueOf(FeatureFlags.spoofLng));
        s.put("spoofLabel",              FeatureFlags.spoofLabel);
        s.put("spoofRecent",             FeatureFlags.spoofRecent);
        s.put("forceReelQuality",        FeatureFlags.forceReelQuality);
        s.put("disableRepost",           FeatureFlags.disableRepost);
        s.put("showFollowerToast",       FeatureFlags.showFollowerToast);
        s.put("showFeatureToasts",       FeatureFlags.showFeatureToasts);
        s.put("enableStoryMentions",     FeatureFlags.enableStoryMentions);
        s.put("openLinksExternally",     FeatureFlags.openLinksExternally);
        s.put("unlimitedAccounts", FeatureFlags.unlimitedAccounts);
        s.put("profileFollowLabel", FeatureFlags.profileFollowLabel);
        s.put("separateThemeProfiles", FeatureFlags.separateThemeProfiles);
        s.put("themeLightPaletteJson", FeatureFlags.themeLightPaletteJson);
        s.put("themeDarkPaletteJson", FeatureFlags.themeDarkPaletteJson);
        s.put("searchComments", FeatureFlags.searchComments);
        s.put("autoExpandText", FeatureFlags.autoExpandText);
        s.put("hideOnboardingPrompts", FeatureFlags.hideOnboardingPrompts);
        s.put("fixNotificationRegistration", FeatureFlags.fixNotificationRegistration);
        s.put("readReceiptExceptions", FeatureFlags.readReceiptExceptions);
        s.put("readReceiptThreadIds", FeatureFlags.readReceiptThreadIds);
        s.put("hideLikedPosts", FeatureFlags.hideLikedPosts);
        s.put("downloadVoiceMessages", FeatureFlags.downloadVoiceMessages);
        s.put("hideStoriesTray", FeatureFlags.hideStoriesTray);
        s.put("storyExactTime", FeatureFlags.storyExactTime);
        s.put("hideNotesTray", FeatureFlags.hideNotesTray);
        s.put("hideNavigationSearch", FeatureFlags.hideNavigationSearch);
        s.put("hideNavigationReels", FeatureFlags.hideNavigationReels);
        s.put("hideNavigationCreate", FeatureFlags.hideNavigationCreate);
        s.put("hideNavigationDirect", FeatureFlags.hideNavigationDirect);
        s.put("hideNavigationNews", FeatureFlags.hideNavigationNews);
        s.put("exactTimestamps", FeatureFlags.exactTimestamps);
        s.put("hideChatButton", FeatureFlags.hideChatButton);
        s.put("mediaActions", FeatureFlags.mediaActions);
        s.put("saveCommentMedia", FeatureFlags.saveCommentMedia);
        s.put("translateText", FeatureFlags.translateText);
        s.put("highResolutionImages", FeatureFlags.highResolutionImages);
        s.put("navigationOrder", FeatureFlags.navigationOrder);
        s.put("reelsDisableTapPause", FeatureFlags.reelsDisableTapPause);
        s.put("reelsAutoScroll", FeatureFlags.reelsAutoScroll);
        s.put("reelsLockScroll", FeatureFlags.reelsLockScroll);
        s.put("airplaneMode", FeatureFlags.airplaneMode);
        s.put("hideShareSheetGroup", FeatureFlags.hideShareSheetGroup);
        s.put("disableSwipeToCamera", FeatureFlags.disableSwipeToCamera);
        s.put("startupTab", FeatureFlags.startupTab);
        s.put("customShareDomain", FeatureFlags.customShareDomain);

        // Downloader
        s.put("enablePostDownload",      FeatureFlags.enablePostDownload);
        s.put("enableStoryDownload",     FeatureFlags.enableStoryDownload);
        s.put("enableReelDownload",      FeatureFlags.enableReelDownload);
        s.put("enableProfileDownload",   FeatureFlags.enableProfileDownload);
        s.put("downloaderUsernameFolder",FeatureFlags.downloaderUsernameFolder);
        s.put("downloaderAddTimestamp",  FeatureFlags.downloaderAddTimestamp);
        s.put("copyMediaLink",           FeatureFlags.copyMediaLink);
        s.put("saveInstants",            FeatureFlags.saveInstants);
        s.put("uploadInstants",          FeatureFlags.uploadInstants);

        JSONObject root = new JSONObject();
        root.put("version",  VERSION);
        root.put("settings", s);
        return root.toString(2);
    }

    /**
     * Applies a backup JSON string to the in-memory FeatureFlags.
     * Supports both the versioned {"version":1,"settings":{...}} format
     * and a flat {key:value} format for forward-compatibility.
     * Unknown keys are silently ignored so older backups work on newer builds.
     */
    public static void fromJson(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        JSONObject s = root.has("settings") ? root.getJSONObject("settings") : root;

        if (s.has("isDevEnabled"))            FeatureFlags.isDevEnabled            = s.getBoolean("isDevEnabled");
        if (s.has("removeBuildExpiredPopup")) FeatureFlags.removeBuildExpiredPopup = s.getBoolean("removeBuildExpiredPopup");

        if (s.has("isGhostSeen"))            FeatureFlags.isGhostSeen            = s.getBoolean("isGhostSeen");
        if (s.has("isGhostTyping"))          FeatureFlags.isGhostTyping          = s.getBoolean("isGhostTyping");
        if (s.has("isGhostScreenshot"))      FeatureFlags.isGhostScreenshot      = s.getBoolean("isGhostScreenshot");
        if (s.has("isGhostViewOnce"))        FeatureFlags.isGhostViewOnce        = s.getBoolean("isGhostViewOnce");
        if (s.has("isGhostStory"))           FeatureFlags.isGhostStory           = s.getBoolean("isGhostStory");
        if (s.has("isGhostLive"))            FeatureFlags.isGhostLive            = s.getBoolean("isGhostLive");
        if (s.has("allowScreenshots"))         FeatureFlags.allowScreenshots         = s.getBoolean("allowScreenshots");
        if (s.has("keepEphemeralMessages"))    FeatureFlags.keepEphemeralMessages    = s.getBoolean("keepEphemeralMessages");
        if (s.has("permanentViewMode"))        FeatureFlags.permanentViewMode        = s.getBoolean("permanentViewMode");
        if (s.has("keepUnsentMessages"))       FeatureFlags.keepUnsentMessages       = s.getBoolean("keepUnsentMessages");

        if (s.has("quickToggleSeen"))        FeatureFlags.quickToggleSeen        = s.getBoolean("quickToggleSeen");
        if (s.has("quickToggleTyping"))      FeatureFlags.quickToggleTyping      = s.getBoolean("quickToggleTyping");
        if (s.has("quickToggleScreenshot"))  FeatureFlags.quickToggleScreenshot  = s.getBoolean("quickToggleScreenshot");
        if (s.has("quickToggleViewOnce"))    FeatureFlags.quickToggleViewOnce    = s.getBoolean("quickToggleViewOnce");
        if (s.has("quickToggleStory"))       FeatureFlags.quickToggleStory       = s.getBoolean("quickToggleStory");
        if (s.has("quickToggleLive"))        FeatureFlags.quickToggleLive        = s.getBoolean("quickToggleLive");
        if (s.has("quickToggleEphemeral"))   FeatureFlags.quickToggleEphemeral   = s.getBoolean("quickToggleEphemeral");
        if (s.has("quickTogglePermanentView")) FeatureFlags.quickTogglePermanentView = s.getBoolean("quickTogglePermanentView");
        if (s.has("quickToggleAllowScreenshots")) FeatureFlags.quickToggleAllowScreenshots = s.getBoolean("quickToggleAllowScreenshots");

        if (s.has("hideSuggestionsInFeed"))     FeatureFlags.hideSuggestionsInFeed     = s.getBoolean("hideSuggestionsInFeed");
        if (s.has("hideThreadsSuggestions"))    FeatureFlags.hideThreadsSuggestions    = s.getBoolean("hideThreadsSuggestions");
        if (s.has("followingOnlyFeed"))         FeatureFlags.followingOnlyFeed         = s.getBoolean("followingOnlyFeed");

        if (s.has("isAdBlockEnabled"))       FeatureFlags.isAdBlockEnabled       = s.getBoolean("isAdBlockEnabled");
        if (s.has("isAnalyticsBlocked"))     FeatureFlags.isAnalyticsBlocked     = s.getBoolean("isAnalyticsBlocked");
        if (s.has("disableTrackingLinks"))   FeatureFlags.disableTrackingLinks   = s.getBoolean("disableTrackingLinks");

        if (s.has("isExtremeMode"))          FeatureFlags.isExtremeMode          = s.getBoolean("isExtremeMode");
        if (s.has("disableStories"))         FeatureFlags.disableStories         = s.getBoolean("disableStories");
        if (s.has("disableFeed"))            FeatureFlags.disableFeed            = s.getBoolean("disableFeed");
        if (s.has("disableReels"))           FeatureFlags.disableReels           = s.getBoolean("disableReels");
        if (s.has("disableReelsExceptDM"))   FeatureFlags.disableReelsExceptDM   = s.getBoolean("disableReelsExceptDM");
        if (s.has("disableExplore"))         FeatureFlags.disableExplore         = s.getBoolean("disableExplore");
        if (s.has("disableComments"))        FeatureFlags.disableComments        = s.getBoolean("disableComments");
        if (s.has("disableDiscoverPeople"))  FeatureFlags.disableDiscoverPeople  = s.getBoolean("disableDiscoverPeople");

        if (s.has("disableStoryFlipping"))   FeatureFlags.disableStoryFlipping   = s.getBoolean("disableStoryFlipping");
        if (s.has("disableVideoAutoPlay"))   FeatureFlags.disableVideoAutoPlay   = s.getBoolean("disableVideoAutoPlay");
        if (s.has("spoofLastSeen"))          FeatureFlags.spoofLastSeen          = s.getBoolean("spoofLastSeen");
        if (s.has("spoofLocation"))          FeatureFlags.spoofLocation          = s.getBoolean("spoofLocation");
        if (s.has("spoofLat"))               FeatureFlags.spoofLat               = parseDouble(s.get("spoofLat"), 0.0);
        if (s.has("spoofLng"))               FeatureFlags.spoofLng               = parseDouble(s.get("spoofLng"), 0.0);
        if (s.has("spoofLabel"))             FeatureFlags.spoofLabel             = s.getString("spoofLabel");
        if (s.has("spoofRecent"))            FeatureFlags.spoofRecent            = s.getString("spoofRecent");
        if (s.has("forceReelQuality"))        FeatureFlags.forceReelQuality       = s.getInt("forceReelQuality");
        if (s.has("disableRepost"))          FeatureFlags.disableRepost          = s.getBoolean("disableRepost");
        if (s.has("openLinksExternally"))    FeatureFlags.openLinksExternally    = s.getBoolean("openLinksExternally");
        if (s.has("unlimitedAccounts")) FeatureFlags.unlimitedAccounts = s.getBoolean("unlimitedAccounts");
        if (s.has("profileFollowLabel")) FeatureFlags.profileFollowLabel = s.getBoolean("profileFollowLabel");
        if (s.has("separateThemeProfiles")) FeatureFlags.separateThemeProfiles = s.getBoolean("separateThemeProfiles");
        if (s.has("themeLightPaletteJson")) FeatureFlags.themeLightPaletteJson = s.getString("themeLightPaletteJson");
        if (s.has("themeDarkPaletteJson")) FeatureFlags.themeDarkPaletteJson = s.getString("themeDarkPaletteJson");
        if (s.has("searchComments")) FeatureFlags.searchComments = s.getBoolean("searchComments");
        if (s.has("autoExpandText")) FeatureFlags.autoExpandText = s.getBoolean("autoExpandText");
        if (s.has("hideOnboardingPrompts")) FeatureFlags.hideOnboardingPrompts = s.getBoolean("hideOnboardingPrompts");
        if (s.has("fixNotificationRegistration")) FeatureFlags.fixNotificationRegistration = s.getBoolean("fixNotificationRegistration");
        if (s.has("readReceiptExceptions")) FeatureFlags.readReceiptExceptions = s.getBoolean("readReceiptExceptions");
        if (s.has("readReceiptThreadIds")) FeatureFlags.readReceiptThreadIds = s.getString("readReceiptThreadIds");
        if (s.has("hideLikedPosts")) FeatureFlags.hideLikedPosts = s.getBoolean("hideLikedPosts");
        if (s.has("downloadVoiceMessages")) FeatureFlags.downloadVoiceMessages = s.getBoolean("downloadVoiceMessages");
        if (s.has("hideStoriesTray")) FeatureFlags.hideStoriesTray = s.getBoolean("hideStoriesTray");
        if (s.has("storyExactTime")) FeatureFlags.storyExactTime = s.getBoolean("storyExactTime");
        if (s.has("hideNotesTray")) FeatureFlags.hideNotesTray = s.getBoolean("hideNotesTray");
        if (s.has("hideNavigationSearch")) FeatureFlags.hideNavigationSearch = s.getBoolean("hideNavigationSearch");
        if (s.has("hideNavigationReels")) FeatureFlags.hideNavigationReels = s.getBoolean("hideNavigationReels");
        if (s.has("hideNavigationCreate")) FeatureFlags.hideNavigationCreate = s.getBoolean("hideNavigationCreate");
        if (s.has("hideNavigationDirect")) FeatureFlags.hideNavigationDirect = s.getBoolean("hideNavigationDirect");
        if (s.has("hideNavigationNews")) FeatureFlags.hideNavigationNews = s.getBoolean("hideNavigationNews");
        if (s.has("exactTimestamps")) FeatureFlags.exactTimestamps = s.getBoolean("exactTimestamps");
        if (s.has("hideChatButton")) FeatureFlags.hideChatButton = s.getBoolean("hideChatButton");
        if (s.has("mediaActions")) FeatureFlags.mediaActions = s.getBoolean("mediaActions");
        if (s.has("saveCommentMedia")) FeatureFlags.saveCommentMedia = s.getBoolean("saveCommentMedia");
        if (s.has("translateText")) FeatureFlags.translateText = s.getBoolean("translateText");
        else if (s.has("translateComments")) FeatureFlags.translateText = s.getBoolean("translateComments");
        if (s.has("highResolutionImages")) FeatureFlags.highResolutionImages = s.getBoolean("highResolutionImages");
        if (s.has("navigationOrder")) FeatureFlags.navigationOrder = s.getString("navigationOrder");
        if (s.has("reelsDisableTapPause")) FeatureFlags.reelsDisableTapPause = s.getBoolean("reelsDisableTapPause");
        if (s.has("reelsAutoScroll")) FeatureFlags.reelsAutoScroll = s.getBoolean("reelsAutoScroll");
        if (s.has("reelsLockScroll")) FeatureFlags.reelsLockScroll = s.getBoolean("reelsLockScroll");
        if (s.has("airplaneMode")) FeatureFlags.airplaneMode = s.getBoolean("airplaneMode");
        if (s.has("hideShareSheetGroup")) FeatureFlags.hideShareSheetGroup = s.getBoolean("hideShareSheetGroup");
        if (s.has("disableSwipeToCamera")) FeatureFlags.disableSwipeToCamera = s.getBoolean("disableSwipeToCamera");
        if (s.has("startupTab")) FeatureFlags.startupTab = s.getString("startupTab");
        if (s.has("customShareDomain")) FeatureFlags.customShareDomain = s.getString("customShareDomain");
        if (s.has("showFollowerToast"))      FeatureFlags.showFollowerToast      = s.getBoolean("showFollowerToast");
        if (s.has("showFeatureToasts"))      FeatureFlags.showFeatureToasts      = s.getBoolean("showFeatureToasts");
        if (s.has("enableStoryMentions"))    FeatureFlags.enableStoryMentions    = s.getBoolean("enableStoryMentions");

        if (s.has("enablePostDownload"))     FeatureFlags.enablePostDownload     = s.getBoolean("enablePostDownload");
        if (s.has("enableStoryDownload"))    FeatureFlags.enableStoryDownload    = s.getBoolean("enableStoryDownload");
        if (s.has("enableReelDownload"))     FeatureFlags.enableReelDownload     = s.getBoolean("enableReelDownload");
        if (s.has("enableProfileDownload"))  FeatureFlags.enableProfileDownload  = s.getBoolean("enableProfileDownload");
        if (s.has("downloaderUsernameFolder")) FeatureFlags.downloaderUsernameFolder = s.getBoolean("downloaderUsernameFolder");
        if (s.has("downloaderAddTimestamp")) FeatureFlags.downloaderAddTimestamp  = s.getBoolean("downloaderAddTimestamp");
        if (s.has("copyMediaLink"))          FeatureFlags.copyMediaLink          = s.getBoolean("copyMediaLink");
        if (s.has("saveInstants"))           FeatureFlags.saveInstants           = s.getBoolean("saveInstants");
        if (s.has("uploadInstants"))         FeatureFlags.uploadInstants         = s.getBoolean("uploadInstants");
    }

    private static double parseDouble(Object raw, double fallback) {
        if (raw instanceof Number) return ((Number) raw).doubleValue();
        if (raw instanceof String) {
            try {
                return Double.parseDouble((String) raw);
            } catch (Throwable ignored) {}
        }
        return fallback;
    }
}
