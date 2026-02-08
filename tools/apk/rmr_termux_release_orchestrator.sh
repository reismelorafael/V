#!/usr/bin/env sh
# Orquestrador de referência para Termux arm64 (ético/legal).
# Uso:
#   sh tools/apk/rmr_termux_release_orchestrator.sh <keystore> <store_pass> <alias> <key_pass>

set -eu

if [ "$#" -ne 4 ]; then
  echo "uso: $0 <keystore> <store_pass> <alias> <key_pass>"
  exit 1
fi

KEYSTORE="$1"
STORE_PASS="$2"
ALIAS="$3"
KEY_PASS="$4"

if [ "$ALIAS" = "androiddebugkey" ]; then
  echo "erro: alias de debug não é permitido em release"
  exit 2
fi

export TERMUX_BUILD=1
export GRADLE_USER_HOME=.gradle

./gradlew --no-daemon :app:clean :app:assembleRelease \
  -Pvectras.universal=true \
  -Pvectras.compliance.profile=IEEE_NIST_W3C_RFC_GDPR_LGPD \
  -Pvectras.signing.ethical=true \
  -Pandroid.injected.signing.store.file="$KEYSTORE" \
  -Pandroid.injected.signing.store.password="$STORE_PASS" \
  -Pandroid.injected.signing.key.alias="$ALIAS" \
  -Pandroid.injected.signing.key.password="$KEY_PASS"
