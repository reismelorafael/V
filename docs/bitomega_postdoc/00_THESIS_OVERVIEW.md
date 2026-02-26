# BITΩ — Unified Directed State System
## Visão geral (tese)

### Objetivo e escopo da integração
O módulo **BITΩ/BITOMEGA** introduz uma camada determinística de governança de estado para o `engine/rmr`, sem substituir os módulos existentes. O escopo atual cobre:
- modelagem de estado unificada para subsistemas do runtime;
- API C estável para chamada por kernel/policy/bridge;
- transição determinística baseada em sinais normalizados (`coherence`, `entropy`, `noise`, `load`).

### Problema
Sistemas físicos, lógicos e computacionais são frequentemente descritos por linguagens incompatíveis, gerando “paradoxos” que são, na prática, falhas de tradução entre níveis.

### Hipótese (A + C)
Existe uma estrutura unificadora baseada em **estados finitos + direcionalidade + operador de transição** que descreve:
- computação (máquinas de estado),
- dinâmica (fluxos, estabilidade, ruído),
- inferência (coerência vs entropia),
com uma interface implementável em kernel (C).

### Contribuições
1. Definição canônica de **10 estados** (BitΩ) e canais de direção.
2. Operador Δ determinístico: `Δ(state, context) -> state`.
3. Invariantes mínimos para coerência do runtime.
4. Implementação C leve, estável, integrável ao `engine/rmr`.

### Artefatos de implementação
- Header/API: `engine/rmr/include/bitomega.h`
- Implementação: `engine/rmr/src/bitomega.c`
- Build: inclusão de `bitomega.c` nas listas CMake dos alvos `rmr` e `vectra_core_accel`.

### Limitações e próximos passos (sumário)
- Δ ainda é heurístico (determinístico, porém com limiares fixos).
- Integração funcional inicial no build, ainda sem acoplamento profundo em policy/tuning.
- Próximo passo: plugar `bitomega_node_t` nos loops de decisão do `rmr_unified_kernel` com telemetria contínua.
