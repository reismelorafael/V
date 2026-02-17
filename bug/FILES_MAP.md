# bug/FILES_MAP.md

Inventário do diretório `bug/` e subdiretórios operacionais.

## Arquivos canônicos de navegação

| Arquivo | Função | Status |
|---|---|---|
| `README.md` | Escopo, fluxo e integração da gestão de bugs. | Canônico |
| `FILES_MAP.md` | Inventário do diretório e subdiretórios. | Canônico |
| `SOURCE_CODE_TRACEABILITY.md` | Matriz documentação ↔ código-fonte ↔ testes. | Canônico |
| `STATUS_CORRECOES_VERIFICADAS.md` | Verificação consolidada do que já está corrigido. | Canônico |

## Base técnica de análise e correção

| Arquivo | Função | Status |
|---|---|---|
| `BUGS_ENUMERATION.md` | Catálogo estruturado de falhas. | Referência principal |
| `BUG_FIXES.md` | Registro consolidado de correções e mitigação. | Referência principal |
| `BUG_FIXES_AND_PATCHES_EXACT_CODE.md` | Patches/códigos de correção detalhados. | Ativo |
| `GUIA_TECNICO_IMPLEMENTACAO_PATCHES.md` | Procedimento técnico de implementação de patch. | Ativo |
| `DEPLOYMENT_GUIDE_COMPLETO.md` | Guia de rollout e validação pós-correção. | Ativo |
| `SUMARIO_EXECUTIVO.md` | Visão executiva de impacto e direção. | Ativo |

## Arquivos analíticos e complementares

| Arquivo | Função | Status |
|---|---|---|
| `1_RAFAELIA_BITRAF64_KERNEL.md` | Especificação de camada kernel no contexto de falhas. | Ativo |
| `2_DETERMINISTIC_COHERENCE_MATRIX.md` | Matriz de coerência determinística para estabilidade. | Ativo |
| `3_ZIP_DETERMINISTIC_CONTAINER.md` | Estratégia de empacotamento determinístico. | Ativo |
| `4_GEOMETRIC_PARITY_REDUNDANCY.md` | Integridade por paridade/redundância geométrica. | Ativo |
| `5_OMEGA_SME_COMPRESSION_CODEC.md` | Proposta de codec/compressão aplicada a artefatos. | Ativo |
| `ANALISE_CODIGO_VECTRAS_v3.6.5.md` | Análise técnica de código versão 3.6.5. | Ativo |
| `ANALISE_SO_COMPLETO_BIOS_FIRMWARE_BOOTLOADER.md` | Diagnóstico de stack SO/boot/firmware. | Ativo |
| `ANALISE_REAL_COMPILACAO_HARDWARE.md` | Estudo real de compilação e hardware. | Ativo |
| `ANALISE_REAL_HARDWARE_COMPLETA.md` | Revisão ampla de compatibilidade hardware. | Ativo |
| `ANALISE_70_METRICAS_DATACENTER.md` | Avaliação por métricas de desempenho/infra. | Ativo |
| `ANALISE_COMPLETA_8_NIVEIS_SEMANTICOS.md` | Revisão semântica multicamada. | Ativo |
| `AUTOTUNE_ADAPTATIVO_REAL.md` | Diretrizes de autotune adaptativo. | Ativo |
| `COMPARACAO_UPSTREAM_VS_OTIMIZADO.md` | Delta entre baseline upstream e otimizações. | Ativo |
| `EXPERIENCIA_USUARIO_IMPACTO_TRABALHO.md` | Impacto em UX e operação. | Ativo |
| `MATRIZ_T_ESPACO_ESTADOS_VERDADE.md` | Espaço de estados e consistência. | Ativo |
| `STATUS_IMPLEMENTACAO_COMPLETO.md` | Situação consolidada de implementação. | Ativo |
| `VERDADE_CRITICA_SEM_ABSTRACCAO.md` | Análise crítica sem abstração. | Ativo |
| `VECTRAS_ANALISE_TECNICA_DETALHADA.md` | Diagnóstico técnico detalhado. | Ativo |
| `VECTRAS_CODIGO_ALEM_DOCUMENTACAO.md` | Relação código vs documentação. | Ativo |

## Subdiretórios

| Diretório | Função | Arquivos de entrada |
|---|---|---|
| `issues/` | Issues técnicas por bug. | `issues/README.md`, `issues/FILES_MAP.md` |
| `prioridade/` | Triagem/priorização de correções. | `prioridade/readme.md`, `prioridade/FILES_MAP.md` |
| `fazer hotfix/` | Fila de correção imediata. | `fazer hotfix/readme.md`, `fazer hotfix/FILES_MAP.md` |
| `feito/` | Histórico de hotfix concluído. | `feito/README.md`, `feito/FILES_MAP.md` |
