# Mapa de Implementação (código ↔ formalismo)

## Objetivo e escopo da integração
Conectar formalismo BITΩ ao runtime RMR com API C pequena, determinística e auditável.

## API pública (`engine/rmr/include/bitomega.h`)
- `bitomega_state_t`: 10 estados canônicos.
- `bitomega_dir_t`: 6 direções/canais.
- `bitomega_node_t`: estado/direção + escalares (`coherence`, `entropy`).
- `bitomega_ctx_t`: sinais de entrada (`coherence_in`, `entropy_in`, `noise_in`, `load`, `seed`).
- `bitomega_transition`, `bitomega_invariant_ok`, `bitomega_ctx_default`, `bitomega_norm01`.

## Implementação (`engine/rmr/src/bitomega.c`)
- normalização robusta (`clamp01`, NaN→0);
- atualização amortecida de campos (`update_fields`, fator 0.25);
- máquina de transição determinística por estado (`switch` com limiares);
- pós-validação de invariantes com fallback seguro para `ZERO/NONE`.

## Integração de build (CMake)
1) `CMakeLists.txt` (root): `engine/rmr/src/bitomega.c` adicionado ao alvo estático `rmr`.
2) `app/src/main/cpp/CMakeLists.txt`: `../../../../engine/rmr/src/bitomega.c` adicionado ao alvo JNI `vectra_core_accel`.

## Integração funcional recomendada no RMR
1) `rmr_unified_kernel.c` manter `bitomega_node_t` por subsistema:
   - bridge (QEMU)
   - policy
   - lowlevel tuning
2) Telemetria: logar `(state, dir, coherence, entropy, noise, load)` em intervalos fixos.

## Pontos onde encaixa naturalmente
- `rmr_policy_kernel.*` (decisões por coerência/ruído/carga)
- `rmr_ll_tuning.*` (mudança de estratégia conforme `EDGE/LOCK`)
- `rmr_qemu_bridge.*` (fallbacks determinísticos quando `NOISE/VOID`)

## Limitações e próximos passos
- Integração funcional ainda incremental (build pronto, acoplamento parcial no runtime).
- Próximo passo: interface explícita `bitomega_tick()` no ciclo do `rmr_unified_kernel`.
