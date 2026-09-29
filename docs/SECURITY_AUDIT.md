# Security & Bug Audit — September 2026

Scope: the whole `app/` module at `74bd7e1` (v0.7.0) plus the uncommitted toolchain bump
(AGP 8.13.2, Gradle 8.13, compileSdk/targetSdk 36, Material 1.14). Review was manual
(source reading, IPC and manifest analysis) followed by `lintDebug`, unit tests and a full
`assembleDebug`.

Severity uses the usual scale: **High** = another installed app can change behaviour or
read data without user action; **Medium** = needs user interaction or an unusual setup;
**Low** = hardening / hygiene.

## Summary

| # | Finding | Severity | Status |
|---|---------|----------|--------|
| 1 | Any app could drive the Instagram-side broadcast receivers | High | Fixed |
| 2 | Settings changes were broadcast implicitly to every app | High | Fixed |
| 3 | Exported `DownloadSaveService` accepted any URL and file name | High | Fixed |
| 4 | Companion trusted spoofable "reply" broadcasts | Medium | Fixed |
| 5 | DM-lock passcode: fast hash, no attempt limit, hash leaked over IPC | Medium | Fixed |
| 6 | `JsonImportActivity` forwarded to caller-chosen package/action | Medium | Fixed |
| 7 | `mc_overrides.json` validated by `{…}` check and written non-atomically | Low | Fixed |
| 8 | Update checker: no timeouts, leaked Activity, wrong version logic | Low | Fixed |
| 9 | Over-broad permissions (`QUERY_ALL_PACKAGES`, storage, `MANAGE_EXTERNAL_STORAGE`) | Low | Fixed |
| 10 | Unused JitPack dependency (`fileprefs`) | Low | Fixed |
| 11 | `createPackageContext(…, CONTEXT_IGNORE_SECURITY)` | Low | Fixed |
| B1 | Build broken after Material 1.14 bump | Bug | Fixed |
| B2 | Settings/config replies never arrived on Android 9–12L | Bug | Fixed |
| B3 | Download thread could hang forever; HTTP errors saved as media | Bug | Fixed |
| B4 | No `onTimeout` for the dataSync FGS (Android 15+) | Bug | Fixed |
| R1–R9 | Residual risks (R7 resolved) | — | See below |

## Findings

### 1. Instagram-side receivers accepted broadcasts from any app — High

`Module.registerSyncReceiver`, `UIHookManager.registerConfigImportReceiver` and
`registerSettingsRestoreReceiver` were registered `RECEIVER_EXPORTED` with no sender check.
Any installed app could:

- flip any feature flag (`ACTION_UPDATE_PREF`), e.g. turn off `lockDirectMessages`, or turn
  off ghost mode so the user unknowingly sends read receipts;
- set any string pref (`ACTION_UPDATE_PREF_STRING`), including `lockDirectPasscode` /
  `lockDirectSalt` → set a known passcode, or change the SAF download URI;
- overwrite Instagram's `mobileconfig/mc_overrides.json` (`ACTION_IMPORT_CONFIG`);
- restore an arbitrary settings backup (`ACTION_RESTORE_SETTINGS`);
- open activities over Instagram (`ACTION_BACKUP_SETTINGS`), clear logs.

**Fix:** a signature-level permission `ps.reso.instaeclipse.permission.MODULE_IPC` is declared
and held by the companion. All receivers inside the hooked process are registered through
`IpcSecurity.registerCompanionOnlyReceiver`, which requires the *sender* to hold it. Only an
APK signed with the InstaEclipse key can hold it. Additionally, the DM-lock hash and salt can
no longer be written through the sync bridge at all.

### 2. Settings were broadcast implicitly — High

`FeaturesFragment` and `ThemeCustomizerActivity` sent `ACTION_UPDATE_PREF*` and
`ACTION_REQUEST_PREFS` without `setPackage`. Implicit broadcasts reach every dynamically
registered receiver on the device, so any app could log what the user toggles, the chosen
spoofed coordinates, the SAF folder URI and the theme.

**Fix:** every companion → Instagram broadcast goes through
`CommonUtils.broadcastToInstagram`, which targets each installed supported package explicitly.

### 3. Exported `DownloadSaveService` accepted anything — High

The service must be exported (Instagram's uid starts it), but it accepted any `url`,
`audioUrl`, `filename`, `username`, `mimeType`. A malicious app could make the companion
download arbitrary content from any server into the user's download folder, with a chosen
name and without the user doing anything; there were also no timeouts or size limits.

**Fix** (`DownloadRequestValidator`, unit-tested):

- only `https` URLs on `*.cdninstagram.com` / `*.fbcdn.net`, no user-info; redirects are
  followed manually and every hop is re-validated;
- file and folder names are reduced to a single safe segment (no `/`, `\`, control chars,
  leading dots, ≤120 chars);
- MIME type must be `image/*` or `video/*`;
- 15 s connect / 30 s read timeouts, non-200 responses rejected, 2 GiB cap.

### 4. Spoofable replies to the companion — Medium

`ACTION_SEND_PREFS`, `ACTION_SEND_CONFIG` and `ACTION_LOGS_REPLY` receivers are exported and
unauthenticated. Any app could inject arbitrary keys into the companion's `instaeclipse_cache`
(which is also read by the module on cold start), push fake logs, or make the companion open a
save dialog with attacker-chosen JSON.

**Fix:** Instagram cannot hold our signature permission, so each request now carries a random
128-bit nonce (`IpcSecurity.newNonce`). Requests are package-targeted, so only Instagram sees
it; the module echoes it and the companion drops any reply without the matching nonce
(constant-time compare).

### 5. DM / whole-app lock passcode — Medium

- Stored as a single `sha256(salt + pin)`. For a 4–6 digit PIN the whole keyspace is
  ≤10⁶ hashes — milliseconds to brute-force by anyone who can read the prefs.
- Unlimited guesses in the lock overlay.
- `ACTION_REQUEST_PREFS` replied with **all** prefs, including hash and salt, which the
  companion then copied into its own prefs file.

**Fix** (`PasscodeHasher`, unit-tested): PBKDF2-HMAC-SHA256, 60 000 iterations, stored as
`pbkdf2$<iter>$<hex>`; legacy hashes still verify and are re-hashed automatically on the next
successful unlock. Five wrong attempts trigger a 30 s lockout. Hash and salt are excluded from
the IPC reply. Comparison is constant-time.

> A numeric PIN stays weak against an attacker with root / backup access even with PBKDF2 —
> it only raises the cost. The lock is a privacy screen, not encryption.

### 6. `JsonImportActivity` confused deputy — Medium

The exported activity sent the chosen file to whatever `target_package` and
`broadcast_action` the caller supplied. Combined with fix #1 this would have let other apps
borrow the companion's permission. Now the target must be in `SUPPORTED_PACKAGES`, the action
must be `ACTION_IMPORT_CONFIG` or `ACTION_RESTORE_SETTINGS`, input is capped at 2 MiB and must
parse as a JSON object.

### 7. `mc_overrides.json` handling — Low

Validation was `startsWith("{") && endsWith("}")` and the file was written in place, so a
malformed file or a crash mid-write could leave Instagram with a broken config. It is now
parsed with `JSONObject` and written to a temp file, `fsync`ed and renamed.

### 8. Update checker — Low

`AsyncTask` (deprecated), no timeouts, stream not closed on error, strong reference to the
Activity (leak / crash when showing a dialog on a destroyed activity), a hard-coded
`CURRENT_VERSION` duplicating `versionName`, and `!equals` comparison (a newer dev build was
told to "update" to an older release). The `update_url` from the network was opened without
checks.

Now: single-thread executor, 10 s timeouts, 16 KiB response cap, `WeakReference` + lifecycle
check, `BuildConfig.VERSION_NAME`, numeric comparison (`VersionComparator`, unit-tested), and
only `https` update URLs are opened.

### 9. Over-broad permissions — Low

The companion requested `QUERY_ALL_PACKAGES`, `READ/WRITE_EXTERNAL_STORAGE`,
`MANAGE_EXTERNAL_STORAGE` and `VIBRATE` without using them — downloads use SAF, and direct
writes / vibration happen inside Instagram's process under Instagram's permissions.
`QUERY_ALL_PACKAGES` is replaced by a `<queries>` block listing only the supported packages.
(`QUERY_ALL_PACKAGES` and all-files access are also Play-policy restricted.)

### 10. Unused JitPack dependency — Low

`com.github.chengxuncc:fileprefs` was on the classpath but never referenced; JitPack builds
artifacts from arbitrary GitHub repositories on demand, a weaker supply-chain guarantee than
Maven Central. Dependency and repository removed.

### 11. `CONTEXT_IGNORE_SECURITY` — Low

`ThemePresets` created the module context with flag `2` (`CONTEXT_IGNORE_SECURITY`). Only
resources are needed, so no flags are required; changed to `0`.

## Bugs fixed

- **B1** — `ThemeCustomizerActivity` referenced `com.google.android.material.R.attr.colorPrimary`,
  which Material 1.14 no longer re-exports with non-transitive R classes; the uncommitted
  dependency bump did not compile. Now uses `androidx.appcompat.R.attr.colorPrimary`.
- **B2** — On API < 33 `FeaturesFragment` registered its reply receivers with
  `ContextCompat.RECEIVER_NOT_EXPORTED`, which on those versions adds a
  `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` that Instagram doesn't hold. Result: current
  settings and dev-config export silently never arrived on Android 9–12L.
- **B3** — `DownloadSaveService` had no timeouts (a stalled CDN kept the foreground service alive
  indefinitely) and did not check the HTTP status. Failure messages showed `null` for
  exceptions without a message.
- **B4** — `targetSdk` ≥ 35 limits `dataSync` foreground services; the service now implements
  `onTimeout` and passes its type to `startForeground` explicitly.
- Deprecated `MainActivity.onBackPressed` override removed (the `FragmentManager` already
  handles back-stack pops through `OnBackPressedDispatcher`).
- Lint: 5 → 0 errors. The remaining ones were false positives for code that runs inside
  Instagram (biometric, custom `ImageView`) and are suppressed with a comment explaining why.

## Residual risks (not fixed)

- **R1** — `DownloadSaveService` is still startable by any app; it can only fetch Instagram CDN
  media into the user's folder now, but it can still be used to spam downloads. A proper fix
  needs a secret shared with the hooked process (e.g. via LSPosed remote preferences).
- **R2** — `JsonExportActivity`, `LocationPickerActivity` and `ThemeCustomizerActivity` are
  exported (Instagram launches them). Any app can open them; every effect requires user
  interaction.
- **R3** — `SUPPORTED_PACKAGES` contains generic names (`com.instagram1.android`,
  `com.instaclone.android`, …). Any app published under one of those names receives the
  companion's settings broadcasts (coordinates, SAF URI, theme). Consider making the list
  user-configurable.
- **R4** — The lock screen is an overlay inside Instagram; with root, ADB or a backup the
  prefs can simply be edited. It protects against shoulder-surfing, not a determined attacker.
- **R5** — `android:allowBackup="true"`: companion prefs (spoofed coordinates, SAF URI) end up
  in device backups. Consider excluding them in `backup_rules.xml` /
  `data_extraction_rules.xml`.
- **R6** — Logs contain usernames, file names and thread names and are meant to be pasted into
  public bug reports. Consider redacting before copy.
- **R7** — ~~`makeLocalCacheWorldReadable()` made the prefs file world-readable for
  `XSharedPreferences`.~~ **Resolved** by the libxposed API 101 migration: the module reads the
  download folder from framework remote preferences and the chmod calls are gone.
- **R8** — The in-Instagram URL filters (`isCdnUrl`, `isCdnMediaUrl`) use `String.contains` on
  the whole URL rather than a host check. Not a security boundary today (the service
  re-validates), but worth tightening.

- **R9** — `libxposed:service` adds an exported `XposedProvider` (authority
  `ps.reso.instaeclipse.XposedService`) through which the framework hands its binder to the
  companion. Any app can call it with a binder of its own; the companion would then write the
  download folder URI/path to that fake service. Impact is limited to disclosing the chosen
  folder. This is inherent to the library's design.

## Verification

- `./gradlew testDebugUnitTest` — all tests pass, including new
  `DownloadRequestValidatorTest`, `PasscodeHasherTest`, `VersionComparatorTest`.
- `./gradlew lintDebug` — 0 errors.
- `./gradlew assembleDebug` — succeeds.
- **Not done:** on-device testing. The IPC changes need to be checked on a real LSPosed
  install (settings sync, logs tab, dev-config import/export, SAF downloads, DM lock
  upgrade from an existing passcode), ideally on Android 9–12 and 14–16.
