# RAFAELIA Engine / RMR

## Estrutura
- `include/`: headers públicos
- `src/`: implementação low-level

## Build
```bash
make all
```

## Artefatos
- `build/engine/librmr.a`
- `build/engine/libbitraf.a`
- `build/engine/libbitraf.so`
- `build/demo/rafaelia_demo`
- `build/demo/bitraf_core`
- `build/bench/rmr_bench`

## Módulo APK determinístico
- Header: `include/rmr_apk_module.h`
- Fonte: `src/rmr_apk_module.c`
- Demo: `build/demo/apk_module_demo`

Recursos do módulo:
- plano determinístico de build `:app:assembleRelease`.
- modo Termux/Android (arm64) com `TERMUX_BUILD=1` e `GRADLE_USER_HOME=.gradle`.
- autotuning low-level via `RmR_HW_Detect` (cacheline/page + ABI host) para orquestração.
- validação de assinatura legítima (sem alias de debug).
- flags de pipeline ético/compliance (`IEEE/NIST/W3C/RFC/GDPR/LGPD`).
