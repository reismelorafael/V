#!/usr/bin/env bash
set -euo pipefail

if git rev-parse --show-toplevel >/dev/null 2>&1; then
  REPO_ROOT="$(git rev-parse --show-toplevel)"
else
  REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
fi

is_source_entry() {
  local entry="$1"
  case "$entry" in
    *.c|*.h|*.cc|*.cpp|*.cxx|*.hpp|*.hh|*.S|*.s|*.asm|*.java|*.kt|*.rs|*.py|*.sh|*.cmake|*/CMakeLists.txt|CMakeLists.txt|Makefile|*.mk)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

shopt -s nullglob
zip_candidates=(
  "$REPO_ROOT"/*OVERLAY*.zip
  "$REPO_ROOT"/*overlay*.zip
)
shopt -u nullglob

if (( ${#zip_candidates[@]} == 0 )); then
  echo "Overlay ZIP check passed: no overlay ZIP found at repo root."
  exit 0
fi

violations=()

for zip_path in "${zip_candidates[@]}"; do
  while IFS= read -r entry; do
    [[ -z "$entry" || "$entry" == */ ]] && continue
    is_source_entry "$entry" || continue

    worktree_path="$REPO_ROOT/$entry"
    if [[ ! -f "$worktree_path" ]]; then
      continue
    fi

    if cmp -s <(unzip -p "$zip_path" "$entry") "$worktree_path"; then
      violations+=("$(basename "$zip_path")::$entry")
    fi
  done < <(zipinfo -1 "$zip_path")
done

if (( ${#violations[@]} > 0 )); then
  echo "ERROR: overlay ZIP with duplicated source content found at repository root."
  echo "Root ZIP overlays are not source of truth; keep only the Git tree."
  echo "Duplicated entries:"
  printf ' - %s\n' "${violations[@]}"
  exit 1
fi

echo "Overlay ZIP check passed: no duplicated source entry found."
