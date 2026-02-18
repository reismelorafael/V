#!/usr/bin/env bash
set -euo pipefail

find_java_home() {
  local candidate

  if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
    printf '%s\n' "$JAVA_HOME"
    return 0
  fi

  local mise_root="${MISE_DATA_DIR:-$HOME/.local/share/mise}/installs/java"
  if [[ -d "$mise_root" ]]; then
    for candidate in "$mise_root"/21* "$mise_root"/17*; do
      if [[ -x "$candidate/bin/java" ]]; then
        printf '%s\n' "$candidate"
        return 0
      fi
    done
  fi

  local candidates=(
    "/usr/lib/jvm/java-21-openjdk-amd64"
    "/usr/lib/jvm/java-21-openjdk"
    "/usr/lib/jvm/temurin-21-jdk-amd64"
    "/usr/lib/jvm/java-17-openjdk-amd64"
    "/usr/lib/jvm/java-17-openjdk"
  )

  local c
  for c in "${candidates[@]}"; do
    if [[ -x "$c/bin/java" ]]; then
      printf '%s\n' "$c"
      return 0
    fi
  done

  return 1
}

JAVA_HOME_DETECTED="$(find_java_home || true)"
if [[ -z "$JAVA_HOME_DETECTED" ]]; then
  echo "ERRO: JDK 21/17 não encontrado para configurar JAVA_HOME." >&2
  exit 2
fi

if [[ "${1:-}" == "--print" ]]; then
  printf 'export JAVA_HOME=%q\n' "$JAVA_HOME_DETECTED"
  printf 'export PATH=%q\n' "$JAVA_HOME_DETECTED/bin:\$PATH"
  exit 0
fi

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  echo "JAVA_HOME detectado: $JAVA_HOME_DETECTED"
  echo "Use no shell atual:"
  echo "  source <(./tools/configure_java_home.sh --print)"
  echo
  ./tools/configure_java_home.sh --print
fi
