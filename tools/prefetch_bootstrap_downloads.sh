#!/usr/bin/env bash
set -euo pipefail

# Baixa e instala os mesmos componentes do bootstrap em um diretório dentro do repositório.
# Mantém tudo em archive/download-mirror para posterior compactação/versionamento.
# Uso:
#   ./tools/prefetch_bootstrap_downloads.sh

if git rev-parse --show-toplevel >/dev/null 2>&1; then
  ROOT_DIR="$(git rev-parse --show-toplevel)"
else
  SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
  ROOT_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd -P)"
fi
cd "$ROOT_DIR"

MIRROR_ROOT="${MIRROR_ROOT:-$ROOT_DIR/archive/download-mirror}"
SDK_ROOT="${SDK_ROOT:-$MIRROR_ROOT/.android-sdk}"
CACHE_HOME="${CACHE_HOME:-$MIRROR_ROOT/.android}"

mkdir -p "$MIRROR_ROOT" "$CACHE_HOME"

export ANDROID_SDK_ROOT="$SDK_ROOT"
export ANDROID_HOME="$SDK_ROOT"
export ANDROID_CACHE_HOME="$CACHE_HOME"

printf '[prefetch] ANDROID_SDK_ROOT=%s\n' "$ANDROID_SDK_ROOT"
printf '[prefetch] ANDROID_CACHE_HOME=%s\n' "$ANDROID_CACHE_HOME"

# Executa o bootstrap oficial para garantir que estamos baixando exatamente os artefatos definidos pelo projeto.
./tools/termux-arm64-orchestrator/bootstrap-termux-android15.sh


# Espelha também os pacotes Alpine que reportaram erro no setup.
./tools/mirror_alpine_apk_failures.sh "$MIRROR_ROOT/alpine-apk-failures"

# Exporta tar.gz contendo repo + tudo que foi baixado/instalado.
ANDROID_SDK_ROOT="$SDK_ROOT" ANDROID_CACHE_HOME="$CACHE_HOME" ./tools/export_source_tarball.sh "$MIRROR_ROOT/source-export"

printf '[prefetch] concluído em %s\n' "$MIRROR_ROOT"
