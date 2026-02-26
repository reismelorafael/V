# Grafo de Transições (Δ)

## Objetivo
Representar o BITΩ como um grafo dirigido:
- nós = estados
- arestas = regras de transição condicionadas pelo contexto

## Heurística (intuição formalizada)
- **coerência alta + ruído baixo** tende a estabilizar (FLOW→LOCK)
- **ruído alto** tende a revelar NOISE/VOID
- **carga alta** empurra para EDGE
- **MIX** resolve em POS/NEG/ZERO conforme diferença (coerência−entropia)

## Como desenhar (recomendado)
- Use Graphviz (`dot`) ou Mermaid.
- Gere em CI um PNG/SVG e salve em `docs/bitomega_postdoc/figs/`.

## Entregável de figura
- `figs/bitomega_transition_graph.svg` (a completar)
