#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

LOG_PREFIX="[termux-bootstrap]"
ANDROID_HOME_DEFAULT="$ROOT_DIR/.android-sdk"
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME_DEFAULT}"
ANDROID_HOME="${ANDROID_HOME:-$ANDROID_SDK_ROOT}"
ANDROID_API_LEVEL="${ANDROID_API_LEVEL:-35}"
ANDROID_BUILD_TOOLS="${ANDROID_BUILD_TOOLS:-35.0.0}"
ANDROID_NDK_VERSION="${ANDROID_NDK_VERSION:-27.2.12479018}"
ANDROID_CMAKE_VERSION="${ANDROID_CMAKE_VERSION:-3.22.1}"
CMDLINE_TOOLS_VERSION="${CMDLINE_TOOLS_VERSION:-13114758}"

export ANDROID_HOME ANDROID_SDK_ROOT

log() { echo "$LOG_PREFIX $*"; }
warn() { echo "$LOG_PREFIX WARN: $*"; }

install_termux_packages_if_available() {
  if ! command -v pkg >/dev/null 2>&1; then
    warn "pkg não disponível (não-Termux). Pulando instalação de pacotes do Termux."
    return 0
  fi

  log "instalando dependências base no Termux"
  pkg update -y
  pkg install -y \
    openjdk-21 \
    wget \
    unzip \
    git \
    cmake \
    ninja \
    ndk-sysroot \
    patchelf \
    python \
    which
}

ensure_cmdline_tools() {
  local tools_dir="$ANDROID_SDK_ROOT/cmdline-tools"
  local latest_dir="$tools_dir/latest"
  local zip_path="$ROOT_DIR/.cache/android-cmdline-tools.zip"
  mkdir -p "$tools_dir" "$ROOT_DIR/.cache"

  if [[ -x "$latest_dir/bin/sdkmanager" ]]; then
    log "cmdline-tools já presentes em $latest_dir"
    return 0
  fi

  local url
  url="https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip"

  log "baixando cmdline-tools: $url"
  wget -q -O "$zip_path" "$url"

  local tmp_unpack="$tools_dir/.tmp-unpack"
  rm -rf "$tmp_unpack"
  mkdir -p "$tmp_unpack"
  unzip -q "$zip_path" -d "$tmp_unpack"

  rm -rf "$latest_dir"
  mkdir -p "$latest_dir"
  cp -a "$tmp_unpack/cmdline-tools/." "$latest_dir/"
  rm -rf "$tmp_unpack"

  log "cmdline-tools instalados"
}

install_android_components() {
  export PATH="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH"

  yes | sdkmanager --sdk_root="$ANDROID_SDK_ROOT" --licenses >/dev/null

  log "instalando platform/build-tools/ndk/cmake"
  sdkmanager --sdk_root="$ANDROID_SDK_ROOT" \
    "platform-tools" \
    "platforms;android-${ANDROID_API_LEVEL}" \
    "build-tools;${ANDROID_BUILD_TOOLS}" \
    "ndk;${ANDROID_NDK_VERSION}" \
    "cmake;${ANDROID_CMAKE_VERSION}"
}

write_local_properties() {
  local lp="$ROOT_DIR/local.properties"
  printf "sdk.dir=%s\n" "$ANDROID_SDK_ROOT" > "$lp"
  log "local.properties atualizado em $lp"
}

print_summary() {
  cat <<EOT
$LOG_PREFIX resumo:
- ANDROID_SDK_ROOT=$ANDROID_SDK_ROOT
- API=$ANDROID_API_LEVEL
- BUILD_TOOLS=$ANDROID_BUILD_TOOLS
- NDK=$ANDROID_NDK_VERSION
- CMAKE=$ANDROID_CMAKE_VERSION
EOT
}

install_termux_packages_if_available
ensure_cmdline_tools
install_android_components
write_local_properties
print_summary
