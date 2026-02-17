# bug/

Camada canônica de gestão de falhas do Vectras VM Android: diagnóstico técnico, rastreabilidade de código-fonte, priorização e execução de hotfix.

## Escopo operacional
- Consolidar bugs conhecidos em artefatos auditáveis.
- Ligar cada bug a arquivos reais do código-fonte e aos testes de regressão.
- Separar backlog priorizado (`prioridade/`), execução ativa (`fazer hotfix/`) e histórico concluído (`feito/`).

## Estrutura de diretórios
- `issues/`: issues técnicas atômicas (uma falha por documento) com arquivo alvo, impacto e critério de aceite.
- `prioridade/`: material de triagem e ordenação de urgência.
- `fazer hotfix/`: fila de implementação imediata.
- `feito/`: destino de documentação encerrada após validação.

## Mapa de código-fonte validado para o ciclo de bugs
Principais alvos já referenciados pelos issues desta pasta:
- `terminal-emulator/src/main/java/com/termux/terminal/TerminalRow.java`
- `app/src/main/java/com/vectras/qemu/MainVNCActivity.java`
- `app/src/main/java/com/vectras/qemu/utils/FileUtils.java`

Testes de regressão associados:
- `terminal-emulator/src/test/java/com/termux/terminal/TerminalRowTest.java`
- `app/src/test/java/com/vectras/qemu/MainVNCActivityDispatchKeyEventTest.java`
- `app/src/test/java/com/vectras/qemu/utils/FileUtilsOpenModeTest.java`
- `app/src/test/java/com/vectras/qemu/utils/FileUtilsPathReplaceTest.java`

## Fluxo recomendado
1. Registrar bug em `issues/` com critérios objetivos.
2. Priorizar em `prioridade/` com risco/impacto.
3. Executar em `fazer hotfix/` com patch + teste.
4. Mover artefatos fechados para `feito/` mantendo trilha de auditoria.

## Navegação
- [FILES_MAP.md](FILES_MAP.md)
- [SOURCE_CODE_TRACEABILITY.md](SOURCE_CODE_TRACEABILITY.md)
- [issues/README.md](issues/README.md)
- [prioridade/readme.md](prioridade/readme.md)
- [fazer hotfix/readme.md](fazer hotfix/readme.md)
- [feito/README.md](feito/README.md)
