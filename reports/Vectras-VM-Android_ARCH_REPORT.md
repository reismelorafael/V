# Vectras-VM-Android — Architecture Report

## Build system
- **Gradle (Android)** with Android Gradle Plugin and Kotlin plugin (`build.gradle`).
- Modules defined in `settings.gradle` (no CMake/Meson/Make/configure found in repository).

## Main modules (top-level Gradle modules)
- `:app`
- `:terminal-emulator`
- `:terminal-view`
- `:shell-loader`
- `:shell-loader:stub`

## Main modules (top-level directories)
`.git`, `.github`, `.gradle`, `3dfx`, `app`, `build`, `docs`, `fastlane`, `gradle`, `resources`, `seguranda`, `shell-loader`, `terminal-emulator`, `terminal-view`, `web`.

## Entry points
### Android manifests
- `app/src/main/AndroidManifest.xml`
- `terminal-emulator/src/main/AndroidManifest.xml`
- `terminal-view/src/main/AndroidManifest.xml`
- `shell-loader/src/main/AndroidManifest.xml`
- `shell-loader/stub/src/main/AndroidManifest.xml`

### Android activities (from `app` manifest)
- `.crashtracker.LastCrashActivity`
- `.WebViewActivity`
- `.settings.X11DisplaySettingsActivity`
- `.setupwizard.SetupWizard2Activity`
- `.settings.ImportExportSettingsActivity`
- `.settings.ThemeActivity`
- `.settings.LanguageModulesActivity`
- `.settings.VNCSettingsActivity`
- `.main.MainActivity`
- `.settings.UpdaterActivity`
- `.settings.ExternalVNCSettingsActivity`
- `.RomReceiverActivity`
- `.Minitools`
- `.benchmark.BenchmarkActivity`
- `.tools.ProfessionalToolsActivity`
- `.RomInfo`
- `.ExportRomActivity`
- `.CqcmActivity`
- `.QemuParamsEditorActivity`
- `.SplashActivity`
- `.AboutActivity`
- `.ImagePrvActivity`
- `com.vectras.qemu.MainVNCActivity`
- `com.vectras.qemu.MainSettingsManager`
- `.VMCreatorActivity`
- `.DataExplorerActivity`
- `.SetArchActivity`
- `com.termux.app.TermuxActivity`
- `.x11.X11Activity`
- `.x11.LoriePreferences`

### QEMU entry points
- **N/A in this repo.** (No `targets/hw/*/docs` tree present; this is an Android application wrapper around QEMU.)

## Tree summary (max depth 3)
```
/
  3dfx/
  app/
    src/
  build/
    reports/
  docs/
    navigation/
  fastlane/
    metadata/
  gradle/
    wrapper/
  resources/
    android/
    lang/
    web/
  seguranda/
  shell-loader/
    src/
    stub/
  terminal-emulator/
    src/
  terminal-view/
    src/
  web/
    data/
```

## 10 most important files (with justification)
1. `README.md` — project overview and primary onboarding entry point.
2. `build.gradle` — top-level build configuration and plugin versions.
3. `settings.gradle` — defines project modules and build graph.
4. `app/build.gradle` — Android app build config, dependencies, build types.
5. `app/src/main/AndroidManifest.xml` — core Android app entry-point definitions.
6. `app/src/main/java/com/vectras/vm/main/MainActivity.java` — main UI navigation hub.
7. `app/src/main/java/com/vectras/qemu/MainVNCActivity.java` — primary VM display/VNC runtime activity.
8. `docs/ARCHITECTURE.md` — canonical architecture description and component map.
9. `docs/CONTRIBUTING.md` — build, test, and workflow instructions.
10. `app/FIREBASE.md` — Firebase configuration requirements for builds.

## Build/exec checklist (from repo docs)
- `./gradlew assembleDebug`
- `./gradlew assembleRelease`
- `./gradlew test`
- `./gradlew lint`
- If Firebase is required, configure per `app/FIREBASE.md` as referenced in `docs/CONTRIBUTING.md`.

## Module focus note (benchmark/core vs creator)
- **Benchmark module**: `app/src/main/java/com/vectras/vm/benchmark/` (runtime benchmarking and performance instrumentation).
- **Core module**: `app/src/main/java/com/vectras/vm/core/` and `app/src/main/java/com/vectras/vm/vectra/` (core runtime utilities and Vectra Core framework).
- **Creator flow**: `VMCreatorActivity` and related UI flow in the app manifest, handling VM creation.
- **Architectural impact:** benchmark/core introduce measurement/instrumentation and runtime subsystems that cross-cut VM lifecycle, while creator focuses on VM provisioning UX and configuration routing into runtime services.
