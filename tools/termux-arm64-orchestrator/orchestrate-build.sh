#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

LOG_PREFIX="[arm64-orchestrator]"
BUILD_SPILL_DIR="${BUILD_SPILL_DIR:-$ROOT_DIR/.build-spill}"
ENABLE_SPILL="${ENABLE_SPILL:-1}"
CI_DRY_RUN="${CI_DRY_RUN:-0}"
BOOTSTRAP_ANDROID="${BOOTSTRAP_ANDROID:-1}"
ANDROID_API_LEVEL="${ANDROID_API_LEVEL:-35}"
VECTRAS_RELEASE_STORE_FILE="${VECTRAS_RELEASE_STORE_FILE:-}"
VECTRAS_RELEASE_KEY_ALIAS="${VECTRAS_RELEASE_KEY_ALIAS:-}"
VECTRAS_RELEASE_STORE_PASSWORD="${VECTRAS_RELEASE_STORE_PASSWORD:-}"
VECTRAS_RELEASE_KEY_PASSWORD="${VECTRAS_RELEASE_KEY_PASSWORD:-}"

if [[ -z "$VECTRAS_RELEASE_STORE_FILE" && -n "${VECTRAS_KEYSTORE:-}" ]]; then
  VECTRAS_RELEASE_STORE_FILE="$VECTRAS_KEYSTORE"
fi
if [[ -z "$VECTRAS_RELEASE_KEY_ALIAS" && -n "${VECTRAS_KEY_ALIAS:-}" ]]; then
  VECTRAS_RELEASE_KEY_ALIAS="$VECTRAS_KEY_ALIAS"
fi
if [[ -z "$VECTRAS_RELEASE_STORE_PASSWORD" && -n "${VECTRAS_STORE_PASSWORD:-}" ]]; then
  VECTRAS_RELEASE_STORE_PASSWORD="$VECTRAS_STORE_PASSWORD"
fi
if [[ -z "$VECTRAS_RELEASE_KEY_PASSWORD" && -n "${VECTRAS_KEY_PASSWORD:-}" ]]; then
  VECTRAS_RELEASE_KEY_PASSWORD="$VECTRAS_KEY_PASSWORD"
fi

export VECTRAS_RELEASE_STORE_FILE VECTRAS_RELEASE_KEY_ALIAS VECTRAS_RELEASE_STORE_PASSWORD VECTRAS_RELEASE_KEY_PASSWORD

APK_PATH="${APK_PATH:-$ROOT_DIR/app/build/outputs/apk/release/app-release.apk}"
GRADLE_WRAPPER="$ROOT_DIR/tools/gradle_with_jdk21.sh"

SPILL_ALLOC_MB="${SPILL_ALLOC_MB:-256}"

source "$ROOT_DIR/tools/termux-arm64-orchestrator/resolve-release-keystore.sh"

run_native_helpers() {
  if [[ ! -x tools/termux-arm64-orchestrator/build-native-helpers.sh ]]; then
    warn "build-native-helpers.sh ausente"
    return
  fi

  log "compilando helpers C low-level"
  bash tools/termux-arm64-orchestrator/build-native-helpers.sh

  if [[ -x tools/termux-arm64-orchestrator/bin/arm64_neon_probe ]]; then
    tools/termux-arm64-orchestrator/bin/arm64_neon_probe | tee "$BUILD_SPILL_DIR/arm64-neon-probe.txt"
  fi

  if [[ "$ENABLE_SPILL" == "1" && -x tools/termux-arm64-orchestrator/bin/storage_spill_allocator ]]; then
    tools/termux-arm64-orchestrator/bin/storage_spill_allocator "$BUILD_SPILL_DIR" "$SPILL_ALLOC_MB" | tee "$BUILD_SPILL_DIR/spill-allocator.txt"
  fi
}

mkdir -p "$BUILD_SPILL_DIR"

log() { echo "$LOG_PREFIX $*"; }
warn() { echo "$LOG_PREFIX WARN: $*"; }

resolve_signing_var() {
  local canonical_name="$1"
  local legacy_name="$2"
  local default_value="${3:-}"
  local canonical_value legacy_value

  canonical_value="$(printenv "$canonical_name" 2>/dev/null || true)"
  legacy_value="$(printenv "$legacy_name" 2>/dev/null || true)"

  if [[ -n "$canonical_value" ]]; then
    printf '%s\n' "$canonical_value"
    return 0
  fi

  if [[ -n "$legacy_value" ]]; then
    warn "variável legada detectada: $legacy_name. Migre para $canonical_name (ponte temporária de retrocompatibilidade)."
    printf '%s\n' "$legacy_value"
    return 0
  fi

  printf '%s\n' "$default_value"
}

configure_signing_env() {
  VECTRAS_RELEASE_STORE_FILE="$(resolve_signing_var VECTRAS_RELEASE_STORE_FILE VECTRAS_KEYSTORE "$ROOT_DIR/vectras.jks")"
  VECTRAS_RELEASE_STORE_PASSWORD="$(resolve_signing_var VECTRAS_RELEASE_STORE_PASSWORD VECTRAS_STORE_PASSWORD "856856")"
  VECTRAS_RELEASE_KEY_ALIAS="$(resolve_signing_var VECTRAS_RELEASE_KEY_ALIAS VECTRAS_KEY_ALIAS "vectras")"
  VECTRAS_RELEASE_KEY_PASSWORD="$(resolve_signing_var VECTRAS_RELEASE_KEY_PASSWORD VECTRAS_KEY_PASSWORD "856856")"

  export VECTRAS_RELEASE_STORE_FILE
  export VECTRAS_RELEASE_STORE_PASSWORD
  export VECTRAS_RELEASE_KEY_ALIAS
  export VECTRAS_RELEASE_KEY_PASSWORD
}

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "$LOG_PREFIX missing required command: $1" >&2
    exit 1
  fi
}

detect_arch() {
  local arch
  arch="$(uname -m || true)"
  log "host arch: ${arch:-unknown}"

  if [[ "$arch" != "aarch64" && "$arch" != "arm64" ]]; then
    warn "host não-arm64; build seguirá com target arm64-v8a"
  fi

  if [[ -f /proc/cpuinfo ]] && rg -i "neon|asimd" /proc/cpuinfo >/dev/null; then
    log "detected NEON/ASIMD support"
  else
    warn "detecção runtime de NEON indisponível; aplicando flags de compile-time"
  fi
}

configure_memory_spill() {
  if [[ "$ENABLE_SPILL" != "1" ]]; then
    log "spill desabilitado por ENABLE_SPILL=$ENABLE_SPILL"
    return
  fi

  export GRADLE_USER_HOME="$BUILD_SPILL_DIR/gradle-home"
  export ORG_GRADLE_PROJECT_orgGradleProjectCacheDir="$BUILD_SPILL_DIR/gradle-cache"
  export TMPDIR="$BUILD_SPILL_DIR/tmp"
  mkdir -p "$GRADLE_USER_HOME" "$ORG_GRADLE_PROJECT_orgGradleProjectCacheDir" "$TMPDIR"

  log "spill configurado em $BUILD_SPILL_DIR"
}

configure_toolchain_flags() {
  export CFLAGS="${CFLAGS:-} -O3 -pipe -fPIC -ffunction-sections -fdata-sections -march=armv8-a+simd"
  export CXXFLAGS="${CXXFLAGS:-} -O3 -pipe -fPIC -ffunction-sections -fdata-sections -march=armv8-a+simd"
  export LDFLAGS="${LDFLAGS:-} -Wl,--gc-sections -Wl,--as-needed"
  export GRADLE_OPTS="${GRADLE_OPTS:-} -Dorg.gradle.daemon=false -Dorg.gradle.caching=true -Dorg.gradle.parallel=true"
  export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -XX:+UseSerialGC"

  log "flags de toolchain configuradas"
}

bootstrap_android_env() {
  if [[ "$BOOTSTRAP_ANDROID" != "1" ]]; then
    log "bootstrap Android desabilitado por BOOTSTRAP_ANDROID=$BOOTSTRAP_ANDROID"
    return
  fi

  log "iniciando bootstrap Android SDK/NDK local"
  bash tools/termux-arm64-orchestrator/bootstrap-termux-android15.sh
}

verify_signing() {
  local apk="$1"
  local verify_rc=0

  if [[ ! -f "$apk" ]]; then
    echo "$LOG_PREFIX release APK not found: $apk" >&2
    exit 1
  fi

  if command -v apksigner >/dev/null 2>&1; then
    log "verificando assinatura com apksigner"
    set +e
    apksigner verify --verbose --print-certs "$apk" 2>&1 | tee "$BUILD_SPILL_DIR/apk-signature.txt"
    verify_rc=${PIPESTATUS[0]}
    set -e

    if [[ $verify_rc -ne 0 ]]; then
      echo "$LOG_PREFIX apksigner verification failed with exit code $verify_rc" >&2
      exit 1
    fi

    if ! rg -n "Verified" "$BUILD_SPILL_DIR/apk-signature.txt" >/dev/null; then
      warn "apksigner retornou sucesso, mas texto esperado ('Verified') não foi encontrado no log"
    fi

    return
  fi

  if command -v jarsigner >/dev/null 2>&1; then
    log "apksigner não encontrado; fallback para jarsigner"
    set +e
    jarsigner -verify -verbose -certs "$apk" 2>&1 | tee "$BUILD_SPILL_DIR/apk-signature.txt"
    verify_rc=${PIPESTATUS[0]}
    set -e

    if [[ $verify_rc -ne 0 ]]; then
      echo "$LOG_PREFIX jarsigner verification failed with exit code $verify_rc" >&2
      exit 1
    fi

    if ! rg -n "jar verified" "$BUILD_SPILL_DIR/apk-signature.txt" >/dev/null; then
      warn "jarsigner retornou sucesso, mas texto esperado ('jar verified') não foi encontrado no log"
    fi

    return
  fi

  warn "sem apksigner/jarsigner para verificação pós-build"
}

run_build() {
  if [[ "$CI_DRY_RUN" == "1" ]]; then
    log "CI_DRY_RUN=1; skipping real Gradle build"
    return
  fi

  chmod +x "$GRADLE_WRAPPER"

  log "starting arm64-v8a release build using injected signing credentials"
  "$GRADLE_WRAPPER" --no-daemon :app:clean :app:assembleRelease \
    -Pandroid.injected.build.abi=arm64-v8a \
    -Pandroid.injected.build.api="$ANDROID_API_LEVEL" \
    -Pandroid.injected.signing.store.file="$VECTRAS_RELEASE_STORE_FILE" \
    -Pandroid.injected.signing.store.password="$VECTRAS_RELEASE_STORE_PASSWORD" \
    -Pandroid.injected.signing.key.alias="$VECTRAS_RELEASE_KEY_ALIAS" \
    -Pandroid.injected.signing.key.password="$VECTRAS_RELEASE_KEY_PASSWORD" \
    -Dorg.gradle.jvmargs="-Xmx2g -XX:MaxMetaspaceSize=512m -XX:+UseSerialGC"

  verify_signing "$APK_PATH"
  log "build finished (signed, local artifact only; sem publicação em loja)"
}

require_cmd bash
require_cmd rg
require_cmd uname
require_cmd "$GRADLE_WRAPPER"

log "running legal compliance gate"
bash tools/termux-arm64-orchestrator/legal-compliance-check.sh

detect_arch
configure_memory_spill
configure_toolchain_flags
configure_signing_env
run_native_helpers
bootstrap_android_env
run_build
