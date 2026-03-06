# SURGICAL PATCHSET — RAFAELIA ingestão textual

## Origem e método
- Patchsets recuperados da origem externa via histórico Git (`5924890`), extraídos fora da árvore (`/tmp/rafaelia_patchsets`).
- Integração feita como mudanças textuais versionadas (sem reintroduzir blobs `.zip`).

---

## Patchset 1 — `RAFAELIA_VECTRA2_PATCHES.zip`

### engine/rmr
- `engine/rmr/include/rafaelia_formulas_core.h` (novo): API C bare-metal para fórmulas RAFAELIA.
  - Funções: `raf_phi_ethica`, `raf_kernel_step`, `raf_vortex_metric`, `raf_retroalimentar`, `raf_spiral`, `raf_toroid_delta_pi_phi`, `raf_trinity_633`, `raf_fibonacci_rafael_step`, `raf_information_bits`, `raf_logical_capacity`, `raf_voo_quantico`, `raf_evolucao_rafaelia`, `raf_in_fomega_band`, `raf_synaptic_weight`, `raf_cycle_step`.
- `engine/rmr/src/rafaelia_formulas_core.c` (novo): implementação das funções acima.
- `engine/rmr/include/rmr_math_fabric.h` (alterado): extensão RAFAELIA no Math Fabric.
  - Tipos/funções: `RmR_MathFabricRafaeliaExt`, `RmR_MathFabric_RafaeliaExtend`, `RmR_MathFabric_Spiral`, `RmR_MathFabric_FibRafaelStep`.
- `engine/rmr/src/rmr_math_fabric.c` (alterado): implementação dos novos operadores Q16.16.
- `CMakeLists.txt` (alterado): adiciona `engine/rmr/src/rafaelia_formulas_core.c` ao build `rmr`.

### Android modules
- `app/src/main/java/com/vectras/vm/core/MathUtils.java` (alterado): incorpora extensões de fórmulas RAFAELIA (spiral/fib/trinity/toroid/capacidade).
- `app/src/main/java/com/vectras/vm/rafaelia/RafaeliaKernelV22.java` (alterado): versão com integração estendida do índice RAFAELIA.
- `app/src/main/java/com/vectras/vm/rafaelia/RafaeliaCognitiveLoop.java` (novo): loop cognitivo ψ→χ→ρ→Δ→Σ→Ω.
- `app/src/main/java/com/vectras/vm/rafaelia/RafaeliaFormulas.java` (novo): catálogo de fórmulas e literais associados.

---

## Patchset 2 — `RAFAELIA-CHANGED-FILES.zip`

### bug/core
- `bug/core/bitraf.c` (alterado): integração textual com diagnóstico determinístico de máscara de rotas.
  - Funções: `bitraf_popcount32`, `bitraf_route_popcount`.
- `bug/core/zipraf_core_bridge.c` (alterado): integração textual de utilitário CRC32 IEEE para inspeção ZIP.
  - Função: `zr_crc32_ieee`.

> Arquivos auxiliares do zip original (`unified-ci.yml`, `llamaRafaelia-CMakeLists.txt`) não foram importados diretamente por não serem caminhos canônicos deste repo; a integração efetiva foi mapeada para módulos existentes.

---

## Patchset 3 — `ZIPDROP_DELTA_fix.zip`

### ingestão documental
- `_incoming/INTEGRATION_EVIDENCE.md` (novo): evidência de integração e origem recuperada.
- `_incoming/README.md` (mantido): diretório permanece documental.
- `_incoming/.keep` (removido): limpeza do diretório para evitar artefato técnico.

### Observação
- O workflow de zipdrop já existia no repositório em `.github/workflows/zipdrop.yml`; portanto, não foi necessária alteração adicional desse arquivo nesta aplicação.
