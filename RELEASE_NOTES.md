# Release Notes — 3.6.6

## Resumo executivo
Este release endurece o runtime contra travamentos operacionais de execução/logs, introduz supervisão determinística de processos com failover real e atualiza permissões/storage para Android moderno.

## Principais mudanças
1. **Anti-deadlock/anti-zumbi/anti-flood** em captura de logs.
2. **ShellExecutor resiliente** com timeout, cancelamento e stdout+stderr bounded.
3. **ProcessSupervisor** com policy START→VERIFY→RUN→DEGRADED→FAILOVER→STOP.
4. **Storage Android 10–15** com Scoped Storage + SAF.
5. **Audit Ledger** rotativo com trilha operacional.

## Riscos e mitigação
- **Risco:** truncamento de logs sob flood.  
  **Mitigação:** design intencional para proteger UI/memória; contador de drop + auditoria.
- **Risco:** ambientes antigos sem suporte pleno de APIs modernas.  
  **Mitigação:** fallback legado para permissão de escrita.

## Validação sugerida
- Build debug/release.
- Testes unitários de componentes de pressão.
- Teste manual de flood com confirmação de responsividade.


## BITOMEGA (integração inicial)
- Inclusão do módulo BITOMEGA no engine nativo, com API em `engine/rmr/include/bitomega.h` e transição determinística em `engine/rmr/src/bitomega.c`.
- Impacto de build: novos fontes C adicionados aos alvos CMake `rmr` (root) e `vectra_core_accel` (JNI Android).
- Impacto esperado em runtime/performance: custo por transição de baixa ordem e previsível; melhora na governança/auditoria de estado sem alterar o caminho crítico de módulos que ainda não consomem BITOMEGA diretamente.
- Compatibilidade: integração aditiva e retrocompatível, preservando comportamento dos módulos existentes até o acoplamento funcional completo.
