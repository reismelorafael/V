# Mapa de Implementação (código ↔ formalismo)

## API pública
- `engine/rmr/include/bitomega.h`

## Implementação
- `engine/rmr/src/bitomega.c`

## Integração recomendada no RMR
1) `rmr_unified_kernel.c` pode manter um `bitomega_node_t` por subsistema:
   - bridge (QEMU)
   - policy
   - lowlevel tuning
2) Telemetria: logar `(state, dir, coherence, entropy)` em intervalos fixos.

## Pontos onde isso encaixa naturalmente
- `rmr_policy_kernel.*` (decisões por coerência/ruído/carga)
- `rmr_ll_tuning.*` (mudança de estratégia conforme EDGE/LOCK)
- `rmr_qemu_bridge.*` (fallbacks determinísticos quando NOISE/VOID)

## Regra de ouro
O BITΩ NÃO substitui o código existente.
Ele atua como “camada de governança de estado”.
