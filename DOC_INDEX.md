# Índice de Documentação do Repositório

## Entradas principais
- [`README.md`](README.md): visão institucional + navegação por camadas.
- [`docs/README.md`](docs/README.md): curadoria técnica.
- [`docs/THREE_LAYER_ANALYSIS.md`](docs/THREE_LAYER_ANALYSIS.md): análise estrutural em 3 camadas.
- [`docs/ROOT_FILE_CHAIN.md`](docs/ROOT_FILE_CHAIN.md): reflexão arquivo-a-arquivo da raiz.

## Documentos de raiz
| Documento | Finalidade | Público-alvo | Classificação | Localização atual | Inbound link |
|---|---|---|---|---|---|
| `README.md` | Porta de entrada do repositório e mapa de navegação geral. | Novos contribuidores, engenharia, auditoria técnica. | **Ativo** | Raiz | [`DOC_INDEX.md`](DOC_INDEX.md) |
| `DOC_INDEX.md` | Índice canônico da documentação e governança documental. | Engenharia, technical writers, manutenção de docs. | **Ativo** | Raiz | [`README.md`](README.md) |
| `PROJECT_STATE.md` | Estado corrente do ciclo de vida (stable/experimental/refactoring). | Liderança técnica, release managers, PM. | **Ativo** | Raiz | [`README.md`](README.md#governança-e-estado---navegação-rápida) |
| `CHANGELOG.md` | Histórico contínuo de mudanças por versão. | Desenvolvimento, QA, release managers. | **Ativo** | Raiz | [`README.md`](README.md#governança-e-estado---navegação-rápida) |
| `RELEASE_NOTES.md` | Notas executivas da release mais recente (registro temporal). | Produto, operação, stakeholders externos. | **Histórico** | Raiz | [`README.md`](README.md#governança-e-estado---navegação-rápida) |
| `THIRD_PARTY_NOTICES.md` | Registro legal/licenças de terceiros. | Compliance, jurídico, segurança. | **Ativo** | Raiz | [`README.md`](README.md#governança-e-estado---navegação-rápida) |
| `VECTRA_CORE.md` | Referência conceitual do runtime Vectra Core MVP. | Engenharia de runtime e arquitetura. | **Ativo** | Raiz | [`README.md`](README.md#governança-e-estado---navegação-rápida) |
| `VECTRAS_MEGAPROMPT_DOCS.md` | Guia estratégico de documentação técnica ampla do projeto. | Documentação técnica, enablement e coordenação. | **Ativo** | Raiz | [`README.md`](README.md#governança-e-estado---navegação-rápida) |
| `archive/root-history/1.md` | Registro pontual de ajuste de compatibilidade/chaves. | Auditoria histórica. | **Arquivado** | `archive/root-history/` | [`README.md`](README.md#histórico-arquivado-raiz) |
| `archive/root-history/ADVANCED_OPTIMIZATIONS.md` | Catálogo histórico de módulos avançados de otimização. | Engenharia/performance (consulta histórica). | **Arquivado** | `archive/root-history/` | [`README.md`](README.md#histórico-arquivado-raiz) |
| `archive/root-history/BENCHMARK_REFACTORING_SUMMARY.md` | Sumário histórico da etapa de refatoração de benchmark. | Performance, engenharia de validação. | **Arquivado** | `archive/root-history/` | [`README.md`](README.md#histórico-arquivado-raiz) |
| `archive/root-history/IMPLEMENTATION_COMPLETE.md` | Registro histórico de implementação consolidada. | Auditoria técnica e rastreabilidade histórica. | **Arquivado** | `archive/root-history/` | [`README.md`](README.md#histórico-arquivado-raiz) |
| `archive/root-history/IMPLEMENTATION_SUMMARY.md` | Resumo histórico de implementação por evidências. | Auditoria técnica e documentação histórica. | **Arquivado** | `archive/root-history/` | [`README.md`](README.md#histórico-arquivado-raiz) |
| `archive/root-history/VECTRAS_ANALYSIS_COMPLETE.md` | Relato histórico de análise consolidada. | Pesquisa histórica e auditoria. | **Arquivado** | `archive/root-history/` | [`README.md`](README.md#histórico-arquivado-raiz) |
| `archive/root-history/VECTRAS_DEEP_EVIDENCE.md` | Evidências históricas detalhadas de revisão/validação. | Auditoria e governança histórica. | **Arquivado** | `archive/root-history/` | [`README.md`](README.md#histórico-arquivado-raiz) |

## Diretórios com documentação completa
Cada diretório tem:
1) `README.md` (camadas 1 e 2)
2) `FILES_MAP.md` (camada 3)

- `app/`, `engine/`, `terminal-emulator/`, `terminal-view/`, `shell-loader/`, `bench/`, `bug/`, `demo_cli/`, `tools/`, `docs/`, `reports/`, `resources/`, `runtime/`, `web/`, `archive/`, `fastlane/`, `gradle/`, `3dfx/`.

- `bug/`: [`bug/README.md`](bug/README.md) e [`bug/FILES_MAP.md`](bug/FILES_MAP.md).


## Domínio de bugs (`bug/`)
- Escopo, objetivo e relação com `docs/`, `reports/` e raiz: [`bug/README.md`](bug/README.md).
- Mapa arquivo-a-arquivo com função e status: [`bug/FILES_MAP.md`](bug/FILES_MAP.md).
- Nomenclatura alinhada ao padrão (`README.md` + `FILES_MAP.md`) usado em `engine/`, `tools/` e `docs/`.

## Documentos técnicos especializados
- Arquitetura: `docs/ARCHITECTURE.md`, `docs/RAFAELIA_COHESION_ENTERPRISE_STACK.md`
- Operação: `docs/OPERATIONS.md`, `docs/BLUEPRINT_FLUXOS_VM.md`
- Performance: `docs/PERFORMANCE_INTEGRITY.md`, `docs/BENCHMARKS.md`
- Conformidade: `docs/LEGAL_AND_LICENSES.md`, `docs/IP_MAP.md`, `THIRD_PARTY_NOTICES.md`


## Governança operacional (CI e segurança)
- CI Android: [`.github/workflows/android.yml`](.github/workflows/android.yml).
- CI Engine: [`.github/workflows/engine-ci.yml`](.github/workflows/engine-ci.yml).
- Automação de dependências: [`.github/dependabot.yml`](.github/dependabot.yml).
- Security allowlist de artefatos sensíveis: [`security/sensitive-artifacts-allowlist.txt`](security/sensitive-artifacts-allowlist.txt).
- Verificador de artefatos sensíveis: [`tools/check_sensitive_artifacts.sh`](tools/check_sensitive_artifacts.sh).

## Núcleo Engine RMR
- Cabeçalhos low-level: [`engine/rmr/include/rmr_corelib.h`](engine/rmr/include/rmr_corelib.h), [`engine/rmr/include/rmr_ll_ops.h`](engine/rmr/include/rmr_ll_ops.h), [`engine/rmr/include/rmr_ll_tuning.h`](engine/rmr/include/rmr_ll_tuning.h), [`engine/rmr/include/rmr_math_fabric.h`](engine/rmr/include/rmr_math_fabric.h).
- Implementações C: [`engine/rmr/src/rmr_corelib.c`](engine/rmr/src/rmr_corelib.c), [`engine/rmr/src/rmr_ll_ops.c`](engine/rmr/src/rmr_ll_ops.c), [`engine/rmr/src/rmr_ll_tuning.c`](engine/rmr/src/rmr_ll_tuning.c), [`engine/rmr/src/rmr_math_fabric.c`](engine/rmr/src/rmr_math_fabric.c).

## Matriz de testes (app)
- Suite unitária de VM/runtime: [`app/src/test/java/com/vectras/vm/`](app/src/test/java/com/vectras/vm/).
- Suite unitária de terminal: [`app/src/test/java/com/vectras/vterm/`](app/src/test/java/com/vectras/vterm/).
- Contratos utilitários/QMP: [`app/src/test/java/com/vectras/qemu/utils/`](app/src/test/java/com/vectras/qemu/utils/).

## Relatórios e bugfix
- Relatório consolidado de correções: [`docs/BUGFIX_REPORT.md`](docs/BUGFIX_REPORT.md).
