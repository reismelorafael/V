#!/usr/bin/env bash
set -euo pipefail

if matches="$(rg -n 'firebasestorage\.googleapis\.com' web 2>/dev/null)" && [[ -n "$matches" ]]; then
  echo "ERROR: found forbidden firebasestorage.googleapis.com links in web/:"
  echo "$matches"
  exit 1
fi

echo "OK: no firebasestorage.googleapis.com links found in web/."
