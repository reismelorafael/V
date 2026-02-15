# app/

## Camada 1 — Propósito do diretório
Módulo Android principal com UI, runtime e testes unitários.

## Camada 2 — Estrutura (até 3 níveis)
- Nível 1: `app/`
- Nível 2: `src/`
- Nível 3: detalhamento por arquivo em [`FILES_MAP.md`](FILES_MAP.md).

## Camada 3 — Arquivos e vínculos
- Catálogo completo: [`FILES_MAP.md`](FILES_MAP.md)
- Contexto global de camadas: [`docs/THREE_LAYER_ANALYSIS.md`](../docs/THREE_LAYER_ANALYSIS.md)

## Cadeia de comando (lógica de inspeção)
```bash
find app -maxdepth 3 -type d | sort
sed -n '1,120p' app/FILES_MAP.md
```

## Fontes nativas obrigatórias do APK

### Obrigatório
- `app/src/main/cpp/vectra_core_accel.c`  
  Motivo: expõe os símbolos JNI consumidos pelo app (`Java_com_vectras_vm_core_NativeFastPath_*` e `Java_com_vectras_vm_core_NativeLogcatBridge_*`) e faz o encadeamento para `rmr_jni_kernel_*`/`RmR_UnifiedKernel_*`.
- `engine/rmr/src/rmr_unified_kernel.c`  
  Motivo: implementação de `RmR_UnifiedKernel_Init`, `RmR_UnifiedKernel_Shutdown`, `RmR_UnifiedKernel_Ingest`, `RmR_UnifiedKernel_Process`, `RmR_UnifiedKernel_Route`, `RmR_UnifiedKernel_Verify`, `RmR_UnifiedKernel_Audit`, `RmR_UnifiedKernel_Copy` e utilitários (`Popcount32`, `ByteSwap32`, rotações, arena).
- `engine/rmr/src/rmr_hw_detect.c`  
  Motivo: provê `RmR_HW_Detect`, usado por `RmR_UnifiedKernel_Detect` (capabilities de hardware retornadas no caminho JNI).

### Obrigatório quando `rmr_unified_kernel.c` NÃO for linkado via target externo `rmr`
- `engine/rmr/src/rmr_corelib.c`  
  Motivo: provê `rmr_mem_set` e `rmr_mem_copy`, chamados diretamente em `rmr_unified_kernel.c`.
- `engine/rmr/src/rmr_policy_kernel.c`  
  Motivo: provê `RmR_CRC32C` e `RmR_EntropyEstimateMilli`, usados no pipeline `Ingest`/`Verify`.
- `engine/rmr/src/bitraf.c`  
  Motivo: provê `bitraf_hash`, usado no caminho de compatibilidade legacy dentro de `rmr_unified_kernel.c`.

### Opcional
- `engine/rmr/src/rmr_cycles.c`, `engine/rmr/src/rmr_bench.c`, `engine/rmr/src/rmr_bench_suite.c`, `engine/rmr/src/rmr_isorf.c`, `engine/rmr/src/rmr_apk_module.c`, `engine/rmr/src/rmr_qemu_bridge.c`, `engine/rmr/src/rmr_math_fabric.c`  
  Motivo: agregam benchmark, bridges e módulos auxiliares; não são necessários para resolver os JNI críticos do APK em `vectra_core_accel.c`.

### Bibliotecas/targets externos para `RmR_UnifiedKernel_*`
- Se a build do APK não compilar todos os `.c` acima localmente, o link deve trazer um target que exporte esses símbolos (ex.: `rmr` do `CMakeLists.txt` raiz).
- No mínimo, o link final precisa resolver `bitraf_hash`, `RmR_CRC32C`, `RmR_EntropyEstimateMilli`, `rmr_mem_set`, `rmr_mem_copy` e `RmR_HW_Detect`, além da família `RmR_UnifiedKernel_*`.

### Manutenção obrigatória
- Sempre que um novo símbolo JNI for adicionado em `app/src/main/cpp/vectra_core_accel.c`, atualize esta seção para refletir:
  1) o `.c` que fornece o símbolo JNI;
  2) os `.c`/targets que fornecem as dependências nativas chamadas por ele;
  3) a classificação entre **obrigatório** e **opcional**.
