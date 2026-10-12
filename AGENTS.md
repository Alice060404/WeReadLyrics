# Repository Guidelines

## Project Structure & Module Organization

This repository contains `development-guide.md`, the project brief and staged acceptance plan. There is no Android source tree, test suite, or Gradle wrapper yet. Place the Android app and LSPosed module in `app/`, optional synchronization logic in `core/`, helper scripts in `scripts/`, and plans, decisions, and test evidence in `docs/`. Keep downloaded APKs, decompiled output, and device logs in an ignored directory such as `local-data/`.

## Build, Test, and Development Commands

No build or test command works in the current checkout. After a Gradle wrapper and modules are added, use `./gradlew assembleDebug` to build a debug APK and `./gradlew test` for JVM unit tests. Confirm the APK exists after a successful build. Use `git diff --check` before commits once Git is initialized. Document the actual toolchain versions and any device commands in `README.md` or `docs/SETUP.md`; do not assume a particular JDK, Android API, or LSPosed API before verifying compatibility.

## Coding Style & Naming Conventions

Use Kotlin conventions for future Android code: four-space indentation, `UpperCamelCase` types, and `lowerCamelCase` functions and properties. Keep Hook adapters, playback synchronization, IPC, overlay UI, and diagnostics in separate components. Favor a pure Kotlin `core/` so playback rules can be tested without Android. No formatter or linter is configured yet; add its command here when one is adopted.

## Testing Guidelines

No testing framework or coverage threshold is established yet. Add JVM unit tests for synchronization rules, especially prefetch, out-of-order events, pause, seek, chapter changes, and stop. Name tests for the behavior they verify. Record device model, app version, steps, and observed result for manual Hook and overlay checks. A passing build or unit test does not establish successful injection or accurate live synchronization.

## Commit & Pull Request Guidelines

There is no Git history to infer an existing commit style. Follow the brief's suggested Conventional Commits, such as `docs: record LSPosed API decision` or `test: cover chapter reset`. Keep each commit focused and state failures honestly. Pull requests should summarize the change, link the relevant plan or issue, list build and test results, and include screenshots for overlay changes.

## Security & Device Work

Never commit signing keys, tokens, raw APKs, full book text, decompiled trees, or sensitive device logs. Limit Hooks to the verified WeRead process and keep diagnostics redacted. Obtain user approval before installing APKs, enabling modules, restarting apps, or changing device state.
