#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

TOOLCHAIN_LICENSES_FILE="tools/termux-arm64-orchestrator/TOOLCHAIN_LICENSES.md"
TOOLCHAIN_BOM_FILE="tools/termux-arm64-orchestrator/toolchain-manifests/toolchain-bom.json"

required_files=(
  "LICENSE"
  "THIRD_PARTY_NOTICES.md"
  "app/build.gradle"
  "$TOOLCHAIN_LICENSES_FILE"
  "$TOOLCHAIN_BOM_FILE"
)

for file in "${required_files[@]}"; do
  if [[ ! -f "$file" ]]; then
    echo "[compliance] missing required file: $file" >&2
    exit 1
  fi
done

validate_toolchain_bom() {
  local bom_file="$1"

  python3 - "$bom_file" <<'PY'
import json
import sys
from pathlib import Path

bom_path = Path(sys.argv[1])
required_fields = ("name", "version", "source", "sha256", "license")

try:
    raw = bom_path.read_text(encoding="utf-8")
except OSError:
    print(f"[compliance] toolchain BOM unreadable: {bom_path}", file=sys.stderr)
    raise SystemExit(1)

try:
    doc = json.loads(raw)
except json.JSONDecodeError as exc:
    print(f"[compliance] invalid toolchain BOM JSON at line {exc.lineno}, column {exc.colno}", file=sys.stderr)
    raise SystemExit(1)

components = doc.get("components")
if not isinstance(components, list) or not components:
    print("[compliance] toolchain BOM must contain a non-empty 'components' array", file=sys.stderr)
    raise SystemExit(1)

for idx, component in enumerate(components):
    item_ref = f"components[{idx}]"
    if not isinstance(component, dict):
        print(f"[compliance] {item_ref} must be an object", file=sys.stderr)
        raise SystemExit(1)

    for field in required_fields:
        if field not in component:
            print(f"[compliance] {item_ref} missing field: {field}", file=sys.stderr)
            raise SystemExit(1)
        value = component[field]
        if not isinstance(value, str) or not value.strip():
            print(f"[compliance] {item_ref}.{field} must be a non-empty string", file=sys.stderr)
            raise SystemExit(1)
PY
}

validate_toolchain_bom "$TOOLCHAIN_BOM_FILE"

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

required_signing_vars=(
  "VECTRAS_RELEASE_STORE_FILE"
  "VECTRAS_RELEASE_STORE_PASSWORD"
  "VECTRAS_RELEASE_KEY_ALIAS"
  "VECTRAS_RELEASE_KEY_PASSWORD"
)

for var_name in "${required_signing_vars[@]}"; do
  if [[ -z "${!var_name:-}" ]]; then
    echo "[compliance] missing required release signing variable: ${var_name}" >&2
    exit 1
  fi
done

if [[ "${VECTRAS_RELEASE_STORE_FILE:0:1}" != "/" ]]; then
  echo "[compliance] invalid VECTRAS_RELEASE_STORE_FILE: expected absolute path" >&2
  exit 1
fi

if [[ ! -f "$VECTRAS_RELEASE_STORE_FILE" ]]; then
  echo "[compliance] invalid VECTRAS_RELEASE_STORE_FILE: file not found" >&2
  exit 1
fi

if [[ -z "${VECTRAS_RELEASE_KEY_ALIAS//[[:space:]]/}" ]]; then
  echo "[compliance] invalid VECTRAS_RELEASE_KEY_ALIAS: alias must be non-empty" >&2
  exit 1
fi

if ! rg -n '"ndk;\$\{ANDROID_NDK_VERSION\}"|"ndk;\$ANDROID_NDK_VERSION"' tools/termux-arm64-orchestrator/bootstrap-termux-android15.sh >/dev/null; then
  echo "[compliance] bootstrap-termux-android15.sh must pin NDK installation via ANDROID_NDK_VERSION" >&2
  exit 1
fi

if ! rg -n '"cmake;\$\{ANDROID_CMAKE_VERSION\}"|"cmake;\$ANDROID_CMAKE_VERSION"' tools/termux-arm64-orchestrator/bootstrap-termux-android15.sh >/dev/null; then
  echo "[compliance] bootstrap-termux-android15.sh must pin CMake installation via ANDROID_CMAKE_VERSION" >&2
  exit 1
fi

if ! rg -n 'JDK 21|JDK 17|JAVA_HOME' tools/gradle_with_jdk21.sh >/dev/null; then
  echo "[compliance] tools/gradle_with_jdk21.sh must enforce local JDK selection (21/17)" >&2
  exit 1
fi


if ! rg -n '"name"\s*:\s*"android-cmdline-tools"|"name"\s*:\s*"android-ndk"|"name"\s*:\s*"android-cmake"|"name"\s*:\s*"jdk"' tools/termux-arm64-orchestrator/toolchain-manifests/toolchain-bom.json >/dev/null; then
  echo "[compliance] toolchain-bom.json must declare android-cmdline-tools/android-ndk/android-cmake/jdk components" >&2
  exit 1
fi

if ! rg -n '"version"\s*:|"source"\s*:|"sha256"\s*:|"license"\s*:' tools/termux-arm64-orchestrator/toolchain-manifests/toolchain-bom.json >/dev/null; then
  echo "[compliance] toolchain-bom.json missing mandatory metadata keys (version/source/sha256/license)" >&2
  exit 1
fi


if ! rg -n '"schema"\s*:\s*"vectras-termux-forks/v1"|"forks"\s*:' tools/termux-arm64-orchestrator/fork-manifests/forks-sources.json >/dev/null; then
  echo "[compliance] forks-sources.json must declare schema vectras-termux-forks/v1 and forks array" >&2
  exit 1
fi

echo "[compliance] legal checks passed"
echo "[compliance] toolchain manifest checks passed"
echo "[compliance] signing checks passed"
echo "[compliance] release metadata checks passed"
