#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ALLOWLIST_FILE="$ROOT_DIR/.ci/sensitive-allowlist.txt"

mapfile -t tracked_files < <(git -C "$ROOT_DIR" ls-files)

is_builtin_allowlisted() {
  local path="$1"
  case "$path" in
    app/google-services.json) return 0 ;;
  esac
  return 1
}

is_file_allowlisted() {
  local path="$1"
  [[ -f "$ALLOWLIST_FILE" ]] || return 1

  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line%$'\r'}"
    line="${line%%#*}"
    line="${line#${line%%[![:space:]]*}}"
    line="${line%${line##*[![:space:]]}}"
    [[ -z "$line" ]] && continue
    if [[ "$line" == "$path" ]]; then
      return 0
    fi
  done < "$ALLOWLIST_FILE"

  return 1
}

is_allowlisted() {
  local path="$1"
  is_builtin_allowlisted "$path" && return 0
  is_file_allowlisted "$path" && return 0
  return 1
}

sensitive_name_regex='(\.jks$|\.keystore$|\.p12$|\.pfx$|(^|/)id_rsa($|\.)|(^|/)id_dsa($|\.)|(^|/)google-services\.json$|(^|/)(secrets?|credentials?)\.(json|ya?ml|env|txt)$)'

violations=()
for file in "${tracked_files[@]}"; do
  if [[ "$file" =~ $sensitive_name_regex ]]; then
    if ! is_allowlisted "$file"; then
      violations+=("$file")
    fi
  fi
done

if ((${#violations[@]})); then
  echo "[sensitive-check] Arquivos sensíveis bloqueados (sem exceção documentada):" >&2
  printf ' - %s\n' "${violations[@]}" >&2
  echo "[sensitive-check] Use .ci/sensitive-allowlist.txt para exceções justificadas." >&2
  exit 1
fi

if rg -n --hidden --glob '!**/.git/**' --glob '!**/*.md' --glob '!**/*.png' --glob '!**/*.jpg' --glob '!**/*.jpeg' --glob '!**/*.gif' --glob '!**/*.svg' \
  "(storePassword\s*['\"][^'\"]+['\"]|keyPassword\s*['\"][^'\"]+['\"]|AKIA[0-9A-Z]{16}|-----BEGIN (RSA|OPENSSH|EC|DSA) PRIVATE KEY-----)" "$ROOT_DIR" >/tmp/sensitive_pattern_hits.txt; then
  echo "[sensitive-check] Padrões de credenciais encontrados:" >&2
  cat /tmp/sensitive_pattern_hits.txt >&2
  exit 1
fi

echo "[sensitive-check] OK: nenhum artefato sensível novo sem exceção documentada."
