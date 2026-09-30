package ps.reso.instaeclipse.mods.ui.theme;

import ps.reso.instaeclipse.utils.feature.FeatureFlags;

/**
 * Pure helpers for resolving the effective palette from a preset id / custom JSON pair.
 * Preset id 0 means "use the custom JSON palette"; any other id looks up a built-in preset.
 */
public final class ThemeSettingsHelper {

    public static final int CUSTOM_PRESET_ID = 0;

    private ThemeSettingsHelper() {}

    public static boolean isCustomMode(int presetId) {
        return presetId == CUSTOM_PRESET_ID;
    }

    public static boolean isNight() {
        android.content.Context ctx = ps.reso.instaeclipse.hook.HostApp.get();
        android.content.res.Resources res = ctx == null ? android.content.res.Resources.getSystem() : ctx.getResources();
        return (res.getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    public static String modePalette() {
        return isNight() ? FeatureFlags.themeDarkPaletteJson : FeatureFlags.themeLightPaletteJson;
    }

    public static IgThemePalette resolveEffectivePalette() {
        if (FeatureFlags.separateThemeProfiles && !modePalette().isEmpty())
            return IgThemePalette.fromJson(modePalette());
        return resolveEffectivePalette(FeatureFlags.themePresetId, FeatureFlags.themePaletteJson);
    }

    public static IgThemePalette resolveEffectivePalette(int presetId, String paletteJson) {
        if (presetId > 0) return ThemePresets.getById(presetId).palette.copy();
        return IgThemePalette.fromJson(paletteJson);
    }
}
