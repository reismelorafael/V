# Limitações e Próximos Passos

## Limitações atuais
- Δ ainda é heurístico (determinístico, porém baseado em limiares estáticos).
- Invariantes são mínimos e focados em segurança semântica básica.
- Integração no build foi realizada, mas integração funcional completa no runtime ainda é incremental.
- Sem geração automática da figura de transição no CI.

## Próximos passos (ordem)
1) Gerar o grafo automaticamente (Graphviz/Mermaid no CI).
2) Criar smoke test + benchmark automatizados e salvar CSV/log por arquitetura.
3) Plugar BITΩ no `rmr_policy_kernel`/`rmr_unified_kernel` como “estado do sistema”.
4) Produzir relatório custo vs benefício (latência, estabilidade, compatibilidade).
5) Evoluir de limiares fixos para calibração orientada por perfil de carga.
