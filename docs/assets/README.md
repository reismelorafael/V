# docs/assets — Convenções e Governança de Diagramas

Este diretório centraliza os diagramas usados na documentação técnica do repositório.

## 1) Convenção de nomes

- Formato canônico: `kebab-case` + extensão `.png`.
- Nome deve refletir o conceito arquitetural principal do diagrama.
- Não renomear arquivos já referenciados em `docs/IMAGES_INDEX.md`; em caso de troca de versão, atualizar o arquivo mantendo o mesmo nome canônico quando possível.

## 2) Origem dos arquivos (fonte e integridade)

| Arquivo local | Origem | SHA-256 | Status |
|---|---|---|---|
| `additional-image-01.png` | `https://github.com/user-attachments/assets/2123ec50-7240-490a-8975-bd675ca1fa92` | N/A (binário não versionado) | Ausente |
| `rafaelia-fractal-architecture.png` | `https://github.com/user-attachments/assets/3c9e94f2-fc94-4782-80e8-b884bc4c6d3e` | N/A (binário não versionado) | Ausente |
| `rafaelia-mathematical.png` | `https://github.com/user-attachments/assets/d69640be-83f6-413b-b3b1-2ebbfc1e7cd4` | N/A (binário não versionado) | Ausente |
| `rafaelia-system-pipeline.png` | `https://github.com/user-attachments/assets/aafce52f-c83b-480b-b1d4-d579256c2363` | N/A (binário não versionado) | Ausente |
| `vectra-mystical-ui-concept.png` | `https://github.com/user-attachments/assets/aa58501e-263c-47fe-91b6-406b373cb3f6` | N/A (binário não versionado) | Ausente |
| `ziprafa-integrity-architecture.png` | `https://github.com/user-attachments/assets/97010343-3677-4766-b070-9fece88ba754` | N/A (binário não versionado) | Ausente |
| `rafaelia-core-eye-toroid.png` | fonte não registrada no repositório (referência: “Provided via chat prompt”) | N/A | Ausente |
| `rafaelia-coherence-layers.png` | fonte não registrada no repositório (referência: “Provided via chat prompt”) | N/A | Ausente |

## 3) Política de atualização

1. Este repositório mantém somente metadados em `docs/assets/`; binários de imagem não devem ser versionados.
2. Sincronizar `docs/IMAGES_INDEX.md` com status real (`✅ Presente` / `❌ Ausente`) e path local canônico.
3. Se houver necessidade de distribuir imagem, publicar em origem externa rastreável e manter apenas URL + nome canônico nesta tabela.
4. Novas imagens só entram com:
   - origem explícita (URL/issue/commit);
   - nome canônico definido;
   - registro no índice e nesta README;
   - sem commit de binário no repositório.
