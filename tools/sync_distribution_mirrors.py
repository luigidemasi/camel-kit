#!/usr/bin/env python3
"""Synchronize Maven model-time mirrors from distribution.properties."""

import argparse
import os
import re
import stat
import sys
import tempfile
from pathlib import Path
from xml.sax.saxutils import escape


ROOT = Path(__file__).resolve().parents[1]
BEGIN_MARKER = "<!-- BEGIN distribution property mirrors"
END_MARKER = "<!-- END distribution property mirrors -->"
MIRROR = re.compile(
    r"(?m)^(?P<indent>[ \t]*)<(?P<key>[A-Za-z][A-Za-z0-9_.-]*)>"
    r"(?P<value>[^<\r\n]*)</(?P=key)>(?P<trailing>[ \t]*)$"
)


def load_properties(path):
    values = {}
    for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        stripped = line.strip()
        if not stripped or stripped.startswith(("#", "!")):
            continue
        key, separator, value = stripped.partition("=")
        if not separator or not key:
            raise ValueError(f"{path}:{line_number}: expected key=value")
        if key in values:
            raise ValueError(f"{path}:{line_number}: duplicate property {key}")
        values[key] = value.strip()
    return values


def synchronized_pom(root):
    distribution_path = root / "distribution.properties"
    pom_path = root / "pom.xml"
    properties = load_properties(distribution_path)
    pom = pom_path.read_text(encoding="utf-8")

    if pom.count(BEGIN_MARKER) != 1 or pom.count(END_MARKER) != 1:
        raise ValueError(f"{pom_path}: expected one marked distribution property mirror block")
    begin = pom.index(BEGIN_MARKER)
    block_start = pom.index("-->", begin) + 3
    block_end = pom.index(END_MARKER, block_start)
    block = pom[block_start:block_end]
    matches = list(MIRROR.finditer(block))
    if not matches or MIRROR.sub("", block).strip():
        raise ValueError(f"{pom_path}: mirror block must contain only literal Maven properties")

    seen = set()
    changed = []
    for match in matches:
        key = match.group("key")
        if key in seen:
            raise ValueError(f"{pom_path}: duplicate mirror {key}")
        seen.add(key)
        if key not in properties:
            raise ValueError(f"{distribution_path}: missing mirrored property {key}")
        if match.group("value") != escape(properties[key]):
            changed.append(key)

    def replacement(match):
        key = match.group("key")
        return (
            f'{match.group("indent")}<{key}>{escape(properties[key])}</{key}>'
            f'{match.group("trailing")}'
        )

    updated_block = MIRROR.sub(replacement, block)
    return pom_path, pom[:block_start] + updated_block + pom[block_end:], changed


def atomic_write(path, content):
    descriptor, temporary_name = tempfile.mkstemp(prefix=f".{path.name}.", dir=path.parent)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8", newline="") as temporary:
            temporary.write(content)
        os.chmod(temporary_name, stat.S_IMODE(path.stat().st_mode))
        os.replace(temporary_name, path)
    except BaseException:
        try:
            os.unlink(temporary_name)
        except FileNotFoundError:
            pass
        raise


def synchronize(root=ROOT, write=True):
    pom_path, content, changed = synchronized_pom(root)
    if changed and write:
        atomic_write(pom_path, content)
    return changed


def main(argv=None, root=ROOT):
    parser = argparse.ArgumentParser(
        description="Synchronize marked root-POM properties from distribution.properties."
    )
    parser.add_argument(
        "--check", action="store_true", help="report stale mirrors without changing pom.xml"
    )
    args = parser.parse_args(argv)

    try:
        changed = synchronize(root, write=not args.check)
    except (OSError, ValueError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 2

    if not changed:
        print("Maven distribution property mirrors are synchronized.")
        return 0
    names = ", ".join(changed)
    if args.check:
        print(
            f"Maven distribution property mirrors are stale: {names}. "
            "Run ./tools/sync_distribution_mirrors.py.",
            file=sys.stderr,
        )
        return 1
    print(f"Synchronized Maven distribution property mirrors: {names}.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
