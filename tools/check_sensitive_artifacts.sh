#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
ALLOWLIST_FILE="$REPO_ROOT/security/sensitive-artifacts-allowlist.txt"

if [[ ! -f "$ALLOWLIST_FILE" ]]; then
  echo "ERROR: allowlist file not found: $ALLOWLIST_FILE"
  exit 1
fi

is_allowlisted() {
  local path="$1"
  grep -Fqx "$path" "$ALLOWLIST_FILE"
}

resolve_range() {
  if [[ -n "${SENSITIVE_CHECK_RANGE:-}" ]]; then
    echo "$SENSITIVE_CHECK_RANGE"
    return
  fi
  if git rev-parse --verify HEAD~1 >/dev/null 2>&1; then
    echo "HEAD~1..HEAD"
    return
  fi
  echo ""
}

RANGE="$(resolve_range)"
violations=()

if [[ -n "$RANGE" ]]; then
  mapfile -t ADDED_FILES < <(git diff --name-only --diff-filter=A "$RANGE")
else
  mapfile -t ADDED_FILES < <(git ls-files)
fi

for file in "${ADDED_FILES[@]}"; do
  case "$file" in
    *.jks|*.keystore|*.p12|*.pfx)
      if ! is_allowlisted "$file"; then
        violations+=("sensitive-binary:$file")
      fi
      ;;
  esac
done

added_lines=""
if [[ -n "$RANGE" ]]; then
  added_lines="$(git diff -U0 "$RANGE" | grep -E '^\+[^+]' || true)"
fi

if [[ -n "$added_lines" ]]; then
  credential_pattern='storePassword\s*[=:]\s*["'\''`][^"'\''`]+["'\''`]|keyPassword\s*[=:]\s*["'\''`][^"'\''`]+["'\''`]|AKIA[0-9A-Z]{16}|-----BEGIN (RSA|EC|OPENSSH|DSA|PRIVATE) KEY-----|AIza[0-9A-Za-z\-_]{35}|ghp_[0-9A-Za-z]{36}|xox[baprs]-[0-9A-Za-z-]{10,}'
  if echo "$added_lines" | grep -Eiq "$credential_pattern"; then
    violations+=("credential-pattern:added-lines")
  fi
fi

if (( ${#violations[@]} > 0 )); then
  echo "Sensitive artifact check failed. Violations:"
  printf ' - %s\n' "${violations[@]}"
  echo "If a file is strictly required, document the exception in security/sensitive-artifacts-allowlist.txt."
  exit 1
fi

echo "Sensitive artifact check passed."
