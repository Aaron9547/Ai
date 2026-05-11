"""Remove UTF-8 BOM (EF BB BF) from all .java files under ./src. Idempotent."""
from __future__ import annotations

import os
import sys

BOM = b"\xef\xbb\xbf"


def main() -> int:
    root = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "src")
    if not os.path.isdir(root):
        print("src not found:", root, file=sys.stderr)
        return 1
    stripped: list[str] = []
    for dirpath, _, filenames in os.walk(root):
        for name in filenames:
            if not name.endswith(".java"):
                continue
            path = os.path.join(dirpath, name)
            with open(path, "rb") as f:
                data = f.read()
            if data.startswith(BOM):
                with open(path, "wb") as f:
                    f.write(data[len(BOM) :])
                stripped.append(path)
    print(f"stripped BOM from {len(stripped)} file(s)")
    for p in stripped:
        print(p)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
