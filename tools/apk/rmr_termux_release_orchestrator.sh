#!/usr/bin/env bash
# Orquestrador ponta-a-ponta Termux/Android arm64 com trilha determinística e verificação de assinatura.
# Uso:
#   bash tools/apk/rmr_termux_release_orchestrator.sh <keystore> <store_pass> <alias> <key_pass> [apk_path]

set -euo pipefail

REPORT_DIR="build/reports/rmr"
APK_DEFAULT="app/build/outputs/apk/release/app-release.apk"
GRADLE_WRAPPER="./tools/gradle_with_jdk21.sh"

log(){ echo "[RMR] $*"; }
warn(){ echo "[RMR][WARN] $*"; }
err(){ echo "[RMR][ERR] $*" >&2; }

require_cmd(){
  if ! command -v "$1" >/dev/null 2>&1; then
    err "comando não encontrado: $1"
    return 1
  fi
}

check_environment(){
  mkdir -p "$REPORT_DIR"
  {
    echo "== java -version =="
    java -version 2>&1 || true
    echo
    echo "== tools/gradle_with_jdk21.sh -v =="
    "$GRADLE_WRAPPER" -v 2>&1 || true
    echo
    echo "== sdkmanager --list (head) =="
    if command -v sdkmanager >/dev/null 2>&1; then
      sdkmanager --list 2>&1 | sed -n '1,80p'
    else
      echo "sdkmanager não encontrado"
    fi
  } | tee "$REPORT_DIR/environment.txt"
}

verify_apk_signing(){
  local apk="$1"

  if [ ! -f "$apk" ]; then
    err "APK não encontrado: $apk"
    return 2
  fi

  if command -v apksigner >/dev/null 2>&1; then
    log "apksigner verify: $apk"
    apksigner verify --verbose --print-certs "$apk" | tee "${apk}.sig.txt"

    grep -qi "Verified using v2 scheme" "${apk}.sig.txt" || warn "v2 não detectado (verifique config signing/v2Enabled)."
    grep -qi "Verified using v3 scheme" "${apk}.sig.txt" || warn "v3 não detectado (ok dependendo do target)."

    if grep -qi "Android Debug" "${apk}.sig.txt"; then
      err "Assinatura parece DEBUG (Android Debug). BLOQUEADO."
      return 3
    fi

    log "assinatura: OK"
    return 0
  fi

  if command -v jarsigner >/dev/null 2>&1; then
    warn "apksigner não encontrado; usando jarsigner (fallback)."
    jarsigner -verify -verbose -certs "$apk" | tee "${apk}.sig.txt"
    if grep -qi "jar verified" "${apk}.sig.txt"; then
      log "jarsigner: OK (fallback)"
      return 0
    fi
    err "jarsigner: falhou"
    return 4
  fi

  err "apksigner/jarsigner não encontrados. Instale Android build-tools ou JDK."
  return 5
}

artifact_metrics(){
  local apk="$1"
  local out="$REPORT_DIR/artifact_metrics.txt"
  {
    echo "apk=$apk"
    if [ -f "$apk" ]; then
      wc -c "$apk"
      if command -v unzip >/dev/null 2>&1; then
        echo "== ABI libs no APK =="
        unzip -l "$apk" | awk '/lib\// {print $4}' | sed -n '1,200p'
      else
        echo "unzip não encontrado"
      fi
      if command -v zipalign >/dev/null 2>&1; then
        echo "== zipalign check =="
        zipalign -c -v 4 "$apk" || true
      else
        echo "zipalign não encontrado"
      fi
    else
      echo "APK ausente"
    fi
  } | tee "$out"
}

if [ "$#" -lt 4 ] || [ "$#" -gt 5 ]; then
  echo "uso: $0 <keystore> <store_pass> <alias> <key_pass> [apk_path]"
  exit 1
fi

KEYSTORE="$1"
STORE_PASS="$2"
ALIAS="$3"
KEY_PASS="$4"
APK_PATH="${5:-$APK_DEFAULT}"

if [ "$ALIAS" = "androiddebugkey" ]; then
  err "alias de debug não é permitido em release"
  exit 2
fi

require_cmd java
require_cmd bash
require_cmd awk
require_cmd "$GRADLE_WRAPPER"
mkdir -p "$REPORT_DIR"

check_environment

export TERMUX_BUILD=1
export GRADLE_USER_HOME=.gradle

PLAN="$GRADLE_WRAPPER --no-daemon :app:clean :app:assembleRelease -Pvectras.universal=true -Pvectras.compliance.profile=IEEE_NIST_W3C_RFC_GDPR_LGPD -Pvectras.signing.ethical=true -Pandroid.injected.signing.store.file=$KEYSTORE -Pandroid.injected.signing.store.password=$STORE_PASS -Pandroid.injected.signing.key.alias=$ALIAS -Pandroid.injected.signing.key.password=$KEY_PASS"
echo "$PLAN" > "$REPORT_DIR/build_plan.txt"

TS0="$(date +%s)"
log "executando build release"
"$GRADLE_WRAPPER" --no-daemon :app:clean :app:assembleRelease \
  -Pvectras.universal=true \
  -Pvectras.compliance.profile=IEEE_NIST_W3C_RFC_GDPR_LGPD \
  -Pvectras.signing.ethical=true \
  -Pandroid.injected.signing.store.file="$KEYSTORE" \
  -Pandroid.injected.signing.store.password="$STORE_PASS" \
  -Pandroid.injected.signing.key.alias="$ALIAS" \
  -Pandroid.injected.signing.key.password="$KEY_PASS" \
  2>&1 | tee "$REPORT_DIR/gradle_build.log"
TS1="$(date +%s)"
echo "build_elapsed_seconds=$((TS1-TS0))" | tee "$REPORT_DIR/build_timing.txt"

if [ ! -f "$APK_PATH" ]; then
  warn "APK default não encontrado em $APK_PATH, tentando localizar"
  CANDIDATE="$(find app/build/outputs/apk -type f -name '*.apk' | head -n 1 || true)"
  if [ -n "$CANDIDATE" ]; then
    APK_PATH="$CANDIDATE"
  fi
fi

echo "$APK_PATH" > "$REPORT_DIR/apk_path.txt"
artifact_metrics "$APK_PATH"
verify_apk_signing "$APK_PATH" | tee "$REPORT_DIR/signing_verify.log"

if command -v sha256sum >/dev/null 2>&1; then
  sha256sum "$APK_PATH" | tee "$REPORT_DIR/apk_sha256.txt"
fi

log "pipeline concluído com sucesso"
