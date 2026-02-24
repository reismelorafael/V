#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

java_major_of() {
  local java_bin="$1"
  "$java_bin" -XshowSettings:properties -version 2>&1 | awk -F= '/java\.specification\.version/ {gsub(/ /,"",$2); print $2; exit}'
}

find_java_home() {
  local candidate

  if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
    local major
    major="$(java_major_of "${JAVA_HOME}/bin/java" || true)"
    if [[ "$major" == "21" || "$major" == "17" ]]; then
      printf '%s\n' "$JAVA_HOME"
      return 0
    fi
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
if [[ -z "$JAVA_HOME_DETECTED" || ! -x "$JAVA_HOME_DETECTED/bin/java" ]]; then
  echo "ERRO: JDK 21/17 não encontrado. Instale JDK 21 (preferencial) ou JDK 17 e tente novamente." >&2
  exit 2
fi

export JAVA_HOME="$JAVA_HOME_DETECTED"
export PATH="$JAVA_HOME/bin:$PATH"

JAVA_MAJOR="$(java_major_of "$JAVA_HOME/bin/java" || true)"
if [[ "$JAVA_MAJOR" != "21" && "$JAVA_MAJOR" != "17" ]]; then
  echo "ERRO: JDK incompatível ($JAVA_MAJOR). Este wrapper aceita apenas Java 21 ou 17." >&2
  exit 3
fi

echo "[gradle_with_jdk21] JAVA_HOME=$JAVA_HOME (major=$JAVA_MAJOR)"

if [[ -x "$REPO_ROOT/tools/check_android_toolchain.sh" ]]; then
  "$REPO_ROOT/tools/check_android_toolchain.sh" --quick >/dev/null || true
fi

cd "$REPO_ROOT"
exec ./gradlew "$@"
