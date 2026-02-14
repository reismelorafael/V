# Auditoria Forense de Arquivos Não-Markdown

Este relatório cobre todos os arquivos do repositório, exceto `.md`, com inventário determinístico e hash SHA-256.

- Total de arquivos não-MD auditados: **896**
- Inventário detalhado: `reports/non_md_inventory.tsv`

## Distribuição por tipo lógico
- source: 738
- image: 81
- config-data: 30
- artifact-binary: 21
- no-extension: 14
- other: 12

## Distribuição text/binary
- text: 786
- binary: 110

## Top extensões
- `.xml`: 355
- `.java`: 296
- `.png`: 73
- `.c`: 24
- `.json`: 19
- `.kt`: 19
- `<noext>`: 14
- `.sh`: 12
- `.h`: 11
- `.rs`: 9
- `.tar`: 8
- `.txt`: 7
- `.gradle`: 7
- `.jpg`: 6
- `.so`: 5
- `.iso`: 4
- `.pro`: 3
- `.html`: 3
- `.img`: 2
- `.fd`: 2

## Top diretórios (arquivos não-MD)
- `app`: 682
- `resources`: 37
- `terminal-emulator`: 36
- `engine`: 35
- `web`: 24
- `tools`: 15
- `shell-loader`: 14
- `<root>`: 12
- `terminal-view`: 11
- `demo_cli`: 8
- `fastlane`: 8
- `3dfx`: 4
- `bench`: 4
- `reports`: 3
- `gradle`: 2
- `bug`: 1

## Arquivos >= 5 MiB
- `app/src/main/assets/roms/QEMU_EFI.img` — 67108864 bytes
- `app/src/main/assets/roms/QEMU_VARS.img` — 67108864 bytes

## Candidatos sensíveis para governança
- `vectras.jks`

## Achados e recomendações
1. Validar política de versionamento para arquivos de chave/assinatura detectados.
2. Revisar periodicamente arquivos binários grandes em assets para controlar footprint do repositório.
3. Executar este auditor em CI para trilha contínua de integridade de inventário.
