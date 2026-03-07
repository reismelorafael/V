# VECTRAS-VM-Android — REALITY DIFF (análise profunda)
> Confronto entre `addthis/*.md` e o estado real dos arquivos do repo.
> Método: leitura direta de paths reais + busca por padrões exatos.

---

## Resumo duro (estado atual)
- Bugs reportados em `addthis/VECTRAS_BUGS.md`: **15**
- Confirmados como **ainda abertos** no código atual: **13**
- Já resolvidos/parcialmente resolvidos: **2**

**Já resolvidos/parcialmente resolvidos**
- BUG-15 (`make run-selftest`) → target existe no `Makefile`.
- BUG-02 (`RmR_GpioPinStride`) → descrição do bug no doc não bate 100% com o código atual; hoje há retorno `1u` para x86/i386 e `8u` para ppc, então o problema real é de política/consistência de stride, não exatamente o `return RMR_ZERO_HW_ARCH_I386_U32` descrito no relatório.

---

## Matriz bug-a-bug (documento vs árvore real)

| Bug | Status atual | Evidência objetiva |
|---|---|---|
| BUG-01 typedef duplicado | ABERTO | `typedef u8/u32/u64` segue presente nos 7 headers-alvo |
| BUG-02 gpio stride | PARCIAL / DESALINHADO COM DOC | função existe, mas implementação atual difere da descrição textual original |
| BUG-03 `.c` em workflows | ABERTO | `.github/workflows/neon_simd_selftest.c` ainda existe |
| BUG-04 workflows duplicados | ABERTO | `android (1).yml`, `(2).yml`, `android-verified (1).yml` ainda existem |
| BUG-05 `VECTRA_HAS_CASM_MARKER` root | ABERTO | não há definição explícita de compile define por target no root |
| BUG-06 baremetal_compat no app JNI | ABERTO | `rmr_baremetal_compat.c` incluído em targets JNI/app |
| BUG-07 weak symbols no bridge | ABERTO | faltam `__attribute__((weak))` nas declarações citadas |
| BUG-08 `RMR_JNI_BUILD` no bench target | ABERTO/PARCIAL | há `-DRMR_JNI_BUILD=1` no app CMake, mas issue do root bench permanece na proposta |
| BUG-09 `#error` no NEON ARM64 | ABERTO | `#error "__aarch64__ build requires <arm_neon.h>"` ainda existe |
| BUG-10 `.arch armv8-a+crc` | ABERTO | cabeçalho ASM inicia fixo com `.arch armv8-a+crc` |
| BUG-11 include guard de tipos | ABERTO | `rmr_hw_detect.h` mantém typedefs locais em vez de `rmr_types.h` |
| BUG-12 RISCV64 root vs app | ABERTO | root trata riscv64, app mantém roadmap inativo; inconsistência persiste |
| BUG-13 OUTPUT_NAME bitraf | ABERTO | static/shared com mesmo `OUTPUT_NAME bitraf` |
| BUG-14 include explícito benchmark | ABERTO | `bench/src/rmr_benchmark_main.c` não inclui `rmr_types.h` no topo |
| BUG-15 make run-selftest | RESOLVIDO | target existe no Makefile e é usado no `ci.yml` |

---

## Evidências por comando (audit trail)

### 1) Arquivos indevidos em workflows
```bash
rg --files .github/workflows | rg 'neon_simd_selftest.c|android \(1\)\.yml|android \(2\)\.yml|android-verified \(1\)\.yml'
```
Resultado: 4 arquivos encontrados (BUG-03/04 abertos).

### 2) Typedefs duplicados nos headers
```bash
rg -n "typedef unsigned (char|int|long long) u(8|32|64);" engine/rmr/include/rmr_hw_detect.h engine/rmr/include/rmr_bench.h engine/rmr/include/rmr_apk_module.h engine/rmr/include/rmr_isorf.h engine/rmr/include/rmr_math_fabric.h engine/rmr/include/rmr_cycles.h engine/rmr/include/rmr_bench_suite.h
```
Resultado: múltiplas ocorrências em todos os arquivos listados (BUG-01/11 abertos).

### 3) Situação real de `RmR_GpioPinStride`
```bash
rg -n "RmR_GpioPinStride|RMR_ZERO_HW_ARCH_I386_U32|return 8u" engine/rmr/src/rmr_hw_detect.c
```
Resultado: implementação não igual ao trecho documentado originalmente; precisa revisão de política de stride por arquitetura.

### 4) Root CMake e marcador CASM
```bash
rg -n "VECTRA_HAS_CASM_MARKER|RMR_HAS_CASM|rmr_apply_casm_marker|add_compile_definitions\(" CMakeLists.txt
```
Resultado: há `RMR_HAS_CASM`, mas não a aplicação explícita por target conforme proposta do addthis.

### 5) App CMake e conflito baremetal/JNI
```bash
rg -n "rmr_baremetal_compat.c|rmr_policy_static|RMR_JNI_BUILD" app/src/main/cpp/CMakeLists.txt
```
Resultado: `rmr_baremetal_compat.c` ainda entra em builds JNI/app.

### 6) Bridge ASM sem weak guard
```bash
rg -n "__attribute__\(\(weak\)\)|rmr_casm_xor_fold32_x86_64|rmr_casm_phi_step_arm64" engine/rmr/src/rmr_casm_bridge.c
```
Resultado: declarações presentes sem weak nas linhas esperadas.

### 7) NEON ARM64
```bash
rg -n "#error|RMR_NEON_AVAILABLE|arm_neon.h|fallback" engine/rmr/src/rmr_neon_simd.c
```
Resultado: ainda contém `#error` em caminho AArch64 sem header.

### 8) ASM ARM64 header
```bash
sed -n '1,40p' engine/rmr/interop/rmr_casm_arm64.S
```
Resultado: `.arch armv8-a+crc` fixo.

### 9) OUTPUT_NAME bitraf
```bash
rg -n "bitraf_static|bitraf_shared|OUTPUT_NAME" CMakeLists.txt
```
Resultado: ambos targets usam `OUTPUT_NAME bitraf`.

### 10) Benchmark includes
```bash
sed -n '1,40p' bench/src/rmr_benchmark_main.c
```
Resultado: sem include explícito de `rmr_types.h` no topo.

### 11) CI selftest target
```bash
rg -n "^run-selftest:|run-selftest" Makefile .github/workflows/ci.yml
```
Resultado: target `run-selftest` existe e é invocado no CI.

---

## Conclusão operacional
- A base `addthis` está **majoritariamente correta na intenção**, mas contém pelo menos 1 item já resolvido (BUG-15) e 1 item que precisa ser reescrito com maior precisão técnica (BUG-02).
- Próxima ação correta: atualizar `VECTRAS_BUGS.md` para marcar BUG-15 como fechado e refinar BUG-02 para refletir a implementação real da função no código atual.
