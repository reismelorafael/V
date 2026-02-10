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
