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
| Dependências de arquivos do repositório | Validar integridade de dependências declaradas em arquivos do repo antes da build | `Verify repository file dependencies` (`./gradlew verifyRepoFileDependencies`) | Saída do Gradle no job CI; falha bloqueia build |
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
