# bug/core/FILES_MAP.md

Inventário específico de `bug/core/` com classificação funcional e status.

## Java (interface e orquestração)

| Arquivo | Função | Status |
|---|---|---|
| `RafaeliaKernel.java` | Controlador do ciclo do kernel e coordenação de módulos core. | Ativo |
| `NativeFastPath.java` | Interface JNI para fast path nativo e contrato de hardware. | Ativo |
| `LowLevelBridge.java` | Bridge JNI com fallback em software para operações low-level. | Ativo |

## C (núcleo nativo)

| Arquivo | Função | Status |
|---|---|---|
| `rmr_unified_kernel.c` | Núcleo unificado de estado/capacidades do RMR. | Ativo |
| `rmr_hw_detect.c` | Detecção multi-arquitetura e capacidades de hardware. | Ativo |
| `rmr_casm_bridge.c` | Dispatcher C↔ASM para rotas aceleradas. | Ativo |
| `rmr_ll_ops.c` | Operações low-level de trace/hash/triple. | Ativo |
| `rmr_ll_tuning.c` | Ajustes de tuning/runtime para caminhos low-level. | Ativo |
| `rmr_cycles.c` | Funções de ciclos, frequência e validações temporais. | Ativo |
| `rmr_math_fabric.c` | Rotinas matemáticas/fabric de suporte ao core. | Ativo |
| `rmr_policy_kernel.c` | Política de coerência/ética de decisão do kernel. | Ativo |
| `bitraf.c` | Implementação de ring/roteamento BITRAF. | Espelho |
| `zipraf_core_bridge.c` | Adaptador ZIPRAF para layout/contrato RMR. | Espelho |
| `zipraf_jni.c` | Bridge JNI de compatibilidade para `ZiprafEngine`. | Evidência |
| `vectra_cpu_safe.c` | Wrapper Android-safe de detecção de CPU. | Evidência |

## ASM (interoperabilidade por arquitetura)

| Arquivo | Função | Status |
|---|---|---|
| `rmr_casm_arm64.S` | Primitivas ARM64 (CRC/phi-fold). | Ativo |
| `rmr_casm_x86_64.S` | Primitivas x86_64 (SSE4.2/phi-fold). | Ativo |
| `rmr_casm_riscv64.S` | Stubs/ponte para RISC-V 64. | Legado |

## Headers

| Arquivo | Função | Status |
|---|---|---|
| `rmr_unified_kernel.h` | Contrato principal entre JNI, C e ASM. | Ativo |
| `rmr_lowlevel.h` | Contrato das operações low-level. | Ativo |
| `rmr_policy_kernel.h` | Contrato da política do kernel. | Ativo |

## Documentação local

| Arquivo | Função | Status |
|---|---|---|
| `README.md` | Guia canônico de navegação do diretório. | Canônico |
| `FILES_MAP.md` | Inventário local com classificação por status. | Canônico |
| `NUCLEUS_README.md` | Documento descritivo original do núcleo. | Espelho |
| `1.md` | Placeholder histórico sem conteúdo técnico efetivo. | Evidência |
