# Toolchain Core API (`tools/termux-arm64-orchestrator/toolchain-core/`)

Submódulo interno para separar contratos de detecção, resolução, ativação e validação da toolchain Android.

## 1) `detect-host.sh`

Detecta arquitetura/SO/recursos do host.

### Input
- Sem argumentos obrigatórios.

### Output (stdout, formato `KEY=VALUE`)
- `HOST_ARCH`
- `HOST_OS`
- `HOST_CPU_COUNT`
- `HOST_IS_ARM64` (`0|1`)
- `HOST_HAS_NEON` (`0|1`)
- `HOST_HAS_ASIMD` (`0|1`)

### Exemplo
```bash
bash tools/termux-arm64-orchestrator/toolchain-core/detect-host.sh > .build-spill/host.env
```

## 2) `resolve-toolchain.sh`

Resolve versões e paths finais de JDK/SDK/NDK/CMake.

### Inputs (env)
- `ROOT_DIR` (opcional)
- `JAVA_HOME` (opcional)
- `ANDROID_HOME` (opcional)
- `ANDROID_SDK_ROOT` (opcional)
- `ANDROID_API_LEVEL` (default `35`)
- `ANDROID_BUILD_TOOLS` (default `35.0.0`)
- `ANDROID_NDK_VERSION` (default `27.2.12479018`)
- `ANDROID_CMAKE_VERSION` (default `3.22.1`)

### Output (stdout, `KEY=VALUE`)
- `ROOT_DIR`, `JAVA_HOME`, `ANDROID_HOME`, `ANDROID_SDK_ROOT`
- `ANDROID_API_LEVEL`, `ANDROID_BUILD_TOOLS`
- `ANDROID_NDK_VERSION`, `ANDROID_CMAKE_VERSION`
- `ANDROID_NDK_ROOT`, `ANDROID_CMAKE_ROOT`
- `ANDROID_BUILD_TOOLS_DIR`, `ANDROID_PLATFORM_DIR`
- `ANDROID_SDKMANAGER_BIN`

### Exemplo
```bash
ROOT_DIR="$(pwd)" bash tools/termux-arm64-orchestrator/toolchain-core/resolve-toolchain.sh > .build-spill/toolchain.env
```

## 3) `activate-env.sh`

Ativa ambiente de build exportando variáveis padrão e ajustando `PATH`.

### Input
- `activate-env.sh <toolchain-env-file>`

### Output (stdout)
- Linhas shell `export ...` prontas para `eval`:
  - `JAVA_HOME`
  - `ANDROID_HOME`
  - `ANDROID_SDK_ROOT`
  - `ANDROID_NDK_ROOT`
  - `ANDROID_NDK_HOME`
  - `ANDROID_CMAKE_ROOT`
  - `PATH`

### Exemplo
```bash
eval "$(bash tools/termux-arm64-orchestrator/toolchain-core/activate-env.sh .build-spill/toolchain.env)"
```

## 4) `verify-toolchain.sh`

Valida executáveis e versões mínimas.

### Input
- `verify-toolchain.sh <toolchain-env-file>`

### Regras de validação
- `JAVA_HOME/bin/java` existente e Java `>= 17`
- `ANDROID_SDK_ROOT` e `sdkmanager` existentes
- Presença de platform/build-tools
- Presença de NDK e CMake resolvidos
- `clang` do NDK e `cmake` executáveis

### Saída
- `0` em sucesso, non-zero em falha.

## 5) Integração no `orchestrate-build.sh`

Fluxo adotado:
1. `detect-host.sh` -> grava `.build-spill/host.env`
2. `resolve-toolchain.sh` -> grava `.build-spill/toolchain.env`
3. `activate-env.sh` -> `eval` para exportar ambiente
4. bootstrap Android (quando habilitado)
5. `resolve-toolchain.sh` + `verify-toolchain.sh` antes do build

## 6) Reuso em outro grupo/repo

Contrato recomendado:
- Consumir todos os módulos via `bash` com stdout `KEY=VALUE` (ou `export ...` no caso de `activate-env.sh`).
- Persistir snapshots de ambiente (`host.env`, `toolchain.env`) para auditoria/reprodutibilidade.
- Chamar `verify-toolchain.sh` imediatamente antes da compilação para fail-fast.
