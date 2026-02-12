#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

required_files=(
  "LICENSE"
  "THIRD_PARTY_NOTICES.md"
  "app/build.gradle"
  "vectras.jks"
)

for file in "${required_files[@]}"; do
  if [[ ! -f "$file" ]]; then
    echo "[compliance] missing required file: $file" >&2
    exit 1
  fi
done

if ! rg -n "signingConfigs" app/build.gradle >/dev/null; then
  echo "[compliance] signingConfigs block not found in app/build.gradle" >&2
  exit 1
fi

if ! rg -n "storeFile\s+file\('\.\./vectras\.jks'\)" app/build.gradle >/dev/null; then
  echo "[compliance] app/build.gradle must reference repository keystore ../vectras.jks" >&2
  exit 1
fi

if ! rg -n "keyAlias\s+'vectras'" app/build.gradle >/dev/null; then
  echo "[compliance] app/build.gradle must use keyAlias 'vectras'" >&2
  exit 1
fi

if ! rg -n "targetSdk\s*=\s*targetApi" app/build.gradle >/dev/null; then
  echo "[compliance] targetSdk declaration not found in app/build.gradle" >&2
  exit 1
fi

echo "[compliance] legal, signing and release metadata checks passed"
