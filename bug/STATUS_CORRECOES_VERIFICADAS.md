# bug/STATUS_CORRECOES_VERIFICADAS.md

Verificação objetiva do que já está corrigido entre os issues ativos de `bug/issues/`.

## Resultado consolidado
- **Total de issues verificadas:** 3
- **Corrigidas no código-fonte:** 3
- **Pendentes:** 0

## Matriz de verificação

| Issue | Status | Evidência de código-fonte | Evidência de teste |
|---|---|---|---|
| `ISSUE_TERMINALROW_COMBINING_LIMIT_AND_UNASSIGNED_WIDTH.md` | Corrigido | `MAX_COMBINING_CODE_POINTS_PER_CELL`, limite de combining em `setChar()`, regra de width para `UNASSIGNED` em `getDisplayWidthForCodePoint()`. | `testCombiningCharacterCountIsCappedPerCell()`, `testUnassignedWidthZeroCodePointDoesNotBehaveAsCombining()`. |
| `ISSUE_MAINVNCACTIVITY_DISPATCHKEYEVENT_IME_COMPOSITION.md` | Corrigido | `dispatchKeyEvent()` + `extractComposedText()` com suporte a `ACTION_MULTIPLE` com payload e filtro de `COMBINING_ACCENT`. | `MainVNCActivityDispatchKeyEventTest` (casos de payload, keycode conhecido e acento intermediário). |
| `ISSUE_FILEUTILS_BACKEND_MODE_AND_ISO_READONLY.md` | Corrigido | `get_fd(context, path, backendMode)` e resolução de modo com precedência read-only para `.iso`. | `FileUtilsOpenModeTest` em `com.vectras.vm.utils` e wrappers em `com.vectras.qemu.utils`. |

## Observação de execução
- Nesta execução, a validação de build/teste via Gradle não pôde ser concluída por incompatibilidade do runtime Java do ambiente (`Unsupported class file major version 66`).
- A verificação foi concluída por inspeção estática de implementação + cobertura de testes existentes no repositório.
