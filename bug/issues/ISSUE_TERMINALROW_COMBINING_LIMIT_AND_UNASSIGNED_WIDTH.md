# Issue: TerminalRow precisa limitar combining chars por célula e tratar UNASSIGNED width=0

## Arquivo alvo
- `terminal-emulator/src/main/java/com/termux/terminal/TerminalRow.java`

## Problema
- `setChar()` acumulava combining marks sem limite por célula, aumentando `mText` indefinidamente em cenários patológicos.
- Code points `Character.UNASSIGNED` com `WcWidth.width()==0` eram tratados como combining, embora não sejam diacríticos de composição.

## Impacto
- Crescimento excessivo de memória por coluna.
- Colunas com comportamento de largura inconsistente para caracteres não atribuídos.

## Critério de aceite
- Limite explícito e determinístico de combining marks por célula.
- `UNASSIGNED` com width zero deve ser tratado como largura imprimível (1) para inserção.
- Cobertura de regressão em `terminal-emulator/src/test/.../TerminalRowTest.java`.

## Status de verificação
- **Situação:** Corrigido no código-fonte.
- **Evidência de implementação:**
  - Constante `MAX_COMBINING_CODE_POINTS_PER_CELL = 5` e bloqueio de inserção quando excede limite.
  - `getDisplayWidthForCodePoint()` força width=1 para `Character.UNASSIGNED` com width zero.
- **Evidência de teste existente:**
  - `testCombiningCharacterCountIsCappedPerCell()`.
  - `testUnassignedWidthZeroCodePointDoesNotBehaveAsCombining()`.
