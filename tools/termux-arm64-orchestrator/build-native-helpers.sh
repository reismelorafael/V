#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

OUT_DIR="tools/termux-arm64-orchestrator/bin"
SRC_DIR="tools/termux-arm64-orchestrator/c"
CC_BIN="${CC:-cc}"

mkdir -p "$OUT_DIR"

"$CC_BIN" -O3 -std=c11 -Wall -Wextra -Werror -o "$OUT_DIR/arm64_neon_probe" "$SRC_DIR/arm64_neon_probe.c"
"$CC_BIN" -O3 -std=c11 -Wall -Wextra -Werror -o "$OUT_DIR/storage_spill_allocator" "$SRC_DIR/storage_spill_allocator.c"

echo "native_helpers:ok"
echo "out_dir:$OUT_DIR"
