# docs/

Camada técnica central de documentação do Vectras VM Android.

## Estrutura de navegação em 5 níveis
- Nível 1 (institucional): [`../README.md`](../README.md)
- Nível 2 (índice global): [`../DOC_INDEX.md`](../DOC_INDEX.md)
- Nível 3 (domínio técnico em docs): [`README.md`](README.md)
- Nível 4 (navegação por audiência/domínio): [`navigation/INDEX.md`](navigation/INDEX.md)
- Nível 5 (documento final rastreável): arquivos específicos em `docs/` e `docs/navigation/`.

## Guias estruturais desta revisão
- [`THREE_LAYER_ANALYSIS.md`](THREE_LAYER_ANALYSIS.md)
- [`ROOT_FILE_CHAIN.md`](ROOT_FILE_CHAIN.md)
- [`../DOC_INDEX.md`](../DOC_INDEX.md)

## Eixos técnicos especializados
- Arquitetura: `ARCHITECTURE.md`, `API.md`, `RAFAELIA_COHESION_ENTERPRISE_STACK.md`
- Operação e benchmark: `OPERATIONS.md`, `BENCHMARKS.md`, `BENCHMARK_MANAGER.md`
- Build e ambiente: `BUILD_ENV_ALIGNMENT.md`
- Qualidade e conformidade: `SECURITY.md`, `LEGAL_AND_LICENSES.md`, `SOURCE_TRACEABILITY_MATRIX.md`, `IP_MAP.md`
- Fullstack (código-fonte ponta a ponta): `FULLSTACK_SOURCE_MAP.md`
- Navegação por público: `navigation/INDEX.md` e derivados
- Inovação e autoria técnica (navegação): `navigation/TECHNOLOGY_INNOVATION_AUTHORSHIP.md`
- Estado atual do ciclo do projeto: `../PROJECT_STATE.md`

## Cadeia de comando de validação documental
```bash
find docs -maxdepth 2 -type f | sort
sed -n '1,120p' docs/THREE_LAYER_ANALYSIS.md
sed -n '1,120p' docs/ROOT_FILE_CHAIN.md
```

## Metadados
- Versão do documento: 1.2
- Última atualização: 2026-02-18
- Commit de referência: `5f551f0`
- Domínio de código coberto: Portal documental transversal para app, engine, tools, web e runtime.
