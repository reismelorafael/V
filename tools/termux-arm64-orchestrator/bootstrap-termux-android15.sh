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
TOOLCHAIN_PACK_DIR="${TOOLCHAIN_PACK_DIR:-$ROOT_DIR/.toolchain-packs}"
ALLOW_NETWORK_TOOLCHAIN="${ALLOW_NETWORK_TOOLCHAIN:-1}"

export ANDROID_HOME ANDROID_SDK_ROOT

log() { echo "$LOG_PREFIX $*"; }
warn() { echo "$LOG_PREFIX WARN: $*"; }

REPORT_FILE="$ROOT_DIR/.build-spill/bootstrap-report.txt"
CMDLINE_TOOLS_LAST_SHA256=""
CMDLINE_TOOLS_SOURCE=""

report_init() {
  mkdir -p "$(dirname "$REPORT_FILE")"
  cat > "$REPORT_FILE" <<EOT
$LOG_PREFIX bootstrap report
timestamp=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
ANDROID_SDK_ROOT=$ANDROID_SDK_ROOT
ANDROID_API_LEVEL=$ANDROID_API_LEVEL
ANDROID_BUILD_TOOLS=$ANDROID_BUILD_TOOLS
ANDROID_NDK_VERSION=$ANDROID_NDK_VERSION
ANDROID_CMAKE_VERSION=$ANDROID_CMAKE_VERSION
CMDLINE_TOOLS_VERSION=$CMDLINE_TOOLS_VERSION
TOOLCHAIN_PACK_DIR=$TOOLCHAIN_PACK_DIR
ALLOW_NETWORK_TOOLCHAIN=$ALLOW_NETWORK_TOOLCHAIN
EOT
}

report_step() {
  local step_name="$1"
  local status="$2"
  local detail="$3"
  printf "step=%s status=%s detail=%s\n" "$step_name" "$status" "$detail" >> "$REPORT_FILE"
}

cmdline_tools_expected_sha256() {
  case "$CMDLINE_TOOLS_VERSION" in
    13114758)
      echo "7ec965280a073311c339e571cd5de778b9975026cfcbe79f2b1cdcb1e15317ee"
      ;;
    *)
      return 1
      ;;
  esac
}

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
  local previous_dir="$tools_dir/latest.prev"
  local tmp_zip
  local tmp_unpack
  mkdir -p "$tools_dir"

  if [[ -x "$latest_dir/bin/sdkmanager" ]]; then
    log "cmdline-tools já presentes em $latest_dir"
    CMDLINE_TOOLS_LAST_SHA256="already-installed"
    CMDLINE_TOOLS_SOURCE="already-installed"
    report_step "ensure_cmdline_tools" "ok" "already-present path=$latest_dir"
    return 0
  fi

  local url
  local expected_sha256
  local local_pack
  local local_pack_sha
  url="https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip"
  local_pack="$TOOLCHAIN_PACK_DIR/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip"
  if ! expected_sha256="$(cmdline_tools_expected_sha256)"; then
    report_step "ensure_cmdline_tools" "fail" "missing-checksum version=$CMDLINE_TOOLS_VERSION"
    echo "checksum esperado não cadastrado para CMDLINE_TOOLS_VERSION=$CMDLINE_TOOLS_VERSION" >&2
    return 1
  fi

  tmp_unpack="$(mktemp -d "$tools_dir/.cmdline-tools-unpack-XXXXXX")"
  trap '[[ -n "${tmp_zip:-}" && "$tmp_zip" == "$tools_dir"/* ]] && rm -f "$tmp_zip"; rm -rf "$tmp_unpack"' RETURN

  if [[ -f "$local_pack" ]]; then
    local_pack_sha="$(sha256sum "$local_pack" | awk '{print $1}')"
    if [[ "$local_pack_sha" != "$expected_sha256" ]]; then
      report_step "ensure_cmdline_tools" "fail" "local-pack-checksum-mismatch expected=$expected_sha256 actual=$local_pack_sha"
      echo "checksum inválido para cmdline-tools local pack" >&2
      return 1
    fi
    tmp_zip="$local_pack"
    CMDLINE_TOOLS_SOURCE="local-pack"
    CMDLINE_TOOLS_LAST_SHA256="$local_pack_sha"
    log "usando cmdline-tools pack local: $local_pack"
  else
    if [[ "$ALLOW_NETWORK_TOOLCHAIN" != "1" ]]; then
      report_step "ensure_cmdline_tools" "fail" "local-pack-missing network-disabled path=$local_pack"
      echo "cmdline-tools local pack ausente e ALLOW_NETWORK_TOOLCHAIN=0" >&2
      return 1
    fi
    tmp_zip="$(mktemp "$tools_dir/.cmdline-tools-${CMDLINE_TOOLS_VERSION}-XXXXXX.zip")"

    log "baixando cmdline-tools: $url"
  local attempt
  local max_attempts=5
    for attempt in $(seq 1 "$max_attempts"); do
      if curl -fsSL --connect-timeout 20 --max-time 300 -o "$tmp_zip" "$url"; then
        break
      fi
      if [[ "$attempt" -eq "$max_attempts" ]]; then
        report_step "ensure_cmdline_tools" "fail" "download-failed attempts=$max_attempts url=$url"
        echo "falha ao baixar cmdline-tools após $max_attempts tentativas" >&2
        return 1
      fi
      local backoff=$((2 ** attempt))
      warn "falha no download (tentativa $attempt/$max_attempts). retry em ${backoff}s"
      sleep "$backoff"
    done
    CMDLINE_TOOLS_SOURCE="network"
  fi

  local actual_sha256
  actual_sha256="$(sha256sum "$tmp_zip" | awk '{print $1}')"
  CMDLINE_TOOLS_LAST_SHA256="$actual_sha256"
  if [[ "$actual_sha256" != "$expected_sha256" ]]; then
    report_step "ensure_cmdline_tools" "fail" "checksum-mismatch expected=$expected_sha256 actual=$actual_sha256"
    echo "checksum inválido para cmdline-tools" >&2
    return 1
  fi

  unzip -q "$tmp_zip" -d "$tmp_unpack"

  if [[ ! -x "$tmp_unpack/cmdline-tools/bin/sdkmanager" ]]; then
    report_step "ensure_cmdline_tools" "fail" "invalid-archive missing-sdkmanager"
    echo "arquivo cmdline-tools inválido: sdkmanager ausente" >&2
    return 1
  fi

  rm -rf "$previous_dir"
  if [[ -d "$latest_dir" ]]; then
    mv "$latest_dir" "$previous_dir"
  fi
  mv "$tmp_unpack/cmdline-tools" "$latest_dir"

  trap - RETURN
  if [[ -n "${tmp_zip:-}" && "$tmp_zip" == "$tools_dir"/* ]]; then
    rm -f "$tmp_zip"
  fi
  rm -rf "$tmp_unpack"

  report_step "ensure_cmdline_tools" "ok" "installed version=$CMDLINE_TOOLS_VERSION source=$CMDLINE_TOOLS_SOURCE sha256=$actual_sha256 backup=$previous_dir"

  log "cmdline-tools instalados"
}

restore_cmdline_tools_backup() {
  local tools_dir="$ANDROID_SDK_ROOT/cmdline-tools"
  local latest_dir="$tools_dir/latest"
  local previous_dir="$tools_dir/latest.prev"

  if [[ ! -d "$previous_dir" ]]; then
    warn "backup latest.prev não encontrado; rollback não aplicado"
    report_step "rollback_cmdline_tools" "warn" "backup-missing path=$previous_dir"
    return 0
  fi

  rm -rf "$latest_dir"
  mv "$previous_dir" "$latest_dir"
  log "rollback de cmdline-tools aplicado a partir de latest.prev"
  report_step "rollback_cmdline_tools" "ok" "restored path=$latest_dir"
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

  report_step "install_android_components" "ok" "sdkmanager-components-installed"
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

report_init

if install_termux_packages_if_available; then
  report_step "install_termux_packages_if_available" "ok" "completed"
else
  report_step "install_termux_packages_if_available" "fail" "command-failed"
  exit 1
fi

if ! ensure_cmdline_tools; then
  exit 1
fi

if ! install_android_components; then
  report_step "install_android_components" "fail" "sdkmanager-command-failed"
  restore_cmdline_tools_backup
  exit 1
fi

write_local_properties
print_summary
report_step "bootstrap" "ok" "complete cmdline_tools_source=$CMDLINE_TOOLS_SOURCE cmdline_tools_sha256=$CMDLINE_TOOLS_LAST_SHA256"
