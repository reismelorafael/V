# BITΩ Postdoc Pack (skeleton)
Este diretório é o “pacote acadêmico” do BITΩ, mantendo a linguagem formal e executável.

## Convenção oficial de nomes (fonte de verdade)
Os nomes esperados pelo processo de validação são os nomes **reais** publicados no
`BITOMEGA_OVERLAY__V1.zip`:

- `01_FORMALISM_BITOMEGA.md`
- `02_TRANSITION_GRAPH.md`
- `03_IMPLEMENTATION_MAP.md`
- `04_EXPERIMENTS.md`
- `05_RESULTS_TABLES.md`

Decisão adotada: **Estratégia A** (atualizar a checagem para os nomes atuais do ZIP).
Não existe etapa de renomeação pós-extração para os aliases
`01_FOUNDATIONS.md`, `02_METHODS.md`, `03_RESULTS.md`, `04_IMPL_DETAILS.md`,
`05_VALIDATION.md`.

## Validação local
Executar:

```bash
docs/bitomega_postdoc/validate_pack.sh
```

O script valida o conjunto completo do pacote (`00` até `06`) usando a mesma
convenção de nomes do ZIP e emite apenas **aviso** se encontrar aliases legados
no diretório.

## Ordem sugerida
1. 00_THESIS_OVERVIEW.md
2. 01_FORMALISM_BITOMEGA.md
3. 02_TRANSITION_GRAPH.md
4. 03_IMPLEMENTATION_MAP.md
5. 04_EXPERIMENTS.md
6. 05_RESULTS_TABLES.md
7. 06_LIMITATIONS_NEXT.md
