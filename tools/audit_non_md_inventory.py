#!/usr/bin/env python3
"""Auditoria forense de arquivos não-Markdown do repositório.

Gera:
- reports/non_md_inventory.tsv  (inventário arquivo-a-arquivo)
- reports/NON_MD_AUDIT_REPORT.md (sumário executivo e achados)
"""

from __future__ import annotations

import hashlib
import os
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REPORTS_DIR = ROOT / "reports"
INVENTORY_PATH = REPORTS_DIR / "non_md_inventory.tsv"
REPORT_PATH = REPORTS_DIR / "NON_MD_AUDIT_REPORT.md"

EXCLUDE_DIRS = {
    ".git",
    ".gradle",
    ".idea",
    "build",
    "node_modules",
    "__pycache__",
}

SMALL_TEXT_SAMPLE = 4096


def iter_files() -> list[Path]:
    files: list[Path] = []
    for dirpath, dirnames, filenames in os.walk(ROOT):
        rel_dir = Path(dirpath).relative_to(ROOT)
        dirnames[:] = [
            d
            for d in dirnames
            if d not in EXCLUDE_DIRS
            and not (rel_dir == Path('.') and d.startswith('.git'))
        ]
        for name in filenames:
            path = Path(dirpath) / name
            rel = path.relative_to(ROOT)
            if rel.suffix.lower() == ".md":
                continue
            files.append(path)
    return sorted(files)


def sha256_of(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        while True:
            chunk = f.read(1024 * 1024)
            if not chunk:
                break
            h.update(chunk)
    return h.hexdigest()


def text_or_binary(path: Path) -> str:
    try:
        with path.open("rb") as f:
            sample = f.read(SMALL_TEXT_SAMPLE)
    except OSError:
        return "unknown"
    if b"\x00" in sample:
        return "binary"
    if not sample:
        return "text"
    # Heurística simples e determinística
    non_printable = 0
    for b in sample:
        if b in (9, 10, 13):  # tabs/newlines
            continue
        if b < 32 or b > 126:
            non_printable += 1
    ratio = non_printable / len(sample)
    return "binary" if ratio > 0.30 else "text"


def file_category(ext: str) -> str:
    if ext in {".java", ".kt", ".c", ".h", ".rs", ".gradle", ".pro", ".xml", ".sh", ".py"}:
        return "source"
    if ext in {".json", ".properties", ".yml", ".yaml", ".toml", ".lock", ".txt"}:
        return "config-data"
    if ext in {".png", ".jpg", ".jpeg", ".webp", ".ico"}:
        return "image"
    if ext in {".so", ".jar", ".apk", ".aab", ".dex", ".jks", ".img", ".iso", ".tar"}:
        return "artifact-binary"
    if ext == "":
        return "no-extension"
    return "other"


def main() -> int:
    REPORTS_DIR.mkdir(parents=True, exist_ok=True)

    files = iter_files()
    ext_counter: Counter[str] = Counter()
    kind_counter: Counter[str] = Counter()
    category_counter: Counter[str] = Counter()
    dir_counter: Counter[str] = Counter()
    big_files: list[tuple[str, int]] = []
    sensitive_candidates: list[str] = []

    with INVENTORY_PATH.open("w", encoding="utf-8") as out:
        out.write("path\tsize_bytes\text\tkind\texec\tsha256\n")
        for path in files:
            rel = str(path.relative_to(ROOT))
            st = path.stat()
            ext = path.suffix.lower()
            kind = text_or_binary(path)
            category = file_category(ext)
            exec_bit = "yes" if os.access(path, os.X_OK) else "no"
            digest = sha256_of(path)

            out.write(f"{rel}\t{st.st_size}\t{ext or '<noext>'}\t{kind}/{category}\t{exec_bit}\t{digest}\n")

            ext_counter[ext or "<noext>"] += 1
            kind_counter[kind] += 1
            category_counter[category] += 1
            top = rel.split("/", 1)[0] if "/" in rel else "<root>"
            dir_counter[top] += 1

            if st.st_size >= 5 * 1024 * 1024:
                big_files.append((rel, st.st_size))

            lower = rel.lower()
            base = path.name.lower()
            if (
                base.endswith(".jks")
                or "keystore" in base
                or "secret" in base
                or "credentials" in base
                or base in {"id_rsa", "id_ed25519", "private.key", "private.pem"}
            ):
                sensitive_candidates.append(rel)

    big_files.sort(key=lambda item: item[1], reverse=True)
    top_ext = ext_counter.most_common(20)
    top_dirs = dir_counter.most_common(20)

    with REPORT_PATH.open("w", encoding="utf-8") as r:
        r.write("# Auditoria Forense de Arquivos Não-Markdown\n\n")
        r.write("Este relatório cobre todos os arquivos do repositório, exceto `.md`, com inventário determinístico e hash SHA-256.\n\n")
        r.write(f"- Total de arquivos não-MD auditados: **{len(files)}**\n")
        r.write(f"- Inventário detalhado: `{INVENTORY_PATH.relative_to(ROOT)}`\n\n")

        r.write("## Distribuição por tipo lógico\n")
        for k, v in category_counter.most_common():
            r.write(f"- {k}: {v}\n")
        r.write("\n## Distribuição text/binary\n")
        for k, v in kind_counter.most_common():
            r.write(f"- {k}: {v}\n")

        r.write("\n## Top extensões\n")
        for ext, count in top_ext:
            r.write(f"- `{ext}`: {count}\n")

        r.write("\n## Top diretórios (arquivos não-MD)\n")
        for d, count in top_dirs:
            r.write(f"- `{d}`: {count}\n")

        r.write("\n## Arquivos >= 5 MiB\n")
        if big_files:
            for rel, sz in big_files:
                r.write(f"- `{rel}` — {sz} bytes\n")
        else:
            r.write("- Nenhum\n")

        r.write("\n## Candidatos sensíveis para governança\n")
        if sensitive_candidates:
            for rel in sorted(set(sensitive_candidates)):
                r.write(f"- `{rel}`\n")
        else:
            r.write("- Nenhum candidato por padrão de nome\n")

        r.write("\n## Achados e recomendações\n")
        r.write("1. Validar política de versionamento para arquivos de chave/assinatura detectados.\n")
        r.write("2. Revisar periodicamente arquivos binários grandes em assets para controlar footprint do repositório.\n")
        r.write("3. Executar este auditor em CI para trilha contínua de integridade de inventário.\n")

    print(f"OK: {INVENTORY_PATH.relative_to(ROOT)}")
    print(f"OK: {REPORT_PATH.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
