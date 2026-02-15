# Vectras VM Android

> Plataforma Android de virtualização com base híbrida (Android + C/C++ + Rust), foco em determinismo operacional, rastreabilidade e governança documental.

## Abstract
Esta revisão consolida a documentação em três camadas por diretório (propósito, estrutura e arquivo-a-arquivo), conectando documentação raiz, mapas locais e cadeia de comandos de inspeção. O objetivo é eliminar lacunas entre arquivos soltos, módulos ativos e documentação técnica, com navegação formal e auditável.


## Governança e estado — navegação rápida
- Estado do projeto: [`PROJECT_STATE.md`](PROJECT_STATE.md)
- Histórico de mudanças: [`CHANGELOG.md`](CHANGELOG.md)
- Notas de release: [`RELEASE_NOTES.md`](RELEASE_NOTES.md)
- Índice documental: [`DOC_INDEX.md`](DOC_INDEX.md)
- Avisos de terceiros/licenciamento: [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)
- Referência do runtime: [`VECTRA_CORE.md`](VECTRA_CORE.md)
- Guia macro de documentação: [`VECTRAS_MEGAPROMPT_DOCS.md`](VECTRAS_MEGAPROMPT_DOCS.md)

## Histórico arquivado (raiz)
- [`archive/root-history/1.md`](archive/root-history/1.md)
- [`archive/root-history/ADVANCED_OPTIMIZATIONS.md`](archive/root-history/ADVANCED_OPTIMIZATIONS.md)
- [`archive/root-history/BENCHMARK_REFACTORING_SUMMARY.md`](archive/root-history/BENCHMARK_REFACTORING_SUMMARY.md)
- [`archive/root-history/IMPLEMENTATION_COMPLETE.md`](archive/root-history/IMPLEMENTATION_COMPLETE.md)
- [`archive/root-history/IMPLEMENTATION_SUMMARY.md`](archive/root-history/IMPLEMENTATION_SUMMARY.md)
- [`archive/root-history/VECTRAS_ANALYSIS_COMPLETE.md`](archive/root-history/VECTRAS_ANALYSIS_COMPLETE.md)
- [`archive/root-history/VECTRAS_DEEP_EVIDENCE.md`](archive/root-history/VECTRAS_DEEP_EVIDENCE.md)

## Dissertação analítica (modelo de 3 camadas)
1. **Camada 1 — Diretório**: define responsabilidade técnica de cada domínio.
2. **Camada 2 — Estrutura**: explicita subdiretórios e fronteiras de módulo.
3. **Camada 3 — Arquivos**: descreve cada arquivo com papel, ligação e comando de inspeção.

Referências estruturais:
- [`docs/THREE_LAYER_ANALYSIS.md`](docs/THREE_LAYER_ANALYSIS.md)
- [`docs/ROOT_FILE_CHAIN.md`](docs/ROOT_FILE_CHAIN.md)

## Mapa de diretórios (com READMEs + FILES_MAP)
| Diretório | README | Mapa de Arquivos |
|---|---|---|
| `app/` | [app/README.md](app/README.md) | [app/FILES_MAP.md](app/FILES_MAP.md) |
| `engine/` | [engine/README.md](engine/README.md) | [engine/FILES_MAP.md](engine/FILES_MAP.md) |
| `terminal-emulator/` | [terminal-emulator/README.md](terminal-emulator/README.md) | [terminal-emulator/FILES_MAP.md](terminal-emulator/FILES_MAP.md) |
| `terminal-view/` | [terminal-view/README.md](terminal-view/README.md) | [terminal-view/FILES_MAP.md](terminal-view/FILES_MAP.md) |
| `shell-loader/` | [shell-loader/README.md](shell-loader/README.md) | [shell-loader/FILES_MAP.md](shell-loader/FILES_MAP.md) |
| `bench/` | [bench/README.md](bench/README.md) | [bench/FILES_MAP.md](bench/FILES_MAP.md) |
| `bug/` | [bug/README.md](bug/README.md) | [bug/FILES_MAP.md](bug/FILES_MAP.md) |
| `demo_cli/` | [demo_cli/README.md](demo_cli/README.md) | [demo_cli/FILES_MAP.md](demo_cli/FILES_MAP.md) |
| `tools/` | [tools/README.md](tools/README.md) | [tools/FILES_MAP.md](tools/FILES_MAP.md) |
| `docs/` | [docs/README.md](docs/README.md) | [docs/FILES_MAP.md](docs/FILES_MAP.md) |
| `reports/` | [reports/README.md](reports/README.md) | [reports/FILES_MAP.md](reports/FILES_MAP.md) |
| `resources/` | [resources/README.md](resources/README.md) | [resources/FILES_MAP.md](resources/FILES_MAP.md) |
| `runtime/` | [runtime/README.md](runtime/README.md) | [runtime/FILES_MAP.md](runtime/FILES_MAP.md) |
| `web/` | [web/README.md](web/README.md) | [web/FILES_MAP.md](web/FILES_MAP.md) |
| `archive/` | [archive/README.md](archive/README.md) | [archive/FILES_MAP.md](archive/FILES_MAP.md) |
| `fastlane/` | [fastlane/README.md](fastlane/README.md) | [fastlane/FILES_MAP.md](fastlane/FILES_MAP.md) |
| `gradle/` | [gradle/README.md](gradle/README.md) | [gradle/FILES_MAP.md](gradle/FILES_MAP.md) |
| `3dfx/` | [3dfx/README.md](3dfx/README.md) | [3dfx/FILES_MAP.md](3dfx/FILES_MAP.md) |


## Política de assinatura (`vectras.jks`)
- A chave `vectras.jks` **não deve permanecer versionada** no Git.
- A assinatura de **release** deve usar segredo de CI/cofre seguro (`VECTRAS_RELEASE_*` / `android.injected.signing.*`).
- Builds de `debug` usam apenas assinatura debug padrão do Android Gradle Plugin (não usar chave de release).
- Rotação recomendada: a cada 90 dias (ou imediatamente após incidente), com revogação e atualização de segredos no CI.
- Acesso mínimo: apenas mantenedores responsáveis por release e conta de automação do CI.

## Cadeia de comando recomendada
```bash
git ls-files
find . -maxdepth 2 -type d | sort
./gradlew verifyRepoFileDependencies
```

## Índices
- [DOC_INDEX.md](DOC_INDEX.md)
- [docs/README.md](docs/README.md)

## Setup rápido de build
- Copie `local.properties.example` para `local.properties` e ajuste `sdk.dir`.
- Ajuste versões via `gradle.properties` (`COMPILE_API`, `TOOLS_VERSION`, `JAVA_LANGUAGE_VERSION`, `CMAKE_VERSION`, `NDK_VERSION`).
- Para override pontual, use `-P` no comando Gradle.

## Referência rápida de bugs
- Escopo e relação com os demais domínios: [`bug/README.md`](bug/README.md)
- Mapa arquivo-a-arquivo do domínio de bugs: [`bug/FILES_MAP.md`](bug/FILES_MAP.md)


## Canal oficial de comunidade e suporte
- Canal oficial neutro: https://vectras.vercel.app/community.html
- Para notícias, suporte e feedback, use sempre o canal oficial acima.
