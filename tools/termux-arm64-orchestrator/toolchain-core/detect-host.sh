#!/usr/bin/env bash
set -euo pipefail

arch="$(uname -m 2>/dev/null || echo unknown)"
os="$(uname -s 2>/dev/null || echo unknown)"
cpu_count="$(getconf _NPROCESSORS_ONLN 2>/dev/null || echo 1)"

is_arm64=0
case "$arch" in
  aarch64|arm64) is_arm64=1 ;;
esac

has_neon=0
has_asimd=0
if [[ -r /proc/cpuinfo ]]; then
  if rg -qi 'neon' /proc/cpuinfo; then
    has_neon=1
  fi
  if rg -qi 'asimd' /proc/cpuinfo; then
    has_asimd=1
  fi
fi

cat <<EOT
HOST_ARCH=$arch
HOST_OS=$os
HOST_CPU_COUNT=$cpu_count
HOST_IS_ARM64=$is_arm64
HOST_HAS_NEON=$has_neon
HOST_HAS_ASIMD=$has_asimd
EOT
