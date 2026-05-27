#!/usr/bin/env python3
"""Removed — use tools/restore_utf8_from_git.py and tools/check_text_encoding.py instead."""
import sys

print(
    "scripts/fix-truncated-java-strings.py is deprecated.\n"
    "Use: python tools/restore_utf8_from_git.py <path>\n"
    "     python tools/check_text_encoding.py\n"
    "Emergency only: python tools/_deprecated_fix_truncated_strings.py",
    file=sys.stderr,
)
raise SystemExit(2)
