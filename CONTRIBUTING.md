# Contributing to InstaEclipse

Thanks for helping out! Bug reports, feature ideas, translations and pull requests are all
welcome.

## Reporting bugs and ideas

- Use the [issue templates](https://github.com/ReSo7200/InstaEclipse/issues/new/choose).
- Include the InstaEclipse version, Instagram version, Android version and whether you use
  LSPosed or LSPatch.
- Attach logs from the **Logs** tab. Check them first and remove anything private
  (usernames, thread names).
- Security problems: **don't** open an issue — see [SECURITY.md](SECURITY.md).

## Development setup

Requirements: JDK 21+, Android SDK with platform 37, and a device/emulator with LSPosed
(or LSPatch) for real testing.

```bash
git clone https://github.com/ReSo7200/InstaEclipse.git
cd InstaEclipse
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The APK ends up in `app/build/outputs/apk/debug/`. After installing, enable the module for
Instagram in LSPosed and force-stop Instagram.

Read [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) first — most bugs come from mixing up
code that runs inside Instagram with code that runs in the companion app.

## Pull requests

1. Branch from `main`; keep a PR to one topic.
2. Match the surrounding style (Java 21, 4-space indent, existing naming).
3. Hooks must fail safe: wrap them in `try/catch (Throwable)`, log with `ModuleLog`, and never
   crash Instagram.
4. Prefer DexKit lookups (cached through `DexKitCache`) over hard-coded obfuscated names.
5. Put Android-free logic in plain classes and add JVM tests in `app/src/test`.
6. Follow the IPC rules in [SECURITY.md](SECURITY.md#design-notes-for-contributors).
7. User-visible strings go in `res/values/strings.xml`; translations in `values-xx/`.
8. Before pushing: `./gradlew testDebugUnitTest lintDebug assembleDebug` must pass (CI runs
   the same).
9. Describe what you tested on a device and which Instagram version.

Commit messages follow a light [Conventional Commits](https://www.conventionalcommits.org/)
style, as in the history: `fix(distraction): …`, `feat(media): …`, `docs: …`.

## Translations

Copy `app/src/main/res/values/strings.xml` into `values-<lang>/strings.xml`, translate the
values (not the keys), and open a PR. Partial translations are fine; missing strings fall
back to English.

## Legal

By contributing you agree that your work is licensed under the project's
[LICENSE](LICENSE). Please read the [DISCLAIMER](DISCLAIMER.md).


### New Kotlin feature code

Use Kotlin when it makes an independent component clearer: pure policies, bounded collections,
resource-managed media operations and null handling. Keep working Java hooks and their public
contracts unless a conversion solves a concrete maintenance problem. New source lives in
`app/src/main/kotlin`; AGP provides built-in Kotlin support and the runtime is explicitly packaged.
Java and Kotlin target JVM 21. Use `@JvmStatic` for policy entry points called from Java; keep
coroutines and Kotlin function types out of hook contracts. Tests should exercise behavior and
Java interoperability, not mirror implementation.

Section 17 additions currently provide English and Turkish strings; other locales fall back to
English. Do not copy English strings into every locale to make translation coverage appear complete.
Use the [feature matrix](docs/FEATURE_BACKLOG_STATUS.md) to distinguish discovery, actual device
behavior, partial coverage and deferred work. A hook count alone is not a passed feature test.
