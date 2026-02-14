# reports/

## Camada 1 — Propósito do diretório
Relatórios técnicos e métricas.

## Camada 2 — Estrutura (até 3 níveis)
- Nível 1: `reports/`
- Nível 2: `baremetal/`, `metrics/`
- Nível 3: detalhamento por arquivo em [`FILES_MAP.md`](FILES_MAP.md).

## Camada 3 — Arquivos e vínculos
- Catálogo completo: [`FILES_MAP.md`](FILES_MAP.md)
- Auditoria não-MD: [`NON_MD_AUDIT_REPORT.md`](NON_MD_AUDIT_REPORT.md) e [`non_md_inventory.tsv`](non_md_inventory.tsv)
- Contexto global de camadas: [`docs/THREE_LAYER_ANALYSIS.md`](../docs/THREE_LAYER_ANALYSIS.md)

## Cadeia de comando (lógica de inspeção)
```bash
find reports -maxdepth 3 -type d | sort
sed -n '1,120p' reports/FILES_MAP.md
```
