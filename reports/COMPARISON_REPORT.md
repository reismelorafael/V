# Cross-Repository Comparison Report (Formal)

## Status
Only `Vectras-VM-Android` is present in the workspace (`/workspace/Vectras-VM-Android`). The following repositories were not found, so direct comparisons cannot be completed in this environment:
- `androidx_RmR-androidx-main`
- `qemu_rafaelia-master`
- `Vectras-VM-Android-master`

## Comparações solicitadas (bloqueadas)
### 1) androidx oficial vs androidx_RmR
- **Bloqueio:** ausência do repositório oficial e de `androidx_RmR-androidx-main`.
- **Foco solicitado:** diretórios extras (`rmr/*`, `room3/*`) e mudanças em `README`/`CONTRIBUTING`.

### 2) qemu oficial vs qemu_rafaelia
- **Bloqueio:** ausência do repositório oficial e de `qemu_rafaelia-master`.
- **Foco solicitado:** docs e diretórios extras, mudanças em `hw/core` e `android/vectras-vm-android`.

### 3) Vectras-VM-Android vs Vectras-VM-Android-master
- **Bloqueio:** ausência de `Vectras-VM-Android-master`.
- **Foco solicitado:** diferenças de módulos (benchmark/core vs creator) e impacto arquitetural.

## Template de comparação (para execução futura)
| Comparação | Diretórios extras | Docs alterados | Arquivos críticos | Resultado |
|-----------|-------------------|----------------|-------------------|-----------|
| androidx | rmr/*, room3/* | README/CONTRIBUTING | build scripts | Pendente |
| qemu | hw/core, android/vectras-vm-android | docs/* | targets/hw/* | Pendente |
| Vectras | módulos benchmark/core/creator | docs/ARCHITECTURE | app/ | Pendente |

## Próximos passos
1. Disponibilizar os repositórios faltantes no workspace.
2. Rodar comparações estruturais (árvore e `.md`).
3. Gerar diffs textuais em arquivos críticos (`README`, `CONTRIBUTING`, `ARCHITECTURE`).
