# Experimentos (reprodutíveis)

## 1) Smoke test de transições
- Inicialize node em ZERO
- Varie contexto (coerência/ruído/carga) em uma grade 0..1
- Conte frequências de estados finais

## 2) Benchmark overhead
- medir custo de `bitomega_transition` por chamada
- alvo: < 20 ns (em desktop), < 80 ns (em arm64) como ordem de grandeza

## 3) Integração com tuning
- Simular carga subindo -> EDGE -> FLOW
- Confirmar que decisões do `rmr_ll_tuning` mudam apenas quando EDGE aparece

## Saídas
- CSV em `reports/bitomega/*.csv`
- logs em `reports/bitomega/*.log`
