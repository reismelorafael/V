#!/usr/bin/env python3
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "engine/rmr/cmake/rmr_sources.cmake"
ROOT_CMAKE = ROOT / "CMakeLists.txt"
APP_CMAKE = ROOT / "app/src/main/cpp/CMakeLists.txt"
MAKEFILE = ROOT / "Makefile"


def fail(msg: str) -> None:
    print(f"[source-parity] ERROR: {msg}")
    sys.exit(1)


def extract_manifest(var: str) -> list[str]:
    text = MANIFEST.read_text()
    m = re.search(rf"set\({var}\s*(.*?)\)", text, re.S)
    if not m:
        fail(f"variable {var} not found in {MANIFEST}")
    block = m.group(1)
    items = []
    for line in block.splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        line = line.replace("${RMR_REPO_ROOT}/", "")
        items.append(line)
    return items


def extract_make(var: str) -> list[str]:
    text = MAKEFILE.read_text()
    m = re.search(rf"^{var}\s*:=\s*(.*?)\n\n", text, re.M | re.S)
    if not m:
        fail(f"variable {var} not found in {MAKEFILE}")
    block = m.group(1).replace("\\\n", "\n")
    items = []
    for line in block.splitlines():
        line = line.strip().rstrip("\\").strip()
        if not line or line.startswith("$"):
            continue
        items.append(line)
    return items


def ensure_contains(path: Path, snippet: str) -> None:
    if snippet not in path.read_text():
        fail(f"expected snippet missing in {path}: {snippet}")


def compare(label: str, left: list[str], right: list[str]) -> None:
    if left == right:
        return
    fail(
        f"{label} mismatch\n  left-only: {sorted(set(left)-set(right))}\n  right-only: {sorted(set(right)-set(left))}"
    )


def main() -> None:
    manifest_core = extract_manifest("RMR_CORE_COMMON_SOURCES")
    manifest_ext = extract_manifest("RMR_EXTENDED_MODULE_SOURCES")
    manifest_policy = extract_manifest("RMR_POLICY_MODULE_SOURCES")

    make_core = extract_make("ENGINE_CORE_COMMON_SRCS")
    make_ext = extract_make("ENGINE_EXTENDED_SRCS")
    make_policy = [line.strip() for line in re.search(r"^ENGINE_POLICY_SRCS\s*:=\s*(.+)$", MAKEFILE.read_text(), re.M).group(1).split()]

    compare("core sources", manifest_core, make_core)
    compare("extended sources", manifest_ext, make_ext)
    compare("policy sources", manifest_policy, make_policy)

    ensure_contains(ROOT_CMAKE, "include(${CMAKE_SOURCE_DIR}/engine/rmr/cmake/rmr_sources.cmake)")
    ensure_contains(ROOT_CMAKE, "${RMR_CORE_COMMON_SOURCES}")
    ensure_contains(ROOT_CMAKE, "${RMR_EXTENDED_MODULE_SOURCES}")
    ensure_contains(APP_CMAKE, "include(${VECTRA_REPO_ROOT}/engine/rmr/cmake/rmr_sources.cmake)")
    ensure_contains(APP_CMAKE, "${RMR_CORE_COMMON_SOURCES}")
    ensure_contains(APP_CMAKE, "${RMR_EXTENDED_MODULE_SOURCES}")

    print("[source-parity] OK: manifest, CMake, and Makefile lists are aligned")


if __name__ == "__main__":
    main()
