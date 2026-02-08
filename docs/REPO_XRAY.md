# RAIO-X do Repositório (Vectras-VM-Android)

## Sub-sistemas identificados
- **Core autoral (alto valor IP):** `engine/rmr/` (bitraf core, hw detect, cycles, bench suite).
- **Runtime showcase Android:** `app/`, `terminal-emulator/`, `terminal-view/`, `shell-loader/`, `3dfx/`.
- **Documentação e produto:** `docs/`, `README.md`, relatórios em `reports/`.
- **Assets e suporte:** `resources/`, `web/`, `fastlane/`.

## Arquivos centrais (prioridade vendável)
- `engine/rmr/src/rafaelia_bitraf_core.c`
- `engine/rmr/src/rmr_hw_detect.c`
- `engine/rmr/src/rmr_cycles.c`
- `engine/rmr/src/rmr_bench.c`
- `engine/rmr/src/rmr_bench_suite.c`

## Duplicações/poluição mapeadas
- Conteúdo experimental fora do fluxo principal movido para `archive/experimental/`.
- Notas de perf movidas para docs de produto (`docs/RAFAELIA_PERF_OPS.md`).

## Decisão de produto
1. Produto 1 primeiro: **Engine RMR/Bitraf** isolado, build reproduzível, bench padronizado.
2. Produto 2 depois: **Runtime Android (showcase)** como consumidor da engine e vitrine.
