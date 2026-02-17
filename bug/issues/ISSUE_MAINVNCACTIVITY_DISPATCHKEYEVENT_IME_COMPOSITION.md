# Issue: Revisar dispatchKeyEvent para IME/composição/acento (ACTION_MULTIPLE)

## Arquivo alvo
- `app/src/main/java/com/vectras/qemu/MainVNCActivity.java`

## Problema
- Fluxo de `dispatchKeyEvent()` só cobria `ACTION_MULTIPLE + KEYCODE_UNKNOWN` e não tratava adequadamente estados intermediários de composição (dead keys/combining accent).

## Impacto
- Acentos/dead keys podem ser encaminhados de forma incorreta.
- Perda de texto composto em alguns IMEs.

## Critério de aceite
- Encaminhar `event.getCharacters()` quando vier payload composto válido em `ACTION_MULTIPLE`.
- Ignorar eventos intermediários de acento combinante sem texto final.
- Manter fallback estável para `super.dispatchKeyEvent(event)`.
- Cobertura de regressão em testes unitários do módulo `app`.

## Status de verificação
- **Situação:** Corrigido no código-fonte.
- **Evidência de implementação:**
  - `dispatchKeyEvent()` usa `extractComposedText(...)` e envia texto composto via `vncCanvas.sendText(...)`.
  - `extractComposedText(...)` retorna `characters` em `ACTION_MULTIPLE` quando payload existe.
  - Eventos intermediários com `KeyCharacterMap.COMBINING_ACCENT` retornam `null`.
- **Evidência de teste existente:**
  - `extractComposedText_shouldReturnCharactersForActionMultiplePayload()`.
  - `extractComposedText_shouldReturnCharactersForActionMultipleEvenWithKnownKeyCode()`.
  - `extractComposedText_shouldIgnoreCombiningAccentIntermediateKey()`.
