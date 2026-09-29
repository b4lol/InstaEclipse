# Changelog

All notable changes to this project are documented here. Format based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added
- **Following-only feed** (Clean Feed): the home feed only shows accounts you follow, by
  forcing the feed request's `pagination_source` to `following`.
- **Open links in external browser**: links open in the default browser instead of
  Instagram's in-app browser; `l.instagram.com` redirects are unwrapped. Meta's own domains
  (login, Accounts Center, payments) stay in-app.
- **Copy without hashtags**: extra button in the caption copy dialog, shown only when the
  caption has hashtags.
- New **Extras** section, with features found by reviewing JTInstagram's patch points and
  re-located on Instagram 447:
  - **Airplane mode**: keeps Instagram's realtime connection (MQTT) down, so you appear
    offline, no typing / seen events are sent live and in-app calls don't ring; the feed and
    browsing keep working. Toggling applies immediately.
  - **Story posting time** in the story header, e.g. "14:32 · 3h".
  - **Reels**: disable tap-to-pause, unlock Instagram's own auto-scroll option, lock
    scrolling.
  - **Disable swipe to camera** on the home feed.
  - **Hide group creation in the share sheet** when several recipients are selected.
  - **Unlimited accounts** in the account switcher.
  - **Startup tab**: open Instagram on Home, Reels, Messages, Search, Profile or
    Notifications.
  - **Custom share domain**: replaces `instagram.com` in copied / shared links (e.g. for
    embed previews).
- "Disable double-tap to like" now also covers comments and DMs; a reaction picked from the
  long-press menu still works.
- `ROADMAP.md`: plan for a gradual Java/Kotlin hybrid migration.

### Changed — Location spoofing
- Every location delivery path is spoofed: platform `LocationManager` listeners,
  `getCurrentLocation`, PendingIntent updates and Play Services' fused provider. Previously
  only the first update was replaced and later real GPS updates reached Instagram.
- Instagram no longer needs the real location permission: while spoofing, location
  permission checks report "granted" and are answered with the spoofed position, so the
  real one never reaches the app.
- Fixes land a few meters around the chosen point with a varying accuracy; the mock-location
  flags read false.
- Map picker: multiple search results, pasted "lat, lng" coordinates, the place name under
  the pin, and recently used places as chips.
- Both settings screens show the place name and apply a recent place with one tap.

### Changed — UI (Material 3 Expressive)
- Both the in-Instagram settings sheet and the companion app follow the wallpaper colors
  (Dynamic Colors) on Android 12+, with the indigo palette as the fallback.
- In-Instagram sheet: segmented rows, redrawn M3 switches and radio buttons, a badge with
  the number of enabled features per section, a status chip, and pages that slide in place
  instead of closing and reopening the sheet.
- Companion app: Material 3 Expressive theme, fade-through tab transitions, round icon
  containers, per-section badges, chevrons, switch check icons and a tonal Instagram status
  card.
- Version `0.7.0-test.2` (versionCode 18).

### Changed — Toolchain
- libxposed API and service 102.0.0 (`targetApiVersion=102`). `minApiVersion` stays 101, so
  frameworks that only support API 101 still load the module; no API 102 call is made yet.
  Added the libxposed annotations and lint checks, which flag API 102 calls without a
  framework version check.
- AGP 9.4.1 and Gradle 9.8.0 (wrapper with a pinned SHA-256); `compileSdk` 37, required by
  libxposed service 102. `targetSdk` stays 36.
- `app/build.gradle` uses Groovy assignment syntax (`prop = value`), required before Gradle 10.

### Changed — Xposed API
- **Migrated from the legacy Xposed API (82) to the modern libxposed API 101.** The module now
  needs a framework with API 101 support (e.g. Vector, current JingMatrix LSPatch); frameworks
  that only load legacy `xposed_init` modules can no longer load it.
- Module metadata moved to `META-INF/xposed/` (`java_init.list`, `module.prop`, `scope.list`);
  the `xposed*` manifest meta-data, `assets/xposed_init` and `libs/api-82.jar` were removed.
- New `hook` package keeps the before/after hook model on top of libxposed's interceptor
  chain, so feature hooks kept their logic; covered by new unit tests.
- The download folder reaches the module through framework remote preferences
  (`libxposed:service`) instead of `XSharedPreferences` and a world-readable prefs file.
- Module resources are loaded with `PackageManager#getResourcesForApplication` instead of
  `XModuleResources`; the host `Application` is captured from the attach hook instead of
  `AndroidAppHelper`.

### Performance
- Hooks on hot paths (`View.onAttachedToWindow`, `getColor`, `getDrawable`, `Uri.parse`, …) no
  longer do string resource lookups per call, share one attach hook, and skip all work (no
  allocation) while their feature is off.
- DexKit is opened only when a cached lookup is missing and closed after startup; all lookups
  are cached per Instagram version and multi-string searches run in one batch pass.
- Logging never holds its lock during disk I/O and no longer reads the log file on the main
  thread at startup; the per-line stack walk is verbose-only.
- In-Instagram downloads have network timeouts and run on a bounded thread pool.
- Passcode hashing runs off the UI thread.

### Fixed
- Location spoofing no longer sends 0,0 when it is enabled but no place has been picked.
- The in-Instagram sheet was cut to about half the screen after the device had been rotated
  to landscape in another app; its height now comes from Instagram's own window.
- First launch after installing/updating Instagram no longer ANRs on Android 16 ("failed to
  complete startup"): DexKit discovery runs in the background when the cache is cold or
  incomplete. Found on-device with Instagram 447.0.0.21.81.
- The module no longer installs its hooks in Instagram's `:fbns` push process.
- Lookups that find nothing are cached, so warm launches never open DexKit (hook installation
  0.52 s instead of 1.64 s on the test device).
- Hidden chats / thread names / unsent log could be wiped if first accessed before Instagram's
  Application was available; these stores are now written atomically in the background.
- `FeatureFlags` changes are visible across threads (`volatile`).

### Security
- Broadcast receivers inside Instagram now only accept broadcasts from the companion app
  (new signature permission `ps.reso.instaeclipse.permission.MODULE_IPC`). Previously any
  installed app could change settings, overwrite `mc_overrides.json` or restore backups.
- Settings changes are sent only to the supported Instagram packages instead of every app.
- Replies from Instagram to the companion are authenticated with a per-request nonce.
- `DownloadSaveService` only downloads https media from Instagram CDNs, re-validates
  redirects, sanitizes file/folder names, enforces timeouts and a size limit.
- DM / app lock passcode is hashed with PBKDF2 (old passcodes keep working and are upgraded
  on the next unlock); 30 s lockout after 5 wrong attempts; hash and salt are no longer
  shared with the companion app.
- `JsonImportActivity` only forwards to supported packages and known actions; input is
  size-limited and must be valid JSON.
- Removed unused permissions: `QUERY_ALL_PACKAGES` (replaced by `<queries>`),
  `READ/WRITE_EXTERNAL_STORAGE`, `MANAGE_EXTERNAL_STORAGE`, `VIBRATE`.
- Removed the unused `fileprefs` dependency and the JitPack repository.

### Fixed
- Build with Material 1.14 (`colorPrimary` attribute reference).
- Settings and dev-config export from Instagram never reached the app on Android 9–12L.
- Downloads could hang forever on a stalled connection; HTTP errors are reported instead
  of being saved as media.
- Foreground download service handles the Android 15+ `dataSync` timeout.
- `mc_overrides.json` is validated as real JSON and written atomically.
- Update check no longer prompts dev builds to "update" to an older release, no longer
  leaks the activity, and has network timeouts.

### Changed
- Toolchain: AGP 8.13.2, Gradle 8.13, compileSdk/targetSdk 36, Gson 2.14, AppCompat 1.8,
  Material 1.14, ConstraintLayout 2.2.2, AndroidX Test 1.3 / Espresso 3.7.
- `BuildConfig.VERSION_NAME` replaces the hard-coded version in the update checker.
- CI: least-privilege token, Gradle wrapper validation and caching via
  `gradle/actions/setup-gradle`, `setup-android@v3`, lint step and lint report upload.

### Docs
- Added `SECURITY.md`, `CONTRIBUTING.md`, `CHANGELOG.md`, `docs/ARCHITECTURE.md` and
  `docs/SECURITY_AUDIT.md`.

## [0.7.0]

See the [GitHub release](https://github.com/ReSo7200/InstaEclipse/releases/tag/v0.7.0).
