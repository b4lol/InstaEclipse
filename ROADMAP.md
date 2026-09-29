# InstaEclipse — Roadmap

> Prepared: 29 September 2026  
> Status: implementation plan; the Kotlin migration and the API 102 adoption have not started.  
> Reference: `modernize/libxposed-101` at `404dc80` (libxposed 102 / AGP 9 toolchain), `v0.7.0-test.2`.

This document has two tracks:

- **Java/Kotlin hybrid migration** (sections 1–6): move new and maintenance-heavy code to Kotlin
  without rewriting working features.
- **libxposed API 102 adoption** (section 7): use what API 102 adds on top of the current hook
  model.

The tracks are independent. They share the invariants, test strategy, release and rollback rules
in sections 8–13, and they are never mixed in one change.

## 1. Goal and definition of success

Turn InstaEclipse into a project where Java and Kotlin live side by side and that is easier to
maintain, while keeping the current libxposed runtime model. Instead of rewriting working features
in bulk, use Kotlin for new development and for areas that need maintenance anyway.

Success is not measured by the share of Kotlin files. Success criteria:

- Hook behavior inside Instagram and user settings are preserved.
- State, errors and lifecycle of new screens and background work become explicit.
- Java–Kotlin boundaries are tested; data contracts between the two processes are documented.
- No unaccepted regression in startup, scrolling, memory or download performance.
- Every step consists of small, reviewable and revertible changes.
- Every published APK maps to a specific source commit, signature and verification record.

This document defines the target architecture and the work to do. Boxes are ticked only when the
matching evidence is attached. Dates and effort estimates are set after the Phase 0 inventory;
the order given here is not a delivery commitment.

## 2. Current state and reference version

| Area | Current setup | Approach during the migration |
| --- | --- | --- |
| Application | Single `app` module, Java and XML UIs | Mixed use inside the same module first |
| Identity | `ps.reso.instaeclipse` | Kept |
| Android | `minSdk 28`, `compileSdk 37`, `targetSdk 36` | Managed independently of the language migration |
| Java target | Java 17 source and bytecode compatibility | Matched by the Kotlin JVM target |
| Build | AGP `9.4.1`, Gradle `9.8.0`, version catalog | Kotlin added in one small change once compatibility is verified |
| Hook engine | libxposed API `102.0.0`, service `102.0.0`; `minApiVersion=101`, `targetApiVersion=102` | Current hook model kept; API 102 features per section 7 |
| Method discovery | DexKit, `LazyDexKit`, `DexKitCache` | Cache and lazy opening kept |
| UI | Activities, Fragments, Views and XML; Material 3 Expressive with Dynamic Colors | Compose is not required for Kotlin |
| Settings | `instaeclipse_prefs` on the Instagram side, `instaeclipse_cache` on the companion side | Keys and their meaning kept |
| Inter-process communication | Package-targeted broadcasts, signature permission, nonce and remote preferences | Security contracts kept |
| Shrinking | `minifyEnabled false` for release | R8 handled as a separate effort |
| Verification | 51 JVM unit tests, lint (including the libxposed checks), debug APK build | Extended with mixed-language and device checks |

The unit tests, lint and a debug build pass on the reference commit. Device checks on Instagram
447.0.0.21.81 with the Vector framework covered module loading, hook installation and part of the
features; they are not a full feature verification.

Feature work since the first modernization — following-only feed, external links, caption copy
without hashtags, the Extras section, the Material 3 Expressive UI and the location spoof rework —
is committed (`421a76f`, `22e6db1`) but only partly verified on a device. Phase 0 must record which
features count as the baseline and their verification state.

Related documents:

- [Architecture](docs/ARCHITECTURE.md)
- [Contributing](CONTRIBUTING.md)
- [Security policy](SECURITY.md)
- [Security audit](docs/SECURITY_AUDIT.md)
- [Performance audit](docs/PERFORMANCE_AUDIT.md)
- [Changelog](CHANGELOG.md)

## 3. Scope and architecture decisions

### 3.1 Accepted direction

1. The libxposed-based runtime model continues.
2. The Java hook core is kept at the start.
3. Kotlin is used first in new Android-independent logic and in the companion app.
4. No class is converted just for the sake of a single language.
5. Language conversion, behavior changes and package moves happen in separate changes wherever
   possible.
6. Module boundaries are first clarified through code dependencies; splitting into Gradle modules
   is justified later.
7. Choices that change product behavior come with tests and a release note entry.

### 3.2 Out of scope

- Converting all Java sources to Kotlin.
- Building a new patcher that modifies the Instagram APK directly.
- Copying Morphe's code or module layout.
- Changing the libxposed API, upgrading the SDK and migrating to Kotlin in a single change.
- Rewriting every screen in Compose.
- Adding a database, DataStore, a DI framework or a new networking library only because Kotlin was
  added.
- Expecting speed, lower RAM use or lasting compatibility with new Instagram versions from a
  language change.

Morphe Patcher is a library that modifies APK bytecode and resources. This plan instead improves
the existing module, which intervenes while Instagram runs. If direct APK patching is ever wanted,
a separate feasibility document must cover packaging, re-signing, version matching, distribution
and maintenance costs.

## 4. Target responsibilities and language split

| Area / current examples | Process | Target language | Decision |
| --- | --- | --- | --- |
| `Xposed/Module`, `hook/*` | Instagram | Java | Entry point and interceptor semantics kept |
| `LazyDexKit`, `DexKitCache`, `ResIds` | Mostly Instagram | Java | Discovery and performance core outside the first migration |
| `mods/ghost`, `mods/network`, `mods/ads`, `mods/extras`, feed and UI hooks | Instagram | Java first | Evaluated separately only for a concrete maintenance need |
| `MainActivity`, `fragments/*` | Companion | Gradual Kotlin | Screen by screen, starting with the existing XML |
| `VersionCheck*`, backup and pure validation logic | Companion / shared | Kotlin candidates | I/O and business rules separated first |
| `DownloadSaveService`, download coordination | Companion | Kotlin candidate later | Not converted before the service lifecycle is verified |
| `SettingsManager`, `RemotePrefs`, `IpcSecurity` | Both processes | Java contract + Kotlin adapter where needed | Storage/IPC behavior kept |
| `FeatureFlags`, `FeatureManager` | Mostly Instagram | Java at first | Synchronous, cheap reads on hot paths kept |
| `DialogUtils`, `ExpressiveKit`, views added inside Instagram | Instagram | Java at first | Not mixed with the companion UI conversion |
| Location and theme Activities, `LocationPresets` | Companion | Kotlin later | Handled separately from the related Instagram hooks |
| Tests | JVM / device | Java and Kotlin | Contract tests may call from both languages |

### 4.1 Dependency direction

```text
Companion UI (Kotlin/Java) ──> companion business logic ──> data/IPC adapters
                                                      │
                                           shared data contracts
                                                      │
Instagram hooks (Java) ──> hook core + local view of the settings
```

Shared contracts must not depend on the companion UI, on Fragment/Activity classes or on specific
Instagram classes. Package separation inside one APK is not process isolation; Context,
ClassLoader, UID and permission ownership must be right on every call.

### 4.2 Possible later Gradle modules

The names below are suggestions; no new module is created at the start:

- `:core-contracts`: data and behavior contracts without Android dependencies.
- `:hook-runtime`: the libxposed and Instagram-side core.
- `:companion`: the companion app's screens and services.
- `:app`: manifest, resources and APK packaging.

No physical split happens before `R` resources, manifest merging, native DexKit packaging,
dependency cycles and the Xposed entry metadata are solved. Staying with a single `app` module is
an acceptable final outcome.

## 5. Java–Kotlin interoperability rules

### 5.1 API and bytecode contracts

- Plain classes, interfaces, enums and explicit result types are preferred for Java callers.
- `suspend`, `Flow` and Kotlin-specific function types are not pushed into the existing Java hook
  API; companion-side adapters are provided where needed.
- The real signatures produced by `object`, `companion object`, default parameters and property
  access are inspected for Java calls.
- `@JvmStatic`, `@JvmField`, `@JvmOverloads`, `@JvmName` and `@Throws` are used only when an
  existing call contract requires them.
- Class/method names, visibility, constructors and parameter types reached through reflection are
  kept. Manifest, explicit intent and `java_init.list` references are checked separately.
- Keeping the package and class name is not enough on its own; field access, static members and
  exception behavior are verified too.
- Kotlin `internal` visibility is not a security or process-isolation boundary.

### 5.2 Null, collection and data compatibility

- Platform types coming from Java are not trusted as non-null data; they are validated at the
  boundary.
- `!!` is an exception in new code and its use must be justified.
- Intent extras, reflection results, disk/JSON data and host app objects are validated before use.
- Primitive/boxed differences, nullable values and shared mutable collections are covered by tests.
- Models used with Gson are tested with old samples for field names, defaults, missing/null fields
  and constructor behavior; converting to a `data class` does not guarantee compatibility.
- Meaningful error types reach the user or caller instead of empty results that hide failures;
  the current fail-safe hook behavior is kept.

### 5.3 Threads and coroutines

- Hook callbacks stay synchronous; work that must affect the result is not deferred to a coroutine.
- No `runBlocking` on hook, UI or main threads.
- No `GlobalScope`; every job has an owner and a cancellation point.
- Work tied to a Fragment's views follows the view lifecycle. Starting the same work twice after a
  recreation is prevented.
- Service work is owned by an explicit service scope and cancelled on shutdown and timeout.
- `CancellationException` is not swallowed as an ordinary error; broad `catch` and `runCatching`
  uses are reviewed for this.
- `Dispatchers.IO` alone does not limit how many downloads run; concurrency is bounded separately.
- Moving to coroutines does not make blocking I/O cancellable; connections, streams and file
  handles are closed.
- `volatile` fields do not make multi-field updates atomic; a consistent settings snapshot or
  explicit synchronization is designed.

### 5.4 ClassLoader and performance

- That Kotlin runtime classes resolve through the module's class loader is tested on a real
  framework; Instagram's own Kotlin dependencies are not relied on.
- Companion ViewModel/lifecycle infrastructure is not brought into the hooked Instagram process.
- No coroutines, Flow collectors, needless lambda/collection chains or new reflection scans on hot
  hook paths.
- The `isActive()` fast exit while a feature is off is kept.
- `ViewAttachDispatcher` is used for view events, `ResIds` for resource lookups and the existing
  DexKit cache for discovery.
- No disk I/O under the log lock; no network, password derivation or long disk work on the main
  thread.

## 6. Phases and exit gates

Dependency order: **Phase 0 → Phase 1 → Phase 2 → Phase 3 → Phase 4 → Phase 5 → Phase 6**.
Phase 7 is optional. Security, tests and documentation are part of every phase.

### Phase 0 — Fix the baseline and measurements

- [ ] Decide which of the committed features (`421a76f`, `22e6db1`) belong to the baseline and
  record their verification state; keep unrelated work out of migration PRs.
- [ ] Record a clean, reproducible baseline commit, the APK checksum and the signing certificate
  fingerprint.
- [ ] Build a feature inventory: settings key, default, hook class, process, dependencies,
  supported versions and verification state.
- [ ] Settle the state of the following-only feed, external links, caption copy, Extras and
  location spoofing on the baseline commit.
- [ ] List the class/method/data contracts used through reflection, the manifest and IPC.
- [ ] Create test fixtures without personal data for settings backups, old JSON samples and broken
  input.
- [ ] Record the current JVM/lint/build results; separate known failures from new regressions.
- [ ] Run the device and performance measurements of section 9 on the reference APK.
- [ ] Record a test owner, device access and verification gaps for every feature.

**Exit gate:** a baseline tied to a source commit, a feature matrix and a measurement record exist.
Areas without device access are explicitly marked "not verified"; no stable release happens while
areas required for it are missing.

### Phase 1 — Kotlin build support

- [ ] On the day of implementation, pick a Kotlin version compatible with the current AGP/Gradle/JDK
  from the official compatibility documents and pin it in the version catalog.
- [ ] Use AGP 9's built-in Kotlin support (no separate `org.jetbrains.kotlin.android` plugin); do
  not mix setup methods from different AGP generations.
- [ ] Align the Java and Kotlin JVM targets at 17; pin the CI JDK explicitly.
- [ ] Decide the source directories: `app/src/main/kotlin` and `app/src/test/kotlin`; the Java
  directories stay.
- [ ] Review the Kotlin runtime version/dependency tree; find duplicated and needless dependencies.
- [ ] Add a small Kotlin component that serves a real pure-logic need, plus a test calling it from
  Java; leave no unused sample classes.
- [ ] Verify a build with Java-to-Kotlin and Kotlin-to-Java calls.
- [ ] Check clean debug/release builds and that the libxposed metadata and native libraries stay in
  the APK.
- [ ] Record the change in APK size, method count and clean/incremental build times.
- [ ] Update `CONTRIBUTING.md` with the Kotlin style and interoperability rules.

**Exit gate:** the mixed-language build passes in CI, current behavior is unchanged and the
framework can load the module. No Compose, storage change or hook conversion is added in this
phase.

### Phase 2 — Low-risk pilot conversion

- [ ] Pick one candidate: Android-independent version comparison or a similarly bounded
  validation/policy component (for example `LocationPresets`' pure helpers).
- [ ] Pin edge cases and the existing Java calls with tests before converting.
- [ ] Convert to Kotlin while keeping the public API, exceptions and null behavior.
- [ ] Put any logic change needed during the conversion in a separate commit/PR.
- [ ] Verify that the old and new implementations give the same results on the same fixtures.
- [ ] Assess the pilot's real gain: readability, testability, dependency cost and review effort.
- [ ] If the pilot fails, revert the conversion; decide separately whether to keep the working
  Kotlin build support.

**Exit gate:** Java callers work unchanged, meaningful regression tests pass and a documented
example for later conversions exists.

### Phase 3 — Gradual conversion of companion screens

Suggested order: `HelpFragment` / a simple screen → `HomeFragment` → `LoggingFragment` →
`FeaturesFragment` → `MainActivity` coordination → theme and location Activities. Real dependency
analysis may change the order.

- [ ] Move the first screen to Kotlin while keeping the existing View/XML look.
- [ ] Tie view references to the view lifecycle; use View Binding where it helps.
- [ ] Move network/disk/IPC work out of Fragment bodies into small service or repository
  interfaces.
- [ ] Use a ViewModel for complex screen state; do not add layers to simple static screens.
- [ ] Model loading/empty/success/error/cancelled states explicitly.
- [ ] Verify rotation, backgrounding, returning and process recreation.
- [ ] Keep the staging/commit behavior of settings changes, reloading on re-entry and updates made
  from both sides.
- [ ] The log screen keeps its bounded buffer and personal data scrubbing; collection stops when the
  screen is left.
- [ ] Check overflow in Turkish, English and an RTL language; font scale, TalkBack, dark theme and
  accessibility.
- [ ] Keep all user-facing text in resource files; keep the existing translation keys.

**Exit gate:** behavior and lifecycle checks pass per converted screen; any UI refresh is reviewed
separately from the language conversion. The in-Instagram `DialogUtils` menu is not part of this
phase's companion conversion.

### Phase 4 — Companion background work

Suggested order: update check → settings backup/restore → download coordination. Working security
components such as `PasscodeHasher` are not converted just to raise the Kotlin share.

- [ ] Define owner, scope, dispatcher, timeout, cancellation and retry policy for every job.
- [ ] Handle concurrent duplicate requests and leaving the screen during the update check.
- [ ] Move backup/restore off the main thread; do not modify existing data before validation.
- [ ] Keep notification, FGS start restrictions, service shutdown and the Android 15+ timeout
  behavior of `DownloadSaveService`.
- [ ] Bound download parallelism and queue size explicitly; no unbounded coroutine launches.
- [ ] Verify stream/connection closing and partial file cleanup on timeout, cancel and retry.
- [ ] Prevent accidental duplication of the same media on retry; document the current naming
  policy.
- [ ] Keep the CDN HTTPS allowlist, redirect validation, size limit and file name sanitizing.
- [ ] Test lost SAF permission, full storage, network loss, service kill and multiple downloads.
- [ ] Verify output integrity for video/audio muxing, carousels and different media types.

**Exit gate:** device tests pass, including cancel and error paths; no lost/broken media, unbounded
job build-up or service leak. Download paths running inside Instagram are not changed by the
companion conversion.

### Phase 5 — Strengthening the settings and IPC contracts

The goal of this phase is not to change the storage technology. Where needed, a Kotlin interface
wraps the existing Java implementation.

- [ ] Document settings keys, types, defaults and which side is authoritative for each field.
- [ ] Evaluate a typed, Java-compatible contract for scattered string-based access.
- [ ] Keep fast `FeatureFlags` reads; no disk reads or Flow subscriptions on hook paths.
- [ ] Test offline/restart scenarios between the companion cache, the Instagram prefs and remote
  preferences.
- [ ] Keep package targeting, the signature permission and nonce matching on every related channel.
- [ ] Add negative tests with missing, mistyped, oversized or unauthorized data.
- [ ] Never send secrets such as passwords/hashes/tokens over IPC or to logs.
- [ ] If the JSON/settings schema really changes, define versioning, upgrade, recovery from broken
  data and old-version behavior separately.
- [ ] Writes stay atomic; a failed import never deletes the existing settings.
- [ ] Verify the current fallback path on a device for setups without framework remote
  preferences.

**Exit gate:** old settings and backups are read, cross-process sync works, unauthorized requests
are rejected and no data loss is seen after the conversion.

### Phase 6 — Regression, test release and stabilization

- [ ] Complete the matrix of sections 9–11 on the target APK.
- [ ] Compare performance and size with the baseline under the same conditions.
- [ ] Close P0/P1 issues; list unresolved lower-priority issues in the release notes.
- [ ] Produce a prerelease that ties every change to a specific commit.
- [ ] Prepare a report template for testers with device/Android/Instagram/framework versions and
  reproduction steps.
- [ ] Publish a test build after every small conversion group; do not wait for the end of a big
  batch.
- [ ] Define the test period and enough device coverage before release; do not rely on "no
  complaints".
- [ ] Update the architecture, contributing, changelog and known-issues documents.
- [ ] Verify the rollback steps with a real settings backup and selected versions.

**Exit gate:** source, artifact, signature, test report and known limitations match. All gates
required for a stable release are closed.

### Phase 7 — Optional architecture improvements

Only once the earlier phases are stable and a concrete benefit is shown:

- [ ] Evaluate a Gradle module split based on dependency cycles and build time data.
- [ ] Write a separate decision record for a Compose prototype on one companion screen; measure the
  APK size and performance impact.
- [ ] Consider a Kotlin DSL for hook registration only if it shows a maintenance gain and low runtime
  cost over the Java API.
- [ ] Treat R8 as a separate effort; create keep rules/tests for reflection, JNI, serialization,
  metadata and entry classes.
- [ ] Handle any storage or DI change with its own migration and rollback plan.

**Exit gate:** every proposal has an accept/reject record with reasons. Skipping this phase does not
mean the hybrid migration failed.

## 7. libxposed API 102 adoption

### 7.1 Current state

The build uses libxposed api and service `102.0.0` (`404dc80`). `module.prop` declares
`targetApiVersion=102` and keeps `minApiVersion=101`, so frameworks that only support API 101 still
load the module. No API 102 method is called yet. The libxposed lint checks flag API 102 calls
made without a framework version check.

API 102 does not change the hook model: the hook layer (`Module`, `MethodHook`, `HookBridge`,
`HookHelpers`, `RemotePrefs`) is the only code that talks to libxposed, and the feature code above
it (85 files, ~21.7k lines) is not affected. **A full rewrite of the hooks for API 102 is not
planned:** it would bring no benefit and would risk breaking the Instagram-version-specific lookups
verified on 447.

What API 102 adds:

| Addition | Where | Use here |
| --- | --- | --- |
| Hook IDs (`HookBuilder.setId`, `HookHandle.getId`) | api | Name every hook after its feature for logs and diagnostics |
| `HookHandle.replaceHook` | api | Swap a hooker in place instead of unhook + hook |
| `XposedModule.detach()` | api | Remove the module from Instagram when every feature is off |
| Hot reload (`onHotReloading` / `onHotReloaded`) | api | Load a new module build without restarting Instagram |
| `getRunningTargets`, `hotReloadModule` | service | Show hooked Instagram processes; trigger a reload from the companion |

### 7.2 Rules

- Every API 102 call is guarded by the framework's API version (`XposedInterface.getApiVersion()`
  in the module, `XposedService.getApiVersion()` in the companion). Lint must stay clean.
- `minApiVersion` stays 101 unless a decision record justifies dropping API 101 frameworks.
- API 102 work is never mixed with a Kotlin conversion in the same change.
- Hot reload stays refused (the default `onHotReloading` returns `false`) until step 5 below is
  complete; the module keeps static state that a reload would otherwise leave stale.

### 7.3 Steps

Estimates are working time at the pace of the recent sessions, including device testing.

| Step | Work | Estimate | Exit gate |
| --- | --- | --- | --- |
| 0 | Log the framework's API version at module load and show it in the companion; confirm whether the test device's Vector supports API 102 | < 1 h | Framework name and API version recorded in the device matrix |
| 1 | Hook IDs through `HookBridge` for all ~174 hook installs; `replaceHook` where a hooker is swapped | 1–2 h | Every hook log line carries its feature ID; no behavior change on API 101 |
| 2 | Companion status card: hooked Instagram processes via `getRunningTargets` | 1–2 h | Card shows running targets on API 102, hides the row on API 101 |
| 3 | `detach()` when every feature is off, re-attach on the next launch | ~1 h | Instagram runs unhooked with all features off; enabling a feature takes effect after a restart |
| 4 | Hot reload design: inventory the 105 mutable static fields in `mods/`, the 9 broadcast receivers, views injected into Instagram, open sheets and DexKit handles; decide what goes into the saved-state `Bundle` | 2–3 h | Written inventory and design note |
| 5 | Hot reload implementation: `onHotReloading` saves state and tears down receivers/views, `onHotReloaded` restores it; companion "Reload in Instagram" action through `hotReloadModule` | 4–7 h | Reload on a device keeps settings, hidden chats, unsent log and open features; no duplicate receivers, hooks or views; repeated reloads do not grow memory |

Steps 0–3 take about half a day and are low risk. Steps 4–5 take about one and a half to two days
and carry most of the risk; their main benefit is development speed (no Instagram restart per
build), so they are done only when that is needed.

### 7.4 Risks

| Risk | Early sign | Mitigation / rollback |
| --- | --- | --- |
| Framework on the test device lacks API 102 | Step 0 reports API 101 | Steps 1–5 stay behind the version check; verify on a framework that supports 102 |
| Stale static state after hot reload | Features act on old settings or old views | Inventory in step 4, reload tests in step 5, keep refusing reloads until they pass |
| Duplicate receivers or hooks after reload | Actions fire twice, rising memory | Explicit teardown in `onHotReloading`; default `onHotReloaded` unhooks old handles |
| `detach()` leaves partial state | Injected views or receivers survive | Detach only at startup before hooks install features, or tear down first |

## 8. Settings, data and security invariants

1. The application identity and the data paths an existing install relies on are not changed by a
   language conversion.
2. Settings keys and defaults are not changed without an explicit product decision.
3. Hidden chats, unsent message logs, the story cache, DM lock data and the location history are
   compared before/after a conversion; sensitive records are not copied into test reports.
4. Upgrading old password hashes, PBKDF2 verification and the lockout behavior are kept.
5. The signature-protected companion → Instagram channel is not loosened.
6. The Instagram process is not assumed to share the companion's UID/permissions; access in the
   other direction (Activities/Services) is verified separately.
7. Exported component inputs and URI permissions are not trusted.
8. New dependencies are reviewed for need, maintenance state, license and APK/runtime impact.
9. Release signing keys, tokens and real user data are never written to the repository or CI logs.
10. If a data schema changes, the old version is not assumed to read the data; the rollback path is
    tested separately.

## 9. Test strategy and device matrix

### 9.1 Automated checks

Current baseline check:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Additionally, when the Kotlin setup or packaging changes:

```bash
./gradlew assembleRelease
```

A passing `assembleRelease` does not mean a signed, distributable APK was produced; the signing
configuration is verified separately. `clean` is not required on every run; a clean-environment
build is done in Phase 1 and at the release gate.

Test coverage:

- Hook interceptor chain: argument changes, early result/exception, after callbacks and callback
  error isolation.
- Java–Kotlin signatures, null behavior, serialization and edge values.
- Settings import/export compatibility, atomic writes and broken data.
- URL/redirect/file name/size validation policies.
- Coroutine cancellation, re-entry, timeouts and bounded parallelism; a proper test scheduler if
  coroutines are added.
- Instrumentation tests where needed for Fragment/Activity lifecycle, permissions and exported
  components.
- API 102 paths with the framework reporting API 101 and API 102.

JVM tests do not verify real libxposed loading, the ClassLoader or Instagram's behavior. Device
checks are needed as well. Tests are written to catch data loss and behavior regressions, not to
repeat the implementation line by line.

### 9.2 Minimum platform coverage

| Dimension | Check |
| --- | --- |
| Minimum Android | Companion install/launch on API 28; hook support stated per tested framework |
| Current target | Full end-to-end check on Android 16 / API 36 |
| Service restrictions | FGS/notification/timeout checks on Android 14/15 or devices giving equivalent coverage |
| Framework | Exact version and libxposed API version (101 or 102) of the framework used |
| LSPatch | Separate verification on a real API 101+ setup if support is claimed |
| Instagram | Reference version, the current version targeted at release time and the supported previous version |
| Package variants | The official package and variants that really claim support; the package allowlist is not test evidence |
| Hardware | A low/mid memory device where possible and at least one physical arm64 device |
| Install | Clean install, upgrade with the same key, backup/restore and process restart |
| Language/accessibility | Turkish, English, RTL, large font, TalkBack and dark theme |

For every row, record device model, ABI, Android build, Instagram version, framework version, APK
commit, result and date. Untested combinations are never presented as "tested".

### 9.3 Feature regression matrix

Every test also covers normal Instagram behavior with the feature on and off.

| Group | Minimum scenarios |
| --- | --- |
| Startup | Companion/Instagram cold and warm start, module enabled/disabled, unsupported process, first launch after an Instagram update |
| Settings | Changes from the companion and from the Instagram sheet, changes while an app is closed, restart and sync |
| Privacy | DM/story seen, typing, screenshots, view-once, disappearing messages and manual mark-as-read |
| Chat data | Hidden chats, unsent logs, DM lock with right/wrong passcode, lockout and restore |
| Feed/UI | Ad/suggestion filters, following-only feed, reels/story blocking, DM exception, Meta AI removal, zoom and menus |
| Media | Photo, video, reel, story, carousel, profile picture, audio muxing, naming, SAF/default folder |
| Helpers | Caption/comment copy (including without hashtags), external links, mention/follow indicators, autoplay and double-tap preferences |
| Extras | Airplane mode (live toggle), story posting time, Reels tap-pause/auto-scroll/scroll lock, swipe-to-camera, share-sheet group, unlimited accounts, startup tab, share domain |
| Theme/location | Theme, fonts, Dynamic Colors; location picker, recent places, spoofing without the real permission, and their effect in Instagram |
| Developer settings | JSON import/export, broken/incompatible files and unauthorized calls |

This table is the starting scope; the final list must cover every key in the Phase 0 inventory.
Extras that could not be verified on the reference device (tap-pause and auto-scroll are already
Instagram's default there; unlimited accounts, share-sheet group, comment/DM double tap and the
share domain need outward actions to test) stay marked "not verified" until tested.

## 10. Performance and size acceptance criteria

The thresholds below are suggested review thresholds; they are not current measurement results or
performance guarantees. Device noise is measured in Phase 0 and the thresholds are then fixed.
They are not loosened after a failure without a reason.

| Measurement | Method | Suggested gate |
| --- | --- | --- |
| Cold/warm start | At least 10 runs on the same device/versions; median, spread and raw results | No progress without review and a reason if the reproducible median grows by more than 5% |
| First launch after an update | Controlled DexKit cache clearing, same scenario | No new ANR; discovery time recorded |
| Warm-start DexKit | Bridge opening count from logs/tracing | No needless bridge opening while the cache is valid |
| Scrolling | Fixed feed/reels scenario, frame times and jank | No reproducible regression accepted before it is explained and fixed |
| Memory | Separate PSS/heap for companion and Instagram; same workload | No steady growth or lifecycle leak; review if stable PSS grows by more than 10% |
| Hook cost | Time/allocation profile of frequent callbacks | No new I/O, discovery or allocation-heavy path while a feature is off |
| Downloads | Same media and network conditions; duration, errors, cancel and output correctness | No unbounded concurrency, broken/incomplete output or resource leak |
| APK/method count | Same build type and signing conditions | Every increase recorded; dependency review if the APK grows by more than 10% |
| Build time | Clean and incremental builds separately on the same machine | New bottlenecks recorded; a reproducible 20% increase is reviewed |

No reliable p95/p99 is derived from small samples; more runs are made for tail-latency decisions.
If network content, thermal state or cache differences distort a comparison, the measurement is
redesigned. A speed gain from the language change is claimed only if these measurements support it.

## 11. CI, versioning and APK distribution

### 11.1 CI work

- [ ] The current workflow only covers pushes to `main`/`0.4`, PRs targeting `main` and manual runs;
  make sure the migration branch gets the required checks automatically.
- [ ] Pin the JDK/Gradle/Kotlin versions; keep wrapper validation (the wrapper already pins the
  distribution SHA-256).
- [ ] Make Java and Kotlin tests, lint and the debug build required checks.
- [ ] Add a release build for packaging changes.
- [ ] Keep lint and test reports as artifacts on failure too.
- [ ] Never expose publishing tokens or signing secrets to untrusted PRs.
- [ ] Keep read-only permissions in build jobs; grant write permission only to the publishing job.

### 11.2 Versioning policy

- Test builds are marked as GitHub prereleases and do not move the stable "latest" marker.
- The tag, version name and `versionCode` policy is set in Phase 0. `versionCode` increases for
  every distributed update.
- The current test build is `0.7.0-test.2` / `18`, so test builds are distinguishable inside the
  app.
- The APK, its SHA-256, the signing certificate details, a test summary and known issues are
  published together, all from the same commit.
- A signing strategy for test and stable builds is decided. Temporary CI debug keys can break
  updates; keys are stored securely.
- If a side-by-side test install with a different applicationId is wanted, the effects on the
  signature permission, IPC targets, provider authority and framework scope get a separate design.
- The APK signature is checked with `apksigner verify`; asset upload and the prerelease/draft state
  of the release are verified at the end of publishing.

### 11.3 Required release note content

- Source commit, tag, app version and build type.
- Areas converted in this release and user-visible changes.
- Minimum Android and the required libxposed API support (101 minimum; which features need 102).
- Device/Instagram/framework combinations actually tested.
- Known issues, untested areas and data compatibility.
- Signing compatibility, upgrade and, if needed, backup steps.
- Artifact checksum and how to report issues.

## 12. Rollback and issue handling

### 12.1 Rollback triggers

- A new crash/ANR or the module failing to load.
- Loss or corruption of settings, message logs or media.
- Unauthorized IPC access or leakage of secret data.
- A reproducible, unaccepted performance regression.
- A widespread install/signing failure or a core feature not working.

### 12.2 Procedure

1. Match the issue to its source commit and device matrix; stop new releases or clearly mark the
   affected release.
2. Revert the related conversion PR; do not assume users can simply downgrade to an older APK.
3. Preferably ship a fix release signed with the same key, with a higher `versionCode`, that
   restores the previous stable behavior.
4. If the schema changed, use the reverse migration or a previously verified backup restore path.
5. If a reinstall is needed, explain data loss and signing conditions in the release notes.
6. Add a regression test and put the rollback APK through the baseline checks too.

Reverting source code does not restore user data by itself. Risky data schema changes are
therefore kept separate from language conversions.

### 12.3 Prioritization

| Priority | Example | Release decision |
| --- | --- | --- |
| P0 | Data loss, security hole, widespread launch crash | Stop releases; urgent fix/rollback |
| P1 | Core feature broken, reproducible ANR, install blocker | Blocks a stable release |
| P2 | Breakage in a limited combination, usable workaround | Decided by impact and support scope, known-issue entry |
| P3 | Cosmetic issue, small doc/UX gap | Planned maintenance |

## 13. Risk register

| Risk | Early sign | Mitigation / rollback |
| --- | --- | --- |
| Kotlin runtime/ClassLoader mismatch | Class/method not found while the module loads | Phase 1 device gate, no reliance on host dependencies, revert of the setup |
| Java API signature changes | Reflection or Java calls break | Signature/contract tests, keep the old facade |
| Lifecycle bugs | Duplicate requests, callbacks into closed screens | Scope ownership, cancellation and recreation tests |
| Settings mismatch | Companion and Instagram show different values | Per-field authority and offline sync tests |
| Data format breakage | Old backups unreadable or defaults changed | Fixture tests, versioned migration, atomic writes |
| Security regression | Permission/nonce checks skipped | Negative IPC tests, exported component review |
| Hot-path load | Scroll stutter, more allocations | Keep the hook core, profiling and cache checks |
| APK/build growth | Runtime or UI dependencies grow needlessly | Minimal dependencies, Compose/DI as separate decisions |
| Instagram update | Obfuscated targets not found | Version matrix, cache invalidation, disable the feature safely |
| Framework API mismatch | API 102 path runs on an API 101 framework | Version checks, lint, tests on both API levels (section 7) |
| Too broad a change | Unclear which change caused an issue | Small PRs, single responsibility, prerelease per phase |
| Signing discontinuity | Update cannot be installed | Fixed signing strategy and a real upgrade test |

## 14. First work items and PR order

| Order | Work | Prerequisite | Concrete output |
| --- | --- | --- | --- |
| 1 | Baseline commit and feature inventory | Scope of the committed features clear | Baseline/test matrix |
| 2 | API 102 steps 0–1 (framework version, hook IDs) | Baseline recorded | Version shown in the companion, hook IDs in logs |
| 3 | Kotlin build support | Phase 0 done | Version catalog, built-in Kotlin, JVM target, mixed-language verification |
| 4 | Pure-logic pilot | Phase 1 device/CI gate | Small Kotlin component and Java compatibility tests |
| 5 | First companion screen | Successful pilot | Kotlin screen with XML kept, lifecycle verification |
| 6 | Update check | Scope policy defined | Tested cancel/timeout and screen integration |
| 7 | Other companion screens | Previous screen stable | Small PR and test record per screen |
| 8 | Backup/restore | Data fixtures ready | Compatibility and broken-data tests |
| 9 | Download coordination | Service/FGS test devices ready | Bounded concurrency, cancel, error and output tests |
| 10 | Settings/IPC adapters | Inventory and contracts ready | Typed boundaries and end-to-end sync verification |
| 11 | API 102 steps 2–3 (running targets, detach) | Step 0 confirms an API 102 framework | Status card row, detach verified on device |
| 12 | Stabilization | Related phases done | Measurement report, prerelease, stable release decision |

API 102 steps 4–5 (hot reload) are scheduled only when development speed makes them worth their
risk. A phase does not have to be one PR, and one PR never covers several independent phases. Test
preparation without dependencies can start early; security fixes never wait for the migration
schedule.

## 15. Completion criteria for every conversion PR

- [ ] The purpose of the change and the reason for moving to Kotlin (or adopting an API 102
  feature) are clear.
- [ ] The affected process, thread and ClassLoader boundary are identified.
- [ ] Java calls, reflection, manifest and serialization contracts are reviewed.
- [ ] Settings/data/IPC behavior is kept, or a separate migration is documented.
- [ ] Required regression tests, lint and the build pass.
- [ ] Device testing proportional to the risk was done; skipped parts are stated explicitly.
- [ ] The reason for and cost of new dependencies are recorded.
- [ ] Changes that affect performance are compared with the baseline.
- [ ] The rollback method is workable and data compatibility is known.
- [ ] User-visible changes and related documents are updated.
- [ ] Unrelated formatting, bulk renames and feature changes are kept separate.

## 16. Progress tracking and open decisions

Each phase record contains:

| Field | Expected record |
| --- | --- |
| Status | Not started / in progress / awaiting verification / done |
| Owner | Implementation and review owners |
| Source | Issue/PR and commit links |
| Evidence | CI, device test, measurement and artifact links |
| Remaining work | Open bugs, missing devices and decisions |
| Rollback | Revert/fix release and data compatibility |

Decisions to settle before implementation:

- [ ] Clean baseline commit and the committed features it includes.
- [ ] A Kotlin version verified with the current toolchain.
- [ ] Pilot component and first screen.
- [ ] Test devices and the limits of the support claim.
- [ ] Test/stable signing keys and the `versionCode` strategy.
- [ ] Measurement thresholds, release test period and owners.
- [ ] Whether the test device's framework supports API 102, and whether hot reload is worth doing.

Ordinary implementation details beyond these decisions follow the existing architecture and
contributing rules. When a new architecture choice comes up, its reasoning, alternatives and impact
go into a short decision record.

## 17. References

- [Android — Add Kotlin to an existing app](https://developer.android.com/kotlin/add-kotlin)
- [Kotlin — Java interoperability](https://kotlinlang.org/docs/java-interop.html)
- [libxposed on Maven Central](https://repo.maven.apache.org/maven2/io/github/libxposed/)
- [Morphe Patcher — how it works](https://github.com/MorpheApp/morphe-patcher)

These links are the basis of the approach. When implementation starts, current version
compatibility must be verified again; a version number from an example document must not be copied
into the project as is.
