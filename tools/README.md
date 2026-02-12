# tools/

## Camada 1 — Propósito do diretório
Automação de verificação e utilitários operacionais.

## Camada 2 — Estrutura (até 3 níveis)
- Nível 1: `tools/`
- Nível 2: `apk/`, `baremetal/`, `termux-arm64-orchestrator/`
- Nível 3: detalhamento por arquivo em [`FILES_MAP.md`](FILES_MAP.md).

## Camada 3 — Arquivos e vínculos
- Catálogo completo: [`FILES_MAP.md`](FILES_MAP.md)
- Contexto global de camadas: [`docs/THREE_LAYER_ANALYSIS.md`](../docs/THREE_LAYER_ANALYSIS.md)

## Cadeia de comando (lógica de inspeção)
```bash
find tools -maxdepth 3 -type d | sort
sed -n '1,120p' tools/FILES_MAP.md
```
