#!/usr/bin/env python3
"""
DEPRECATED — do not use for production fixes.

Only closes Java string literals ending with ?); — does NOT restore Chinese text.
For encoding repair use:
  - tools/restore_utf8_from_git.py
  - git restore --source=HEAD --worktree -- <path>
  - tools/check_text_encoding.py
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

PATTERNS = [
    re.compile(r'(throw new AccessDeniedException\(")([^"]*?)\?\);'),
    re.compile(r'(throw new ResponseStatusException\([^,]+,\s*")([^"]*?)\?\);'),
    re.compile(r'(log\.debug\(")([^"]*?)\?\);'),
    re.compile(r'(failRun\(run,\s*")([^"]*?)\?\);'),
    re.compile(r'(\?\s*")([^"]*?)\?\s*:\s*"([^"]*?)\?\);'),
    re.compile(r'(\s+")([^"]*?)\?\);', re.MULTILINE),
]

TERNARY_FIX = re.compile(
    r'h\.intentFlowTicket\(\)\s*!=\s*null\s*\?\s*"[^"]*"\s*:\s*"[^"]*"\);'
)

FILES = list((ROOT / "modules").rglob("*.java"))


def fix_text(text: str) -> str:
    if "h.intentFlowTicket() != null ?" in text and "??" in text:
        text = TERNARY_FIX.sub('h.intentFlowTicket() != null ? "有" : "无");', text)
    for pat in PATTERNS[:-1]:
        text = pat.sub(lambda m: m.group(1) + m.group(2) + '");', text)
    text = re.sub(r'("(?:[^"\\]|\\.)*?)\?\);', r'\1");', text)
    return text


def main() -> int:
    print(
        "WARNING: deprecated emergency script — may compile but corrupts Chinese.",
        file=sys.stderr,
    )
    changed = 0
    for path in FILES:
        try:
            raw = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            raw = path.read_text(encoding="utf-8", errors="replace")
        fixed = fix_text(raw)
        if fixed != raw:
            path.write_text(fixed, encoding="utf-8", newline="\n")
            print("fixed", path.relative_to(ROOT))
            changed += 1
    print("total", changed)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
