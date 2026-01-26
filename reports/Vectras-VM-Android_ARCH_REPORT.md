# Vectras-VM-Android — Relatório de Arquitetura (Formal)

**Versão:** 1.1
**Data:** 2026-01-01

## Sumário
1. [Build System](#1-build-system)
2. [Módulos Principais](#2-módulos-principais)
3. [Pontos de Entrada](#3-pontos-de-entrada)
4. [Árvore Resumida](#4-árvore-resumida)
5. [Arquivos Mais Importantes](#5-arquivos-mais-importantes)
6. [Checklist de Build/Execução](#6-checklist-de-buildexecução)
7. [Notas de Impacto Arquitetural](#7-notas-de-impacto-arquitetural)

---

## 1. Build system
- **Gradle (Android)** com Android Gradle Plugin e Kotlin plugin (`build.gradle`).
- Módulos definidos em `settings.gradle`.
- **Observação:** não foram encontrados CMake/Meson/Make/configure no repositório.

## 2. Módulos principais
### 2.1 Módulos Gradle
- `:app`
- `:terminal-emulator`
- `:terminal-view`
- `:shell-loader`
- `:shell-loader:stub`

### 2.2 Diretórios top-level
`.git`, `.github`, `.gradle`, `3dfx`, `app`, `build`, `docs`, `fastlane`, `gradle`, `resources`, `seguranda`, `shell-loader`, `terminal-emulator`, `terminal-view`, `web`.

## 3. Pontos de entrada
### 3.1 Android manifests
- `app/src/main/AndroidManifest.xml`
- `terminal-emulator/src/main/AndroidManifest.xml`
- `terminal-view/src/main/AndroidManifest.xml`
- `shell-loader/src/main/AndroidManifest.xml`
- `shell-loader/stub/src/main/AndroidManifest.xml`

### 3.2 Activities (manifest do módulo `app`)
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

### 3.3 QEMU entry points
- **N/A neste repositório.** (Não há árvore `targets/hw/*/docs`; este repo é um front-end Android para QEMU.)

## 4. Árvore resumida (até 3 níveis)
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

## 5. Arquivos mais importantes (top-10)
| # | Arquivo | Justificativa | Impacto |
|---|---------|---------------|---------|
| 1 | `README.md` | Porta de entrada do projeto | Alto |
| 2 | `build.gradle` | Configura o build global | Alto |
| 3 | `settings.gradle` | Define módulos | Alto |
| 4 | `app/build.gradle` | Configura o app e dependências | Alto |
| 5 | `app/src/main/AndroidManifest.xml` | Entry points Android | Alto |
| 6 | `app/src/main/java/com/vectras/vm/main/MainActivity.java` | UI principal | Alto |
| 7 | `app/src/main/java/com/vectras/qemu/MainVNCActivity.java` | Execução/VNC | Alto |
| 8 | `docs/ARCHITECTURE.md` | Arquitetura oficial | Médio |
| 9 | `docs/CONTRIBUTING.md` | Build e testes | Médio |
| 10 | `app/FIREBASE.md` | Configuração Firebase | Médio |

## 6. Checklist de build/execução
- `./gradlew assembleDebug`
- `./gradlew assembleRelease`
- `./gradlew test`
- `./gradlew lint`
- Para builds com Firebase, seguir `app/FIREBASE.md` conforme `docs/CONTRIBUTING.md`.

## 7. Notas de impacto arquitetural
- **Benchmark/Core** adicionam instrumentação e subsistemas de runtime que influenciam execução e métricas do VM lifecycle.
- **Creator (VMCreator)** concentra lógica de provisionamento e geração de VMs, impactando fluxo de configuração.
- **Implicação geral:** a arquitetura combina UI (provisionamento/controle) + runtime (benchmark/core) + execução (QEMU/VNC), exigindo documentação integrada.
