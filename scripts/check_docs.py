#!/usr/bin/env python3

"""Validate repository-local Markdown links and fenced code blocks."""

from pathlib import Path
import re
import sys
from urllib.parse import unquote


ROOT = Path(__file__).resolve().parent.parent
DOCUMENTS = [ROOT / "README.md", *sorted((ROOT / "docs").rglob("*.md"))]
LINK = re.compile(r"\[[^\]]*\]\(([^)]+)\)")


def check(document: Path) -> list[str]:
    text = document.read_text(encoding="utf-8")
    relative = document.relative_to(ROOT)
    errors: list[str] = []

    if text.count("```") % 2:
        errors.append(f"{relative}: unclosed fenced code block")

    for raw_target in LINK.findall(text):
        target = raw_target.split("#", 1)[0]
        if not target or "://" in target or target.startswith("mailto:"):
            continue
        resolved = (document.parent / unquote(target)).resolve()
        try:
            resolved.relative_to(ROOT)
        except ValueError:
            errors.append(f"{relative}: link escapes the repository: {raw_target}")
            continue
        if not resolved.exists():
            errors.append(f"{relative}: missing link target: {raw_target}")

    return errors


def main() -> int:
    errors = [error for document in DOCUMENTS for error in check(document)]
    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1
    print(f"Validated {len(DOCUMENTS)} Markdown files")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
