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
  - Motivo: exporta os símbolos JNI consumidos pelo app (`Java_com_vectras_vm_core_NativeFastPath_*` e `Java_com_vectras_vm_core_NativeLogcatBridge_*`) e faz a ponte direta para o núcleo `RmR_UnifiedKernel_*`.
- `engine/rmr/src/rmr_unified_kernel.c`
  - Motivo: provê os símbolos-chave `RmR_UnifiedKernel_*` usados pelo bridge JNI, incluindo init/shutdown, ingest/process/route/verify/audit, operações de arena e utilitários bitwise/checksum.
- `engine/rmr/src/rmr_hw_detect.c`
  - Motivo: provê `RmR_HW_Detect`, dependência direta de `RmR_UnifiedKernel_Detect`/`RmR_UnifiedKernel_Init` para montar o contrato de capacidades de hardware retornado ao app.

### Opcional
- Não há `.c` opcionais no target atual do APK (`vectra_core_accel`): os três arquivos acima são todos os sources compilados no `add_library`.

### Bibliotecas/targets externos para `RmR_UnifiedKernel_*`
- Target local obrigatório no APK: `vectra_core_accel` (biblioteca `SHARED` gerada pelo CMake do app).
- Biblioteca do NDK necessária no link: `log` (`find_library(log-lib log)` + `target_link_libraries(vectra_core_accel ${log-lib})`).
- Runtime C/pthreads vem do toolchain Android (não há target externo adicional fora do C local para resolver `RmR_UnifiedKernel_*`; os símbolos são resolvidos pelos `.c` listados acima).

### Manutenção
- Sempre que entrar um novo símbolo JNI em `app/src/main/cpp/vectra_core_accel.c`, atualizar esta seção para manter o mapeamento “símbolo JNI -> fonte `.c` obrigatória” e o inventário de dependências de link do APK.
