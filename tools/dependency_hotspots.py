#!/usr/bin/env python3
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BUILD_FILE = ROOT / "app" / "build.gradle"
SRC_ROOT = ROOT / "app" / "src"
REPORT = ROOT / "reports" / "external-dependency-hotspots.md"

DEP_RE = re.compile(r"^\s*(implementation|testImplementation|androidTestImplementation|annotationProcessor)\s+['\"]([^'\"]+)['\"]")
IMPORT_RE = re.compile(r"^\s*import\s+([a-zA-Z0-9_.]+)")

PACKAGE_HINTS = {
    "androidx.appcompat": ["androidx.appcompat"],
    "com.google.android.material": ["com.google.android.material"],
    "androidx.annotation": ["androidx.annotation"],
    "androidx.core": ["androidx.core"],
    "androidx.drawerlayout": ["androidx.drawerlayout"],
    "androidx.preference": ["androidx.preference"],
    "androidx.swiperefreshlayout": ["androidx.swiperefreshlayout"],
    "androidx.viewpager": ["androidx.viewpager"],
    "com.google.code.gson": ["com.google.gson"],
    "com.squareup.okhttp3": ["okhttp3", "okio"],
    "androidx.window": ["androidx.window"],
    "org.apache.commons": ["org.apache.commons"],
    "androidx.activity": ["androidx.activity"],
    "androidx.constraintlayout": ["androidx.constraintlayout"],
    "androidx.documentfile": ["androidx.documentfile"],
    "androidx.work": ["androidx.work"],
    "com.github.bumptech.glide": ["com.bumptech.glide"],
    "org.robolectric": ["org.robolectric"],
    "org.mockito": ["org.mockito"],
    "androidx.test": ["androidx.test"],
}

DEPENDENCY_CONCEPTS = {
    "androidx": "Jetpack/AndroidX: bibliotecas oficiais de alto nível para UI, ciclo de vida, storage e compatibilidade Android.",
    "com.google.android.material": "Material Components: toolkit de UI do Android para componentes visuais padronizados.",
    "com.google.code.gson": "Serialização JSON em runtime (parse/mapeamento de objetos), frequentemente sensível a alocação/GC.",
    "com.squareup.okhttp3": "Stack HTTP cliente (rede, pooling e conexões), impacta latência, throughput e uso de memória.",
    "org.apache.commons": "Utilitários Java de propósito geral (aqui: compressão/arquivamento), com impacto de I/O e buffers.",
    "com.github.bumptech.glide": "Pipeline de imagem (decode/cache/transform), tipicamente um hotspot de heap e GC em listas.",
    "junit": "Framework de testes unitários (não embarca em runtime de produção).",
    "org.robolectric": "Ambiente de teste Android em JVM local (somente testes).",
    "org.mockito": "Mocking para testes unitários/instrumentados (somente testes).",
    "androidx.test": "Infra de testes Android (runner, core, espresso, ext).",
}

RUNTIME_CLASS = {
    "implementation": "produção",
    "annotationProcessor": "build-time",
    "testImplementation": "teste-local",
    "androidTestImplementation": "teste-instrumentado",
}

REFACTOR_NOTES = {
    "com.google.code.gson:gson": "Reduzir alocações evitando parse completo para objetos grandes; priorizar streaming com JsonReader em caminhos críticos.",
    "com.squareup.okhttp3:okhttp": "Reutilizar singleton de OkHttpClient e pools, evitando novos clients por request para diminuir GC e overhead de conexão.",
    "com.github.bumptech.glide:glide": "Fixar tamanhos alvo, habilitar downsampling e recycle de targets para reduzir picos de heap/GC em listas.",
    "androidx.work:work-runtime": "Consolidar jobs periódicos e evitar enfileiramento redundante; usar constraints mínimas para reduzir wakeups.",
    "org.apache.commons:commons-compress": "Substituir fluxos bufferizados pequenos por buffers fixos maiores em I/O pesado para reduzir churn de objetos.",
}


def parse_dependencies() -> list[tuple[str, str]]:
    deps: list[tuple[str, str]] = []
    for line in BUILD_FILE.read_text(encoding="utf-8").splitlines():
        m = DEP_RE.match(line)
        if not m:
            continue
        cfg, coord = m.groups()
        if ":" not in coord:
            continue
        deps.append((cfg, coord))
    return deps


def collect_imports() -> dict[Path, list[str]]:
    imports_by_file: dict[Path, list[str]] = {}
    for file in SRC_ROOT.rglob("*"):
        if file.suffix not in {".kt", ".java"}:
            continue
        imports: list[str] = []
        try:
            for line in file.read_text(encoding="utf-8", errors="ignore").splitlines():
                m = IMPORT_RE.match(line)
                if m:
                    imports.append(m.group(1))
        except OSError:
            continue
        if imports:
            imports_by_file[file] = imports
    return imports_by_file


def find_matches(coord: str, imports_by_file: dict[Path, list[str]]) -> list[Path]:
    group, artifact, *_ = (coord.split(":") + [""])[:3]
    hints = PACKAGE_HINTS.get(group, [])
    if not hints:
        hints = [group]

    matched: list[Path] = []
    for file, imports in imports_by_file.items():
        if any(any(imp.startswith(prefix) for prefix in hints) for imp in imports):
            matched.append(file.relative_to(ROOT))
    return sorted(matched)


def main() -> None:
    deps = parse_dependencies()
    imports_by_file = collect_imports()
    REPORT.parent.mkdir(parents=True, exist_ok=True)

    lines: list[str] = []
    lines.append("# External Dependency Hotspots (Performance/GC)")
    lines.append("")
    lines.append("Relatório gerado automaticamente a partir de `app/build.gradle` + imports em `app/src`. Foco: pontos para refatoração visando reduzir GC, overhead e fricção de runtime.")
    lines.append("")
    lines.append("## Dependências externas detectadas")
    lines.append("")
    for cfg, coord in deps:
        lines.append(f"- `{cfg}` ({RUNTIME_CLASS.get(cfg, 'desconhecido')}) → `{coord}`")

    lines.append("")
    lines.append("## Conceitos (AndroidX, JDK, SDK e tipos)")
    lines.append("")
    lines.append("- **AndroidX (Jetpack)**: conjunto de bibliotecas Android mantidas pelo Google, distribuídas via Maven (não fazem parte do Java SE puro).")
    lines.append("- **Android SDK**: APIs da plataforma Android (`android.*`) fornecidas pelo sistema e pelo compile SDK; não aparecem como coordenadas Maven em `dependencies {}`.")
    lines.append("- **JDK/JVM**: toolchain de compilação/execução Java/Kotlin no build e testes locais; também não aparece como dependência de app em `build.gradle`.")
    lines.append("- **Tipos de dependência Gradle**: `implementation` (runtime de produção), `annotationProcessor` (build-time), `testImplementation` (teste local), `androidTestImplementation` (teste instrumentado).")
    lines.append("- **Foco de otimização**: para reduzir GC/overhead, priorizar primeiro bibliotecas de `implementation` em caminhos quentes de UI, I/O, rede e parse.")

    lines.append("")
    lines.append("## Classificação conceitual por dependência")
    lines.append("")
    for cfg, coord in deps:
        group = coord.split(":", 1)[0]
        concept = "Dependência externa de suporte; validar necessidade em runtime e possibilidade de módulo autoral equivalente."
        for prefix, text in DEPENDENCY_CONCEPTS.items():
            if group.startswith(prefix):
                concept = text
                break
        lines.append(f"- `{coord}`: {concept}")

    lines.append("")
    lines.append("## Hotspots por dependência")
    lines.append("")

    for cfg, coord in deps:
        files = find_matches(coord, imports_by_file)
        lines.append(f"### `{coord}` ({cfg})")
        note = ""
        group, artifact, *_ = (coord.split(":") + [""])[:3]
        note = REFACTOR_NOTES.get(f"{group}:{artifact}", "Avaliar remoção gradual com módulo autoral equivalente, priorizando caminhos críticos de CPU/memória.")
        lines.append(f"- Oportunidade de refatoração: {note}")
        if files:
            lines.append(f"- Arquivos impactados ({len(files)}):")
            for f in files[:25]:
                lines.append(f"  - `{f.as_posix()}`")
            if len(files) > 25:
                lines.append(f"  - `... +{len(files) - 25} arquivos`")
        else:
            lines.append("- Arquivos impactados: nenhum import direto encontrado no código-fonte atual.")
        lines.append("")

    REPORT.write_text("\n".join(lines).rstrip() + "\n", encoding="utf-8")
    print(f"Report written: {REPORT}")


if __name__ == "__main__":
    main()
