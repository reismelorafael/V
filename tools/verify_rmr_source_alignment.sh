#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

python3 tools/sync_rmr_manifest_to_mk.py --check

if ! rg -Fq 'include(${CMAKE_SOURCE_DIR}/engine/rmr/sources_rmr_core.cmake)' CMakeLists.txt; then
  echo "[rmr-manifest] root CMakeLists.txt does not include canonical manifest" >&2
  exit 1
fi

if ! rg -Fq 'include engine/rmr/sources_rmr_core.mk' Makefile; then
  echo "[rmr-manifest] Makefile does not include generated canonical mk manifest" >&2
  exit 1
fi

if ! rg -Fq 'include(${VECTRA_REPO_ROOT}/engine/rmr/sources_rmr_core.cmake)' app/src/main/cpp/CMakeLists.txt; then
  echo "[rmr-manifest] Android CMakeLists.txt does not include canonical manifest" >&2
  exit 1
fi

echo "[rmr-manifest] source alignment verified"
