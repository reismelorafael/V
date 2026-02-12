#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

LOG_PREFIX="[termux-local-build]"

log(){ echo "$LOG_PREFIX $*"; }

if [[ -n "${GITHUB_ACTIONS:-}" ]]; then
  echo "$LOG_PREFIX este entrypoint é exclusivo para build local em terminal (não GitHub Actions)." >&2
  exit 1
fi

if [[ ! -f "$ROOT_DIR/vectras.jks" ]]; then
  echo "$LOG_PREFIX keystore obrigatório ausente: $ROOT_DIR/vectras.jks" >&2
  exit 1
fi

export BOOTSTRAP_ANDROID="${BOOTSTRAP_ANDROID:-1}"
export ENABLE_SPILL="${ENABLE_SPILL:-1}"
export CI_DRY_RUN="${CI_DRY_RUN:-0}"

log "iniciando bootstrap + compliance + build local"
if [[ "$BOOTSTRAP_ANDROID" == "1" ]]; then
  bash tools/termux-arm64-orchestrator/bootstrap-termux-android15.sh
else
  log "bootstrap desabilitado por BOOTSTRAP_ANDROID=$BOOTSTRAP_ANDROID"
fi
bash tools/termux-arm64-orchestrator/legal-compliance-check.sh
bash tools/termux-arm64-orchestrator/orchestrate-build.sh
log "build local concluído"
