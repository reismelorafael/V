# IP_MAP — RAFAELIA / Vectras

## 1) Núcleo autoral (prioridade de proteção)
- `engine/rmr/src/rafaelia_bitraf_core.c`
- `engine/rmr/src/bitraf.c`
- `engine/rmr/src/rmr_*` (cycles/hw_detect/bench/isorf)
- `engine/rmr/include/bitraf.h`, `bitraf_version.h`
- `bench/src/rmr_benchmark_main.c`
- `bench/scripts/run_bench.sh`

Potencial patenteável (avaliar com counsel):
- Método de frame determinístico com hash orientado a seed e reconstrução verificável.
- Método de scoring determinístico orientado a ciclos em suite compacta.
- Estratégia de detecção+adaptação de benchmark por arquitetura.
- Estrutura de integração engine-runtime com trilha de evidência CSV/JSON.

## 2) Tabela SPDX por diretório

| Diretório | Conteúdo | Provenance | SPDX sugerido |
|---|---|---|---|
| `engine/rmr/include` | API pública + headers low-level RMR/Bitraf | Majoritariamente autoral nesta árvore | `GPL-2.0-only` |
| `engine/rmr/src` | Núcleo C low-level (Bitraf/RMR) | Majoritariamente autoral nesta árvore | `GPL-2.0-only` |
| `bench/` | Harness e automação benchmark | Autoral nesta árvore | `GPL-2.0-only` |
| `demo_cli/` | Demo CLI de integração engine | Autoral nesta árvore | `GPL-2.0-only` |
| `app/` | Runtime Android e integrações VM | Fork + contribuições locais | `GPL-2.0-only` |
| `3dfx/` | Componentes gráficos/integração | Terceiros/fork | `LicenseRef-See-THIRD-PARTY-NOTICES` |
| `terminal-emulator/`, `terminal-view/`, `shell-loader/` | Módulos auxiliares Android | Fork/terceiros + locais | `LicenseRef-See-THIRD-PARTY-NOTICES` |
| `docs/` | Documentação técnica/produto/IP | Autoral + referência de fork | `GPL-2.0-only` |
| `resources/`, `web/` | Assets, web data, materiais auxiliares | Misto (fork/terceiros/locais) | `LicenseRef-See-THIRD-PARTY-NOTICES` |
| `archive/experimental/` | Conteúdo histórico arquivado | Misto | `LicenseRef-Archived-Mixed` |

## 3) Third-party provenance
- Repositório base: fork de `xoureldeen/Vectras-VM-Android` (GPL-2.0).
- Dependências Android/Gradle e componentes de emulação seguem termos próprios.
- Componentes com origem externa devem permanecer rastreados em `THIRD_PARTY_NOTICES.md`.

## 4) Dependências e licenças
- Engine C: toolchain padrão (`cc`, `make`, `cmake`) sem libs externas novas.
- Runtime Android: ecossistema Gradle/Android existente no fork.

## 5) Riscos de IP
- Mistura entre código autoral novo e herança de fork sem fronteira explícita.
- Ambiguidade de licença em assets e módulos legados não-SPDX.
- Risco de redistribuição sem trilha atualizada de third-party notices.

## 6) Mitigações aplicadas
- Core isolado em `engine/rmr` com API pública estável (`bitraf.h`).
- Assets/experimental isolados em `archive/experimental`.
- Documentos de produto e benchmark centralizados em `docs/`.
- Tabela SPDX por diretório adicionada para governança contínua.
