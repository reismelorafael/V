# Vectras VM Android

Este repositório mantém o aplicativo **Vectras VM** para Android, baseado em QEMU, com foco em execução de máquinas virtuais em dispositivos móveis.

## Origem do projeto

Este repositório é um fork do projeto original [xoureldeen/Vectras-VM-Android](https://github.com/xoureldeen/Vectras-VM-Android). O crédito de autoria do conceito e da base original permanece com o autor original.

## Objetivo

Fornecer uma aplicação Android para gerenciamento e execução de VMs, com integração de componentes nativos, terminal embarcado e fluxo de configuração para imagens de sistemas operacionais compatíveis.

## Estrutura atual do código

A estrutura abaixo reflete os módulos configurados no Gradle (`settings.gradle`):

- `app`: aplicativo Android principal (UI, gerenciamento de VM, utilitários, integrações, benchmark e componentes RAFAELIA).
- `terminal-emulator`: biblioteca de emulação de terminal.
- `terminal-view`: camada de visualização do terminal.
- `shell-loader` e `shell-loader:stub`: suporte ao carregamento de shell.

Outros diretórios relevantes:

- `docs/`: documentação técnica e operacional.
- `app/src/main/cpp/`: código nativo compilado com CMake.
- `resources/`: ativos visuais e recursos auxiliares.
- `fastlane/`: metadados de publicação.

## Estado técnico resumido

- Linguagens principais: Java e Kotlin.
- Build Android: AGP 8.5.2.
- Kotlin Gradle plugin: 2.0.21.
- `compileSdk`: 36.
- `minSdk`: 23.
- `targetSdk`: 28.
- Versão do app (`app/build.gradle`): `3.6.5`.

## Documentação

A documentação foi reorganizada para refletir o estado atual do repositório:

- Índice principal: [docs/README.md](docs/README.md)
- Índice rápido: [DOC_INDEX.md](DOC_INDEX.md)
- Arquitetura: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
- Matriz de rastreabilidade: [docs/SOURCE_TRACEABILITY_MATRIX.md](docs/SOURCE_TRACEABILITY_MATRIX.md)
- Licenças e conformidade: [docs/LEGAL_AND_LICENSES.md](docs/LEGAL_AND_LICENSES.md)

## Build local

Pré-requisitos:

- JDK 17
- Android SDK instalado
- Variáveis e ferramentas Android configuradas no ambiente

Comandos:

```bash
./gradlew :app:assembleDebug
```

Para validar a configuração geral do projeto:

```bash
./gradlew projects
```

## Licença

Distribuído sob a licença GNU GPL v2.0. Consulte [LICENSE](LICENSE).
