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
- **Liga com**: usa `vectras.jks`, Gradle wrapper, `local.properties` e `apksigner` disponível no Android SDK.
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



## `tools/termux-arm64-orchestrator/README.md`
- **Papel**: documentação local do orquestrador de build arm64/Android 15.
- **Liga com**: ver [`tools/termux-arm64-orchestrator/orchestrate-build.sh`](termux-arm64-orchestrator/orchestrate-build.sh) e [`tools/termux-arm64-orchestrator/run-local-termux-build.sh`](termux-arm64-orchestrator/run-local-termux-build.sh).
- **Inspeção**: `file "tools/termux-arm64-orchestrator/README.md"` e, quando texto, `sed -n "1,120p" "tools/termux-arm64-orchestrator/README.md"`.

## `tools/termux-arm64-orchestrator/legal-compliance-check.sh`
- **Papel**: gate documental/legal + validação de assinatura com `vectras.jks` antes da compilação de release.
- **Liga com**: ver [`tools/termux-arm64-orchestrator/orchestrate-build.sh`](termux-arm64-orchestrator/orchestrate-build.sh) e [`THIRD_PARTY_NOTICES.md`](../THIRD_PARTY_NOTICES.md).
- **Inspeção**: `file "tools/termux-arm64-orchestrator/legal-compliance-check.sh"` e, quando texto, `sed -n "1,140p" "tools/termux-arm64-orchestrator/legal-compliance-check.sh"`.


## `tools/termux-arm64-orchestrator/bootstrap-termux-android15.sh`
- **Papel**: bootstrap de SDK/NDK/CMake para compilação Android 15 em terminal/Termux.
- **Liga com**: ver [`tools/termux-arm64-orchestrator/orchestrate-build.sh`](termux-arm64-orchestrator/orchestrate-build.sh) e `local.properties` gerado automaticamente.
- **Inspeção**: `file "tools/termux-arm64-orchestrator/bootstrap-termux-android15.sh"` e, quando texto, `sed -n "1,260p" "tools/termux-arm64-orchestrator/bootstrap-termux-android15.sh"`.

## `tools/termux-arm64-orchestrator/orchestrate-build.sh`
- **Papel**: orquestra build arm64-v8a com tuning de memória/spill, assinatura via `vectras.jks` e verificação pós-build.
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


## `tools/audit_non_md_inventory.py`
- **Papel**: script de auditoria forense para inventariar todos os arquivos não-Markdown com hash SHA-256.
- **Liga com**: gera [`reports/NON_MD_AUDIT_REPORT.md`](../reports/NON_MD_AUDIT_REPORT.md) e [`reports/non_md_inventory.tsv`](../reports/non_md_inventory.tsv).
- **Inspeção**: `python3 tools/audit_non_md_inventory.py` e `sed -n "1,80p" "reports/NON_MD_AUDIT_REPORT.md"`.
