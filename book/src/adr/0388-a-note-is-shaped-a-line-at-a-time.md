# 388. A note is shaped a line at a time, and drawn a screenful at a time

Date: 2026-09-18

## Status

Accepted. Closes `docs/gaps.md` G44. Changes what
[ADR-0331](0331-a-gutter-numbers-hard-lines-at-soft-positions.md) numbers — the
rows in view rather than the document — without changing how. Leaves
[ADR-0299](0299-a-cache-smaller-than-one-frame-is-worse-than-no-cache.md)'s
paragraph cache as it is and adds one counter to it.

## Context

The entry arrived as a table from a note editor downstream: one `text-area` in
edit mode, gutter on, `class="mono"`, one character typed per frame, and a style
span that grew about linearly with the note while the tree stayed the same size.
It named three suspects — the cascade, per-run shaping, the gutter's numbers —
and said it was guessing.

It reproduces here. `TextAreaFrameBenchmark` (`:widgets`) is that editor: one
filling `text-area` in a 640×600 window, a character per frame, each stage of
the frame timed separately in the launcher's order. Against this branch's base,
`5d70609b`, on linux-x64, medians in ms:

| note | build | style | layout | raster | frame |
|---|---|---|---|---|---|
| 2 kB | 0.12 | 0.73 | 0.40 | 3.12 | 4.63 |
| 50 kB | 0.52 | **6.38** | 0.72 | 12.53 | 20.47 |
| 500 kB | 5.09 | **92.26** | 6.30 | 104.98 | **207.39** |

The first two rows are two hundred frames. The third is sixteen, because two
hundred is not reachable: a 500 kB note dies with an `OutOfMemoryError` after
about thirty keystrokes in Gradle's 512 MB test JVM. That is the first finding
and it is not a detail — see below.

**What it is not.** Restyling a `text-area` whose text did *not* change was
already flat: 0.11 ms at 2 kB and 0.38 ms at 500 kB, on the same checkout. The
tree is seventy-five elements either way, and nothing walks it twice.
[ADR-0315](0315-a-rebuild-is-not-a-restyle.md) holds; a `text-area` does not
defeat it. The cascade was never the term.

**What it is.** `TextAreaBox.render` handed the whole note to
`Paints.Context.paragraph`. A `Paragraph` is shaped whole and keeps two prefix
sums over it, an `int` per character each, over a run of six more `int[]` — for
half a million characters that is about 17 MB and half a megabyte of HarfBuzz.
Every keystroke makes a *different* string, so every keystroke paid all of it.
Counted rather than timed, one keystroke on the old code shaped:

| note | characters shaped | characters drawn |
|---|---|---|
| 2 kB | 2 062 | 2 127 |
| 50 kB | 50 005 | 52 060 |
| 500 kB | 500 005 | 524 600 |

And it always missed the paragraph cache **exactly once**, at every size, which
is why no counter in the toolkit saw this coming: `ParagraphCache.misses()`
counts paragraphs, and the paragraph was the document.

Three consequences of the same fact:

- **The heap.** The cache is sized in entries — 256 of them, "roughly 200 bytes
  an entry" as ADR-0299 sized it, which is a *word*. Two hundred and fifty-six
  versions of a 500 kB note is four gigabytes. Every keystroke put one in.
- **The raster.** `Paragraph.paint` draws every line of its layout and the clip
  only decides what survives, so a ten-thousand-line note made ten thousand
  glyph-run fills to show thirty-five rows. 105 ms of the 207.
- **The gutter.** ADR-0331 draws the numbers as one paragraph with a blank line
  per wrap. It built that string for the whole document — fifty kilobytes for a
  500 kB note, rebuilt every frame and re-shaped whenever the line count moved —
  to draw thirty-five numbers. Real, and second-order next to the text.

There were O(text) scans in the frame too: `hardLines()` counted newlines across
the whole note on every frame, and `caretRect` walked every visual line in the
document to find the caret's. Both are third-order, and both are gone anyway.

## Decision

**A `text-area` holds a document, not a label: the geometry comes from a text
shaped one hard line at a time, and the glyphs are one paragraph of the rows on
screen.**

- **`io.github.digitalsmile.goldberry.text.document.TextDocument`** shapes a text
  one *hard line* at a time. Wrapping was already per hard line —
  `Paragraph.layout` splits on `\n` first and breaks each piece on its own — so
  nothing is lost by shaping the pieces apart. Given the document the last frame
  built it compares the two strings from both ends, a scan that allocates
  nothing, widens the bracket to whole hard lines, and rebuilds only those. Every
  other line keeps the `Paragraph` instance it had, and its wrap memo with it.
- **`DocumentLines`** is that document broken at one width: a computed
  `List<TextLine>` in the whole text's offsets, so everything written against
  `Paragraph.layout().lines()` keeps working and a ten-thousand-line note does
  not allocate ten thousand records to answer three questions.
- **`TextAreaBox` draws the rows in view**, as a slice of the text between two
  line starts. Greedy wrapping restarts at every line start, so re-wrapping that
  slice at the same width gives back exactly the rows the document said.
- **One box for the text, not one per row.** Yoga rounds every box it places onto
  the pixel grid; a box per row would round each row separately while the rows
  *inside* a wrapped line stayed exact. The numbers are placed from the same
  origin for the same reason — one rounding, shared, is what keeps them from
  drifting.
- **`Value.carrier`** is a `text-value` that shapes nothing and reports what the
  cascade resolved. The node still exists, because that is where the ink, the
  `white-space` and the `.placeholder` rule are decided; it cannot hold the
  glyphs, because which rows are in view is settled during `render` and a child
  is described before it.
- **`ParagraphCache.shapedCharacters()`** counts what the misses shaped. The miss
  count could not see this and a stopwatch is not a test.

## Consequences

Same benchmark, same machine, two hundred frames at every size, medians in ms:

| note | build | style | layout | raster | frame |
|---|---|---|---|---|---|
| 2 kB | 0.09 | 0.83 | 0.47 | 3.47 | 4.92 |
| 50 kB | 0.06 | 0.53 | 0.33 | 3.08 | 4.10 |
| 500 kB | 0.05 | 0.90 | 0.27 | 3.04 | 4.36 |

The rows are flat. A keystroke into a 500 kB note shapes 2 021 characters and
draws 2 020, against 1 941 and 1 940 for a 2 kB one — the rows on screen and the
numbers beside them, and the small difference is that six-digit line numbers are
wider than four-digit ones. A settled frame shapes nothing, as it did before.
What G44 said this blocked was a preview under 100 ms on a large note: the whole
frame is 4.4 ms.

- **Opening a note is still proportional to it, and that part is irreducible
  here.** A 500 kB note shapes 499 079 characters when it is first rendered —
  78 to 95 ms across runs on this machine — and again on a theme change that
  resolves a different face. How tall the content is, how far it scrolls and
  where every line breaks are facts about every line, and nothing knows a line's
  height without shaping it. What changed
  is that it is paid **once per document** rather than once per keystroke.
  Shaping the lines below the fold off the frame would close that too, and is in
  `book/src/TODO.md` rather than here: nothing has measured a note large enough
  for 95 ms of opening to be the complaint, and ADR-0045 is about exactly that.
- **The heap is bounded.** The cache never holds more than one version of a hard
  line, and no entry is larger than a line. The benchmark that could not run two
  hundred frames at 500 kB now does.
- **Scrolling re-shapes the window** — about 2 kB per step, which did not happen
  before because the whole document was already shaped. It is flat in the note's
  size and it is what makes everything above flat.
- **The pictures are unchanged.** No golden moved: the text box is one box at one
  origin, as it was, and its lines fall where they fell.
- **`text-value` no longer carries a `text-area`'s glyphs.** A test that read the
  value node's paragraph reads the control's own box instead.
  `TextAreaGutterTest` checks the numbers against the text's own top now rather
  than against the scroll offset — both are drawn from the first row in view, and
  what the entry is about is that they agree.
- **`text-input` is untouched.** A single line is its own window, and the same
  change there would be machinery around a string nobody types half a megabyte
  into.
- **`TextDocument` is exported.** `Editor` — the `canvas` editing seam from
  ADR-0285 — has the same whole-string shaping and is the obvious second caller.
  It is not changed here: nothing has measured it, and ADR-0045 is about exactly
  that.
- `TextAreaKeystrokeCostTest` guards it, in counts rather than milliseconds: a
  keystroke into a 500 kB note shapes and draws about what a keystroke into a
  2 kB one does. Run against `5d70609b` it fails, with 500 005 characters shaped
  against 2 062 and 524 599 drawn against 2 126 — which is the point of a guard.
