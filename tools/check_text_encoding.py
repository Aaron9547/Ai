#!/usr/bin/env python3
"""Scan repo text sources for UTF-8 / mojibake / truncated-string issues. Exit 1 if any FAIL."""
from __future__ import annotations

import argparse
import re
import subprocess
import sys
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

GLOBS = [
    "src/main/java/**/*.java",
    "src/test/java/**/*.java",
    "src/main/resources/**/*.properties",
    "src/main/resources/**/*.yml",
    "src/main/resources/**/*.yaml",
    "web/**/src/**/*.ts",
    "web/**/src/**/*.vue",
    "web/**/src/**/locales/**/*.ts",
    "db/mysql/**/*.sql",
]

TEXT_SUFFIXES_WITH_CJK_CHECKS = {".java", ".ts", ".vue", ".properties", ".sql"}

# UTF-8 misread as Latin-1/GBK (severe mojibake)
MOJIBAKE_MARKERS = (
    "绠＄",
    "浠呮",
    "閭€",
    "鏃犳",
    "æ—",
    "ç®¡",
    "ä»…",
    "å",
    "Ã©",
    "Ã§",
)

# Suspicious: many consecutive ? in string-like context
MANY_QMARK = re.compile(r'"[^"]*\?{2,}[^"]*"')

# Chinese char immediately followed by ? inside quotes (truncation heuristic)
CJK_TRUNC = re.compile(r'"[^"]*[\u4e00-\u9fff]\?[^"\\]*"')

# Unclosed string on common throw/log lines
UNCLOSED_LINE = re.compile(
    r'^\s*(throw new \w+Exception\(|log\.\w+\(|failRun\([^)]*,\s*|'
    r'new ResponseStatusException\([^,]+,\s*)"[^"]*$'
)

BOM = b"\xef\xbb\xbf"


@dataclass
class Issue:
    rule: str
    line: int
    snippet: str


@dataclass
class FileReport:
    path: Path
    issues: list[Issue] = field(default_factory=list)

    @property
    def failed(self) -> bool:
        return len(self.issues) > 0


def iter_files(module_filter: str | None) -> list[Path]:
    out: list[Path] = []
    for pattern in GLOBS:
        for p in ROOT.glob(pattern):
            if not p.is_file():
                continue
            rel = p.relative_to(ROOT).as_posix()
            if module_filter and f"src/{module_filter}/" not in rel and f"/{module_filter}/" not in rel:
                continue
            out.append(p)
    return sorted(set(out))


def read_text(path: Path) -> tuple[str | None, list[Issue]]:
    issues: list[Issue] = []
    raw = path.read_bytes()
    if raw.startswith(BOM):
        issues.append(Issue("utf8_bom", 1, "file starts with UTF-8 BOM"))
    try:
        text = raw.decode("utf-8")
    except UnicodeDecodeError as e:
        issues.append(Issue("invalid_utf8", 1, str(e)))
        return None, issues
    if "\ufffd" in text:
        for i, line in enumerate(text.splitlines(), 1):
            if "\ufffd" in line:
                issues.append(Issue("replacement_char", i, line.strip()[:120]))
    for marker in MOJIBAKE_MARKERS:
        if marker in text:
            for i, line in enumerate(text.splitlines(), 1):
                if marker in line:
                    issues.append(Issue("mojibake", i, f"marker {marker!r}: {line.strip()[:100]}"))
                    break
    if "????" in text and path.suffix.lower() in TEXT_SUFFIXES_WITH_CJK_CHECKS:
        for i, line in enumerate(text.splitlines(), 1):
            if "????" in line:
                issues.append(Issue("placeholder_q", i, line.strip()[:120]))
    for i, line in enumerate(text.splitlines(), 1):
        if UNCLOSED_LINE.search(line):
            issues.append(Issue("unclosed_string", i, line.strip()[:120]))
        if CJK_TRUNC.search(line) and "?" not in line.split("//")[0]:
            # skip lines that are clearly ternary with ASCII
            if " != null ?" not in line and " ? " not in line:
                issues.append(Issue("cjk_truncation", i, line.strip()[:120]))
    return text, issues


def git_head_text(rel: str) -> str | None:
    try:
        proc = subprocess.run(
            ["git", "show", f"HEAD:{rel}"],
            cwd=ROOT,
            capture_output=True,
            check=True,
        )
        return proc.stdout.decode("utf-8", errors="replace")
    except (subprocess.CalledProcessError, FileNotFoundError):
        return None


def classify_changed_java() -> dict[str, str]:
    """CORRUPT | TRUNCATED | OK for paths changed vs HEAD."""
    try:
        proc = subprocess.run(
            ["git", "diff", "--name-only", "HEAD", "--", "src"],
            cwd=ROOT,
            capture_output=True,
            check=True,
            text=True,
            encoding="utf-8",
        )
        paths = [p for p in proc.stdout.splitlines() if p.endswith(".java")]
    except (subprocess.CalledProcessError, FileNotFoundError):
        return {}
    result: dict[str, str] = {}
    for rel in paths:
        p = ROOT / rel
        if not p.is_file():
            continue
        work, w_issues = read_text(p)
        head = git_head_text(rel)
        w_score = len(w_issues) + (500 if work and any(m in work for m in MOJIBAKE_MARKERS) else 0)
        h_score = 0
        if head:
            _, h_issues = read_text_from_str(head)
            h_score = len(h_issues)
        if w_score > h_score + 50 or (work and any(m in work for m in MOJIBAKE_MARKERS)):
            result[rel] = "CORRUPT"
        elif w_issues or (head and h_issues):
            result[rel] = "TRUNCATED"
        else:
            result[rel] = "OK"
    return result


def read_text_from_str(text: str) -> tuple[str, list[Issue]]:
    issues: list[Issue] = []
    for marker in MOJIBAKE_MARKERS:
        if marker in text:
            issues.append(Issue("mojibake", 0, marker))
    for i, line in enumerate(text.splitlines(), 1):
        if UNCLOSED_LINE.search(line):
            issues.append(Issue("unclosed_string", i, line.strip()[:80]))
        if CJK_TRUNC.search(line):
            issues.append(Issue("cjk_truncation", i, line.strip()[:80]))
    return text, issues


def main() -> int:
    parser = argparse.ArgumentParser(description="Check UTF-8 text encoding in Ai repo")
    parser.add_argument("--module", help="Only scan paths containing this segment (e.g. main/java or chat)")
    parser.add_argument("--report", type=Path, default=ROOT / "tools" / "encoding-report.txt")
    parser.add_argument("--write-report", action="store_true")
    args = parser.parse_args()

    reports: list[FileReport] = []
    for path in iter_files(args.module):
        _, issues = read_text(path)
        if issues:
            reports.append(FileReport(path, issues))

    lines: list[str] = []
    if args.write_report or reports:
        changed = classify_changed_java()
        lines.append("# Encoding scan report\n")
        lines.append(f"Root: {ROOT}\n")
        lines.append(f"FAIL files: {len(reports)}\n\n")
        by_mod: dict[str, list[FileReport]] = {}
        for r in reports:
            rel = r.path.relative_to(ROOT).as_posix()
            mod = "other"
            if rel.startswith("src/"):
                # e.g. src/main/java/com/aaron/cloud/chat/... -> chat (or common)
                parts = rel.split("/")
                if "com" in parts and "aaron" in parts and "cloud" in parts:
                    i = parts.index("cloud")
                    if i + 1 < len(parts):
                        mod = parts[i + 1]
                else:
                    mod = "src"
            elif rel.startswith("web/"):
                mod = rel.split("/")[1]
            by_mod.setdefault(mod, []).append(r)
        for mod in sorted(by_mod):
            lines.append(f"## {mod}\n")
            for r in sorted(by_mod[mod], key=lambda x: x.path.as_posix()):
                rel = r.path.relative_to(ROOT).as_posix()
                tag = changed.get(rel, "")
                lines.append(f"### {rel} {tag}\n")
                for iss in r.issues:
                    lines.append(f"- [{iss.rule}] L{iss.line}: {iss.snippet}\n")
                lines.append("\n")
        if changed:
            lines.append("## Changed Java classification (vs HEAD)\n")
            for rel in sorted(changed):
                lines.append(f"- {changed[rel]}: {rel}\n")
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text("".join(lines), encoding="utf-8")

    if reports:
        print(f"FAIL: {len(reports)} file(s) with encoding issues", file=sys.stderr)
        for r in reports[:20]:
            rel = r.path.relative_to(ROOT)
            print(f"  {rel}: {r.issues[0].rule}", file=sys.stderr)
        if len(reports) > 20:
            print(f"  ... and {len(reports) - 20} more", file=sys.stderr)
        if args.write_report:
            print(f"Report: {args.report}")
        return 1
    print("OK: no encoding issues detected")
    if args.write_report:
        args.report.write_text("# Encoding scan report\n\nOK: no issues\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
