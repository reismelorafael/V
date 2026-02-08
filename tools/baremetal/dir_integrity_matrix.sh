#!/usr/bin/env bash
set -euo pipefail

out="${1:-reports/baremetal/dir_integrity_matrix.json}"
mkdir -p "$(dirname "$out")"

collect_files() {
  # Deterministic file source: prefer tracked files, fallback to filesystem.
  if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    git ls-files | LC_ALL=C sort -u
  else
    find . -type f -print | sed 's#^\./##' | LC_ALL=C sort -u
  fi
}

detect_workers() {
  # Auto-tuning for host CPU while keeping deterministic output ordering.
  local w=1
  if command -v getconf >/dev/null 2>&1; then
    w="$(getconf _NPROCESSORS_ONLN 2>/dev/null || echo 1)"
  elif command -v nproc >/dev/null 2>&1; then
    w="$(nproc 2>/dev/null || echo 1)"
  fi

  case "$w" in
    ''|*[!0-9]*) w=1 ;;
  esac
  [ "$w" -lt 1 ] && w=1
  [ "$w" -gt 16 ] && w=16
  echo "$w"
}

files_list="$(mktemp)"
idx_unsorted="$(mktemp)"
idx_file="$(mktemp)"
dirs_file="$(mktemp)"
trap 'rm -f "$files_list" "$idx_unsorted" "$idx_file" "$dirs_file"' EXIT

collect_files > "$files_list"
file_count="$(wc -l < "$files_list" | tr -d ' \t')"
workers="$(detect_workers)"

# Auto-tuning: avoid process-fanout overhead on small trees.
if [ "$file_count" -lt 2000 ]; then
  workers=1
fi

# Build deterministic index with one file hash per file.
if [ "$workers" -eq 1 ]; then
  while IFS= read -r f; do
    [ -f "$f" ] || continue
    h="$(sha256sum "$f" | awk '{print $1}')"
    printf '%s\t%s\n' "$f" "$h" >> "$idx_unsorted"
  done < "$files_list"
else
  tr '\n' '\0' < "$files_list" | xargs -0 -P "$workers" -I{} sh -c '
    f="$1"
    [ -f "$f" ] || exit 0
    h=$(sha256sum "$f" | awk "{print \$1}")
    printf "%s\t%s\n" "$f" "$h"
  ' _ {} > "$idx_unsorted"
fi

LC_ALL=C sort -t $'\t' -k1,1 -u "$idx_unsorted" > "$idx_file"

awk -F'\t' '
  {
    file=$1;
    print ".";
    n=split(file, p, "/");
    if (n > 1) {
      d="";
      for (i=1; i<n; i++) {
        d=(d=="" ? p[i] : d "/" p[i]);
        print d;
      }
    }
  }
' "$idx_file" | LC_ALL=C sort -u > "$dirs_file"

count_files() {
  local d="$1"
  local p="$d"
  [ "$p" = "." ] && p=""

  awk -F'\t' -v p="$p" '
    {
      if (p == "" || index($1, p "/") == 1) c++;
    }
    END { print c + 0 }
  ' "$idx_file"
}

hash_dir() {
  local d="$1"
  local p="$d"
  [ "$p" = "." ] && p=""

  awk -F'\t' -v p="$p" '
    {
      if (p == "" || index($1, p "/") == 1) print $2 "  " $1;
    }
  ' "$idx_file" | sha256sum | awk '{print $1}'
}

n="$(wc -l < "$dirs_file" | tr -d ' ')"
i=0

{
  echo "{"
  echo '  "schema": "baremetal-integrity-v1",'
  echo '  "directories": ['
  while IFS= read -r d; do
    i=$((i+1))
    h="$(hash_dir "$d")"
    c="$(count_files "$d")"
    comma=","
    [ "$i" -eq "$n" ] && comma=""
    echo "    {\"path\": \"$d\", \"files\": $c, \"sha256_tree\": \"$h\"}$comma"
  done < "$dirs_file"
  echo "  ]"
  echo "}"
} > "$out"

cat "$out"
