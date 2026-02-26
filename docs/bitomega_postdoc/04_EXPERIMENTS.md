# Experimentos (reprodutíveis)

## Objetivo e escopo
Validar custo, estabilidade e previsibilidade da integração BITΩ sobre o runtime.

## 1) Smoke test de transições
- Inicializar node em `ZERO`.
- Variar contexto (`coerência/entropia/ruído/carga`) em grade 0..1.
- Contar frequências de estados finais e transições inválidas.

## 2) Benchmark overhead
- Medir custo de `bitomega_transition` por chamada.
- Alvo inicial de ordem de grandeza: `< 20 ns` desktop, `< 80 ns` arm64.

## 3) Integração com tuning
- Simular carga subindo -> `EDGE` -> `FLOW`.
- Confirmar que decisões do `rmr_ll_tuning` mudam apenas quando `EDGE` aparece.

## Saídas
- CSV em `reports/bitomega/*.csv`
- logs em `reports/bitomega/*.log`

## Limitações e próximos passos
- Ainda não há suíte automatizada completa no CI para esses cenários.
- Próximo passo: adicionar alvo de benchmark/smoke dedicado em CMake e publicar artefatos por arquitetura.
