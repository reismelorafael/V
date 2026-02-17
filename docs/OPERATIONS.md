# Operações e Troubleshooting

## Indicadores-chave
- `dropped_logs` (counter)
- bytes processados no stream
- estado do supervisor (`RUN/DEGRADED/FAILOVER/STOP`)
- timeout e cancelamentos em ShellExecutor

## Audit Ledger
- Arquivo: `files/audit-ledger.jsonl`
- Rotação simples: `audit-ledger.prev.jsonl`
- Campos: `ts_mono`, `ts_wall`, `vm_id`, `state_from`, `state_to`, `cause_code`, `dropped_logs`, `bytes`, `stall_ms`, `action_taken`

## Matriz de gates de CI (auditoria de processo)
| Gate | Objetivo | Etapa no workflow | Evidência/auditoria |
|---|---|---|---|
| Build Android | Garantir compilação de artefatos Debug/Release com toolchain suportada | `Build Debug APK` e `Build Release APK` | Logs do job `build` + APKs gerados |
| Dependências de arquivos do repositório | Validar integridade de dependências declaradas em arquivos do repo antes da build | `Verify repository file dependencies` (`./tools/gradle_with_jdk21.sh verifyRepoFileDependencies`) | Saída do Gradle no job CI; falha bloqueia build |
| Documentação crítica | Verificar links markdown locais em docs essenciais para evitar referências quebradas | `Validate local Markdown links (optional)` | Log da etapa (não bloqueante por `continue-on-error`) |
| Artefatos | Publicar APKs para rastreabilidade e distribuição interna | `Upload Debug APK as artifact` e `Upload Release APK as artifact` | Artefatos versionados no GitHub Actions |

## Cenário de teste manual: flood
1. Iniciar VM.
2. Rodar comando de alto volume no terminal da VM.
3. Confirmar que UI permanece responsiva.
4. Confirmar crescimento de `dropped_logs` e evento `DEGRADED` no ledger.

## Troubleshooting rápido
| Sintoma | Ação |
|---|---|
| VM não encerra | Validar failover TERM→KILL e `waitFor` |
| Logs cortados | Esperado em flood (backpressure) |
| Erro de pasta externa Android 11+ | Selecionar pasta via SAF |

## Matriz de gates (auditoria de processo)
| Gate | Objetivo | Execução/critério | Evidência gerada |
|---|---|---|---|
| Build Android (debug + release) | Garantir integridade de compilação dos módulos Android antes de distribuição | Workflow `Android CI` deve executar `./tools/gradle_with_jdk21.sh assembleDebug` e `./tools/gradle_with_jdk21.sh assembleRelease` (ou exportar `JAVA_HOME` 17/21 antes do Gradle) com versão de SDK/NDK parametrizada. | APKs publicados como artefatos `android-debug-apk` e `android-release-apk` no Actions. |
| Dependências de arquivos de repositório | Bloquear divergências entre documentação, mapeamentos e cadeia de arquivos essenciais | Etapa explícita `./tools/gradle_with_jdk21.sh verifyRepoFileDependencies` executada antes das etapas de build. | Log da etapa `Verify repository file dependencies` no job de CI. |
| Documentação crítica (links locais markdown) | Detectar referências quebradas em `README.md` e `docs/**/*.md` | Etapa opcional `Validate local markdown links` em Python (com `continue-on-error`) verifica caminhos locais não-HTTP. | Relatório no log da etapa, com lista de links inválidos quando detectados. |
| Artefatos de distribuição | Assegurar rastreabilidade de binários gerados na pipeline | Upload automatizado dos APKs de debug/release via `actions/upload-artifact@v4`. | Artefatos versionados por run no GitHub Actions + upload Telegram condicionado a segredo. |
