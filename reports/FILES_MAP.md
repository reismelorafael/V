# reports/FILES_MAP.md

Mapa arquivo-a-arquivo em três linhas por item: papel, ligação e comando de inspeção.

## `reports/ANALISE_COMPLETA_VECTRAS.md`
- **Papel**: documentação textual e/ou especificação técnica.
- **Liga com**: ver [`reports/README.md`](README.md) e [`docs/THREE_LAYER_ANALYSIS.md`](../docs/THREE_LAYER_ANALYSIS.md) para contexto de camadas.
- **Inspeção**: `file "reports/ANALISE_COMPLETA_VECTRAS.md"` e, quando texto, `sed -n "1,80p" "reports/ANALISE_COMPLETA_VECTRAS.md"`.

## `reports/COMPARISON_REPORT.md`
- **Papel**: documentação textual e/ou especificação técnica.
- **Liga com**: ver [`reports/README.md`](README.md) e [`docs/THREE_LAYER_ANALYSIS.md`](../docs/THREE_LAYER_ANALYSIS.md) para contexto de camadas.
- **Inspeção**: `file "reports/COMPARISON_REPORT.md"` e, quando texto, `sed -n "1,80p" "reports/COMPARISON_REPORT.md"`.

## `reports/MD_INDEX_Vectras-VM-Android.md`
- **Papel**: documentação textual e/ou especificação técnica.
- **Liga com**: ver [`reports/README.md`](README.md) e [`docs/THREE_LAYER_ANALYSIS.md`](../docs/THREE_LAYER_ANALYSIS.md) para contexto de camadas.
- **Inspeção**: `file "reports/MD_INDEX_Vectras-VM-Android.md"` e, quando texto, `sed -n "1,80p" "reports/MD_INDEX_Vectras-VM-Android.md"`.

## `reports/POST_FIX_VALIDATION.md`
- **Papel**: documentação textual e/ou especificação técnica.
- **Liga com**: ver [`reports/README.md`](README.md) e [`docs/THREE_LAYER_ANALYSIS.md`](../docs/THREE_LAYER_ANALYSIS.md) para contexto de camadas.
- **Inspeção**: `file "reports/POST_FIX_VALIDATION.md"` e, quando texto, `sed -n "1,80p" "reports/POST_FIX_VALIDATION.md"`.

## `reports/README.md`
- **Papel**: documentação local do diretório.
- **Liga com**: ver [`reports/README.md`](README.md) e [`docs/THREE_LAYER_ANALYSIS.md`](../docs/THREE_LAYER_ANALYSIS.md) para contexto de camadas.
- **Inspeção**: `file "reports/README.md"` e, quando texto, `sed -n "1,80p" "reports/README.md"`.

## `reports/Vectras-VM-Android_ARCH_REPORT.md`
- **Papel**: documentação textual e/ou especificação técnica.
- **Liga com**: ver [`reports/README.md`](README.md) e [`docs/THREE_LAYER_ANALYSIS.md`](../docs/THREE_LAYER_ANALYSIS.md) para contexto de camadas.
- **Inspeção**: `file "reports/Vectras-VM-Android_ARCH_REPORT.md"` e, quando texto, `sed -n "1,80p" "reports/Vectras-VM-Android_ARCH_REPORT.md"`.

## `reports/baremetal/.gitignore`
- **Papel**: artefato de suporte do diretório.
- **Liga com**: ver [`reports/README.md`](README.md) e [`docs/THREE_LAYER_ANALYSIS.md`](../docs/THREE_LAYER_ANALYSIS.md) para contexto de camadas.
- **Inspeção**: `file "reports/baremetal/.gitignore"` e, quando texto, `sed -n "1,80p" "reports/baremetal/.gitignore"`.

## `reports/metrics/README.md`
- **Papel**: documentação local do diretório.
- **Liga com**: ver [`reports/README.md`](README.md) e [`docs/THREE_LAYER_ANALYSIS.md`](../docs/THREE_LAYER_ANALYSIS.md) para contexto de camadas.
- **Inspeção**: `file "reports/metrics/README.md"` e, quando texto, `sed -n "1,80p" "reports/metrics/README.md"`.

## `reports/metrics/rafaelia_metrics_250.json`
- **Papel**: configuração declarativa de build, metadata ou catálogo.
- **Liga com**: ver [`reports/README.md`](README.md) e [`docs/THREE_LAYER_ANALYSIS.md`](../docs/THREE_LAYER_ANALYSIS.md) para contexto de camadas.
- **Inspeção**: `file "reports/metrics/rafaelia_metrics_250.json"` e, quando texto, `sed -n "1,80p" "reports/metrics/rafaelia_metrics_250.json"`.



## `reports/NON_MD_AUDIT_REPORT.md`
- **Papel**: relatório executivo da auditoria de arquivos não-MD (estatísticas, riscos e recomendações).
- **Liga com**: gerado por [`tools/audit_non_md_inventory.py`](../tools/audit_non_md_inventory.py).
- **Inspeção**: `file "reports/NON_MD_AUDIT_REPORT.md"` e `sed -n "1,200p" "reports/NON_MD_AUDIT_REPORT.md"`.

## `reports/non_md_inventory.tsv`
- **Papel**: inventário forense arquivo-a-arquivo (path, tamanho, extensão, tipo, executável, SHA-256).
- **Liga com**: gerado por [`tools/audit_non_md_inventory.py`](../tools/audit_non_md_inventory.py).
- **Inspeção**: `head -n 5 "reports/non_md_inventory.tsv"` e `wc -l "reports/non_md_inventory.tsv"`.
