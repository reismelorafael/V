#!/usr/bin/env bash
set -euo pipefail

resolve_release_keystore() {
  local root_dir="$1"
  local log_prefix="$2"
  local private_local_keystore_fallback="$root_dir/.secrets/vectras-release.jks"
  local release_store_file="${VECTRAS_RELEASE_STORE_FILE:-}"

  if [[ -z "$release_store_file" && -f "$private_local_keystore_fallback" ]]; then
    release_store_file="$private_local_keystore_fallback"
    echo "$log_prefix usando fallback local privado de keystore em $private_local_keystore_fallback"
  fi

  if [[ -z "$release_store_file" ]]; then
    echo "$log_prefix keystore release ausente. Defina VECTRAS_RELEASE_STORE_FILE ou crie $private_local_keystore_fallback (fora do Git)." >&2
    exit 1
  fi

  if [[ ! -f "$release_store_file" ]]; then
    echo "$log_prefix keystore informado em VECTRAS_RELEASE_STORE_FILE não encontrado: $release_store_file" >&2
    exit 1
  fi

  export VECTRAS_RELEASE_STORE_FILE="$release_store_file"
}
