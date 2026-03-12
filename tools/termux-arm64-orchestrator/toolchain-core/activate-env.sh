#!/usr/bin/env bash
set -euo pipefail

ENV_FILE="${1:-}"
if [[ -z "$ENV_FILE" || ! -f "$ENV_FILE" ]]; then
  echo "usage: $0 <toolchain-env-file>" >&2
  exit 1
fi

while IFS='=' read -r key value; do
  [[ -z "$key" ]] && continue
  [[ "$key" =~ ^# ]] && continue
  export "$key=$value"
done < "$ENV_FILE"

PATH_SEGMENTS=()
[[ -n "${JAVA_HOME:-}" ]] && PATH_SEGMENTS+=("$JAVA_HOME/bin")
PATH_SEGMENTS+=(
  "${ANDROID_SDK_ROOT:-}/platform-tools"
  "${ANDROID_BUILD_TOOLS_DIR:-}"
  "${ANDROID_CMAKE_ROOT:-}/bin"
  "${ANDROID_NDK_ROOT:-}"
)

for seg in "${PATH_SEGMENTS[@]}"; do
  [[ -n "$seg" ]] || continue
  case ":$PATH:" in
    *":$seg:"*) ;;
    *) PATH="$seg:$PATH" ;;
  esac
done

printf 'export JAVA_HOME=%q\n' "${JAVA_HOME:-}"
printf 'export ANDROID_HOME=%q\n' "${ANDROID_HOME:-}"
printf 'export ANDROID_SDK_ROOT=%q\n' "${ANDROID_SDK_ROOT:-}"
printf 'export ANDROID_NDK_ROOT=%q\n' "${ANDROID_NDK_ROOT:-}"
printf 'export ANDROID_NDK_HOME=%q\n' "${ANDROID_NDK_ROOT:-}"
printf 'export ANDROID_CMAKE_ROOT=%q\n' "${ANDROID_CMAKE_ROOT:-}"
printf 'export PATH=%q\n' "$PATH"
