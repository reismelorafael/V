# engine/

## Camada 1 — Propósito do diretório
Núcleo nativo C/Rust para políticas e performance.

## Camada 2 — Estrutura (até 3 níveis)
- Nível 1: `engine/`
- Nível 2: `rmr/`, `vectra_policy_kernel/`
- Nível 3: detalhamento por arquivo em [`FILES_MAP.md`](FILES_MAP.md).

## Camada 3 — Arquivos e vínculos
- Catálogo completo: [`FILES_MAP.md`](FILES_MAP.md)
- Contexto global de camadas: [`docs/THREE_LAYER_ANALYSIS.md`](../docs/THREE_LAYER_ANALYSIS.md)

## Cadeia de comando (lógica de inspeção)
```bash
find engine -maxdepth 3 -type d | sort
sed -n '1,120p' engine/FILES_MAP.md
```

## Core RMR unificado
- O ponto único de verdade do core nativo agora é `engine/rmr/include/rmr_unified_kernel.h`.
- A implementação correspondente está em `engine/rmr/src/rmr_unified_kernel.c`, encapsulando policy kernel, autodetect de hardware, bitraf e corelib por uma API pública estável.

## Fonte de verdade para linkagem do APK (`app/src/main/cpp/vectra_core_accel.c`)

Esta matriz é a referência oficial de linkagem JNI→C para o APK.

### 1) Símbolos chamados diretamente no JNI (foco pedido)

#### Família `RmR_UnifiedKernel_*`

| Símbolo | Uso direto no JNI (`vectra_core_accel.c`) | Definição C local (`engine/rmr/src/*.c`) | Status de linkagem APK |
|---|---|---|---|
| `RmR_UnifiedKernel_ArenaAlloc` | sim | `engine/rmr/src/rmr_unified_kernel.c` | local (compilado no target `vectra_core_accel`) |
| `RmR_UnifiedKernel_ArenaCopy` | sim | `engine/rmr/src/rmr_unified_kernel.c` | local (compilado no target `vectra_core_accel`) |
| `RmR_UnifiedKernel_ArenaFill` | sim | `engine/rmr/src/rmr_unified_kernel.c` | local (compilado no target `vectra_core_accel`) |
| `RmR_UnifiedKernel_ArenaFree` | sim | `engine/rmr/src/rmr_unified_kernel.c` | local (compilado no target `vectra_core_accel`) |
| `RmR_UnifiedKernel_ArenaWrite` | sim | `engine/rmr/src/rmr_unified_kernel.c` | local (compilado no target `vectra_core_accel`) |
| `RmR_UnifiedKernel_ArenaXorChecksum` | sim | `engine/rmr/src/rmr_unified_kernel.c` | local (compilado no target `vectra_core_accel`) |
| `RmR_UnifiedKernel_Audit` | sim | `engine/rmr/src/rmr_unified_kernel.c` | local (compilado no target `vectra_core_accel`) |
| `RmR_UnifiedKernel_ByteSwap32` | sim | `engine/rmr/src/rmr_unified_kernel.c` | local (compilado no target `vectra_core_accel`) |
| `RmR_UnifiedKernel_Copy` | sim | `engine/rmr/src/rmr_unified_kernel.c` | local (compilado no target `vectra_core_accel`) |
| `RmR_UnifiedKernel_Ingest` | sim | `engine/rmr/src/rmr_unified_kernel.c` | local (compilado no target `vectra_core_accel`) |
| `RmR_UnifiedKernel_Popcount32` | sim | `engine/rmr/src/rmr_unified_kernel.c` | local (compilado no target `vectra_core_accel`) |
| `RmR_UnifiedKernel_Process` | sim | `engine/rmr/src/rmr_unified_kernel.c` | local (compilado no target `vectra_core_accel`) |
| `RmR_UnifiedKernel_QueryCapabilities` | sim | `engine/rmr/src/rmr_unified_kernel.c` | local (compilado no target `vectra_core_accel`) |
| `RmR_UnifiedKernel_Rotl32` | sim | `engine/rmr/src/rmr_unified_kernel.c` | local (compilado no target `vectra_core_accel`) |
| `RmR_UnifiedKernel_Rotr32` | sim | `engine/rmr/src/rmr_unified_kernel.c` | local (compilado no target `vectra_core_accel`) |
| `RmR_UnifiedKernel_Route` | sim | `engine/rmr/src/rmr_unified_kernel.c` | local (compilado no target `vectra_core_accel`) |
| `RmR_UnifiedKernel_Verify` | sim | `engine/rmr/src/rmr_unified_kernel.c` | local (compilado no target `vectra_core_accel`) |
| `RmR_UnifiedKernel_XorChecksum` | sim | `engine/rmr/src/rmr_unified_kernel.c` | local (compilado no target `vectra_core_accel`) |

#### Família `rmr_kernel_*`

- Nenhum símbolo `rmr_kernel_*` é chamado diretamente no JNI de `vectra_core_accel.c`.

#### Família `bitraf_*`

- Nenhum símbolo `bitraf_*` é chamado diretamente no JNI de `vectra_core_accel.c`.

#### Símbolos explícitos pedidos: `RmR_HW_Detect`, `RmR_CRC32C`

- `RmR_HW_Detect`: **não chamado diretamente** no JNI de `vectra_core_accel.c`.
- `RmR_CRC32C`: **não chamado diretamente** no JNI de `vectra_core_accel.c`.

### 2) Dependências externas / artefatos separados

- Nesta versão, **nenhum** símbolo JNI da família `RmR_UnifiedKernel_*` está sem implementação C local.
- Se um build futuro remover `engine/rmr/src/rmr_unified_kernel.c` do APK, os símbolos `RmR_UnifiedKernel_*` passarão a depender de artefato externo; o nome esperado do artefato deve ser uma lib dedicada ao kernel unificado (ex.: `librmr_unified_kernel`) ou permanência no target atual `vectra_core_accel`.
