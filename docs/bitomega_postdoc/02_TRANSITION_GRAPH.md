# Grafo de Transições (Δ)

## Objetivo e escopo
Representar o BITΩ como um grafo dirigido para auditoria de comportamento do runtime:
- nós = estados
- arestas = regras de transição condicionadas pelo contexto

Escopo da versão atual: documentação das transições implementadas em `engine/rmr/src/bitomega.c`.

## Heurística (intuição formalizada)
- **coerência alta + ruído baixo** tende a estabilizar (`FLOW→LOCK`)
- **ruído alto** tende a revelar `NOISE/VOID`
- **carga alta** empurra para `EDGE`
- `MIX` resolve em `POS/NEG/ZERO` conforme diferença (`coerência−entropia`)

## Mapeamento direto ao código
- `FLOW`: fecha em `LOCK` sob alta coerência e baixo ruído.
- `LOCK`: degrada para `MIX` por entropia/ruído, ou `EDGE` por carga.
- `ZERO`: entra em `FLOW` com coerência suficiente, ou `NOISE` em ruído extremo.
- `NOISE/NEG`: podem colapsar para `VOID` em entropia alta.
- `VOID`: só sai com recuperação forte de coerência.

## Como desenhar (recomendado)
- Use Graphviz (`dot`) ou Mermaid.
- Gere em CI um PNG/SVG e salve em `docs/bitomega_postdoc/figs/`.

## Entregável de figura
- `figs/bitomega_transition_graph.svg` (pendente)

## Limitações e próximos passos
- Limitação: figura ainda não é gerada automaticamente.
- Próximo passo: pipeline CI para extrair transições do `switch` e publicar SVG versionado.
