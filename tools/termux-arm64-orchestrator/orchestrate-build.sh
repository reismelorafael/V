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
APK_PATH="${APK_PATH:-$ROOT_DIR/app/build/outputs/apk/release/app-release.apk}"
GRADLE_WRAPPER="$ROOT_DIR/tools/gradle_with_jdk21.sh"

SPILL_ALLOC_MB="${SPILL_ALLOC_MB:-256}"
HOST_PROBE_FILE="$BUILD_SPILL_DIR/host-probe.txt"
HOST_MEMORY_PROFILE="balanced"
HOST_TOOLCHAIN_PATH_PROFILE="repo-sdk"

source "$ROOT_DIR/tools/termux-arm64-orchestrator/resolve-release-keystore.sh"

run_native_helpers() {
  if [[ ! -x tools/termux-arm64-orchestrator/build-native-helpers.sh ]]; then
    warn "build-native-helpers.sh ausente"
    return
  fi

  log "compilando helpers C low-level"
  bash tools/termux-arm64-orchestrator/build-native-helpers.sh

  if [[ -x tools/termux-arm64-orchestrator/bin/host_probe ]]; then
    set +e
    tools/termux-arm64-orchestrator/bin/host_probe "$BUILD_SPILL_DIR" | tee "$HOST_PROBE_FILE"
    local host_probe_rc=${PIPESTATUS[0]}
    set -e
    if [[ $host_probe_rc -ne 0 ]]; then
      warn "host_probe retornou rc=$host_probe_rc (continuando com fallback)"
    fi
  else
    warn "host_probe não compilado; fallback para heurística padrão"
  fi

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
  local spill_write="1"
  if [[ -f "$HOST_PROBE_FILE" ]]; then
    spill_write="$(probe_value spill_dir_writable 1)"
  fi

  if [[ "$ENABLE_SPILL" != "1" ]]; then
    log "spill desabilitado por ENABLE_SPILL=$ENABLE_SPILL"
    return
  fi

  if [[ "$spill_write" != "1" ]]; then
    warn "host probe reporta spill_dir sem permissão de escrita; spill desabilitado"
    ENABLE_SPILL="0"
    return
  fi

  export GRADLE_USER_HOME="$BUILD_SPILL_DIR/gradle-home"
  export ORG_GRADLE_PROJECT_orgGradleProjectCacheDir="$BUILD_SPILL_DIR/gradle-cache"
  export TMPDIR="$BUILD_SPILL_DIR/tmp"
  mkdir -p "$GRADLE_USER_HOME" "$ORG_GRADLE_PROJECT_orgGradleProjectCacheDir" "$TMPDIR"

  log "spill configurado em $BUILD_SPILL_DIR"
}

configure_toolchain_flags() {
  local cpu_features=""
  local page_size="4096"
  local march="-march=armv8-a+simd"
  local gradle_jvmargs="-Xmx2g -XX:MaxMetaspaceSize=512m -XX:+UseSerialGC"

  if [[ -f "$HOST_PROBE_FILE" ]]; then
    cpu_features="$(probe_value cpu_features)"
    page_size="$(probe_value page_size 4096)"
  fi

  if [[ "$cpu_features" == *"sve"* || "$cpu_features" == *"sve2"* ]]; then
    march="-march=armv8.2-a+sve"
    HOST_MEMORY_PROFILE="high-throughput"
    gradle_jvmargs="-Xmx3g -XX:MaxMetaspaceSize=768m -XX:+UseSerialGC"
  elif [[ "$page_size" =~ ^[0-9]+$ ]] && (( page_size >= 16384 )); then
    HOST_MEMORY_PROFILE="large-page"
    gradle_jvmargs="-Xmx2304m -XX:MaxMetaspaceSize=640m -XX:+UseSerialGC"
  fi

  export CFLAGS="${CFLAGS:-} -O3 -pipe -fPIC -ffunction-sections -fdata-sections $march"
  export CXXFLAGS="${CXXFLAGS:-} -O3 -pipe -fPIC -ffunction-sections -fdata-sections $march"
  export LDFLAGS="${LDFLAGS:-} -Wl,--gc-sections -Wl,--as-needed"
  export GRADLE_OPTS="${GRADLE_OPTS:-} -Dorg.gradle.daemon=false -Dorg.gradle.caching=true -Dorg.gradle.parallel=true -Dorg.gradle.configureondemand=true"
  export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -XX:+UseSerialGC"
  export ORCHESTRATOR_GRADLE_JVMARGS="$gradle_jvmargs"

  log "flags de toolchain configuradas (profile=$HOST_MEMORY_PROFILE, march=$march)"
}

probe_value() {
  local key="$1"
  local default_value="${2:-}"

  if [[ ! -f "$HOST_PROBE_FILE" ]]; then
    printf '%s\n' "$default_value"
    return
  fi

  local value
  value="$(awk -F= -v lookup="$key" '$1==lookup {print substr($0, index($0,"=")+1)}' "$HOST_PROBE_FILE" | tail -n1)"
  if [[ -z "$value" ]]; then
    printf '%s\n' "$default_value"
  else
    printf '%s\n' "$value"
  fi
}

configure_toolchain_paths() {
  local repo_sdk="$ROOT_DIR/.android-sdk"
  local home_sdk="${HOME:-$ROOT_DIR}/.android-sdk"
  local spill_writable
  spill_writable="$(probe_value spill_dir_writable 1)"

  if [[ -d "$repo_sdk" || "$spill_writable" == "1" ]]; then
    export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$repo_sdk}"
    HOST_TOOLCHAIN_PATH_PROFILE="repo-sdk"
  else
    export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$home_sdk}"
    HOST_TOOLCHAIN_PATH_PROFILE="home-sdk"
  fi
  export ANDROID_HOME="${ANDROID_HOME:-$ANDROID_SDK_ROOT}"

  log "toolchain path profile=$HOST_TOOLCHAIN_PATH_PROFILE ANDROID_SDK_ROOT=$ANDROID_SDK_ROOT"
}

write_final_report() {
  local report_file="$BUILD_SPILL_DIR/final-report.txt"
  {
    echo "$LOG_PREFIX final report"
    echo "timestamp=$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
    echo "memory_profile=$HOST_MEMORY_PROFILE"
    echo "toolchain_path_profile=$HOST_TOOLCHAIN_PATH_PROFILE"
    echo "enable_spill=$ENABLE_SPILL"
    echo "host_probe_file=$HOST_PROBE_FILE"
    if [[ -f "$HOST_PROBE_FILE" ]]; then
      echo "host_probe_begin"
      cat "$HOST_PROBE_FILE"
      echo "host_probe_end"
    fi
  } > "$report_file"
  log "final report gerado em $report_file"
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
    -Dorg.gradle.jvmargs="${ORCHESTRATOR_GRADLE_JVMARGS:--Xmx2g -XX:MaxMetaspaceSize=512m -XX:+UseSerialGC}"

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
configure_signing_env
run_native_helpers
configure_memory_spill
configure_toolchain_paths
configure_toolchain_flags
bootstrap_android_env
run_build
write_final_report
