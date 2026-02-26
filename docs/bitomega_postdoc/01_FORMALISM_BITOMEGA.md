# Formalismo do BITΩ

## 1) Estrutura
Definimos:
- Espaço de estados **S** com 10 elementos.
- Direções **D** (canais) discretas.
- Operador de transição **Δ**.
- Conjunto de invariantes **I**.

\[
\mathcal{U} = (S, D, \Delta, I)
\]

## 2) Estados
\[
S=\{NEG, ZERO, POS, MIX, VOID, EDGE, FLOW, LOCK, NOISE, META\}
\]

## 3) Direções
\[
D=\{NONE, UP, DOWN, FORWARD, RECURSE, NULL\}
\]

## 4) Contexto
\[
C=(coherence\_in, entropy\_in, noise\_in, load, seed)
\]
com sinais normalizados em \([0,1]\).

## 5) Operador Δ
\[
\Delta: S \times C \rightarrow S
\]
Implementação: `bitomega_transition(node, ctx)` em `engine/rmr/src/bitomega.c`.

Detalhes práticos alinhados ao código:
- `update_fields` faz suavização conservadora (fator `a=0.25`) entre estado interno e entrada.
- Regras são por estado atual (`switch`), com thresholds explícitos para `coherence/entropy/noise/load`.
- Em inconsistência, há “self-heal” para `ZERO/NONE`.

## 6) Invariantes mínimos
- coerência e entropia dentro de \([0,1]\)
- VOID ⇒ direção ∈ {NONE, NULL}
- META ⇒ coerência ≥ entropia

Esses invariantes são “guard rails” do sistema e são validados por `bitomega_invariant_ok` (header em `engine/rmr/include/bitomega.h`).

## 7) Escopo atual vs futuro
- **Atual:** formalismo + implementação C + inclusão no build.
- **Futuro:** calibração automática dos thresholds por perfil/arquitetura e integração com feedback do policy kernel.
