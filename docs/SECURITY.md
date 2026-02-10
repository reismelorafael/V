# Segurança Operacional

## Princípios
- **Fail-safe não bloqueante:** observabilidade não pode travar execução.
- **Menor privilégio:** evitar permissões legadas em Android moderno.
- **Contenção de recurso:** quotas de memória/linhas/bytes em logs.

## Mitigações implementadas
| Vetor | Mitigação |
|---|---|
| Deadlock stdout/stderr | Drenagem paralela (`ProcessOutputDrainer`) |
| DoS local por flood de logs | Rate limit + drop contabilizado + modo degradado |
| Crescimento ilimitado de memória | Ring buffer com limites rígidos |
| Processo órfão/zumbi | Supervisor com timeout + TERM/KILL escalonado |
| Storage legado inseguro | Scoped Storage + SAF em Android 10+ |

## Logging seguro
- Logs extensivos são limitados por token bucket.
- Em saturação, o sistema troca para saída resumida (`DEGRADED`) e registra auditoria.

## Storage e permissões
- Android 10+ prioriza armazenamento interno e SAF.
- Android legado mantém fallback de `WRITE_EXTERNAL_STORAGE`.
