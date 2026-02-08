# BENCHMARKS (RMR Engine)

## Build rápido
```bash
make clean && make all
```

## Rodar 1 execução
```bash
./build/bench/rmr_bench bench/results/latest.csv bench/results/latest.json
```

## Rodar N execuções + mediana/p95
```bash
bench/scripts/run_bench.sh 9 bench/results
```

Saídas:
- CSV por execução: `bench/results/run_*.csv`
- JSON por execução: `bench/results/run_*.json`
- Sumário estatístico: `bench/results/summary.json` (median/p95/min/max)

## Campos
- `score`: throughput relativo por teste
- `variance`: dispersão interna por teste
- `error_margin`: margem estimada
- `total_score`, `total_error`: agregados da suite (50 métricas)
