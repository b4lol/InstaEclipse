# Architecture

InstaEclipse ships as a single APK that plays two roles.

```
┌──────────────────────── Instagram process (IG uid) ────────────────────────┐
│  Xposed/Module (libxposed XposedModule, API 102, loads on 101+)            │
│    ├─ hook/*                → before/after hook layer on the interceptor chain│
│    ├─ DexKit + DexKitCache  → locate obfuscated IG classes/methods         │
│    ├─ mods/*                → feature hooks (ghost, ads, media, ui, …)     │
│    ├─ SettingsManager       → instaeclipse_prefs (IG private storage)      │
│    ├─ DialogUtils           → in-app InstaEclipse menu                     │
│    └─ sync receiver         ← companion broadcasts (signature permission)  │
└─────────────────────────────────────┬──────────────────────────────────────┘
                 broadcasts / startActivity / startForegroundService
┌─────────────────────────────────────┴──────────────────────────────────────┐
│  Companion app (ps.reso.instaeclipse uid)                                  │
│    ├─ MainActivity + fragments (Home, Features, Logs, Help)                │
│    ├─ instaeclipse_cache prefs  (mirror of the module settings)            │
│    ├─ DownloadSaveService       → writes media to the user's SAF folder    │
│    └─ JsonImport/Export, LocationPicker, ThemeCustomizer activities        │
└────────────────────────────────────────────────────────────────────────────┘
```

## Source layout (`app/src/main/java/ps/reso/instaeclipse`)

| Package | Role | Runs in |
|---------|------|---------|
| `Xposed/` | Entry point (`META-INF/xposed/java_init.list`), hook registration, sync receiver | Instagram |
| `hook/` | Hook layer over libxposed API 101+: `MethodHook` (with `isActive()` fast path), `HookBridge`, `HookHelpers`, `HostApp`, `ModuleResources`, `ViewAttachDispatcher` (shared `onAttachedToWindow` hook) | Instagram |
| `mods/ads`, `mods/ghost`, `mods/feed`, `mods/misc`, `mods/network`, `mods/devops` | Feature hooks | Instagram |
| `mods/media` | Download hooks (Instagram) and `DownloadSaveService` (companion) | both |
| `mods/ui` | UI hooks, theme engine, DM lock, custom fonts | Instagram |
| `mods/location` | Location spoof hook (Instagram) and picker activity (companion) | both |
| `fragments/`, `ui/`, `MainActivity` | Companion UI | companion |
| `utils/core` | Settings, DexKit cache, IPC helpers (`IpcSecurity`, `CommonUtils`), `PasscodeHasher` | both |
| `utils/feature` | `FeatureFlags` (in-memory state), status tracking | Instagram |
| `utils/log` | Ring-buffer log shared with the Logs tab | both |
| `utils/version` | Update check | companion |

Rule of thumb: anything under `mods/` that is not an `Activity` or `Service` runs inside
Instagram and must not assume the companion's permissions, resources or classloader
(use `I18n` / module context for strings).

## Settings flow

1. The user toggles something in `FeaturesFragment`; changes are staged and committed to
   `instaeclipse_cache`.
2. On commit the companion sends `ACTION_UPDATE_PREF[_STRING|_INT]` to every installed
   supported package (`CommonUtils.broadcastToInstagram`).
3. The module's sync receiver — registered with the `MODULE_IPC` signature permission —
   writes the value to `instaeclipse_prefs`, reloads `FeatureFlags` and refreshes hooks.
4. On resume the companion asks for the current state (`ACTION_REQUEST_PREFS` + nonce);
   the module replies with `ACTION_SEND_PREFS` (secrets excluded) echoing the nonce.
5. If Instagram wasn't running, the module reads the download folder on cold start from the
   framework's **remote preferences** (group `instaeclipse_shared`), which the companion writes
   through `libxposed:service` (`RemotePrefs`). Frameworks without remote-preference support
   (e.g. some embedded/LSPatch modes) fall back to the sync broadcast.

## Downloads

- No custom folder: the hook downloads inside Instagram and saves with Instagram's storage
  access.
- SAF folder chosen: the hook forwards the CDN URL(s) to `DownloadSaveService` in the
  companion, which holds the persistable SAF grant. The service validates the request
  (`DownloadRequestValidator`), downloads, optionally muxes video + audio, and writes the file.

## Xposed API (libxposed 101)

The module targets the modern [libxposed API](https://github.com/libxposed/api), version 101.

| Piece | Where |
|-------|-------|
| Entry class list | `app/src/main/resources/META-INF/xposed/java_init.list` |
| `minApiVersion` / `targetApiVersion` / `staticScope` / `exceptionMode` | `META-INF/xposed/module.prop` |
| Default scope | `META-INF/xposed/scope.list` |
| Module name / description | `android:label` / `android:description` on `<application>` |

Lifecycle: `onModuleLoaded` attaches the framework to `HookBridge` and locates the module APK
and `libdexkit.so`; `onPackageReady` (first package only, supported packages only) creates the
DexKit bridge and hooks `Application.attach` (fallback: `Instrumentation.callApplicationOnCreate`),
where every feature hook is installed.

Hooks are written against `hook.MethodHook` (`beforeHookedMethod` / `afterHookedMethod` with a
`MethodHookParam`). `MethodHook` runs those callbacks inside libxposed's interceptor chain with
the same semantics as the classic API: changing `param.args`, short-circuiting with
`setResult` / `setThrowable`, replacing the result afterwards, and logging-and-ignoring
exceptions thrown by a callback. Behaviour is covered by `MethodHookTest`.

| Legacy API | Replacement |
|------------|-------------|
| `IXposedHookLoadPackage` / `IXposedHookZygoteInit` | `XposedModule#onModuleLoaded` / `#onPackageReady` |
| `XC_MethodHook`, `MethodHookParam` | `hook.MethodHook`, `MethodHook.MethodHookParam` |
| `XposedBridge.hookMethod/hookAllMethods/log` | `hook.HookBridge` |
| `XposedHelpers.*` | `hook.HookHelpers` |
| `AndroidAppHelper.currentApplication()` | `hook.HostApp.get()` |
| `XModuleResources` | `hook.ModuleResources` |
| `XSharedPreferences` + world-readable prefs | framework remote preferences (`utils.core.RemotePrefs`) |

## Build

- AGP 9.4, Gradle 9.8, Java 21 source/target, `compileSdk` 37, `targetSdk` 36, `minSdk` 28.
- Versions live in `gradle/libs.versions.toml`.
- `compileOnly io.github.libxposed:api:102.0.0` provides the Xposed API (the framework supplies
  it at runtime); `io.github.libxposed:service:102.0.0` is bundled for the companion side.
  `module.prop` declares `minApiVersion=101` / `targetApiVersion=102`: API 102 calls must be
  guarded by a framework version check, which the `io.github.libxposed:lint` checks enforce.
- API 102 in use (only when the framework reports it): hook IDs set by `HookBridge`,
  `detach()` from package events once hooks are installed (and in secondary processes), and
  the companion's status card reading framework info and hooked processes from the service.
- R8 is off (`minifyEnabled false`): hooks rely on reflection and stable class names.

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

## Performance rules for hooks

See [PERFORMANCE_AUDIT.md](PERFORMANCE_AUDIT.md) for the background.

- Hooks on hot methods override `MethodHook.isActive()` with their feature flag.
- React to views appearing through `ViewAttachDispatcher`, not a new `onAttachedToWindow` hook.
- Resolve resource ids with `ResIds` (cached, including "not found"), never
  `getIdentifier`/`getResourceEntryName` per call.
- Take DexKit lookups through `LazyDexKit.findMethodsCached` / `findMethodsUsingAnyStringCached`
  (or `DexKitCache` directly) so a warm launch never opens DexKit.
- Network calls need timeouts; background work goes to a bounded executor; no disk I/O on the
  UI thread (`AtomicFiles.writeAsync` for small stores).

## Tests

JVM unit tests live in `app/src/test`. Keep logic that doesn't need Android (parsers,
validators, policies) in plain Java classes so it can be covered there —
see `MediaTypeDetector`, `StoryDownloadChoicePolicy`, `DownloadRequestValidator`,
`PasscodeHasher`, `VersionComparator`, and the hook layer (`MethodHookTest`, `HookHelpersTest`).
