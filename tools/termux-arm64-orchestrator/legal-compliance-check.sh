#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

required_files=(
  "LICENSE"
  "THIRD_PARTY_NOTICES.md"
  "app/build.gradle"
)

for file in "${required_files[@]}"; do
  if [[ ! -f "$file" ]]; then
    echo "[compliance] missing required file: $file" >&2
    exit 1
  fi
done

check_legal_requirements() {
  local legal_files=(
    "LICENSE"
    "THIRD_PARTY_NOTICES.md"
  )

  for file in "${legal_files[@]}"; do
    if [[ ! -f "$file" ]]; then
      echo "[compliance][legal] missing required file: $file" >&2
      exit 1
    fi
  done
}

check_release_signing_requirements() {
  local gradle_file="app/build.gradle"

  if ! rg -n "^\s*signingConfigs\s*\{" "$gradle_file" >/dev/null; then
    echo "[compliance][signing] signingConfigs block not found in $gradle_file" >&2
    echo "[compliance][signing] configure release signing through CI secrets/variables (android.injected.signing.* or VECTRAS_RELEASE_*) instead of committed sensitive files." >&2
    exit 1
  fi

  local signing_property_patterns=(
    "android\.injected\.signing\.store\.file"
    "android\.injected\.signing\.store\.password"
    "android\.injected\.signing\.key\.alias"
    "android\.injected\.signing\.key\.password"
    "VECTRAS_RELEASE_STORE_FILE"
    "VECTRAS_RELEASE_STORE_PASSWORD"
    "VECTRAS_RELEASE_KEY_ALIAS"
    "VECTRAS_RELEASE_KEY_PASSWORD"
  )

  local missing_signing_inputs=()
  for pattern in "${signing_property_patterns[@]}"; do
    if ! rg -n "$pattern" "$gradle_file" >/dev/null; then
      missing_signing_inputs+=("$pattern")
    fi
  done

  if [[ "${#missing_signing_inputs[@]}" -gt 0 ]]; then
    printf '[compliance][signing] signing input mapping is incomplete in %s. Missing references: %s\n' "$gradle_file" "${missing_signing_inputs[*]}" >&2
    echo "[compliance][signing] expose release credentials via CI secrets/variables (android.injected.signing.* and/or VECTRAS_RELEASE_*) and avoid hardcoded keystore paths or aliases in Git." >&2
    exit 1
  fi

  if ! rg -n "tasks\.register\(\s*\"validateReleaseSigningConfig\"" "$gradle_file" >/dev/null; then
    echo "[compliance][signing] release signing gate task not found (expected validateReleaseSigningConfig or equivalent)." >&2
    echo "[compliance][signing] enforce release signing at build time using a dedicated validation gate backed by CI secrets/variables." >&2
    exit 1
  fi

  if ! rg -n "dependsOn\(tasks\.named\(\"validateReleaseSigningConfig\"\)\)" "$gradle_file" >/dev/null; then
    echo "[compliance][signing] validateReleaseSigningConfig is not wired to release lifecycle tasks." >&2
    echo "[compliance][signing] ensure preRelease/prePerfRelease (or equivalent release gates) depend on signing validation configured via secrets/variables." >&2
    exit 1
  fi
}

check_release_metadata_requirements() {
  local gradle_file="app/build.gradle"

  if ! rg -n "targetSdk\s*=\s*(rootProject\.ext\.)?targetApi" "$gradle_file" >/dev/null; then
    echo "[compliance][metadata] targetSdk declaration not found in $gradle_file" >&2
    exit 1
  fi
}

check_legal_requirements
check_release_signing_requirements
check_release_metadata_requirements

echo "[compliance] legal checks passed"
echo "[compliance] signing checks passed"
echo "[compliance] release metadata checks passed"
