#!/usr/bin/env bash
set -euo pipefail

# Exporta:
# 1) código-fonte versionado do repositório
# 2) downloads/caches e código-fonte instalado por bootstrap Android SDK/NDK (quando presentes)
#
# Uso:
#   ./tools/export_source_tarball.sh [destino_base]
#
# Exemplo:
#   ./tools/export_source_tarball.sh archive/source-export

BASE_DIR="${1:-archive/source-export}"
if git rev-parse --show-toplevel >/dev/null 2>&1; then
  ROOT_DIR="$(git rev-parse --show-toplevel)"
  IN_GIT_REPO=1
else
  SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
  ROOT_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd -P)"
  IN_GIT_REPO=0
fi
cd "$ROOT_DIR"

STAMP="$(date +%Y%m%d-%H%M%S)"
EXPORT_DIR="${BASE_DIR}/vectras-source-${STAMP}"
REPO_SRC_DIR="${EXPORT_DIR}/repo-sources"
EXT_DIR="${EXPORT_DIR}/post-download-installed"
MANIFEST="${EXPORT_DIR}/manifest.txt"
REPO_LIST="${EXPORT_DIR}/repo-sources.list"
EXT_LIST="${EXPORT_DIR}/post-download-installed.list"
TARBALL="${EXPORT_DIR}/vectras-source-${STAMP}.tar.gz"

ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ROOT_DIR/.android-sdk}"
ANDROID_CACHE_HOME="${ANDROID_CACHE_HOME:-$HOME/.android}"

mkdir -p "$REPO_SRC_DIR" "$EXT_DIR"

printf 'export_timestamp=%s\n' "$(date -u +"%Y-%m-%dT%H:%M:%SZ")" > "$MANIFEST"
printf 'root_dir=%s\n' "$ROOT_DIR" >> "$MANIFEST"
printf 'android_sdk_root=%s\n' "$ANDROID_SDK_ROOT" >> "$MANIFEST"
printf 'android_cache_home=%s\n' "$ANDROID_CACHE_HOME" >> "$MANIFEST"

# 1) Repo sources versionados
if [[ "$IN_GIT_REPO" -eq 1 ]]; then
  git ls-files | awk '
    /\.(java|kt|kts|c|cc|cpp|cxx|h|hh|hpp|hxx|s|S|asm|aidl|rs|proto|gradle|properties|xml|json|yml|yaml|sh|mk|cmake|txt|md)$/ {
      print
    }
  ' > "$REPO_LIST"
else
  find . -type f -print | sed 's#^\./##' | awk '
    /\.(java|kt|kts|c|cc|cpp|cxx|h|hh|hpp|hxx|s|S|asm|aidl|rs|proto|gradle|properties|xml|json|yml|yaml|sh|mk|cmake|txt|md)$/ {
      print
    }
  ' | LC_ALL=C sort -u > "$REPO_LIST"
fi

if [[ -s "$REPO_LIST" ]]; then
  while IFS= read -r file; do
    mkdir -p "$REPO_SRC_DIR/$(dirname "$file")"
    cp -a "$file" "$REPO_SRC_DIR/$file"
  done < "$REPO_LIST"
fi

# 2) Downloads + fontes instaladas pós-download (SDK/NDK/CMake)
: > "$EXT_LIST"

add_tree_if_exists() {
  local src="$1"
  local tag="$2"
  if [[ -e "$src" ]]; then
    printf '%s\t%s\n' "$tag" "$src" >> "$EXT_LIST"
  fi
}

# Evidências de download/cache
add_tree_if_exists "$ANDROID_CACHE_HOME/cache" "android-cache"
add_tree_if_exists "$ANDROID_SDK_ROOT/cmdline-tools/latest" "cmdline-tools-installed"
add_tree_if_exists "$ANDROID_SDK_ROOT/platform-tools" "platform-tools-installed"
add_tree_if_exists "$ANDROID_SDK_ROOT/build-tools" "build-tools-installed"
add_tree_if_exists "$ANDROID_SDK_ROOT/licenses" "licenses"
add_tree_if_exists "$ANDROID_SDK_ROOT/packages.xml" "packages-xml"

# Conteúdo instalado com foco em código-fonte/headers/scripts úteis para redação
if [[ -d "$ANDROID_SDK_ROOT/ndk" ]]; then
  while IFS= read -r ndk_dir; do
    add_tree_if_exists "$ndk_dir/source.properties" "ndk-meta"
    add_tree_if_exists "$ndk_dir/sources" "ndk-sources"
    add_tree_if_exists "$ndk_dir/toolchains/llvm/prebuilt" "ndk-llvm-prebuilt"
  done < <(find "$ANDROID_SDK_ROOT/ndk" -mindepth 1 -maxdepth 1 -type d | sort)
fi

if [[ -d "$ANDROID_SDK_ROOT/cmake" ]]; then
  while IFS= read -r cmake_dir; do
    add_tree_if_exists "$cmake_dir" "cmake-installed"
  done < <(find "$ANDROID_SDK_ROOT/cmake" -mindepth 1 -maxdepth 1 -type d | sort)
fi

if [[ -d "$ANDROID_SDK_ROOT/platforms" ]]; then
  while IFS= read -r platform_dir; do
    add_tree_if_exists "$platform_dir" "platform-installed"
  done < <(find "$ANDROID_SDK_ROOT/platforms" -mindepth 1 -maxdepth 1 -type d | sort)
fi

if [[ -s "$EXT_LIST" ]]; then
  while IFS=$'\t' read -r tag src; do
    rel="${src#/}"
    dest="$EXT_DIR/$tag/$rel"
    mkdir -p "$(dirname "$dest")"
    cp -a "$src" "$dest"
  done < "$EXT_LIST"
fi

tar -czf "$TARBALL" -C "$EXPORT_DIR" repo-sources post-download-installed manifest.txt repo-sources.list post-download-installed.list

printf 'Export concluído:\n'
printf '  Repo sources:            %s\n' "$REPO_SRC_DIR"
printf '  Pós-download instalados: %s\n' "$EXT_DIR"
printf '  Manifest:                %s\n' "$MANIFEST"
printf '  Tarball:                 %s\n' "$TARBALL"
