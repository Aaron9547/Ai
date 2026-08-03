"""Remove UTF-8 BOM (EF BB BF) from text sources under src/, web/, db/mysql/, tools/, scripts/. Idempotent."""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BOM = b"\xef\xbb\xbf"

SCAN_ROOTS = (
    ROOT / "src",
    ROOT / "web",
    ROOT / "db" / "mysql",
    ROOT / "tools",
    ROOT / "scripts",
)

TEXT_SUFFIXES = {
    ".java",
    ".ts",
    ".tsx",
    ".vue",
    ".js",
    ".jsx",
    ".json",
    ".sql",
    ".md",
    ".yml",
    ".yaml",
    ".xml",
    ".properties",
    ".py",
    ".ps1",
    ".sh",
    ".css",
    ".scss",
    ".html",
}


def main() -> int:
    stripped: list[str] = []
    for root in SCAN_ROOTS:
        if not root.is_dir():
            continue
        for path in root.rglob("*"):
            if not path.is_file() or path.suffix.lower() not in TEXT_SUFFIXES:
                continue
            data = path.read_bytes()
            if not data.startswith(BOM):
                continue
            path.write_bytes(data[len(BOM) :])
            stripped.append(str(path.relative_to(ROOT)))
    print(f"stripped BOM from {len(stripped)} file(s)")
    for rel in stripped:
        print(rel)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
