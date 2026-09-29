# Performance & Resource Audit — September 2026

Scope: the whole `app/` module after the libxposed API 101 migration. Static review only —
hot paths were identified by what is hooked and how often Android calls it; **nothing was
measured on a device**. Impact estimates are qualitative and should be confirmed with the
measurement plan at the end before and after any change.

InstaEclipse runs almost entirely *inside Instagram's process*, often on its UI thread, so
the budget is Instagram's frame time (≈16 ms at 60 Hz, ≈8 ms at 120 Hz) and its cold start.

**Impact:** 🔴 High = likely visible jank / startup delay / unbounded resource use;
🟠 Medium = measurable cost in common flows; 🟢 Low = hygiene.

## Summary

| # | Finding | Where | Impact | Status |
|---|---------|-------|--------|--------|
| P1 | 3× `getIdentifier()` on **every** view attach | `FeedVideoDownloadHook.java:225-231` | 🔴 | ✅ Fixed |
| P2 | Resource-name lookups + thrown exceptions on every view attach | `ProfilePicDownloadHook.java:66-74` | 🔴 | ✅ Fixed |
| P3 | "Not found" id cached as `0` ⇒ `getIdentifier()` retried on every attach | `GhostDMMarkAsReadHook`, `GhostChannelMarkAsReadHook` | 🟠→🔴 | ✅ Fixed |
| P4 | ≥15 uncached full-APK DexKit scans on every launch, no batching | `RemoveMetaAIHook`, `DisableRepostHook`, `HideChatsHook` | 🔴 | ✅ Fixed |
| P5 | DexKit bridge opened every launch, never closed | `Module.java:124` | 🔴 memory | ✅ Fixed |
| P6 | Log file I/O done while holding the lock hook threads append under | `Logging.java:124-160` | 🔴 | ✅ Fixed |
| P7 | Log file (≤4 MB) read synchronously in `Application.attach` | `Logging.java:65` | 🟠 startup | ✅ Fixed |
| P8 | Stack walk on every `ModuleLog.line` | `ModuleLog.java:30` | 🟠 | ✅ Fixed |
| P9 | Hot framework hooks installed even when the feature is off | theme, autoplay, attach hooks | 🟠 | ◐ Partly |
| P10 | Hook adapter allocates per call, even on the "feature off" path | `hook/MethodHook.java:42` | 🟠 | ✅ Fixed |
| P11 | Resource type/name lookups on every `getDrawable`/`setImageResource` | `DisableVideoAutoPlayHook.java:333` | 🟠 | ✅ Fixed |
| P12 | Downloads inside Instagram: no timeouts + unbounded thread pool | 4 call sites, `FeedVideoDownloadHook.java:133` | 🟠 leak | ✅ Fixed |
| P13 | PBKDF2 (60k iterations) on the UI thread | `LockDirectMessagesHook.java:281` | 🟠 | ✅ Fixed |
| P14 | `HookHelpers.findField` builds a cache key string per call | `hook/HookHelpers.java:83` | 🟢 | ✅ Fixed |
| P15 | JSON stores rewritten synchronously and non-atomically | `HiddenThreads`, `ThreadNames`, `UnsentLog` | 🟢 (+ data-loss bug) | ✅ Fixed |
| P16 | Full-size avatar decode for small circles | `AvatarLoader.java:73` | 🟢 | ➖ Not an issue |
| P17 | `FeatureFlags` fields are not `volatile` | `FeatureFlags.java` | 🟢 correctness | ✅ Fixed |

---

## Hot paths inside Instagram

### P1 — `getIdentifier()` ×3 per view attach 🔴

`FeedVideoDownloadHook.installViewHook` hooks `View.onAttachedToWindow` for the whole app and,
whenever *Post download* is enabled, resolves `row_feed_button_like`, `like_button` and
`clips_ufi_component` by name **on every call**. `Resources.getIdentifier` is a string-keyed
lookup through the AssetManager (Android docs discourage it for this reason). Instagram attaches
hundreds of views per screen and continuously while scrolling feed and reels.

**Fix:** resolve the three ids once (per package) into static fields, like the other hooks
already do, then compare ints. Use a "not found" sentinel (see P3).

### P2 — `getResourceEntryName()` per view attach 🔴

`ProfilePicDownloadHook` caches the id only after it has *seen* `expanded_profile_pic`. Until
then — i.e. almost always — every attached view with an id triggers
`getResourceEntryName(vid)`: a JNI call, a new `String`, and for framework/generated ids a
thrown and caught `NotFoundException` (exceptions are expensive to construct).

**Fix:** resolve once with `getIdentifier("expanded_profile_pic", "id", pkg)` and compare.

### P3 — `0` used both as "not resolved" and "not found" 🟠→🔴

`GhostDMMarkAsReadHook` (`sCachedContainerId`) and `GhostChannelMarkAsReadHook`
(`sCachedSeenStateId`, `sCachedHeaderButtonsId`) store the result of `getIdentifier`, but
`getIdentifier` returns `0` when a resource doesn't exist. If an Instagram update renames the id,
the lookup repeats on every view attach for the rest of the session — turning a broken feature
into app-wide jank.

**Fix:** use `-1` (or a separate boolean) for "resolved, not found".

### P4 — Uncached, unbatched DexKit scans every launch 🔴

`DexKitCache` persists resolved methods per Instagram version and 25 hooks use it. These don't,
so they rescan the whole APK on **every cold start**, even when their feature is disabled:

| Hook | `usingStrings` / `usingNumbers` queries per launch |
|------|---------------------------------------------------|
| `RemoveMetaAIHook.installReels` | ≈10 (4 markers, 3 anchors, several gates; one `usingNumbers` query scans method bodies) |
| `DisableRepostHook` | 3 anchors |
| `HideChatsHook` | 1 |

Across the code base there are 85 `bridge.find*` calls and no use of DexKit's
`batchFindMethodUsingStrings` / `batchFindClassUsingStrings`, which resolve many strings in a
single pass.

**Fix:** store results in `DexKitCache` (it already supports lists of methods), and group
string anchors into one `batchFind…UsingStrings` call per hook (or one per launch).

### P5 — DexKit bridge never closed 🔴 memory

`Module.onPackageReady` always calls `DexKitBridge.create(apk)` — even when every hook will be
served from `DexKitCache` — and keeps it in a static field for the process lifetime. The
bridge maps and indexes Instagram's dex files in native memory, which is significant for an app
of Instagram's size, and it is never used after hook installation.

**Fix:** create the bridge lazily (only on the first cache miss) and `close()` it at the end of
`onAfterApplicationAttach`. With P4 fixed, most launches then never open it.

### P6 — Disk I/O under the logging lock 🔴

`Logging.append()` is called from hooks on the UI thread and takes `LOCK`. The I/O thread holds
the same `LOCK` while it opens the log file, writes, and closes it (`appendLineToFileLocked`),
and while it rewrites the **whole** file — up to 10 000 lines — in `rewriteFileLocked`. A UI-thread
log call during a rewrite blocks for the duration of that disk write. Repeated identical lines
trigger a full rewrite every 750 ms while they keep coming.

**Fix:** under the lock, only mutate the in-memory deque and copy what needs writing; do all file
work outside the lock. Keep one `BufferedWriter` open on the I/O thread instead of reopening per
line; replace periodic full rewrites with append + occasional compaction.

### P7 — Log file loaded on the main thread at startup 🟠

`Logging.init` → `loadFromFileLocked` reads up to 4 MB line by line inside
`Application.attach`, on Instagram's main thread, before the first frame. Load it on the I/O
thread (appends can queue until the load finishes).

### P8 — Stack walk per log line 🟠

`ModuleLog.line` calls `Thread.getStackTrace()` for every message to add
`[Class.method:line]`. That's one of the more expensive operations on ART. `probe()` is correctly
gated behind `verbose`, but `line()` is used inside hooks too (e.g. `DisableRepostHook` logs on
every neutralized tap). Make the caller prefix opt-in (or verbose-only), or pass a tag explicitly.

### P9 — Hot hooks installed regardless of the feature 🟠

Every hook is installed at startup and checks its `FeatureFlags` field at call time, so that
features can be toggled live. For hooks on very hot methods the trampoline + adapter cost is paid
even when the feature is off:

- `Resources.getColor` ×2, `Context.getColor`, `TypedArray.getColor` (`IgThemeHook`) — called
  constantly during inflation and drawing;
- `Resources.getDrawable`, `getDrawableForDensity`, `ImageView.setImageResource/setImageDrawable`,
  `View.performClick/callOnClick` (`DisableVideoAutoPlayHook` — installed unconditionally at
  `handleAutoPlayDisable` line 39);
- `View.onAttachedToWindow` ×4 (four separate hooks), `LayoutInflater.inflate` ×2, `Uri.parse`.

**Fix options:** (a) libxposed API 101 returns a `HookHandle` with `unhook()`, so hot hooks can be
installed when their feature is enabled and removed when it's disabled (the sync receiver already
knows when flags change); (b) merge the four `onAttachedToWindow` hooks into one dispatcher that
compares the view id against a small set of resolved ids.

### P10 — Per-call allocation in the hook adapter 🟠

`MethodHook.intercept` builds a `MethodHookParam` and copies the argument list
(`chain.getArgs().toArray()`) before calling `beforeHookedMethod`, even when the callback's
first statement is `if (!FeatureFlags.x) return;`. On `getColor`/`getDrawable`/`onAttachedToWindow`
this is steady GC pressure.

**Fix:** add an overridable `protected boolean isActive()` (default `true`); when it returns
`false`, `intercept` returns `chain.proceed()` directly with zero allocations. Hot hooks override it
with their flag check.

### P11 — Resource name lookups per drawable load 🟠

With *Disable video autoplay* on, `isManualVideoPlayDrawableResource` runs
`getResourceTypeName` + `getResourceEntryName` for every `Resources.getDrawable` and
`ImageView.setImageResource`. Resolve `play_button` / `play_button_large` ids once and compare
ints (drawable ids are stable per Resources package).

### P12 — Downloads inside Instagram: no timeouts, unbounded pool 🟠

`StoryDownloadHook.java:1052`, `ProfilePicDownloadHook.java:221` and
`FeedVideoDownloadHook.java:2665/2677` open `HttpURLConnection`s without connect/read timeouts;
default is infinite. They run on `Executors.newCachedThreadPool()` (unbounded), so stalled CDN
connections accumulate threads that never finish. (The companion's `DownloadSaveService`
already got timeouts.) Add timeouts and use a small bounded pool (2–3 threads).

### P13 — PBKDF2 on the UI thread 🟠

Introduced by the passcode hardening: `verifyPass` runs 60 000 PBKDF2 iterations synchronously
when the user taps *Unlock* (tens to a couple of hundred ms depending on the device). Run it on
a background thread and post the result; disable the button meanwhile.

## Resource management & hygiene

- **P14** — `HookHelpers.findField` builds `name + '#' + field + '@' + hash` for every
  `getObjectField` call (the network interceptor calls it per request). Use a two-level
  `ConcurrentHashMap<Class<?>, ConcurrentHashMap<String, Field>>`.
- **P15** — `HiddenThreads`, `ThreadNames`, `UnsentLog` rewrite their whole JSON file
  synchronously on the calling (often UI) thread; `UnsentLog.add` re-serializes up to 1 000
  entries per captured message. Writes are not atomic (crash mid-write ⇒ corrupt file ⇒
  everything lost on next load). **Also a data-loss bug:** `ensureLoaded()` sets `loaded = true`
  *before* checking that a `Context` exists; if first called before `HostApp` is available, the
  store stays empty and the next `persist()` overwrites the user's file. Write via temp file +
  rename on a background thread, and only mark loaded after a successful read.
- **P16** — `AvatarLoader` decodes contributor avatars at full size into a 6 MB LRU for small
  circular views; decode with `inSampleSize` to the target size.
- **P17** — `FeatureFlags` fields are plain statics written on the main thread (sync receiver)
  and read from network/background threads (e.g. `IGNetworkInterceptor`). Without `volatile` a
  thread may keep seeing a stale value. Declaring them `volatile` costs nothing measurable here.

## What is already fine

- `DexKitCache` for 25 hooks; most reflective lookups in hooks are memoized in static fields
  (`ReelDownloadHook`, `CommentCopyHook`) or run only on user actions.
- In-memory caches for `HiddenThreads` / `ThreadNames` / `UnsentLog` (no disk read per inbox row).
- `CacheAutoClear` runs only when Instagram goes to the background, throttled, off the main thread.
- `Logging` ring buffer is bounded (10 000 lines / 4 MB) and IPC snapshots are size-capped.
- `IGNetworkInterceptor` does only string checks per request (`URI.getPath()` is cached by `URI`).
- Companion: `LocationPickerActivity` and `LoggingFragment` shut their executors down;
  `AvatarLoader` and `DownloadSaveService` use timeouts.

## Applied changes

All items were applied in the suggested order; unit tests, lint and the debug build pass.
Nothing has been profiled on a device yet — use the measurement plan below to confirm.

| # | Change |
|---|--------|
| P1–P3, P11 | New `utils/ui/ResIds` resolves resource ids by name once per process and caches "not found" (0) as well. The attach hooks and the autoplay drawable check now compare ints. |
| P6, P7 | `Logging` rewritten: `LOCK` only guards memory, all file work happens on the I/O thread with one open writer; full rewrites are debounced and go through temp file + rename; the old log is loaded on the I/O thread. |
| P8 | `ModuleLog` adds the `[Class.method:line]` prefix (stack walk) only in verbose mode; per-event `PROBE` lines in hooks were switched to `ModuleLog.probe`. |
| P4, P5 | New `utils/core/LazyDexKit`: libdexkit.so and the APK index are opened only on a cache miss and closed after hook installation. `RemoveMetaAIHook`, `DisableRepostHook`, `HideChatsHook`, `StoryDownloadHook`, `KeepUnsentMessagesHook`, `ViewOnceBadgeHook` and the main-activity lookups in `UIHookManager` now use `DexKitCache` (empty results cached too); multi-anchor searches use one `batchFindMethodUsingStrings` pass. `DexKitCache` gained constructor support (`saveMembers`/`loadMembers`). Startup logs whether DexKit was needed. |
| P10 | `MethodHook.isActive()` fast path: while it returns false the hook just proceeds, with no allocation. Overridden by the theme (`getColor` ×3, `TypedArray.getColor`, `resolveAttribute`), autoplay (drawable/image/click), `Uri.parse`, `LayoutInflater.inflate` and `Intent.putExtra/createChooser` hooks. |
| P9 | The four `View.onAttachedToWindow` hooks were merged into `hook/ViewAttachDispatcher` (one hook, per-feature listeners, no work while all are off). Installing/uninstalling hooks when a feature is toggled (option a) was **not** done: with the fast path the remaining cost of an inactive hook is one trampoline + a field read, and dynamic re-installation would add DexKit/ordering risk for little gain. |
| P12 | `MediaHttp`: 15 s connect / 30 s read timeouts on all four in-Instagram download connections; the download executor is a bounded pool (3 threads, idle threads exit). |
| P13 | Passcode verification and new-passcode hashing run on a dedicated background thread; results and the legacy-hash upgrade are published on the main thread; the unlock button is disabled while a check runs. |
| P14 | `HookHelpers.findField` uses a two-level `ConcurrentHashMap` — no per-call key string. |
| P15 | `HiddenThreads`, `ThreadNames`, `UnsentLog` only mark themselves loaded once a `Context` exists (fixes the data-loss path) and persist via `utils/core/AtomicFiles` (temp file + fsync + rename, on one background thread). |
| P16 | Re-checked: `HomeFragment` already requests `?size=144` avatars, so decoding is small. No change. |
| P17 | All `FeatureFlags` fields are `volatile`. |

## Device test — Instagram 447.0.0.21.81

Redmi 2409BRN2CA, Android 16 (SDK 36), arm64, Magisk + Vector v2.2 (3080, framework API 102).
Instagram installed from the APKMirror bundle (arm64-v8a, all splits signed with
`3a10c50c…c014`). Instagram was not logged in, so only startup, hook installation and IPC were
exercised.

**Module loading (libxposed API 101):** Vector loads `ps.reso.instaeclipse.Xposed.Module`,
dispatches `onPackageReady`, and the `Application.attach` hook works directly (no fallback).
36 feature hooks install; the only warnings are pre-existing and harmless (no `onResume`
declared on `InstagramMainActivity`; `onCreate` found via the signature fallback).

**Problems found on the device and fixed:**

| # | Problem | Fix |
|---|---------|-----|
| D1 | First launch after installing/updating Instagram: DexKit discovery ran inside `Application.attach` for ~14 s; Android 16 killed Instagram with *"failed to complete startup"* (ANR). The kill happened mid-installation, so the cache stayed partial and the next launch ANR'd too. | When the cache is invalid **or** the previous installation didn't finish (`_install_complete` marker), hooks are installed on a background thread. |
| D2 | Every hook (and DexKit) was also installed in the `:fbns` push process. | Only the main process (`processName == packageName`) is hooked. |
| D3 | With a "valid" cache DexKit still opened on every launch, because lookups with **no result** were never cached (`DoubleTapLike` feed, two `Reel` gates, `VideoUrlCapture`, `DictUserGetter`). | Negative results are cached; `LazyDexKit` logs which hook opened the bridge. |

**Measured (module log timestamps, main process):**

| Launch | Before | After |
|--------|--------|-------|
| First launch for a new Instagram version | ANR, killed after ~16 s (twice in a row) | No ANR, UI up immediately; hooks ready ≈32 s later in background |
| Warm launch: hook installation on the main thread | 1.64 s, DexKit opened (~450 ms) | **0.52 s**, `served entirely from cache` |
| `:fbns` process | ~2 s of hook installation + DexKit | nothing |

**IPC / security on device:** an `ACTION_REQUEST_PREFS` broadcast from `adb shell` (no
permission) is ignored, the same broadcast from root is handled — the signature-permission
gate works. The companion's Logs tab receives Instagram's log (nonce-checked reply), and the
Home tab detects Instagram through the new `<queries>` block.

**Logged-in feature test (same device, after the fixes):**

| Feature | Result |
|---------|--------|
| Settings sync, companion → Instagram (Features tab: ghost, ads, downloader, SAF folder) | ✅ every toggle arrives (`Sync: Updating …`) and is persisted in Instagram's prefs |
| In-app InstaEclipse menu (long-press search) | ✅ opens; module icons via `ModuleResources` and translated strings render; state matches the companion |
| Post download (⋯ menu → İndir) | ✅ via `DownloadSaveService` to the SAF folder — JPEG 720×720 |
| Reel download | ✅ MP4, h264 720×1280 + AAC |
| Story download ("video with music") | ✅ MP4, h264 720×1280 + AAC, 15 s |
| Profile picture download (long-press expanded picture) | ✅ JPEG 1080×1080 |
| Custom theme on/off | ✅ recolors live (8 attrs, 30 colors, 53-entry remap table), reverts cleanly |
| Ghost story seen | ⚠️ hooks installed; actually staying off the viewer list needs a second account to confirm |
| Allow screenshots in DMs | ✅ toggled live: the DM thread window lost `FLAG_SECURE` and became capturable; reverted afterwards |
| Ghost "mark as read" button in the DM composer | ✅ injected (shared `ViewAttachDispatcher` + `ResIds` + module icon via `ModuleResources`); not pressed, to avoid sending a read receipt in a real conversation |
| DM lock: set passcode | ✅ stored as `pbkdf2$60000$…` with a random salt, hashed off the UI thread |
| DM lock: overlay, 5 wrong codes | ✅ "Too many attempts. Try again in 30s"; the correct code is rejected during the lockout |
| DM lock: unlock after lockout / disable / clear | ✅ unlocks; disabling asks for the code; passcode and salt cleared; all lock settings restored afterwards |
| Crashes / ANRs since the fixes | ✅ none in `dumpsys dropbox` (only the two pre-fix ANRs at 11:53 and 11:55) |

**Not tested:** anything that would act in a real conversation (DM seen/typing suppression,
pressing mark-as-read, keep unsent — needs a second account), hide chats, whole-app lock,
upgrading an existing legacy SHA-256 passcode, Meta AI removal.

**Other observations:** the DM lock overlay ("Enter passcode to open DMs") also appeared
over the home feed right after enabling the lock, not only when opening DMs — likely because the
inbox fragment was still alive in the main activity; pre-existing behaviour, worth checking.
 Android logs `Foreground service start … does not have any types`
for `DownloadSaveService` although the manifest declares `dataSync` and the service passes the
type to `startForeground` (harmless on this device, worth a look).

**Seen, not fixed:** the Logs tab's text area is only a few lines tall in landscape.

## Suggested order

1. P1, P2, P3, P11 — small, local changes, remove per-view/per-drawable string work.
2. P6, P7 — logging lock and startup read.
3. P4, P5 — DexKit caching/batching and closing the bridge (largest startup and memory win).
4. P10, then P9 — adapter fast path, then install/uninstall hot hooks on toggle.
5. P12, P13, P15 — threading and I/O hygiene.

## Measurement plan

Nothing here was profiled. Before/after each step:

- **Cold start:** `adb shell am force-stop com.instagram.android`, then
  `adb shell am start -W -n $(adb shell cmd package resolve-activity --brief com.instagram.android | tail -1)`
  (TotalTime), 10 runs each with the module enabled/disabled; plus timing logs around
  `DexKitBridge.create` and each `install…` call in `Module.onAfterApplicationAttach`.
- **Jank:** `adb shell dumpsys gfxinfo com.instagram.android reset`, scroll feed/reels for 30 s,
  then read janky-frame % and 90th/99th percentile frame times; or a Perfetto trace with
  `android.view.Choreographer` slices.
- **Memory:** `adb shell dumpsys meminfo com.instagram.android` (Native Heap / TOTAL PSS) after
  startup, with and without closing the DexKit bridge.
- **Threads:** `adb shell ps -T -p <pid> | wc -l` during and after a batch of downloads.
