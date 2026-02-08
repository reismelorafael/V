# WHITEPAPER — RAFAELIA Engine (Resumo Executivo)

## 1. Problema
Ambientes Android/VM misturam runtime, UI e desempenho low-level no mesmo espaço de evolução, dificultando produto vendável, benchmarking reproduzível e proteção de IP.

## 2. Proposta
Separar o núcleo autoral **RMR/Bitraf** como engine independente com:
- build mínimo (Makefile/CMake),
- benchmark padronizado (CSV/JSON + mediana/p95),
- runtime Android como showcase desacoplado.

## 3. Arquitetura
Camadas:
1. `engine/rmr/include` (contratos C)
2. `engine/rmr/src` (núcleo low-level)
3. `bench/` (suite executável + scripts)
4. `demo_cli/` (execução mínima)
5. `runtime/showcase/` (ponte para app Android existente)

## 4. Diferenciais
- Determinismo orientado a ciclos.
- Detecção de hardware nativa.
- Suite de 50 métricas de benchmark.
- Sem dependências pesadas para engine C.

## 5. Segurança e integridade
- Execução local/offline para benchmark base.
- Telemetria separada do runtime de app.
- Menor superfície de risco ao isolar engine.

## 6. Resultados da reestruturação
- Core movido para árvore dedicada (`engine/rmr`).
- Build reproduzível em Linux (cc/make/cmake).
- Bench com saídas formais para comparação histórica.

## 7. Roadmap curto
- API estável `v1` para integração JNI.
- Baselines por arquitetura (x86_64/aarch64).
- Publicação de artefatos benchmark assinados.

## 8. Modelo de produto
- **Produto 1 (Engine):** licenciamento B2B de benchmark/integridade.
- **Produto 2 (Runtime):** app showcase para validação de mercado.

## 9. Métricas de sucesso
- Reprodutibilidade de benchmark (p95 controlado).
- Tempo de integração em terceiros.
- Adoção da API de engine por múltiplos runtimes.

## 10. Conclusão
A separação Engine-first reduz acoplamento, aumenta clareza de IP e acelera comercialização técnica.
