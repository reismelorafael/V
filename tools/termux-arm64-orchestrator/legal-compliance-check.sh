#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

required_files=(
  "LICENSE"
  "THIRD_PARTY_NOTICES.md"
  "app/build.gradle"
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

echo "[compliance] legal, signing and release metadata checks passed"
