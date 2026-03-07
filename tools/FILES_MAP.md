# tools/FILES_MAP.md

Mapa arquivo-a-arquivo em três linhas por item: papel, ligação e comando de inspeção.

## `tools/README.md`
- **Papel**: documentação local do diretório.
- **Liga com**: ver [`tools/README.md`](README.md) e [`docs/THREE_LAYER_ANALYSIS.md`](../docs/THREE_LAYER_ANALYSIS.md) para contexto de camadas.
- **Inspeção**: `file "tools/README.md"` e, quando texto, `sed -n "1,80p" "tools/README.md"`.

## `tools/apk/README.md`
- **Papel**: documentação operacional dos scripts de build e validação de APK assinado.
- **Liga com**: ver [`tools/apk/build_release_signed_local.sh`](apk/build_release_signed_local.sh) para execução ponta-a-ponta.
- **Inspeção**: `file "tools/apk/README.md"` e, quando texto, `sed -n "1,140p" "tools/apk/README.md"`.

## `tools/apk/rmr_termux_release_orchestrator.sh`
- **Papel**: código-fonte ou automação executável.
- **Liga com**: ver [`tools/README.md`](README.md) e [`docs/THREE_LAYER_ANALYSIS.md`](../docs/THREE_LAYER_ANALYSIS.md) para contexto de camadas.
- **Inspeção**: `file "tools/apk/rmr_termux_release_orchestrator.sh"` e, quando texto, `sed -n "1,80p" "tools/apk/rmr_termux_release_orchestrator.sh"`.

## `tools/apk/build_release_signed_local.sh`
- **Papel**: gera release assinado, coleta logs de build e valida assinatura/checksum do artefato final.
- **Liga com**: usa keystore via `VECTRAS_RELEASE_STORE_FILE`/`android.injected.signing.store.file`, Gradle wrapper, `local.properties` e `apksigner` do Android SDK.
- **Inspeção**: `file "tools/apk/build_release_signed_local.sh"` e, quando texto, `sed -n "1,240p" "tools/apk/build_release_signed_local.sh"`.

## `tools/baremetal/dir_integrity_matrix.sh`
- **Papel**: código-fonte ou automação executável.
- **Liga com**: ver [`tools/README.md`](README.md) e [`docs/THREE_LAYER_ANALYSIS.md`](../docs/THREE_LAYER_ANALYSIS.md) para contexto de camadas.
- **Inspeção**: `file "tools/baremetal/dir_integrity_matrix.sh"` e, quando texto, `sed -n "1,80p" "tools/baremetal/dir_integrity_matrix.sh"`.

## `tools/baremetal/hw_caps_detect.sh`
- **Papel**: código-fonte ou automação executável.
- **Liga com**: ver [`tools/README.md`](README.md) e [`docs/THREE_LAYER_ANALYSIS.md`](../docs/THREE_LAYER_ANALYSIS.md) para contexto de camadas.
- **Inspeção**: `file "tools/baremetal/hw_caps_detect.sh"` e, quando texto, `sed -n "1,80p" "tools/baremetal/hw_caps_detect.sh"`.

## `tools/verify_repo_file_dependencies.py`
- **Papel**: código-fonte ou automação executável.
- **Liga com**: ver [`tools/README.md`](README.md) e [`docs/THREE_LAYER_ANALYSIS.md`](../docs/THREE_LAYER_ANALYSIS.md) para contexto de camadas.
- **Inspeção**: `file "tools/verify_repo_file_dependencies.py"` e, quando texto, `sed -n "1,80p" "tools/verify_repo_file_dependencies.py"`.


## `tools/verify_bootstrap_assets.py`
- **Papel**: valida presença, tamanho e estrutura TAR dos bootstraps versionados em `app/src/main/assets/bootstrap/`.
- **Liga com**: consumido pela task Gradle `verifyBootstrapAssets` em [`build.gradle`](../build.gradle).
- **Inspeção**: `python3 tools/verify_bootstrap_assets.py`.



## `tools/termux-arm64-orchestrator/README.md`
- **Papel**: documentação local do orquestrador de build arm64/Android 15.
- **Liga com**: ver [`tools/termux-arm64-orchestrator/orchestrate-build.sh`](termux-arm64-orchestrator/orchestrate-build.sh) e [`tools/termux-arm64-orchestrator/run-local-termux-build.sh`](termux-arm64-orchestrator/run-local-termux-build.sh).
- **Inspeção**: `file "tools/termux-arm64-orchestrator/README.md"` e, quando texto, `sed -n "1,120p" "tools/termux-arm64-orchestrator/README.md"`.

## `tools/termux-arm64-orchestrator/legal-compliance-check.sh`
- **Papel**: gate documental/legal + validação do contrato de assinatura por variável antes da compilação de release.
- **Liga com**: ver [`tools/termux-arm64-orchestrator/orchestrate-build.sh`](termux-arm64-orchestrator/orchestrate-build.sh) e [`THIRD_PARTY_NOTICES.md`](../THIRD_PARTY_NOTICES.md).
- **Inspeção**: `file "tools/termux-arm64-orchestrator/legal-compliance-check.sh"` e, quando texto, `sed -n "1,140p" "tools/termux-arm64-orchestrator/legal-compliance-check.sh"`.


## `tools/termux-arm64-orchestrator/bootstrap-termux-android15.sh`
- **Papel**: bootstrap de SDK/NDK/CMake para compilação Android 15 em terminal/Termux.
- **Liga com**: ver [`tools/termux-arm64-orchestrator/orchestrate-build.sh`](termux-arm64-orchestrator/orchestrate-build.sh) e `local.properties` gerado automaticamente.
- **Inspeção**: `file "tools/termux-arm64-orchestrator/bootstrap-termux-android15.sh"` e, quando texto, `sed -n "1,260p" "tools/termux-arm64-orchestrator/bootstrap-termux-android15.sh"`.

## `tools/termux-arm64-orchestrator/orchestrate-build.sh`
- **Papel**: orquestra build arm64-v8a com tuning de memória/spill, assinatura via segredo/variável e verificação pós-build.
- **Liga com**: ver [`tools/termux-arm64-orchestrator/legal-compliance-check.sh`](termux-arm64-orchestrator/legal-compliance-check.sh) e [`tools/termux-arm64-orchestrator/run-local-termux-build.sh`](termux-arm64-orchestrator/run-local-termux-build.sh).
- **Inspeção**: `file "tools/termux-arm64-orchestrator/orchestrate-build.sh"` e, quando texto, `sed -n "1,220p" "tools/termux-arm64-orchestrator/orchestrate-build.sh"`.


## `tools/termux-arm64-orchestrator/build-native-helpers.sh`
- **Papel**: compila utilitários C low-level locais para probe ARM64/NEON e spill allocator.
- **Liga com**: ver [`tools/termux-arm64-orchestrator/c/arm64_neon_probe.c`](termux-arm64-orchestrator/c/arm64_neon_probe.c) e [`tools/termux-arm64-orchestrator/c/storage_spill_allocator.c`](termux-arm64-orchestrator/c/storage_spill_allocator.c).
- **Inspeção**: `file "tools/termux-arm64-orchestrator/build-native-helpers.sh"` e, quando texto, `sed -n "1,160p" "tools/termux-arm64-orchestrator/build-native-helpers.sh"`.

## `tools/termux-arm64-orchestrator/c/arm64_neon_probe.c`
- **Papel**: código C autoral para detectar capacidades ARM64 (NEON/ASIMD/SVE) via HWCAP.
- **Liga com**: ver [`tools/termux-arm64-orchestrator/orchestrate-build.sh`](termux-arm64-orchestrator/orchestrate-build.sh) e artefato `bin/arm64_neon_probe`.
- **Inspeção**: `file "tools/termux-arm64-orchestrator/c/arm64_neon_probe.c"` e, quando texto, `sed -n "1,220p" "tools/termux-arm64-orchestrator/c/arm64_neon_probe.c"`.

## `tools/termux-arm64-orchestrator/c/storage_spill_allocator.c`
- **Papel**: código C autoral para alocar arquivo de spill em storage e reduzir risco de kill por memória.
- **Liga com**: ver [`tools/termux-arm64-orchestrator/orchestrate-build.sh`](termux-arm64-orchestrator/orchestrate-build.sh) e artefato `bin/storage_spill_allocator`.
- **Inspeção**: `file "tools/termux-arm64-orchestrator/c/storage_spill_allocator.c"` e, quando texto, `sed -n "1,260p" "tools/termux-arm64-orchestrator/c/storage_spill_allocator.c"`.



## `tools/export_source_tarball.sh`
- **Papel**: exporta código-fonte do repositório e também downloads/código instalado via SDK/NDK/CMake (quando presentes), gerando pacote `.tar.gz` para redação/edição externa.
- **Liga com**: integra `git ls-files`, `.android-sdk` (cmdline-tools/platform-tools/build-tools/ndk/cmake/platforms) e cache `~/.android/cache`, organizando saída em `archive/source-export/` por timestamp.
- **Inspeção**: `bash tools/export_source_tarball.sh` e `find archive/source-export -maxdepth 3 -type f | sort`.


## `tools/mirror_alpine_apk_failures.sh`
- **Papel**: baixa para o repositório os pacotes `.apk` do Alpine que falharam no setup (com retry), incluindo índices e manifesto de resolução/falha.
- **Liga com**: usado por [`tools/prefetch_bootstrap_downloads.sh`](prefetch_bootstrap_downloads.sh) para espelhar downloads não-Android também.
- **Inspeção**: `bash tools/mirror_alpine_apk_failures.sh` e `find archive/download-mirror/alpine-apk-failures -maxdepth 3 -type f | sort`.

## `tools/prefetch_bootstrap_downloads.sh`
- **Papel**: executa bootstrap oficial para baixar/instalar componentes Android, espelha falhas de pacotes Alpine e depois chama export para gerar `.tar.gz` com conteúdo pós-download.
- **Liga com**: usa [`tools/termux-arm64-orchestrator/bootstrap-termux-android15.sh`](termux-arm64-orchestrator/bootstrap-termux-android15.sh) e [`tools/export_source_tarball.sh`](export_source_tarball.sh).
- **Inspeção**: `bash tools/prefetch_bootstrap_downloads.sh` e `find archive/download-mirror -maxdepth 3 -type f | sort`.

## `tools/audit_non_md_inventory.py`
- **Papel**: script de auditoria forense para inventariar todos os arquivos não-Markdown com hash SHA-256.
- **Liga com**: gera [`reports/NON_MD_AUDIT_REPORT.md`](../reports/NON_MD_AUDIT_REPORT.md) e [`reports/non_md_inventory.tsv`](../reports/non_md_inventory.tsv).
- **Inspeção**: `python3 tools/audit_non_md_inventory.py` e `sed -n "1,80p" "reports/NON_MD_AUDIT_REPORT.md"`.

## `tools/baremetal/rafcode_phi/README.md`
- **Papel**: guia local da base RAFCODE❤️PHI C→ASM com emissão de opcodes em hexadecimal.
- **Liga com**: ver [`docs/RAFCODE_PHI_COMPILER_HEADER.md`](../docs/RAFCODE_PHI_COMPILER_HEADER.md) para contrato técnico e [`tools/baremetal/`](baremetal/) para contexto low-level.
- **Inspeção**: `file "tools/baremetal/rafcode_phi/README.md"` e, quando texto, `sed -n "1,200p" "tools/baremetal/rafcode_phi/README.md"`.

## `tools/baremetal/rafcode_phi/include/rafcode_phi_abi.h`
- **Papel**: contrato ABI autoral C↔ASM (tipos fixos, opcodes hex e assinaturas de emissão).
- **Liga com**: ver [`tools/baremetal/rafcode_phi/c/rafcode_phi_front_shell.c`](baremetal/rafcode_phi/c/rafcode_phi_front_shell.c) e [`tools/baremetal/rafcode_phi/asm/rafcode_phi_emit_word.S`](baremetal/rafcode_phi/asm/rafcode_phi_emit_word.S).
- **Inspeção**: `file "tools/baremetal/rafcode_phi/include/rafcode_phi_abi.h"` e, quando texto, `sed -n "1,220p" "tools/baremetal/rafcode_phi/include/rafcode_phi_abi.h"`.

## `tools/baremetal/rafcode_phi/c/rafcode_phi_front_shell.c`
- **Papel**: casca C determinística para converter tokens em opcodes hex e consolidar CRC32C do bloco emitido.
- **Liga com**: usa ABI em [`tools/baremetal/rafcode_phi/include/rafcode_phi_abi.h`](baremetal/rafcode_phi/include/rafcode_phi_abi.h) e hook ASM `rafphi_emit_word_asm`.
- **Inspeção**: `file "tools/baremetal/rafcode_phi/c/rafcode_phi_front_shell.c"` e, quando texto, `sed -n "1,260p" "tools/baremetal/rafcode_phi/c/rafcode_phi_front_shell.c"`.

## `tools/baremetal/rafcode_phi/asm/rafcode_phi_emit_word.S`
- **Papel**: rotina ASM bare-metal para serializar palavra `opcode_hex` no buffer de saída com retorno de sucesso/falha.
- **Liga com**: implementa contrato de [`tools/baremetal/rafcode_phi/include/rafcode_phi_abi.h`](baremetal/rafcode_phi/include/rafcode_phi_abi.h) para caminhos `__aarch64__` e `__x86_64__`.
- **Inspeção**: `file "tools/baremetal/rafcode_phi/asm/rafcode_phi_emit_word.S"` e, quando texto, `sed -n "1,220p" "tools/baremetal/rafcode_phi/asm/rafcode_phi_emit_word.S"`.
