# BITΩ Overlay — Patch Notes
Data: 2026-02-25

## Adicionado
- engine/rmr/include/bitomega.h
- engine/rmr/src/bitomega.c
- docs/bitomega_postdoc/* (skeleton pós-doc)

## Alterações necessárias (aplicar no seu repo)
1) Root CMakeLists.txt: adicionar `engine/rmr/src/bitomega.c` na lista do alvo `rmr`.
2) app/src/main/cpp/CMakeLists.txt: adicionar `../../../../engine/rmr/src/bitomega.c` no `vectra_core_accel`.

> Eu deixei abaixo os trechos prontos (copiar/colar).

### (1) Root CMakeLists.txt
Na lista `add_library(rmr STATIC ...)`, adicionar:
- engine/rmr/src/bitomega.c

### (2) app/src/main/cpp/CMakeLists.txt
Na lista `add_library(vectra_core_accel SHARED ...)`, adicionar:
- ../../../../engine/rmr/src/bitomega.c
