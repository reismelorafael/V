#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

RELEASE_SIGNING_REQUIRED="${RELEASE_SIGNING_REQUIRED:-1}"

# Compatibilidade com variáveis legadas de assinatura
if [[ -z "${VECTRAS_RELEASE_STORE_FILE:-}" && -n "${VECTRAS_KEYSTORE:-}" ]]; then
  export VECTRAS_RELEASE_STORE_FILE="$VECTRAS_KEYSTORE"
fi
if [[ -z "${VECTRAS_RELEASE_KEY_ALIAS:-}" && -n "${VECTRAS_KEY_ALIAS:-}" ]]; then
  export VECTRAS_RELEASE_KEY_ALIAS="$VECTRAS_KEY_ALIAS"
fi
if [[ -z "${VECTRAS_RELEASE_STORE_PASSWORD:-}" && -n "${VECTRAS_STORE_PASSWORD:-}" ]]; then
  export VECTRAS_RELEASE_STORE_PASSWORD="$VECTRAS_STORE_PASSWORD"
fi
if [[ -z "${VECTRAS_RELEASE_KEY_PASSWORD:-}" && -n "${VECTRAS_KEY_PASSWORD:-}" ]]; then
  export VECTRAS_RELEASE_KEY_PASSWORD="$VECTRAS_KEY_PASSWORD"
fi

required_files=(
  "LICENSE"
  "THIRD_PARTY_NOTICES.md"
  "app/build.gradle"
  "tools/gradle_with_jdk21.sh"
  "tools/termux-arm64-orchestrator/bootstrap-termux-android15.sh"
  "tools/termux-arm64-orchestrator/toolchain-pack.sh"
  "tools/termux-arm64-orchestrator/forks-sync.sh"
  "tools/termux-arm64-orchestrator/fork-manifests/forks-sources.json"
  "tools/termux-arm64-orchestrator/TOOLCHAIN_LICENSES.md"
  "tools/termux-arm64-orchestrator/TOOLCHAIN_CORE.md"
  "tools/termux-arm64-orchestrator/toolchain-manifests/toolchain-bom.json"
  "tools/termux-arm64-orchestrator/toolchain-core/detect-host.sh"
  "tools/termux-arm64-orchestrator/toolchain-core/resolve-toolchain.sh"
  "tools/termux-arm64-orchestrator/toolchain-core/activate-env.sh"
  "tools/termux-arm64-orchestrator/toolchain-core/verify-toolchain.sh"
)

for file in "${required_files[@]}"; do
  if [[ ! -f "$file" ]]; then
    echo "[compliance] missing required file: $file" >&2
    exit 1
  fi
done

if ! rg -n "android\.injected\.signing\.store\.file|VECTRAS_RELEASE_STORE_FILE" app/build.gradle >/dev/null; then
  echo "[compliance] app/build.gradle must support signing store path via android.injected.signing.store.file or VECTRAS_RELEASE_STORE_FILE" >&2
  exit 1
fi

if ! rg -n "android\.injected\.signing\.key\.alias|VECTRAS_RELEASE_KEY_ALIAS|keyAlias\s+releaseKeyAlias" app/build.gradle >/dev/null; then
  echo "[compliance] app/build.gradle must support signing key alias via variável injetada" >&2
  exit 1
fi

if ! rg -n "targetSdk\s*=\s*.*targetApi" app/build.gradle >/dev/null; then
  echo "[compliance] targetSdk declaration not found in app/build.gradle" >&2
  exit 1
fi

if [[ "$RELEASE_SIGNING_REQUIRED" == "1" ]]; then
  required_signing_vars=(
    "VECTRAS_RELEASE_STORE_FILE"
    "VECTRAS_RELEASE_STORE_PASSWORD"
    "VECTRAS_RELEASE_KEY_ALIAS"
    "VECTRAS_RELEASE_KEY_PASSWORD"
  )

  for var_name in "${required_signing_vars[@]}"; do
    if [[ -z "${!var_name:-}" ]]; then
      echo "[compliance] missing required release signing variable: ${var_name}" >&2
      exit 1
    fi
  done

  if [[ "${VECTRAS_RELEASE_STORE_FILE:0:1}" != "/" ]]; then
    echo "[compliance] invalid VECTRAS_RELEASE_STORE_FILE: expected absolute path" >&2
    exit 1
  fi

  if [[ ! -f "$VECTRAS_RELEASE_STORE_FILE" ]]; then
    echo "[compliance] invalid VECTRAS_RELEASE_STORE_FILE: file not found" >&2
    exit 1
  fi

  if [[ -z "${VECTRAS_RELEASE_KEY_ALIAS//[[:space:]]/}" ]]; then
    echo "[compliance] invalid VECTRAS_RELEASE_KEY_ALIAS: alias must be non-empty" >&2
    exit 1
  fi
else
  echo "[compliance] release signing gate skipped by RELEASE_SIGNING_REQUIRED=$RELEASE_SIGNING_REQUIRED"
fi

if ! rg -n '"ndk;\$\{ANDROID_NDK_VERSION\}"|"ndk;\$ANDROID_NDK_VERSION"' tools/termux-arm64-orchestrator/bootstrap-termux-android15.sh >/dev/null; then
  echo "[compliance] bootstrap-termux-android15.sh must pin NDK installation via ANDROID_NDK_VERSION" >&2
  exit 1
fi

if ! rg -n '"cmake;\$\{ANDROID_CMAKE_VERSION\}"|"cmake;\$ANDROID_CMAKE_VERSION"' tools/termux-arm64-orchestrator/bootstrap-termux-android15.sh >/dev/null; then
  echo "[compliance] bootstrap-termux-android15.sh must pin CMake installation via ANDROID_CMAKE_VERSION" >&2
  exit 1
fi

if ! rg -n 'JDK 21|JDK 17|JAVA_HOME' tools/gradle_with_jdk21.sh >/dev/null; then
  echo "[compliance] tools/gradle_with_jdk21.sh must enforce local JDK selection (21/17)" >&2
  exit 1
fi


if ! rg -n '"name"\s*:\s*"android-cmdline-tools"|"name"\s*:\s*"android-ndk"|"name"\s*:\s*"android-cmake"|"name"\s*:\s*"jdk"' tools/termux-arm64-orchestrator/toolchain-manifests/toolchain-bom.json >/dev/null; then
  echo "[compliance] toolchain-bom.json must declare android-cmdline-tools/android-ndk/android-cmake/jdk components" >&2
  exit 1
fi

if ! rg -n '"version"\s*:|"source"\s*:|"sha256"\s*:|"license"\s*:' tools/termux-arm64-orchestrator/toolchain-manifests/toolchain-bom.json >/dev/null; then
  echo "[compliance] toolchain-bom.json missing mandatory metadata keys (version/source/sha256/license)" >&2
  exit 1
fi


if ! rg -n '"schema"\s*:\s*"vectras-termux-forks/v1"|"forks"\s*:' tools/termux-arm64-orchestrator/fork-manifests/forks-sources.json >/dev/null; then
  echo "[compliance] forks-sources.json must declare schema vectras-termux-forks/v1 and forks array" >&2
  exit 1
fi

echo "[compliance] legal checks passed"
echo "[compliance] toolchain manifest checks passed"
echo "[compliance] signing checks passed"
echo "[compliance] release metadata checks passed"
