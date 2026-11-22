# Repository Guidelines

## Project Structure & Module Organization

Flue is a nine-module Android project for watches, with two application modules and seven shared libraries:

- `:app`: HOME launcher, settings, services, and built-in watch-face configuration. Kotlin sources live in `app/src/main/kotlin/com/example/wlauncher` (package `com.flue.launcher`); resources are under `app/src/main/res`.
- `:studio`: Flue Studio creation application hosting the editor and Agent settings; it does not host the store.
- `:ui-kit`: shared themes, controls, motion, round-screen geometry, and overlays.
- `:watchface-core`: shared watch-face models, expression classification, canvas geometry, and pure algorithms.
- `:watchface-runtime`: WatchMaker/JB parsing, Lua execution, rendering, and archive installation.
- `:watchface-editor`: watch-face creation/editor, accessed through `WatchEditorHost`; publishing uses the store client without depending on store UI.
- `:watchface-store-client`: shared store API, credentials, metadata, catalog, and install transactions; no editor or store UI dependency.
- `:watchface-store`: store browsing, installation, and upload UI hosted by HOME through `WatchStoreHost`; no editor/Agent/FFmpeg dependency.
- `:agent`: Agent conversations, providers, workspace tools, and local runtime integration.

JVM tests live in each module's `src/test` tree; launcher tests use `app/src/test/kotlin` and `app/src/test/java`, with fixtures in `app/src/test/resources`. The browser watch-face helper is in `tools/watchface-agent-web`. Design notes and validation records are in `docs/`.

Launcher entry points are `LauncherActivity`, `SettingsActivity`, and `LauncherViewModel`. Keep launcher business logic out of shared modules and use host interfaces rather than depending on launcher activities or its ViewModel.

## Build, Test, and Development Commands

Use PowerShell or Android Studio with JDK 21:

```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain
.\gradlew.bat :ui-kit:testDebugUnitTest :watchface-core:testDebugUnitTest :watchface-runtime:testDebugUnitTest :watchface-editor:testDebugUnitTest :watchface-store-client:testDebugUnitTest :watchface-store:testDebugUnitTest :agent:testDebugUnitTest :studio:testDebugUnitTest --no-daemon --console=plain
.\gradlew.bat :app:assembleDebug :studio:assembleDebug --no-daemon --console=plain
.\gradlew.bat :app:assembleRelease :studio:assembleRelease --no-daemon --console=plain
.\gradlew.bat :app:installDebug :studio:installDebug
```

Use `./gradlew` instead of `.\gradlew.bat` on Linux/macOS. The test commands cover JVM/Robolectric tasks across all nine modules (Studio's task may have no tests). CI runs tests only on push/PR, including version-tag pushes; APK assembly/upload is manual (`workflow_dispatch`), and signed release publishing additionally requires selecting a `v*` tag. Assemble tasks produce the two APKs; module-qualified `installDebug` tasks deploy them to a connected device. For the web helper, run `python -W error::ResourceWarning -m unittest test_backend.py` and `npm test` from `tools/watchface-agent-web`.

## Coding Style & Naming Conventions

Follow Kotlin official style and the repository's four-space indentation. Use `PascalCase` for classes and composables, `camelCase` for functions and properties, and `UPPER_SNAKE_CASE` for constants. Keep package placement aligned with the existing feature directory and prefer small, focused Compose components. Store user-facing text in Android resources, including locale variants, rather than hard-coding strings.

## Testing Guidelines

Name JVM tests with the production subject plus `Test` (for example, `WatchStoreSearchFieldsTest`). Add focused tests beside the feature and reuse fixtures in `app/src/test/resources`. Run the affected test class during iteration, then run `:app:testDebugUnitTest` before opening a PR. Unit tests and Release builds do not replace device checks for notifications, watch faces, or animation behavior.

## Commit & Pull Request Guidelines

Use a short Simplified-Chinese commit subject that states the completed behavior, such as `修复表盘列表更新分页越界`. Keep one coherent topic per branch, normally `yzp/<topic>`, and stage reviewed paths explicitly. PRs should explain the user-visible change, list validation commands and device checks, link related issues, and include screenshots or recordings for UI changes. Before requesting review, run `git diff --check` and confirm CI's release build can run without committed signing secrets.

## Security & Configuration Tips

Do not commit passwords, keystores, generated APKs, logs, or local configuration. Release signing values may be supplied through `keystore.properties`, environment variables, or Gradle properties; keep those inputs local and verify the resulting APK separately.
