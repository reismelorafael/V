#!/usr/bin/env python3
import argparse
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
CMAKE_MANIFEST = ROOT / "engine/rmr/sources_rmr_core.cmake"
MK_MANIFEST = ROOT / "engine/rmr/sources_rmr_core.mk"

GROUPS = [
    "RMR_SOURCE_GROUP_CORE",
    "RMR_SOURCE_GROUP_OPTIONAL_POLICY",
    "RMR_SOURCE_GROUP_ANDROID_ONLY",
    "RMR_SOURCE_GROUP_HOST_ONLY",
    "RMR_SOURCE_GROUP_ASM_X86_64",
    "RMR_SOURCE_GROUP_ASM_ARM64",
    "RMR_SOURCE_GROUP_ASM_RISCV64",
]


def parse_cmake_sets(text: str) -> dict[str, list[str]]:
    out: dict[str, list[str]] = {}
    for group in GROUPS:
        pattern = re.compile(rf"set\({group}\s*(.*?)\)", re.S)
        match = pattern.search(text)
        if not match:
            raise RuntimeError(f"missing {group} in {CMAKE_MANIFEST}")
        values = []
        for raw in match.group(1).splitlines():
            line = raw.split("#", 1)[0].strip()
            if line:
                values.append(line)
        out[group] = values
    return out


def render_mk(groups: dict[str, list[str]]) -> str:
    lines = [
        "# GENERATED FILE: do not edit directly.",
        "# Source of truth: engine/rmr/sources_rmr_core.cmake",
        "# Regenerate with: tools/sync_rmr_manifest_to_mk.py",
        "",
    ]
    for group in GROUPS:
        vals = groups[group]
        lines.append(f"{group} := \\")
        for idx, src in enumerate(vals):
            cont = " \\" if idx < len(vals) - 1 else ""
            lines.append(f"\t{src}{cont}")
        if not vals:
            lines.append("\t")
        lines.append("")
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="fail if mk manifest is out of date")
    args = parser.parse_args()

    groups = parse_cmake_sets(CMAKE_MANIFEST.read_text(encoding="utf-8"))
    rendered = render_mk(groups)

    if args.check:
        existing = MK_MANIFEST.read_text(encoding="utf-8") if MK_MANIFEST.exists() else ""
        if existing != rendered:
            print(f"[rmr-manifest] {MK_MANIFEST} is out of date; run tools/sync_rmr_manifest_to_mk.py", file=sys.stderr)
            return 1
        print("[rmr-manifest] mk manifest is in sync")
        return 0

    MK_MANIFEST.write_text(rendered, encoding="utf-8")
    print(f"[rmr-manifest] wrote {MK_MANIFEST.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
