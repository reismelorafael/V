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
