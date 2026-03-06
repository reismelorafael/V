# bug/FILES_MAP.md

Inventário reconciliado de `bug/` com classificação por trilha documental.

## 1) Arquivos canônicos

| Arquivo | Papel |
|---|---|
| `README.md` | Documento de entrada do diretório `bug/`. |
| `FILES_MAP.md` | Inventário mestre de `bug/`. |
| `SOURCE_CODE_TRACEABILITY.md` | Matriz doc ↔ código ↔ testes. |
| `STATUS_CORRECOES_VERIFICADAS.md` | Estado consolidado das correções validadas. |

## 2) Arquivos operacionais (ativos)

| Arquivo | Papel |
|---|---|
| `BUGS_ENUMERATION.md` | Catálogo estruturado de falhas. |
| `BUG_FIXES.md` | Registro consolidado de correções. |
| `BUG_FIXES_AND_PATCHES_EXACT_CODE.md` | Patches e snippets aplicáveis. |
| `GUIA_TECNICO_IMPLEMENTACAO_PATCHES.md` | Procedimento técnico de implementação. |
| `DEPLOYMENT_GUIDE_COMPLETO.md` | Rollout/validação pós-correção. |
| `SUMARIO_EXECUTIVO.md` | Leitura executiva consolidada. |
| `STATUS_IMPLEMENTACAO_COMPLETO.md` | Status geral de implementação. |
| `RAFGITTOOLS_AUDIT_REPORT.md` | Relatório operacional (RAFGITTOOLS). |
| `RAFGITTOOLS_BUG_HUNTER_v6.md` | Relatório operacional (RAFGITTOOLS). |
| `VECTRAS_VM_BUG_HUNTER.md` | Relatório operacional (VECTRAS). |
| `VECTRAS_V7_BUG_HUNTER_55.md` | Relatório operacional (VECTRAS). |
| `Vectras_BugReport.md` | Report consolidado de bugs. |

## 3) Histórico técnico (texto consolidado)

| Arquivo | Papel |
|---|---|
| `1_RAFAELIA_BITRAF64_KERNEL.md` | Registro técnico temático. |
| `2_DETERMINISTIC_COHERENCE_MATRIX.md` | Registro técnico temático. |
| `3_ZIP_DETERMINISTIC_CONTAINER.md` | Registro técnico temático. |
| `4_GEOMETRIC_PARITY_REDUNDANCY.md` | Registro técnico temático. |
| `5_OMEGA_SME_COMPRESSION_CODEC.md` | Registro técnico temático. |
| `ANALISE_CODIGO_VECTRAS_v3.6.5.md` | Análise técnica histórica. |
| `ANALISE_SO_COMPLETO_BIOS_FIRMWARE_BOOTLOADER.md` | Análise técnica histórica. |
| `ANALISE_REAL_COMPILACAO_HARDWARE.md` | Análise técnica histórica. |
| `ANALISE_REAL_HARDWARE_COMPLETA.md` | Análise técnica histórica. |
| `ANALISE_70_METRICAS_DATACENTER.md` | Análise técnica histórica. |
| `ANALISE_COMPLETA_8_NIVEIS_SEMANTICOS.md` | Análise técnica histórica. |
| `AUTOTUNE_ADAPTATIVO_REAL.md` | Diretriz técnica histórica. |
| `COMPARACAO_UPSTREAM_VS_OTIMIZADO.md` | Comparativo técnico histórico. |
| `EXPERIENCIA_USUARIO_IMPACTO_TRABALHO.md` | Impacto operacional/UX histórico. |
| `MATRIZ_T_ESPACO_ESTADOS_VERDADE.md` | Matriz de consistência histórica. |
| `VERDADE_CRITICA_SEM_ABSTRACCAO.md` | Registro analítico histórico. |
| `VECTRAS_ANALISE_TECNICA_DETALHADA.md` | Análise detalhada histórica. |
| `VECTRAS_CODIGO_ALEM_DOCUMENTACAO.md` | Relação código/documentação histórica. |

## 4) Evidência bruta (`.txt`)

| Arquivo | Origem/tema |
|---|---|
| `SIGMA_SUMMARY.txt` | Sumário bruto de observações. |
| `VECTRAS_VM_ADDITIONAL_56_BUGS.txt` | Inventário de bugs adicionais. |
| `VECTRAS_VM_ANALYSIS_SUMMARY.txt` | Sumário analítico bruto. |
| `VECTRAS_VM_ANDROID_BUG_REPORT.txt` | Relatório bruto Android. |
| `VECTRAS_VM_ANDROID_CORRECTIONS.txt` | Correções brutas Android. |
| `VECTRAS_VM_COGNITIVE_STRATEGIC_TACTICAL_SOLUTIONS.txt` | Estratégia/tática em texto bruto. |
| `VECTRAS_VM_COMPLETE_BENCHTABLE_TESTS.txt` | Evidências de bench em texto bruto. |
| `VECTRAS_VM_DETAILED_INVENTORY_COMPLETE.txt` | Inventário detalhado bruto. |
| `VECTRAS_VM_EXPLICACAO_EXECUTIVA_CONSOLIDADA.txt` | Explicação executiva bruta. |
| `VECTRAS_VM_LISTA_76_BUGS.txt` | Lista bruta de bugs. |
| `VECTRAS_VM_SYSTEMIC_FAILURE_ANALYSIS.txt` | Análise sistêmica em texto bruto. |

## 5) Binário anexado (realocado)

| Arquivo original em `bug/` | Novo caminho | Classe |
|---|---|---|
| `Auditoria_RAFAELIA_VectrasVM.docx` | `archive/evidencias/Auditoria_RAFAELIA_VectrasVM.docx` | Binário anexado + histórico |
| `Bugs_RAFAELIA_VectrasVM_Detalhado.docx` | `archive/evidencias/Bugs_RAFAELIA_VectrasVM_Detalhado.docx` | Binário anexado + histórico |

## 6) Seção específica de `core/`

`bug/core/` contém fontes C/ASM/Java de suporte low-level e está classificado em `bug/core/FILES_MAP.md`.

Resumo de status:
- **Ativo:** contratos (`*.h`), kernel/ops (`rmr_*.c`) e interfaces JNI Java.
- **Espelho:** bridges de compatibilidade (`bitraf.c`, `zipraf_core_bridge.c`, `NUCLEUS_README.md`).
- **Legado:** stubs/rotas não prioritárias (`rmr_casm_riscv64.S`).
- **Evidência:** material auxiliar/placeholder (`zipraf_jni.c`, `vectra_cpu_safe.c`, `1.md`).

## 7) Subdiretórios e pares canônicos

| Diretório | Par canônico | Observação |
|---|---|---|
| `core/` | `core/README.md` + `core/FILES_MAP.md` | Núcleo low-level C/ASM/Java. |
| `issues/` | `issues/README.md` + `issues/FILES_MAP.md` | Issues técnicas atômicas. |
| `prioridade/` | `prioridade/README.md` + `prioridade/FILES_MAP.md` | Priorização (mantém `readme.md` legado). |
| `fazer hotfix/` | `fazer hotfix/README.md` + `fazer hotfix/FILES_MAP.md` | Execução imediata (mantém `readme.md` legado). |
| `feito/` | `feito/README.md` + `feito/FILES_MAP.md` | Histórico de hotfix concluído. |
| `archive/` | `archive/README.md` + `archive/FILES_MAP.md` | Trilha histórica. |
| `archive/evidencias/` | `archive/evidencias/README.md` + `archive/evidencias/FILES_MAP.md` | Binários/evidências brutas. |
