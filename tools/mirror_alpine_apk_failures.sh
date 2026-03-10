#!/usr/bin/env bash
set -euo pipefail

# Espelha pacotes .apk do Alpine (inclusive os que falharam no setup) para dentro do repositório.
# Uso:
#   ./tools/mirror_alpine_apk_failures.sh [destino]
#
# Variáveis opcionais:
#   ALPINE_ARCH (default: aarch64)
#   ALPINE_REPOS (linhas com URLs base de repositório)

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if git rev-parse --show-toplevel >/dev/null 2>&1; then
  ROOT_DIR="$(git rev-parse --show-toplevel)"
else
  ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
fi
cd "$ROOT_DIR"

DEST_ROOT="${1:-archive/download-mirror/alpine-apk-failures}"
ARCH="${ALPINE_ARCH:-aarch64}"

mkdir -p "$DEST_ROOT/index" "$DEST_ROOT/apk"

REPOS_FILE="$DEST_ROOT/repos.list"
REQ_FILE="$DEST_ROOT/requested-packages.list"
RESOLVED_FILE="$DEST_ROOT/resolved-packages.tsv"
MISSING_FILE="$DEST_ROOT/missing-packages.list"

if [[ -n "${ALPINE_REPOS:-}" ]]; then
  printf '%s\n' "$ALPINE_REPOS" > "$REPOS_FILE"
else
  cat > "$REPOS_FILE" <<REPOS
https://mirror.uepg.br/alpine/v3.19/main
https://mirror.uepg.br/alpine/v3.19/community
https://mirror.uepg.br/alpine/edge/testing
REPOS
fi

cat > "$REQ_FILE" <<'PKG'
spirv-llvm-translator-libs-17.0.0-r0
libclc-17.0.5-r0
sdl2-dev-2.28.5-r0
sdl2_image-2.6.3-r0
PKG

: > "$RESOLVED_FILE"
: > "$MISSING_FILE"

fetch_index() {
  local repo="$1"
  local idx_path="$DEST_ROOT/index/$(echo "$repo" | sed 's#https\?://##; s#[^A-Za-z0-9._-]#_#g')_${ARCH}_APKINDEX.tar.gz"
  curl -fsSL --retry 5 --retry-delay 2 -o "$idx_path" "$repo/$ARCH/APKINDEX.tar.gz"
  echo "$idx_path"
}

parse_index() {
  local idx_tgz="$1"
  local repo="$2"
  tar -xOf "$idx_tgz" APKINDEX | awk -v repo="$repo" '
    BEGIN { pkg=""; ver="" }
    /^P:/ { pkg=substr($0,3) }
    /^V:/ { ver=substr($0,3) }
    /^$/ {
      if (pkg != "" && ver != "") {
        printf "%s\t%s\t%s\n", pkg, ver, repo
      }
      pkg=""; ver=""
    }
  '
}

INDEX_DATA="$DEST_ROOT/index/all-packages.tsv"
: > "$INDEX_DATA"
while IFS= read -r repo; do
  [[ -z "$repo" ]] && continue
  idx="$(fetch_index "$repo")"
  parse_index "$idx" "$repo" >> "$INDEX_DATA"
done < "$REPOS_FILE"

while IFS= read -r requested; do
  [[ -z "$requested" ]] && continue

  pkg_name="${requested%-*-r*}"
  pkg_ver_rel="${requested#${pkg_name}-}"

  match_line="$(awk -F'\t' -v p="$pkg_name" -v v="$pkg_ver_rel" '$1==p && $2==v {print; exit}' "$INDEX_DATA" || true)"

  if [[ -z "$match_line" ]]; then
    printf '%s\n' "$requested" >> "$MISSING_FILE"
    continue
  fi

  repo="$(printf '%s' "$match_line" | awk -F'\t' '{print $3}')"
  apk_name="${requested}.apk"
  apk_url="$repo/$ARCH/$apk_name"
  out="$DEST_ROOT/apk/$apk_name"

  curl -fsSL --retry 5 --retry-delay 2 -o "$out" "$apk_url"
  printf '%s\t%s\t%s\n' "$requested" "$repo" "$apk_name" >> "$RESOLVED_FILE"
done < "$REQ_FILE"

printf 'Mirror Alpine concluído:\n'
printf '  destino:   %s\n' "$DEST_ROOT"
printf '  resolvidos:%s\n' "$(wc -l < "$RESOLVED_FILE")"
printf '  faltantes: %s\n' "$(wc -l < "$MISSING_FILE")"
