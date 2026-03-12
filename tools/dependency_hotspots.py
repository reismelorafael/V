#!/usr/bin/env python3
from __future__ import annotations

import os
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BUILD_FILE = ROOT / "app" / "build.gradle"
REPORT = ROOT / "reports" / "external-dependency-hotspots.md"

DEP_RE = re.compile(r"^\s*(implementation|testImplementation|androidTestImplementation|annotationProcessor)\s+['\"]([^'\"]+)['\"]")


def parse_dependencies() -> list[tuple[str, str]]:
    deps: list[tuple[str, str]] = []
    if not BUILD_FILE.exists():
        return deps
    for line in BUILD_FILE.read_text(encoding="utf-8").splitlines():
        m = DEP_RE.match(line)
        if not m:
            continue
        cfg, coord = m.groups()
        if ":" not in coord:
            continue
        deps.append((cfg, coord))
    return deps


def existing(paths: list[Path]) -> list[Path]:
    return [p for p in paths if p.exists()]


def detect_sdk_root() -> Path | None:
    candidates = [
        os.environ.get("ANDROID_SDK_ROOT", ""),
        os.environ.get("ANDROID_HOME", ""),
        str(Path.home() / "Android" / "Sdk"),
        "/opt/android-sdk",
        "/usr/local/lib/android/sdk",
    ]
    for c in candidates:
        if not c:
            continue
        p = Path(c)
        if p.exists():
            return p
    return None


def detect_ndk_root(sdk_root: Path | None) -> Path | None:
    env = [os.environ.get("ANDROID_NDK_HOME", ""), os.environ.get("NDK_HOME", "")]
    for c in env:
        if c and Path(c).exists():
            return Path(c)

    if sdk_root is None:
        return None
    ndk_base = sdk_root / "ndk"
    if not ndk_base.exists():
        return None
    versions = sorted([d for d in ndk_base.iterdir() if d.is_dir()], reverse=True)
    return versions[0] if versions else None


def detect_jdk_root() -> Path | None:
    java_home = os.environ.get("JAVA_HOME", "")
    if java_home and Path(java_home).exists():
        return Path(java_home)

    javac = shutil_which("javac")
    if javac:
        p = Path(javac).resolve()
        if p.parent.name == "bin":
            return p.parent.parent
    return None


def shutil_which(binary: str) -> str | None:
    for folder in os.environ.get("PATH", "").split(os.pathsep):
        candidate = Path(folder) / binary
        if candidate.exists() and os.access(candidate, os.X_OK):
            return str(candidate)
    return None


def collect_sdk_files(sdk: Path | None) -> list[Path]:
    if sdk is None:
        return []
    out: list[Path] = []

    platforms = sorted((sdk / "platforms").glob("android-*"), reverse=True)
    if platforms:
        out += existing([platforms[0] / "android.jar", platforms[0] / "framework.aidl"])

    build_tools = sorted((sdk / "build-tools").glob("*"), reverse=True)
    if build_tools:
        bt = build_tools[0]
        out += existing([
            bt / "aapt2",
            bt / "d8",
            bt / "zipalign",
            bt / "apksigner",
            bt / "aidl",
        ])

    out += existing([
        sdk / "platform-tools" / "adb",
        sdk / "cmdline-tools" / "latest" / "bin" / "sdkmanager",
        sdk / "cmdline-tools" / "latest" / "bin" / "avdmanager",
    ])

    cmakes = sorted((sdk / "cmake").glob("*"), reverse=True)
    if cmakes:
        out += existing([cmakes[0] / "bin" / "cmake", cmakes[0] / "bin" / "ninja"])

    return out


def collect_ndk_files(ndk: Path | None) -> list[Path]:
    if ndk is None:
        return []
    out: list[Path] = []
    prebuilt_dirs = sorted((ndk / "toolchains" / "llvm" / "prebuilt").glob("*"))
    for pb in prebuilt_dirs[:1]:
        out += existing([
            pb / "bin" / "clang",
            pb / "bin" / "clang++",
            pb / "bin" / "ld.lld",
            pb / "bin" / "llvm-ar",
            pb / "bin" / "llvm-strip",
            pb / "sysroot" / "usr" / "include" / "android" / "api-level.h",
        ])
    out += existing([
        ndk / "build" / "cmake" / "android.toolchain.cmake",
        ndk / "source.properties",
    ])
    return out


def collect_jdk_files(jdk: Path | None) -> list[Path]:
    if jdk is None:
        return []
    return existing([
        jdk / "bin" / "java",
        jdk / "bin" / "javac",
        jdk / "bin" / "jar",
        jdk / "bin" / "jlink",
        jdk / "release",
    ])


def collect_gradle_files() -> list[Path]:
    home = Path.home()
    out: list[Path] = []
    for f in sorted((home / ".gradle" / "wrapper" / "dists").glob("**/gradle*/bin/gradle"))[:5]:
        out.append(f)
    for f in sorted((home / ".gradle" / "caches" / "modules-2" / "files-2.1").glob("**/*.jar"))[:20]:
        out.append(f)
    return out


def collect_maven_artifacts_for_deps(deps: list[tuple[str, str]]) -> list[Path]:
    base = Path.home() / ".gradle" / "caches" / "modules-2" / "files-2.1"
    if not base.exists():
        return []

    out: list[Path] = []
    for cfg, coord in deps:
        if cfg != "implementation":
            continue
        parts = coord.split(":")
        if len(parts) < 3:
            continue
        group, artifact, version = parts[:3]
        p = base / group / artifact / version
        if not p.exists():
            continue
        jars = sorted(p.glob("**/*.jar"))
        poms = sorted(p.glob("**/*.pom"))
        if jars:
            out.append(jars[0])
        if poms:
            out.append(poms[0])
    return out


def rel_or_abs(path: Path) -> str:
    try:
        return str(path.relative_to(ROOT))
    except ValueError:
        return str(path)


def emit_section(lines: list[str], title: str, files: list[Path]) -> None:
    lines.append(f"## {title}")
    lines.append("")
    if not files:
        lines.append("- nenhum arquivo detectado neste ambiente.")
        lines.append("")
        return
    for i, f in enumerate(files, start=1):
        lines.append(f"- [{i:02d}] `{rel_or_abs(f)}`")
    lines.append("")


def main() -> None:
    deps = parse_dependencies()
    sdk = detect_sdk_root()
    ndk = detect_ndk_root(sdk)
    jdk = detect_jdk_root()

    sdk_files = collect_sdk_files(sdk)
    ndk_files = collect_ndk_files(ndk)
    jdk_files = collect_jdk_files(jdk)
    gradle_files = collect_gradle_files()
    dep_artifacts = collect_maven_artifacts_for_deps(deps)

    REPORT.parent.mkdir(parents=True, exist_ok=True)

    lines: list[str] = []
    lines.append("# External Build/Toolchain Files (SDK/NDK/JDK/Gradle)")
    lines.append("")
    lines.append("Inventário direto de arquivos externos ao repositório que participam da compilação e resolução de dependências Android.")
    lines.append("")
    lines.append("## Roots detectados")
    lines.append("")
    lines.append(f"- SDK root: `{sdk}`")
    lines.append(f"- NDK root: `{ndk}`")
    lines.append(f"- JDK root: `{jdk}`")
    lines.append("")

    emit_section(lines, "Arquivos externos - Android SDK", sdk_files)
    emit_section(lines, "Arquivos externos - Android NDK", ndk_files)
    emit_section(lines, "Arquivos externos - JDK", jdk_files)
    emit_section(lines, "Arquivos externos - Gradle Wrapper/Caches", gradle_files)
    emit_section(lines, "Artefatos externos de dependências (implementation)", dep_artifacts)

    REPORT.write_text("\n".join(lines).rstrip() + "\n", encoding="utf-8")
    print(f"Report written: {REPORT}")


if __name__ == "__main__":
    main()
