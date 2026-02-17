# bug/SOURCE_CODE_TRACEABILITY.md

Matriz de rastreabilidade entre documentação de bugs e código-fonte real do repositório.

## Matriz issue → código

| Issue | Arquivo(s) de código-fonte | Teste(s) associado(s) | Status documental |
|---|---|---|---|
| `ISSUE_TERMINALROW_COMBINING_LIMIT_AND_UNASSIGNED_WIDTH.md` | `terminal-emulator/src/main/java/com/termux/terminal/TerminalRow.java` | `terminal-emulator/src/test/java/com/termux/terminal/TerminalRowTest.java` | Mapeado |
| `ISSUE_MAINVNCACTIVITY_DISPATCHKEYEVENT_IME_COMPOSITION.md` | `app/src/main/java/com/vectras/qemu/MainVNCActivity.java` | `app/src/test/java/com/vectras/qemu/MainVNCActivityDispatchKeyEventTest.java` | Mapeado |
| `ISSUE_FILEUTILS_BACKEND_MODE_AND_ISO_READONLY.md` | `app/src/main/java/com/vectras/qemu/utils/FileUtils.java` | `app/src/test/java/com/vectras/qemu/utils/FileUtilsOpenModeTest.java`, `app/src/test/java/com/vectras/qemu/utils/FileUtilsPathReplaceTest.java` | Mapeado |

## Áreas de código com alta incidência de bug no histórico local
- Terminal/TTY: `terminal-emulator/src/main/java/com/termux/terminal/*`
- Entrada/IME/UI Android: `app/src/main/java/com/vectras/qemu/*`
- I/O e compatibilidade de arquivo/mídia: `app/src/main/java/com/vectras/qemu/utils/*`
- Núcleo de execução e política determinística: `app/src/main/java/com/vectras/vm/core/*`

## Política de atualização deste documento
- Sempre que um novo bug mencionar arquivo de produção, incluir linha na matriz.
- Sempre que houver teste de regressão criado/ajustado, referenciar o caminho completo.
- Ao concluir hotfix, manter referência histórica (não apagar entradas, apenas atualizar status em documentos de execução).
