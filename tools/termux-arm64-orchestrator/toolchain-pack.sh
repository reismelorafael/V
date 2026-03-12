#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

LOG_PREFIX="[toolchain-pack]"
CMDLINE_TOOLS_VERSION="${CMDLINE_TOOLS_VERSION:-13114758}"
TOOLCHAIN_PACK_DIR="${TOOLCHAIN_PACK_DIR:-$ROOT_DIR/.toolchain-packs}"
URL="https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip"
EXPECTED_SHA256="7ec965280a073311c339e571cd5de778b9975026cfcbe79f2b1cdcb1e15317ee"
OUT="$TOOLCHAIN_PACK_DIR/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip"

log(){ echo "$LOG_PREFIX $*"; }

mkdir -p "$TOOLCHAIN_PACK_DIR"

log "download cmdline-tools pack: $URL"
curl -fsSL --connect-timeout 20 --max-time 300 -o "$OUT" "$URL"

ACTUAL_SHA256="$(sha256sum "$OUT" | awk '{print $1}')"
if [[ "$ACTUAL_SHA256" != "$EXPECTED_SHA256" ]]; then
  echo "$LOG_PREFIX checksum mismatch expected=$EXPECTED_SHA256 actual=$ACTUAL_SHA256" >&2
  exit 1
fi

log "pack ready: $OUT"
log "sha256: $ACTUAL_SHA256"
