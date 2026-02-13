#!/usr/bin/env bash
set -euo pipefail

JDK21_ROOT="/root/.local/share/mise/installs/java/21.0.2"
if [ ! -x "$JDK21_ROOT/bin/java" ]; then
  echo "JDK 21.0.2 não encontrado em $JDK21_ROOT" >&2
  echo "Instale/aponte JDK 21 e execute novamente." >&2
  exit 2
fi

export JAVA_HOME="$JDK21_ROOT"
export PATH="$JAVA_HOME/bin:$PATH"

exec ./gradlew "$@"
