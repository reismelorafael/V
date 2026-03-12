# Termux ARM64 Android 15 Orchestrator

Pipeline real para compilar **dentro do terminal** (Termux/ambiente similar), preparando SDK/NDK/CMake localmente e executando build `arm64-v8a`.

## Diretriz principal

Este módulo é para **build local no terminal**, sem depender de GitHub Actions para compilar.

## O que este módulo resolve

- Bootstrap automático de componentes Android necessários quando faltam no ambiente de terminal.
- Build release `arm64-v8a` com foco em flags de performance e redução de falhas por memória.
- Assinatura obrigatória por segredo/variáveis de ambiente canônicas (`VECTRAS_RELEASE_STORE_FILE`, `VECTRAS_RELEASE_STORE_PASSWORD`, `VECTRAS_RELEASE_KEY_ALIAS`, `VECTRAS_RELEASE_KEY_PASSWORD`) e injeção Gradle via `android.injected.signing.*`, sem versionar keystore no Git.
- Helpers C low-level autorais para detecção ARM64/NEON e alocação de spill em storage.
- Gate mínimo de conformidade legal/documental antes da compilação.

## Arquivos

- `build-native-helpers.sh`: compila os binários C low-level locais.
- `c/arm64_neon_probe.c`: detector de HWCAP/NEON/ASIMD/SVE em ARM64.
- `c/storage_spill_allocator.c`: cria arquivo de spill (`spill.bin`) para suporte de memória por storage.
- `bootstrap-termux-android15.sh`: instala/prepara cmdline-tools + SDK + NDK + CMake local (`.android-sdk`) e gera `local.properties`.
- `toolchain-core/`: submódulo com contratos explícitos para detectar host, resolver toolchain, ativar env e validar pré-requisitos.
- `orchestrate-build.sh`: orquestrador principal (detecção, spill, bootstrap, build e verificação de assinatura).
- `legal-compliance-check.sh`: valida pré-requisitos legais e metadados de release + contrato de assinatura por variável.
- `run-local-termux-build.sh`: entrypoint único para execução local no terminal.
- `TOOLCHAIN_CORE.md`: documentação da API de integração do submódulo `toolchain-core`.

## Execução local (recomendada)

```bash
bash tools/termux-arm64-orchestrator/run-local-termux-build.sh
```

## Execução por etapas (manual)

```bash
bash tools/termux-arm64-orchestrator/build-native-helpers.sh
bash tools/termux-arm64-orchestrator/bootstrap-termux-android15.sh
bash tools/termux-arm64-orchestrator/legal-compliance-check.sh
bash tools/termux-arm64-orchestrator/orchestrate-build.sh
```

## Variáveis úteis

- `SPILL_ALLOC_MB` (default `256`)
- `ANDROID_API_LEVEL` (default `35`)
- `ANDROID_BUILD_TOOLS` (default `35.0.0`)
- `ANDROID_NDK_VERSION` (default `27.2.12479018`)
- `ANDROID_CMAKE_VERSION` (default `3.22.1`)
- `BUILD_SPILL_DIR` (default `.build-spill`)
- `VECTRAS_RELEASE_STORE_FILE` (obrigatória para release; fallback local privado opcional em `.secrets/vectras-release.jks`, fora do Git)
- `VECTRAS_RELEASE_KEY_ALIAS` (obrigatória para release; compatível com legado `VECTRAS_KEY_ALIAS`)
- `VECTRAS_RELEASE_STORE_PASSWORD` (obrigatória para release; compatível com legado `VECTRAS_STORE_PASSWORD`)
- `VECTRAS_RELEASE_KEY_PASSWORD` (obrigatória para release; compatível com legado `VECTRAS_KEY_PASSWORD`)
- `TOOLCHAIN_PACK_DIR` (default `.toolchain-packs`)
- `ALLOW_NETWORK_TOOLCHAIN=0|1` (quando `0`, exige pack local de cmdline-tools)
- `ENABLE_FORK_SYNC=0|1` (sincroniza forks externos declarados antes do bootstrap)
- `ALLOW_NETWORK_FORKS=0|1` (quando `0`, não baixa forks; forks obrigatórios ausentes geram erro)
- `BOOTSTRAP_ANDROID=0|1`
- `CI_DRY_RUN=0|1`
