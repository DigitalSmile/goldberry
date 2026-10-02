#!/usr/bin/env python3
"""Check the doc comments of source files against the house style.

Reports, for every file given (or every Java, Gradle and workflow source when
none is), the three things ``SourceDocsTest`` in build-logic will fail on and
one thing the formatter will break:

* a citation of the decision log (``ADR-0481``, ``the ADR``);
* in a Java file, a section sign ``§`` (a specification cited at itself, unless
  the line names a public standard such as ITU-T or an RFC), or a working
  document under ``docs/`` named by file;
* a ``https://goldberry.dev/docs/…`` link to a page the book does not have, or
  to a heading that page does not have;
* a ``package-info.java`` in a published module with no guide link;
* a ``///`` doc line the formatter would wrap: over 120 characters at the top
  level, or over 120 minus the indent inside a type, because the half it
  pushes down is no longer a comment.

Usage::

    tools/book/guide_links.py [--anchors CHAPTER] [FILE ...]

``--anchors components/text.md`` prints the anchors a chapter has, the way
mdBook derives them, so a link can be written against them. Exit status is 1
when anything is reported.

The house style is ``book/src/contributing/doc-comments.md``.
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BOOK = ROOT / "book" / "src"
BASE = "https://goldberry.dev/docs/"

RECORD = re.compile(r"\bADR-?\s?\d{3,4}\b|\bADRs?\b")
SECTION = re.compile(r"§")
STANDARD = re.compile(r"\b(ITU-T|ISO|IEC|RFC|ECMA|POSIX|IEEE|H\.26[45]|MPEG|C11|C17|C\+\+)\b")
WORKING_DOCUMENT = re.compile(r"\bdocs/[a-z0-9-]+\.md\b")
GAP = re.compile(r"\bG\d{1,2}\b")
LINK = re.compile(r"https://goldberry\.dev/docs/[^\s)\]\"'<>`]*")
HEADING = re.compile(r"^(#{1,6})\s+(.*?)\s*$")
HTML_ID = re.compile(r'\sid="([^"]+)"')
MAY_CITE = {
    "build-logic/src/test/java/dev/goldberry/build/repository/DecisionLogTest.java",
    "build-logic/src/test/java/dev/goldberry/build/book/SourceDocsTest.java",
}
DOC_LINE_LIMIT = 120


def doc_line_limit(line: str) -> int:
    """The longest a ``///`` line may be before spotless wraps it.

    Measured against palantir-java-format 2.97 on 2026-10-01: a top-level
    ``///`` block, before a type, is left alone up to 120 columns, and a block
    inside a type is reformatted as javadoc, which wraps a line once it is
    longer than 120 minus its indent (116 at four spaces, 112 at eight). The
    half it pushes down loses the ``///`` and is no longer a comment. A line
    that is one backtick code span cannot be broken and is exempt.
    """
    indent = len(line) - len(line.lstrip())
    return DOC_LINE_LIMIT - indent


def is_code_span_line(line: str) -> bool:
    """A doc line whose whole text is one backtick code span, which the formatter never breaks."""
    text = line.strip()[3:].strip()
    return text.startswith("`")


def anchor_of(heading: str) -> str:
    """The anchor mdBook writes for a heading: the same rule as ``Book.anchorOf``."""
    plain = re.sub(r"<[^>]+>", "", heading).replace("`", "").strip()
    out = []
    for ch in plain:
        if ch.isalnum() or ch in "_-":
            out.append(ch.lower())
        elif ch.isspace():
            out.append("-")
    return "".join(out)


def anchors(chapter: str) -> list[str] | None:
    """The anchors a chapter has, or None when there is no such chapter."""
    page = BOOK / chapter
    if not page.is_file():
        return None
    text = page.read_text(encoding="utf-8")
    found = [anchor_of(m.group(2)) for line in text.splitlines() if (m := HEADING.match(line))]
    found += HTML_ID.findall(text)
    return found


def listed_chapters() -> set[str]:
    summary = (BOOK / "SUMMARY.md").read_text(encoding="utf-8")
    return set(re.findall(r"\]\(([^)]+\.md)\)", summary))


def page_of(href: str) -> str | None | bool:
    """The chapter a link names: None for the front page, False when malformed."""
    rest = href[len(BASE):]
    path, _, _fragment = rest.partition("#")
    if not re.fullmatch(r"[A-Za-z0-9/._-]*", path):
        return False
    if path == "":
        return None
    if path.endswith("/"):
        return path + "index.md"
    if path.endswith(".html"):
        return path[: -len(".html")] + ".md"
    return False


def is_published(module: Path) -> bool:
    script = module / "build.gradle"
    return script.is_file() and re.search(r"id\s+'goldberry\.publish'", script.read_text(encoding="utf-8")) is not None


def default_files() -> list[Path]:
    files: list[Path] = []
    for module in ROOT.iterdir():
        if (module / "src").is_dir():
            files += [p for p in (module / "src").rglob("*.java") if "/build/" not in str(p)]
        if (module / "build.gradle").is_file():
            files.append(module / "build.gradle")
    files += [ROOT / "build.gradle", ROOT / "settings.gradle"]
    files += list((ROOT / "build-logic" / "src" / "main" / "groovy").glob("*.gradle"))
    files += list((ROOT / ".github" / "workflows").glob("*.yml"))
    return sorted(p for p in files if p.is_file())


def check(file: Path, listed: set[str]) -> list[str]:
    relative = file.resolve().relative_to(ROOT).as_posix()
    text = file.read_text(encoding="utf-8")
    problems: list[str] = []
    lines = text.splitlines()
    if relative not in MAY_CITE:
        for number, line in enumerate(lines, 1):
            if RECORD.search(line):
                problems.append(f"{relative}:{number}: cites a record: {line.strip()[:100]}")
    if file.suffix == ".java":
        for number, line in enumerate(lines, 1):
            if WORKING_DOCUMENT.search(line):
                problems.append(f"{relative}:{number}: names a working document under docs/; say the rule and link the guide")
            if SECTION.search(line) and not STANDARD.search(line):
                problems.append(f"{relative}:{number}: cites a section (§) of a specification; say the rule in words")
            if GAP.search(line) and ("gap" in line.lower() or "G" in line and WORKING_DOCUMENT.search(line)):
                problems.append(f"{relative}:{number}: cites a gap id; say what the gap was")
        for number, line in enumerate(lines, 1):
            if line.lstrip().startswith("///") and len(line) > doc_line_limit(line) and not is_code_span_line(line):
                problems.append(
                    f"{relative}:{number}: /// line is {len(line)} chars, over {doc_line_limit(line)} at this indent;"
                    " the formatter will break it"
                )
        for number, line in enumerate(lines, 1):
            for href in LINK.findall(line):
                page = page_of(href)
                if page is False:
                    problems.append(f"{relative}:{number}: {href} names no page mdBook writes")
                    continue
                if page is None:
                    continue
                found = anchors(page)
                if found is None or page not in listed:
                    problems.append(f"{relative}:{number}: {href} -> no chapter {page}")
                    continue
                _, _, fragment = href.partition("#")
                if fragment and fragment not in found:
                    problems.append(f"{relative}:{number}: {href} -> {page} has no heading #{fragment}")
        if file.name == "package-info.java" and "/src/main/java/" in file.as_posix():
            module = ROOT / relative.split("/", 1)[0]
            if is_published(module) and BASE not in text:
                problems.append(f"{relative}: a published package with no link to {BASE}")
    return problems


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--anchors", metavar="CHAPTER", help="print the anchors of a chapter under book/src")
    parser.add_argument("files", nargs="*", type=Path)
    args = parser.parse_args(argv)
    if args.anchors:
        found = anchors(args.anchors)
        if found is None:
            print(f"no chapter {args.anchors}", file=sys.stderr)
            return 1
        print("\n".join(found))
        return 0
    listed = listed_chapters()
    files = args.files or default_files()
    problems: list[str] = []
    for file in files:
        if file.is_dir():
            problems += [p for f in sorted(file.rglob("*")) if f.is_file() and f.suffix in {".java", ".gradle", ".yml"} for p in check(f, listed)]
        elif file.is_file():
            problems += check(file, listed)
    for problem in problems:
        print(problem)
    print(f"{len(problems)} problem(s) in {len(files)} path(s)", file=sys.stderr)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
