# Resultados (tabelas)

## Objetivo e escopo
Padronizar leitura de impacto do BITΩ em estabilidade, overhead e compatibilidade.

## Estrutura padrão

| experimento | métrica | baseline | com BITΩ | ganho/perda |
|---|---:|---:|---:|---:|

## Métricas recomendadas
- Runtime: latência média/p95 por ciclo de decisão.
- Performance: custo de `bitomega_transition` por chamada.
- Compatibilidade: incidência de regressões por ABI/plataforma.
- Estabilidade: taxa de transições para `VOID`/fallback de segurança.

## Leitura esperada
- No estágio inicial, a meta é **estabilidade** e **auditabilidade**.
- Ganhos de performance devem ser tratados como consequência de melhor governança de estado, não objetivo primário.

## Limitações e próximos passos
- Tabelas ainda dependem de coleta sistemática (pendente de automação).
- Próximo passo: consolidar baseline por arquitetura e publicar série histórica por release.
