# BITΩ — Unified Directed State System
## Visão geral (tese)

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

### Artefatos
- `engine/rmr/include/bitomega.h`
- `engine/rmr/src/bitomega.c`
- Experimentos: benchmark + logs reprodutíveis (a completar)
