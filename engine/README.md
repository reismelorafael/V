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

## Fonte de verdade para linkagem do APK (JNI `vectra_core_accel.c`)

Escopo: símbolos chamados diretamente pelo JNI em `app/src/main/cpp/vectra_core_accel.c`, filtrados pelas famílias `RmR_UnifiedKernel_*`, `rmr_kernel_*`, `bitraf_*`, `RmR_HW_Detect` e `RmR_CRC32C`.

Target de link esperado no APK:
- build atual: todos os símbolos abaixo entram em `libvectra_core_accel.so` via `app/src/main/cpp/CMakeLists.txt`.
- fallback quando faltar implementação C local: tratar como dependência externa separada `librmr_unified_kernel.so` (ou `librmr_unified_kernel.a`) com os mesmos símbolos exportados.

| Símbolo JNI direto | Status da definição C local (`engine/rmr/src/*.c`) | Arquivo-fonte (fonte de verdade) | Resolução de linkagem APK |
|---|---|---|---|
| `RmR_UnifiedKernel_QueryCapabilities` | Encontrado | `engine/rmr/src/rmr_unified_kernel.c` | `libvectra_core_accel.so` |
| `RmR_UnifiedKernel_Copy` | Encontrado | `engine/rmr/src/rmr_unified_kernel.c` | `libvectra_core_accel.so` |
| `RmR_UnifiedKernel_XorChecksum` | Encontrado | `engine/rmr/src/rmr_unified_kernel.c` | `libvectra_core_accel.so` |
| `RmR_UnifiedKernel_Popcount32` | Encontrado | `engine/rmr/src/rmr_unified_kernel.c` | `libvectra_core_accel.so` |
| `RmR_UnifiedKernel_ByteSwap32` | Encontrado | `engine/rmr/src/rmr_unified_kernel.c` | `libvectra_core_accel.so` |
| `RmR_UnifiedKernel_Rotl32` | Encontrado | `engine/rmr/src/rmr_unified_kernel.c` | `libvectra_core_accel.so` |
| `RmR_UnifiedKernel_Rotr32` | Encontrado | `engine/rmr/src/rmr_unified_kernel.c` | `libvectra_core_accel.so` |
| `RmR_UnifiedKernel_ArenaAlloc` | Encontrado | `engine/rmr/src/rmr_unified_kernel.c` | `libvectra_core_accel.so` |
| `RmR_UnifiedKernel_ArenaFree` | Encontrado | `engine/rmr/src/rmr_unified_kernel.c` | `libvectra_core_accel.so` |
| `RmR_UnifiedKernel_ArenaCopy` | Encontrado | `engine/rmr/src/rmr_unified_kernel.c` | `libvectra_core_accel.so` |
| `RmR_UnifiedKernel_ArenaXorChecksum` | Encontrado | `engine/rmr/src/rmr_unified_kernel.c` | `libvectra_core_accel.so` |
| `RmR_UnifiedKernel_ArenaFill` | Encontrado | `engine/rmr/src/rmr_unified_kernel.c` | `libvectra_core_accel.so` |
| `RmR_UnifiedKernel_ArenaWrite` | Encontrado | `engine/rmr/src/rmr_unified_kernel.c` | `libvectra_core_accel.so` |
| `RmR_UnifiedKernel_Ingest` | Encontrado | `engine/rmr/src/rmr_unified_kernel.c` | `libvectra_core_accel.so` |
| `RmR_UnifiedKernel_Process` | Encontrado | `engine/rmr/src/rmr_unified_kernel.c` | `libvectra_core_accel.so` |
| `RmR_UnifiedKernel_Route` | Encontrado | `engine/rmr/src/rmr_unified_kernel.c` | `libvectra_core_accel.so` |
| `RmR_UnifiedKernel_Verify` | Encontrado | `engine/rmr/src/rmr_unified_kernel.c` | `libvectra_core_accel.so` |
| `RmR_UnifiedKernel_Audit` | Encontrado | `engine/rmr/src/rmr_unified_kernel.c` | `libvectra_core_accel.so` |

### Famílias pedidas sem chamada JNI direta em `vectra_core_accel.c`

| Família/símbolo solicitado | Resultado no JNI direto | Observação de linkagem |
|---|---|---|
| `rmr_kernel_*` | Não encontrado no JNI direto | Se introduzido no futuro sem C local, depender de `librmr_unified_kernel` externa |
| `bitraf_*` | Não encontrado no JNI direto | Hoje é dependência transitiva interna de `rmr_unified_kernel.c` |
| `RmR_HW_Detect` | Não encontrado no JNI direto | Usado internamente no core unificado e implementado em `engine/rmr/src/rmr_hw_detect.c` |
| `RmR_CRC32C` | Não encontrado no JNI direto | Usado internamente no core unificado e implementado em `engine/rmr/src/rmr_policy_kernel.c` |

### Controle de dependência externa
- Símbolos `RmR_UnifiedKernel_*` sem implementação C local (caso apareçam em regressões futuras) devem ser marcados como **dependência externa / artefato separado**.
- Nome de target/biblioteca esperado: `librmr_unified_kernel.so` (Android) ou `librmr_unified_kernel.a` (link estático).
