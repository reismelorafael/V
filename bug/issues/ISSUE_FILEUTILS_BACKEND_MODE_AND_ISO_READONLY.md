# Issue: FileUtils deve receber modo do backend e aplicar read-only real para ISO

## Arquivo alvo
- `app/src/main/java/com/vectras/qemu/utils/FileUtils.java`

## Problema
- `get_fd()` não recebia modo de abertura do backend e aplicava heurística local incompleta.
- ISOs precisam forçar read-only tanto para `content://` quanto para caminho de arquivo local.

## Impacto
- Inconsistência entre intenção do backend e modo efetivo.
- Risco de abertura indevida em escrita para mídia ISO.

## Critério de aceite
- Novo caminho com `backendMode` explícito.
- Mapeamento de modo para `ContentResolver` e `ParcelFileDescriptor`.
- Precedência de read-only para `.iso` em todos os fluxos.
- Cobertura de regressão em testes unitários do módulo `app`.

## Status de verificação
- **Situação:** Corrigido no código-fonte.
- **Evidência de implementação:**
  - `get_fd(context, path, backendMode)` presente e propagado.
  - `resolveContentOpenMode(...)` força `"r"` para `.iso`.
  - `resolveParcelOpenMode(...)` força `MODE_READ_ONLY` para `.iso`.
  - Wrappers legados em `com.vectras.qemu.utils.FileUtils` delegam para implementação canônica em `com.vectras.vm.utils.FileUtils`.
- **Evidência de teste existente:**
  - `resolveContentOpenMode_shouldHonorSupportedModesAndFallbackSafely()`.
  - `resolveParcelOpenMode_shouldRespectIsoAndReadOnlyBackendMode()`.
  - `openModeWrappers_shouldMatchVmCanonicalContract()`.
