#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DEFAULT_SDK_ROOT="/workspace/android-sdk"
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$DEFAULT_SDK_ROOT}}"
PREFERRED_JAVA17="/usr/lib/jvm/java-17-openjdk-amd64"
APK_PATH="$ROOT_DIR/app/build/outputs/apk/release/app-release.apk"
KEYSTORE_PATH="$ROOT_DIR/vectras.jks"
REPORT_DIR="$ROOT_DIR/build/reports/apk-local"
GRADLE_WRAPPER="$ROOT_DIR/tools/gradle_with_jdk21.sh"
GRADLE_LOG="$REPORT_DIR/gradle_assemble_release.log"
VERIFY_LOG="$REPORT_DIR/apksigner_verify.log"
APK_META="$REPORT_DIR/apk_metadata.txt"

log(){ printf '[APK-BUILD] %s\n' "$*"; }
fail(){ printf '[APK-BUILD][ERR] %s\n' "$*" >&2; exit 1; }

prepare_java(){
  if [[ ! -x "$GRADLE_WRAPPER" ]]; then
    fail "Wrapper Gradle não encontrado: $GRADLE_WRAPPER"
  fi

  if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
    export PATH="$JAVA_HOME/bin:$PATH"
    return
  fi

  if [[ -x "$PREFERRED_JAVA17/bin/java" ]]; then
    export JAVA_HOME="$PREFERRED_JAVA17"
    export PATH="$JAVA_HOME/bin:$PATH"
  fi
}

prepare_sdk(){
  [[ -d "$SDK_ROOT" ]] || fail "ANDROID_SDK_ROOT não encontrado: $SDK_ROOT"
  export ANDROID_SDK_ROOT="$SDK_ROOT"
  export PATH="$ANDROID_SDK_ROOT/platform-tools:$PATH"
}

ensure_local_properties(){
  local file="$ROOT_DIR/local.properties"
  if [[ -f "$file" ]]; then
    if ! rg -q '^sdk\.dir=' "$file"; then
      printf '\nsdk.dir=%s\n' "$ANDROID_SDK_ROOT" >> "$file"
      log "local.properties atualizado com sdk.dir"
    fi
  else
    printf 'sdk.dir=%s\n' "$ANDROID_SDK_ROOT" > "$file"
    log "local.properties criado"
  fi
}

resolve_apksigner(){
  if [[ -x "$ANDROID_SDK_ROOT/build-tools/35.0.0/apksigner" ]]; then
    printf '%s\n' "$ANDROID_SDK_ROOT/build-tools/35.0.0/apksigner"
    return 0
  fi

  local found
  found="$(find "$ANDROID_SDK_ROOT/build-tools" -maxdepth 2 -type f -name apksigner 2>/dev/null | sort | tail -n 1 || true)"
  [[ -n "$found" ]] || fail "apksigner não encontrado em $ANDROID_SDK_ROOT/build-tools"
  printf '%s\n' "$found"
}

purge_incompatible_gradle_cache(){
  log "Limpando cache Gradle incompatível de bytecode"
  rm -rf "$ROOT_DIR/.gradle"
  rm -rf "$HOME/.gradle/caches" "$HOME/.gradle/daemon"
  if [[ -n "${GRADLE_USER_HOME:-}" ]]; then
    rm -rf "$GRADLE_USER_HOME/caches" "$GRADLE_USER_HOME/daemon"
  fi
}

build_release(){
  mkdir -p "$REPORT_DIR"
  log "Iniciando assembleRelease"
  set +e
  (
    cd "$ROOT_DIR"
    "$GRADLE_WRAPPER" --no-daemon :app:assembleRelease --stacktrace 2>&1 | tee "$GRADLE_LOG"
  )
  local rc=$?
  set -e

  if [[ $rc -ne 0 ]] && rg -q 'Unsupported class file major version 66' "$GRADLE_LOG"; then
    log "Detectado erro de cache Java incompatível; aplicando recovery"
    purge_incompatible_gradle_cache
    (
      cd "$ROOT_DIR"
      "$GRADLE_WRAPPER" --no-daemon :app:assembleRelease --stacktrace 2>&1 | tee "$GRADLE_LOG"
    )
    return
  fi

  [[ $rc -eq 0 ]] || fail "assembleRelease falhou. Verifique $GRADLE_LOG"
}

verify_artifact(){
  [[ -f "$KEYSTORE_PATH" ]] || fail "Keystore esperado não encontrado: $KEYSTORE_PATH"
  [[ -f "$APK_PATH" ]] || fail "APK não encontrado: $APK_PATH"

  local apksigner_bin
  apksigner_bin="$(resolve_apksigner)"

  log "Verificando assinatura com $apksigner_bin"
  "$apksigner_bin" verify --verbose --print-certs "$APK_PATH" 2>&1 | tee "$VERIFY_LOG"

  log "Coletando metadados"
  {
    printf 'artifact=%s\n' "$APK_PATH"
    stat -c 'size_bytes=%s' "$APK_PATH"
    sha256sum "$APK_PATH"
  } | tee "$APK_META"

  if command -v zipalign >/dev/null 2>&1; then
    zipalign -c -v 4 "$APK_PATH" >> "$APK_META" 2>&1 || true
  fi

  log "Artefato pronto: $APK_PATH"
  log "Relatórios: $REPORT_DIR"
}

prepare_java
prepare_sdk
ensure_local_properties
build_release
verify_artifact
