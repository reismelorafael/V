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
Implementação: `bitomega_transition(node, ctx)`.

## 6) Invariantes mínimos
- coerência e entropia dentro de \([0,1]\)
- VOID ⇒ direção ∈ {NONE, NULL}
- META ⇒ coerência ≥ entropia

Esses invariantes são “guard rails” do sistema.
