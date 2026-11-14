# ADR-0703: Text carries marks

- **Status:** Accepted. D1–D9 of `docs/rich-text-input.md` were put to the
  owner on 2026-11-14 and taken as recommended ("go for it"); this record
  holds D1, D2, D4, D5 and D6, and how the editor and the keys took them.
- **Date:** 2026-11-14
- **Relates to:** ADR-0376 (one key map, three editors), ADR-0697 (rich text
  that answers the pointer)

## Context

A messenger's composer needs inline formatting in an editable field: bold,
italic, code, links and mentions, kept by undo and the clipboard (Tessera's
G-02, here T-02). Everything the toolkit edits text with speaks offsets into
one string: `TextEdit`, `TextDocument`, `EditKeys`, `BreakIterator`, the
clipboard and `Paragraph`. So does Tessera's storage, which keeps a message as
a string and a list of `(offset, length, type)` entities in UTF-16 units.

The plan weighed flat marks against a document tree (D1), a fixed set of
kinds against an open one (D2), the canvas `Editor` gaining marks against a
second editor (D4), how undo keeps formatting (D5) and how marks travel
through the clipboard (D6).

## Decision

**The value is a string and its marks (D1).** `dev.goldberry.text.mark`, a
new exported package in `:core`: `Mark(start, end, kind)` in `char` offsets,
`MarkedText(text, marks)` with the marks in a normal form (sorted, inside the
text, never empty, one mark per stretch of one non-atomic kind, no two
conflicting kinds over one char), so equal formatting is an equal value.
`MarkedEdit(value, anchor, caret, typing)` is the marked twin of `TextEdit`:
every movement and deletion is `TextEdit`'s, and marks add three rules.
Typing takes the kinds of the marks the caret is inside or that end at it and
grow, or what a toggle with nothing selected set; atoms are stepped over and
deleted whole; a paste keeps its own marks and takes nothing from beside it.
`MarkedText.replace` is the one edit, and its four rules are in its comment.
No nested blocks: a composer needs none, and a tree would be a second
position model under every text API.

**The kinds are a sealed set and the application's own (D2).** `MarkKind` is
`Style` (`BOLD`, `ITALIC`, `UNDERLINE`, `STRIKE`, `CODE`, `SPOILER`),
`Link(URI)`, `Mention(id)` and `Custom(name, value, Behaviour)`. A kind's
`Behaviour` says whether it grows when typed at its end, whether it is an
atom, and whether anything may overlap it; `MarkKind.conflict` decides
overlaps (code admits only a spoiler, two links to different places
conflict, nothing overlaps an atom). Its `className()` is how the stylesheet
styles it.

**`Editor` holds a `MarkedEdit` (D4).** Its public API is unchanged with no
marks, and gains `value()`, `value(MarkedText)`, `marked()`, `toggle`,
`mark`, `unmark`, `clearFormatting`, `replace` (how a mention goes in where
its query was typed), `activeKinds()` and `markStyles(MarkStyles)`. Without
styles the marks are kept, copied and undone, and drawn plain. With them a
hard line whose marks change the font is shaped piece by piece in
`MarkStyle.font()` and joined by the new `Paragraph.joinOnGrid`, which
measures every line in the editor's own font, so the caret, a click and the
scroll keep counting rows of one height. Colours, rules and fills become
`SpanPaint`s. `TextDocument` learns that a line is its text and its marks:
`TextDocument.MarkedShaper`, and a line shaped again when either changes, with
a marks-only change touching only the lines whose marks moved.

**The formatting keys are their own map.** `EditKeys.format(event)` answers a
`FormatCommand` (`Toggle(style)`, `CLEAR`, `LINK`), apart from `EditCommand`,
so `text-input` and `text-area` change nothing and leave `Ctrl+B` to the
application. The set is Telegram Desktop's. An `Editor` takes them once it has
styles; `Ctrl+K` is left to its caller, who asks for an address.

**Undo keeps states (D5).** `EditHistory` is `EditHistory<S>` over whatever
state its editor holds, with a fourth kind, `FORMATTING`, that never folds.
A toggle with nothing selected changes only what is typed next and is no
step at all.

**The clipboard carries a type of the toolkit's own (D6).**
`MarkedClipboard` offers `application/x-goldberry-marked` (a header, a mark
per line, the text, UTF-8) with `text/plain;charset=utf-8` and `text/plain`,
and falls back to `Clipboard.text` where a backend refuses typed offers.
A paste prefers the toolkit's type, then the text; bytes that do not decode
are read as nothing. HTML in either direction stays with `:html` (phase 6).

## Consequences

- `EditHistory` is generic: a field typed as the raw class compiles with a
  warning, and the three editors in the toolkit name their state. Listed in
  the breaking changes, though no call breaks.
- `text.edit` and `text.mark` depend on each other (`MarkedEdit` builds on
  `TextEdit`, `Editor` on `MarkedEdit`).
- A mark in a larger face draws larger glyphs on the field's rows rather than
  pushing them apart. A label that wants that uses `Paragraph.join`.
- The canvas sticky note in the showcase takes the formatting keys.
- What another desktop application does with the toolkit's type, and whether
  SDL serves `text/plain;charset=utf-8` from a data offer as text on every
  platform, are checked by hand, not by a test here.
- Tested by `MarkKindTest`, `MarkedTextTest` (normal form, asking, the edit
  rules), `MarkedEditTest` (typing, toggles, atoms, undo), a seeded property
  test over random edits (`MarkedEditPropertyTest`: normal form, atoms never
  split or changed, the caret never inside one, undo back to the start),
  `MarkRunTest`, `MarkedClipboardTest`, `TextDocumentMarksTest` (which lines
  are shaped again), `FormatKeysTest`, `EditorMarksTest` (shaping, rows on the
  grid, the caret across a seam, a click at a seam, `Down` keeping its column,
  keys, read-only, a mention, a composition in bold, the clipboard between two
  editors) and `EditHistoryTest`'s `FORMATTING` case. The editor's existing
  tests pass unchanged.
