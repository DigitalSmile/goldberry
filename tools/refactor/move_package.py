#!/usr/bin/env python3
"""Move Java types between packages, rewriting every reference in the repo.

Written for the package refactor in ADR-0172. A package move is four edits and
it is the *fourth* that is easy to forget: the file that used the type without
an import because it shared a package with it, and now does not.

  1. the file moves, and its `package` line changes
  2. every `import old.Type;` in the repo becomes `import new.Type;`
  3. every fully-qualified `old.Type` -- in code and in javadoc -- follows
  4. every file *left behind* in the old package gains an import, and every
     file that *moved* gains imports for what it left behind

It also fixes the `../` depth of the relative markdown links this codebase's
javadoc uses to point at ADRs, because a file that moved a package deeper is
one `../` further from `book/`.

Usage:
  move_package.py --from <pkg> --to <pkg> --classes A,B,C [--root .] [--dry-run]
"""
from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
from pathlib import Path

SRC_GLOBS = ("src/main/java", "src/test/java", "src/testFixtures/java")


def java_files(root: Path) -> list[Path]:
    out = []
    for p in root.rglob("*.java"):
        parts = p.parts
        if "build" in parts or ".git" in parts or ".deps" in parts:
            continue
        out.append(p)
    return out


def package_of(path: Path) -> str | None:
    try:
        head = path.read_text(encoding="utf-8")[:4000]
    except OSError:
        return None
    m = re.search(r"^\s*package\s+([\w.]+)\s*;", head, re.M)
    return m.group(1) if m else None


def source_root_of(path: Path, pkg: str) -> Path | None:
    """The `src/main/java`-style directory `path` sits under, given its package."""
    rel = Path(*pkg.split("."))
    parent = path.parent
    try:
        return parent.relative_to(parent).__class__(str(parent)[: -len(str(rel))].rstrip("/"))
    except ValueError:
        return None


def fix_adr_depth(text: str, delta: int) -> str:
    """Re-anchor `](../../../book/...)` style links after a move of `delta` levels."""
    if delta == 0:
        return text

    def repl(m: re.Match[str]) -> str:
        ups = m.group(1)
        n = ups.count("../") + delta
        if n < 0:
            n = 0
        return "](" + "../" * n + m.group(2)

    return re.sub(r"\]\(((?:\.\./)+)((?:book|docs)/)", repl, text)


def source_set(path: Path) -> str:
    """`src/main/java`-style root a file sits under.

    Imports are only wired between files of the *same* source set: a main class
    must never gain an import of a test class because its javadoc named one.
    """
    parts = path.parts
    for i in range(len(parts) - 2):
        if parts[i] == "src" and parts[i + 2] == "java":
            return "/".join(parts[: i + 3])
    return str(path.parent)


#: which source sets a source set compiles against
VISIBLE_FROM = {
    "main": {"main"},
    "testFixtures": {"main", "testFixtures"},
    "test": {"main", "testFixtures", "test"},
}


def kind(path: Path) -> str:
    """`main`, `test` or `testFixtures` -- which source set a file is in."""
    parts = path.parts
    for i in range(len(parts) - 2):
        if parts[i] == "src" and parts[i + 2] == "java":
            return parts[i + 1]
    return "main"


def sees(user: Path, used: Path) -> bool:
    """Whether `user` can name `used`.

    A main class must never gain an import of a test class because its javadoc
    happened to name one; a test class importing a main class is ordinary.
    """
    return kind(used) in VISIBLE_FROM.get(kind(user), {"main"})


def simple_name_used(text: str, name: str) -> bool:
    """Whether `name` appears as a type reference outside package/import lines."""
    body = re.sub(r"^\s*(package|import)\s+[^;]+;", "", text, flags=re.M)
    return re.search(rf"(?<![\w.$]){re.escape(name)}(?![\w$])", body) is not None


def add_imports(text: str, imports: list[str]) -> str:
    wanted = [i for i in imports if f"import {i};" not in text]
    if not wanted:
        return text
    lines = text.split("\n")
    last_import = -1
    pkg_line = -1
    for i, ln in enumerate(lines):
        if re.match(r"\s*package\s+[\w.]+\s*;", ln):
            pkg_line = i
        if re.match(r"\s*import\s+", ln):
            last_import = i
    block = [f"import {i};" for i in sorted(wanted)]
    if last_import >= 0:
        lines[last_import + 1 : last_import + 1] = block
    elif pkg_line >= 0:
        lines[pkg_line + 1 : pkg_line + 1] = [""] + block
    else:
        lines[0:0] = block
    return "\n".join(lines)


def drop_self_imports(text: str, pkg: str) -> str:
    return re.sub(rf"^\s*import\s+(?:static\s+)?{re.escape(pkg)}\.[A-Z]\w*\s*;\n", "", text, flags=re.M)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--from", dest="old", required=True)
    ap.add_argument("--to", dest="new", required=True)
    ap.add_argument("--classes", required=True)
    ap.add_argument("--root", default=".")
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    root = Path(args.root).resolve()
    old, new = args.old, args.new
    names = [c.strip() for c in args.classes.split(",") if c.strip()]

    all_java = java_files(root)
    by_pkg: dict[str, list[Path]] = {}
    for p in all_java:
        pk = package_of(p)
        if pk:
            by_pkg.setdefault(pk, []).append(p)

    moving: list[Path] = []
    for p in by_pkg.get(old, []):
        if p.stem in names:
            moving.append(p)
    found = {p.stem for p in moving}
    missing = [n for n in names if n not in found]
    if missing:
        print(f"!! not found in {old}: {', '.join(missing)}", file=sys.stderr)
        return 1

    depth_delta = len(new.split(".")) - len(old.split("."))

    # --- 1. move the files -------------------------------------------------
    moved_targets: list[Path] = []
    for p in moving:
        src_root = str(p.parent)[: -len(str(Path(*old.split("."))))].rstrip("/")
        dest_dir = Path(src_root) / Path(*new.split("."))
        dest = dest_dir / p.name
        if args.dry_run:
            print(f"mv {p} -> {dest}")
        else:
            dest_dir.mkdir(parents=True, exist_ok=True)
            subprocess.run(["git", "mv", str(p), str(dest)], cwd=root, check=True)
        moved_targets.append(dest)

    if args.dry_run:
        return 0

    remaining_old = [p for p in by_pkg.get(old, []) if p not in moving]

    # --- 2 & 3. rewrite every reference in the repo ------------------------
    for p in java_files(root):
        text = original = p.read_text(encoding="utf-8")
        for n in names:
            text = re.sub(rf"(?<![\w.$]){re.escape(old)}\.{n}(?![\w$])", f"{new}.{n}", text)
        if text != original:
            p.write_text(text, encoding="utf-8")

    # --- 1b. the moved files' own package line and link depth --------------
    for p in moved_targets:
        text = p.read_text(encoding="utf-8")
        text = re.sub(rf"^\s*package\s+{re.escape(old)}\s*;", f"package {new};", text, flags=re.M)
        text = fix_adr_depth(text, depth_delta)
        text = drop_self_imports(text, new)
        # what it left behind in the old package, and now needs by name
        siblings = sorted(
            {q.stem for q in remaining_old if sees(p, q) and simple_name_used(text, q.stem)}
        )
        text = add_imports(text, [f"{old}.{s}" for s in siblings])
        p.write_text(text, encoding="utf-8")

    # --- 4. the files left behind need imports for what walked out ---------
    for p in remaining_old:
        text = p.read_text(encoding="utf-8")
        needed = [
            f"{new}.{n}"
            for n, q in ((q.stem, q) for q in moved_targets)
            if sees(p, q) and simple_name_used(text, n)
        ]
        text = add_imports(text, needed)
        p.write_text(text, encoding="utf-8")

    # --- 4b. files already in the destination package -----------------------
    for p in by_pkg.get(new, []):
        text = p.read_text(encoding="utf-8")
        text = drop_self_imports(text, new)
        p.write_text(text, encoding="utf-8")

    print(f"moved {len(moved_targets)} file(s): {old} -> {new}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
