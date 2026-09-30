# Section 17 implementation status

Updated 30 September 2026. All new boolean settings default off. This document describes
the working tree, not a published release. Existing user changes to API 102 integration are
preserved; the feature work does not require converting the existing hooks to Kotlin.

Kotlin is used for navigation, dates, media signatures, comment matching, receipt ID rules
and media actions. These components benefit from explicit null handling, collection operations
and resource cleanup. Existing Java hook and IPC entry points keep their contracts.

## Implemented paths and limits

“Implemented” means code exists; it does not imply device acceptance. Discovery counts below
were observed on Instagram 447.0.0.21.81 and do not establish compatibility with other versions.

| Item | Setting / entry point | Scope and validation still required |
| --- | --- | --- |
| #223 voice messages | `downloadVoiceMessages`, long-press a voice message | Resolves the selected message through a typed audio model path; offers an extra confirmation above the native menu. Saves AAC as M4A, including SAF. Latest discovery and playback are unverified. |
| #242 comment images/GIF | `saveCommentMedia`, modern comment long-press | Bounded traversal of the selected comment's media holders, validated CDN URLs and actual file signatures. Does not support every Pando holder or the legacy comment menu. Device check pending. |
| #241 Notes | `hideNotesTray` | Collapses the Notes resource view; restores its saved dimensions when disabled. Device check pending. |
| #239 profile friendship | `profileFollowLabel` | Reads the current binder's friendship model and adds a label beside the name only in a compatible vertical layout. Initial discovery found no target; revised mapper query remains unverified. |
| #238 navigation | `hideNavigation*`, `navigationOrder` | Filters/reorders validated native enum lists, preserves Home/Profile and unknown tabs. Order picker is in Instagram Extras. Five hook candidates installed; actual tab behavior pending. Restart after changing order. |
| #232 timestamps | `exactTimestamps` | Stories plus Android relative-time formatting. Custom Instagram formatters remain uncovered. Three platform hooks installed; date policy unit-tested. |
| #234 audio only | `mediaActions`, existing post/Reel download action | Extracts the first AAC track without re-encoding. Unsupported codecs/no-audio fail without pretending a video is audio. Custom folder uses the existing service. Synthetic device tests added, execution pending. |
| #228 image clipboard | `mediaActions` | Downloads and saves an image to MediaStore, then copies its content URI. Check paste into a receiving app and permission grant. |
| #231 media info | `mediaActions` | Dialog with downloaded file size, MIME and dimensions/duration; not an overlay. Requires fetching the selected media. |
| #221 hide-chat button | `hideChatButton` | Hides the button while preserving existing hidden-chat functionality. Check disable/re-enable and thread navigation. |
| #227 per-chat seen | `readReceiptExceptions`, long-press the ghost eye | Confirms an exact thread ID. Native sender exception is limited to the inspected Instagram version and argument shape; network route matches an exact thread segment. Unknown sender shapes remain blocked. Unit-tested ID matching; no real receipt sent during development. |
| #249 liked posts | `hideLikedPosts` | Exact `has_liked` getters on the parsed item/media holders; applies on feed parsing, not instant removal after liking. Target discovery and behavior unverified. |
| #246 expand text | `autoExpandText` | Named `ExpandingTextView` state only; no synthetic clicks. Two hooks installed. Does not cover Compose or unrelated text widgets. |
| #230 comment search | `searchComments`, modern comment menu | Case-insensitive search of the available snapshot (max 500 comments, 100 results), no server pagination. Matching policy tested. |
| #233 translation | `translateComments` | Read-only Android `ACTION_PROCESS_TEXT` chooser for installed translation/text tools; no bundled translation service. Device handler check pending. |
| #251 light/dark themes | `separateThemeProfiles`, theme editor save buttons | Each mode has a saved palette; an empty mode uses Instagram colors. Mode changes invalidate the palette/remap cache. Requires existing custom-theme toggle. Compose coverage is unchanged. |
| Story-tray hiding | `hideStoriesTray` | Independent UI-only option; does not enable the story network blocker. Check restore and activity recreation. |
| Higher-resolution images | `highResolutionImages` | Selects the largest real candidate from the native list; does not invent URLs or force global DPI. One hook installed. |
| External downloader | `mediaActions` | Sends a validated selected CDN URL to an Android view chooser. Requires an app that accepts the URL. |
| Permission onboarding | `hideOnboardingPrompts` | Suppresses only two exact optional NDX prompt IDs, not runtime permissions or account/security screens. One hook installed. |
| Notification registration | `fixNotificationRegistration` | Adds an immutable PendingIntent flag only for the known invalid-package registration sentinel on Android 12+. One hook installed; original crash not reproduced on the reference device. |

Settings are wired through persistence, backup/restore, companion toggles and Instagram Extras.
New strings are English/Turkish, with normal English fallback elsewhere. Menu badges count the
new flags. A discovery miss leaves Instagram's native behavior unchanged.

## Remaining work and concrete blockers

These items are not implemented. They are not judged impossible; they need additional target
mapping or product decisions before an implementation can be called reliable.

| Item | Missing prerequisite |
| --- | --- |
| #222 inline unsent marker | Stable message-ID-to-bound-view mapping. Matching text can mark a different message; the existing unsent log remains available. |
| #250 bulk highlights | Exact highlight-group membership and pagination; downloading every cached story would include unrelated media. |
| #236 companion deep-link downloader | Authenticated post-to-media resolver and a background work contract across the two apps. |
| #244 first message | Complete thread pagination with stable ordering; scrolling the loaded list is not the first message. |
| #243 edit history | Stable message revision schema, account ownership and bounded local storage. |
| #245 bulk unsend | Own-message selection, native request target, preview, cancellation and partial-failure handling. No destructive requests tested. |
| #225 full chat export | Complete pagination, attachment representation and account-scoped storage. Exporting the unsent log alone would not satisfy this request. |
| #226 selective story seen | Scoped pending seen requests and replay target; temporarily disabling global ghost mode can expose unrelated views. |
| #247 seekbar/speed | Verified player lifecycle, position and speed targets across supported versions. |
| #235 like delay/undo | Optimistic UI state and cancellable server request boundary. Delaying a callback alone can leave UI/server state inconsistent. |
| #237 profile Saved tab | Native tab-controller registration and its lifecycle; a shortcut is not a grid tab. |
| #219 private saved folder | Storage/privacy model, local access controls and behavior for removed or expired media. |
| #224 follower CSV | Relationship pagination and account-bound resolver. |
| #248 “Something new” | No concrete behavior specified. |
| Story looping | Verified current-item/completion transition; the static reference patches an internal branch. |
| Story ring size | Native measurement/drawing target; generic view scaling also scales avatars/layout. |
| Story filtering | Owner/type model mapping and list update semantics. |
| Story audio autoplay | Verified audio state setter; selecting an arbitrary boolean method is not a stable lookup. |
| Full-size profile viewer | Native viewer/media target and lifecycle; existing expanded-picture download is not a new viewer. |
| Like animation | Animation/drawing state target. |
| Empty bottom space | Insets and native bottom-bar measurement target that preserves system navigation. |
| Compose AMOLED | Compose Prism palette caches and update target; current View theme hooks do not cover this. |
| Curated employee/recommended flags | Version-specific verified flag set. Existing developer JSON options remain available. |

Manual DM mark-as-read already exists through `GhostDMMarkAsReadHook`; it is not a new feature
in this batch. Existing story blocking is retained alongside the new UI-only tray option.

## Regression checks

Run each case with its setting off and on. Installed hooks alone do not pass these checks.

| Group | Required cases |
| --- | --- |
| Persistence | Toggle in each app, restart both, export/import, missing old-backup keys; all new defaults off. |
| Navigation/trays | Every hide toggle, custom/reset order, unknown host tab, Home/Profile retained, rotate/restart; tray restoration without network blocking. |
| Profile/text | Own/following/non-following profiles, recycled profile views, expand/collapse, legacy and modern comment menus, blank/mixed-case search, no text-processing app. |
| Media | JPEG/PNG/GIF/WebP signatures; carousel selection; AAC and silent/unsupported video; voice vs text long-press; custom/default folder; cancellation, bad redirects, failed download and receiving clipboard app. |
| Privacy | Two different chats, switching while confirmation is open, disabled exception flag, unknown sender version, exact IDs without substring matches; receipt checks only with designated test accounts. |
| Feed/time | Liked/unliked feed items, suggested units, refresh, locale/timezone/DST, invalid/future timestamps and uncovered custom formatters. |
| Theme | Distinct light/dark palettes, live mode switch, empty per-mode palette, custom theme disabled, restart and backup restore; verify no stale remap colors. |
| Stability | Cold/warm start, missing DexKit targets, prompt suppression boundaries, notification sentinel only, original Instagram actions remain usable. |

## Validation record

- Before the latest continuation, `testDebugUnitTest lintDebug assembleDebug` succeeded. The
  test XML records 63 tests with no failures/errors. This covers the Kotlin policy code from Java,
  existing media validation and the existing hook/security tests.
- Earlier device startup installed the hook counts recorded above without an observed fatal
  startup error. Voice/liked-post paths and the revised profile mapper were added afterward.
- Latest continuation adds the independent tray toggle, theme cache fix, text-processing intent,
  menu badge updates and synthetic audio tests. Full Gradle verification is currently blocked:
  the default Gradle home is read-only; using a writable home and the installed distribution
  then fails to create the lock-contention service because socket/network-interface access is
  denied. ADB also cannot start its socket listener and has no USB bus access in this session.
- Standalone Java 21 compilation passed for the updated feature flags, tray hook, theme engine,
  feed filter, voice/profile hooks and the audio instrumentation test, using the installed Android
  SDK and cached dependencies. This is a limited compile check, not a replacement for Gradle,
  resource linking or executing the device tests. String XML and `git diff --check` also passed.
- Therefore the existing debug APK predates these last changes. Do not publish it as a build
  containing them. Run `testDebugUnitTest lintDebug assembleDebug` and
  `connectedDebugAndroidTest` in a normal Android build environment before release.
