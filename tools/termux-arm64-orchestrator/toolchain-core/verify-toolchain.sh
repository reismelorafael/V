#!/usr/bin/env bash
set -euo pipefail

ENV_FILE="${1:-}"
if [[ -z "$ENV_FILE" || ! -f "$ENV_FILE" ]]; then
  echo "usage: $0 <toolchain-env-file>" >&2
  exit 1
fi

while IFS='=' read -r key value; do
  [[ -z "$key" ]] && continue
  [[ "$key" =~ ^# ]] && continue
  export "$key=$value"
done < "$ENV_FILE"

fail() {
  echo "[toolchain-core] $*" >&2
  exit 1
}

[[ -n "${JAVA_HOME:-}" ]] || fail "JAVA_HOME vazio"
[[ -x "$JAVA_HOME/bin/java" ]] || fail "java não encontrado em $JAVA_HOME/bin/java"

java_major="$($JAVA_HOME/bin/java -version 2>&1 | awk -F '[\".]' '/version/ {print $2; exit}')"
[[ -n "$java_major" ]] || fail "não foi possível detectar versão do Java"
(( java_major >= 17 )) || fail "Java >= 17 requerido; detectado: $java_major"

[[ -d "${ANDROID_SDK_ROOT:-}" ]] || fail "ANDROID_SDK_ROOT inexistente: ${ANDROID_SDK_ROOT:-<vazio>}"
[[ -x "${ANDROID_SDKMANAGER_BIN:-}" ]] || fail "sdkmanager ausente: ${ANDROID_SDKMANAGER_BIN:-<vazio>}"
[[ -d "${ANDROID_BUILD_TOOLS_DIR:-}" ]] || fail "build-tools ausente: ${ANDROID_BUILD_TOOLS_DIR:-<vazio>}"
[[ -d "${ANDROID_PLATFORM_DIR:-}" ]] || fail "platform ausente: ${ANDROID_PLATFORM_DIR:-<vazio>}"
[[ -d "${ANDROID_NDK_ROOT:-}" ]] || fail "NDK ausente: ${ANDROID_NDK_ROOT:-<vazio>}"
[[ -d "${ANDROID_CMAKE_ROOT:-}" ]] || fail "CMake ausente: ${ANDROID_CMAKE_ROOT:-<vazio>}"

ndk_clang="${ANDROID_NDK_ROOT}/toolchains/llvm/prebuilt/linux-x86_64/bin/clang"
[[ -x "$ndk_clang" ]] || fail "clang do NDK ausente: $ndk_clang"
[[ -x "${ANDROID_CMAKE_ROOT}/bin/cmake" ]] || fail "binário cmake ausente em ${ANDROID_CMAKE_ROOT}/bin/cmake"

echo "[toolchain-core] toolchain verificada com sucesso"
