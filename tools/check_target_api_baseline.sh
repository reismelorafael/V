#!/usr/bin/env bash
set -euo pipefail

file="${1:-gradle.properties}"

if [[ ! -f "$file" ]]; then
  echo "Arquivo não encontrado: $file" >&2
  exit 1
fi

target_api="$(awk -F= '/^TARGET_API=/{print $2}' "$file" | tail -n1 | tr -d '[:space:]')"
release_min_target_api="$(awk -F= '/^RELEASE_MIN_TARGET_API=/{print $2}' "$file" | tail -n1 | tr -d '[:space:]')"

if [[ -z "$target_api" ]]; then
  echo "TARGET_API não definido em $file" >&2
  exit 1
fi

if [[ -z "$release_min_target_api" ]]; then
  echo "RELEASE_MIN_TARGET_API não definido em $file" >&2
  exit 1
fi

if ! [[ "$target_api" =~ ^[0-9]+$ && "$release_min_target_api" =~ ^[0-9]+$ ]]; then
  echo "Valores inválidos: TARGET_API=$target_api RELEASE_MIN_TARGET_API=$release_min_target_api" >&2
  exit 1
fi

if (( target_api < release_min_target_api )); then
  echo "Falha: TARGET_API=$target_api está abaixo do mínimo de release RELEASE_MIN_TARGET_API=$release_min_target_api" >&2
  exit 1
fi

echo "OK: TARGET_API=$target_api atende ao mínimo de release RELEASE_MIN_TARGET_API=$release_min_target_api"
