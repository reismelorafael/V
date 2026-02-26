#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Fonte de verdade: nomes internos do BITOMEGA_OVERLAY__V1.zip
expected_files=(
  "00_THESIS_OVERVIEW.md"
  "01_FORMALISM_BITOMEGA.md"
  "02_TRANSITION_GRAPH.md"
  "03_IMPLEMENTATION_MAP.md"
  "04_EXPERIMENTS.md"
  "05_RESULTS_TABLES.md"
  "06_LIMITATIONS_NEXT.md"
)

# Aliases legados (não-canônicos) que NÃO fazem parte da convenção do ZIP.
deprecated_aliases=(
  "01_FOUNDATIONS.md"
  "02_METHODS.md"
  "03_RESULTS.md"
  "04_IMPL_DETAILS.md"
  "05_VALIDATION.md"
)

missing=0
for file in "${expected_files[@]}"; do
  if [[ ! -f "${ROOT_DIR}/${file}" ]]; then
    echo "[bitomega-postdoc] missing: ${file}" >&2
    missing=1
  fi
done

for alias in "${deprecated_aliases[@]}"; do
  if [[ -f "${ROOT_DIR}/${alias}" ]]; then
    echo "[bitomega-postdoc] aviso: alias legado detectado (ignorado): ${alias}" >&2
  fi
done

if [[ "${missing}" -ne 0 ]]; then
  echo "[bitomega-postdoc] validação falhou: conjunto incompleto" >&2
  exit 1
fi

echo "[bitomega-postdoc] validação OK (${#expected_files[@]} arquivos)"
