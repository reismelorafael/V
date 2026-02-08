# Rafaelia Performance Ops (Authorial Notes)

Este documento registra as decisões autorais para evolução de desempenho no Vectras VM
com foco em TCG, SoftMMU e fluxo de I/O. O objetivo é manter um mapa estruturalista
das alavancas reais de performance e as ações já integradas no app.

## Implementações aplicadas no app

1. **TCG TB cache/lookup**
   - Normalização de `-accel tcg` para garantir `tb-size` quando Rafaelia está habilitado.
   - Defaults do perfil Rafaelia agora incluem `tb-size=2048`.

2. **Controle de invalidação (infra)**
   - Sem alterações diretas no core do QEMU neste repo (app Android).
   - Estratégia: reforçar parâmetros de execução e preparar telemetria Rafaelia
     para futuras decisões de invalidation no fork qemu_rafaelia.

3. **SoftMMU / cputlb**
   - Sem alterações diretas aqui (core do QEMU está fora do escopo do app).
   - Plano: parametrizar fast-path por presets e medir impactos via Rafaelia Bench.

4. **BQL contention**
   - Telemetria de execução já integrada via Rafaelia (bench + log capture).
   - Próximo passo: correlacionar vCPU count com latência de input.

5. **I/O + virtio**
   - Preparar presets para uso de `virtio` e `aio` dentro dos modelos do app
     (sem forçar drivers no guest).

6. **Toolchain**
   - A ser aplicado na árvore do QEMU (PGO/LTO e flags ARM).

## Próximas oportunidades (pipeline 50+)

- Consolidação de batching em DMA e coroutines (qemu_rafaelia).
- Heurística de “range mínimo” para invalidação de TB.
- Ajuste de TLB packing e redução de branches hot-path.
- Mapeamento de cache mode por workload (boot vs runtime).
- Perfilização contínua: baseline → workload fixo → métricas (boot/throughput/latência).

## Observações

As melhorias de core (TCG/SoftMMU/BQL) serão aplicadas no fork QEMU, e este diretório
serve como trilha de decisões e controle do que já foi implementado via app.
