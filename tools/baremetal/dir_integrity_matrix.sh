#!/usr/bin/env bash
set -euo pipefail

out="${1:-reports/baremetal/dir_integrity_matrix.json}"
mkdir -p "$(dirname "$out")"

collect_paths() {
  # Generate deterministic lists covering all tracked files and subdirectories.
  # Fall back to filesystem scan if git metadata is unavailable.
  if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    git ls-files | awk -F/ '
      {
        print $0 "\tfile";
        if (NF == 1) {
          print ".\tdir";
        } else {
          path="";
          for (i = 1; i < NF; i++) {
            path = (path == "" ? $i : path "/" $i);
            print path "\tdir";
          }
        }
      }
    ' | LC_ALL=C sort -u
  else
    find . -type f -print | sed 's#^\./##' | awk -F/ '
      {
        print $0 "\tfile";
        if (NF == 1) {
          print ".\tdir";
        } else {
          path="";
          for (i = 1; i < NF; i++) {
            path = (path == "" ? $i : path "/" $i);
            print path "\tdir";
          }
        }
      }
    ' | LC_ALL=C sort -u
  fi
}

mapfile -t dirs < <(collect_paths | awk -F'\t' '$2=="dir"{print $1}' | LC_ALL=C sort -u)
mapfile -t files < <(collect_paths | awk -F'\t' '$2=="file"{print $1}' | LC_ALL=C sort -u)

hash_dir() {
  local d="$1"
  local prefix="$d"
  [ "$prefix" = "." ] && prefix=""

  {
    local f
    for f in "${files[@]}"; do
      if [ -z "$prefix" ] || [[ "$f" == "$prefix"/* ]]; then
        sha256sum "$f"
      fi
    done
  } | sha256sum | awk '{print $1}'
}

count_files() {
  local d="$1"
  local prefix="$d"
  [ "$prefix" = "." ] && prefix=""

  local n=0
  local f
  for f in "${files[@]}"; do
    if [ -z "$prefix" ] || [[ "$f" == "$prefix"/* ]]; then
      n=$((n+1))
    fi
  done

  echo "$n"
}

{
  echo "{"
  echo '  "schema": "baremetal-integrity-v1",'
  echo '  "directories": ['
  n=${#dirs[@]}
  i=0
  for d in "${dirs[@]}"; do
    i=$((i+1))
    h="$(hash_dir "$d")"
    c="$(count_files "$d")"
    comma=","
    [ "$i" -eq "$n" ] && comma=""
    echo "    {\"path\": \"$d\", \"files\": $c, \"sha256_tree\": \"$h\"}$comma"
  done
  echo "  ]"
  echo "}"
} > "$out"

cat "$out"
