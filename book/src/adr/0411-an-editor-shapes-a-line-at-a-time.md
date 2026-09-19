# 411. An editor shapes a line at a time

Date: 2026-09-19

## Status

Accepted. Closes the `book/src/TODO.md` entry "`Editor` still shapes its whole
text" under *Rendering and performance*, which
[ADR-0388](0388-a-note-is-shaped-a-line-at-a-time.md) left open in as many words:
"`TextDocument` is exported. `Editor` — the `canvas` editing seam from
[ADR-0285](0285-a-caret-is-the-text-stacks-and-not-a-controls.md) — has the same
whole-string shaping and is the obvious second caller. It is not changed here:
nothing has measured it."

Something has now. `TextDocument` is unchanged; `TextGeometry` gains the four
questions over one.

## Context

`Editor` held one `Paragraph` over everything it was given:

```java
public Paragraph paragraph() {
    var current = paragraph;
    if (current == null) {
        current = Paragraph.of(font, displayText());
        paragraph = current;
        layout = null;
    }
    return current;
}
```

A `Paragraph` is shaped whole and keeps two prefix sums over it, an `int` per
character each (ADR-0388 measured that at about 17 MB for half a million
characters). **Every keystroke makes a different string**, so every keystroke
paid all of it — the same fact that made a `text-area` over a 500 kB note cost
207 ms a frame, one layer down and with no widget in the way.

The entry said nobody had measured it, and that was the honest reason not to act:
`Editor` was written for "a sticky on a board, a label on a shape, a cell in a
drawing" (ADR-0285), and shaping a sticky is microseconds. But the class is
exported, it is what an application drives when it wants an editor the toolkit
has no widget for, and `example`'s own canvas screen puts one on a board. Nothing
in its API says "not for documents"; it takes `multiline(true)` and a
`wrapWidth`, which is a document's two knobs.

`EditorKeystrokeBenchmark` (`:core`) asks the narrow question: one keystroke — a
character typed, the caret placed — at three sizes. On linux-x64, medians in ms,
on a machine with a load average of 13 (this repo's own note about
`FrameBudgetTest` applies: the absolute numbers move by a factor between runs,
the ratio does not):

| text | before, one paragraph | after, a hard line at a time |
|---|---|---|
| 2 kB | 2.498 | **0.235** |
| 50 kB | 10.860 | **0.553** |
| 500 kB | 111.454 | **1.444** |

A quieter repeat of the same benchmark read 1.583 / 9.702 / 145.731 against
0.206 / 0.413 / 0.780.

Counted rather than timed, which is the number that does not depend on the
machine: one keystroke into a 500 kB text used to shape 500 097 characters. It
now shapes **132** — the hard line the caret was on — against 129 for a 2 kB one,
and the difference between those two is that `Line 3521` is four characters
longer than `Line 9`.

## Decision

**An `Editor` holds a `TextDocument`: the text shaped one hard line at a time,
re-shaped one hard line at a time, exactly as `text-area` has since ADR-0388.**

### The shaping

```java
public TextDocument document() {
    var current = document;
    if (current == null || stale) {
        current = TextDocument.of(font, displayText(), current, shaper);
        document = current;
        stale = false;
    }
    return current;
}
```

`displayText()` and not `text()`, unchanged from before: a composition is drawn
*inside* the text so the words after it move along, and the caret, the hit test
and the paint must all measure the same shaping
([ADR-0289](0289-a-composition-is-not-an-edit.md)).

The old document goes **in** as well as coming out, which is what makes it
incremental: `TextDocument.of` brackets the edit by comparing the two strings
from both ends, widens the bracket to whole hard lines, and rebuilds only those.
Every other line keeps the `Paragraph` instance it had, and its wrap memo with
it.

`stale` is a flag rather than a null, because the stale document is the *input*
to the new one. And a flag rather than letting `TextDocument.of` notice by
itself: that comparison is two passes over the text, and one frame asks for the
caret, the selection, the rows and the paint. One comparison per edit is the
bargain this class is making; four per frame is not.

### `shaper`, so a caller with a cache can offer it

`TextDocument.Shaper` is a function so that the class that shapes does not decide
who caches (ADR-0388). `Editor` passes it along: `Paragraph::of` with its own
font by default, which caches nothing, and a caller inside a frame can hand over
the renderer's paragraph cache and get the sharing the rest of the frame gets.
Lines already shaped keep the paragraphs they have — a new shaper is asked only
for the lines that change from here, so handing one over on the first frame that
has one is free.

### The geometry moves to `TextGeometry`'s document forms

`caretAt`, `offsetAt`, `moveLine` and `selectionRects` gain a form over a
`TextDocument` and its `DocumentLines`. They are in `TextGeometry` and not in a
second class, because what a caret *is* does not depend on how the glyphs are
held, and two classes would be two places for that answer to drift.

One of the four is not a translation:

```java
var last = Math.min(lines.indexOf(to), lines.size() - 1);
for (var i = lines.indexOf(from); i <= last; i++) {
```

The paragraph form walks **every** line of the layout and skips what does not
intersect. That is free for a label and is a walk over ten thousand rows to draw
a highlight over three of them — every frame, in the one place a user is holding
the mouse button down. The document form visits the rows the selection is on.

### The paint is one paragraph per hard line

```java
for (var k = 0; k < shaped.hardLineCount(); k++) {
    shaped.paragraphOf(k).paint(frame, x, top + rows.firstVisualOf(k) * lineHeight, wrapWidth, argb, flow);
}
```

Where the whole text's single paragraph drew each line is exactly where this
draws it: wrapping was already per hard line — `Paragraph.layout` splits on `\n`
first and breaks each piece on its own — and the rows of a wrapped line are
consecutive, so hard line `k` starts at the number of rows above it.

**ADR-0388's other half is not available here, and that is a decision rather than
an omission.** A `text-area` draws the rows in view because it *is* the viewport:
it owns the scroll offset and the box. An `Editor` is handed an `(x, top)` and
told to draw, under a transform it never sees; the rows on screen are the
caller's arithmetic, and a class that guessed at them would clip a board's sticky
at a zoom it knew nothing about. So the raster is still proportional to the text,
the caller clips as it already must, and `viewportHeight`
([ADR-0410](0410-a-page-is-the-callers-height.md)) is deliberately **not** read
here: it is how tall the viewport is, not where it is.

## Consequences

- **`paragraph()` and `layout()` are gone**, replaced by `document()` and
  `lines()`. This is a published API and the break is the point: a method that
  hands out one `Paragraph` over the whole text cannot survive a text that has no
  such paragraph, and leaving it to shape one on demand would have kept the cost
  under a name that looks like a getter. `lines()` returns a `DocumentLines`,
  which is a `List<TextLine>` in the whole text's offsets, so everything written
  against `layout().lines()` reads the same. In-repo there were three callers and
  all three are tests.
- **A resize now re-wraps and shapes nothing.** The old `wrapWidth` threw the
  layout away and kept the shaping; `TextDocument.lines(width)` memoises the
  break per line, so both survive.
- **Opening is still proportional to the text**, and this does not fix it: how
  tall the content is and where every line breaks are facts about every line, and
  nothing knows a line's height without shaping it. Measured, on the same loaded
  machine: 3.9 ms at 2 kB, 13.7 ms at 50 kB, 130.1 ms at 500 kB. What changed is
  that it is paid **once per text** rather than once per keystroke. Shaping the
  lines below the fold off the frame would close it, needs a viewport this class
  does not have, and is ADR-0045's subject.
- **A keystroke is no longer flat in the text's size, but it is flat in the
  shaping.** 0.235 ms to 1.444 ms across 250× the text is `TextDocument.of`'s
  own comparison — two passes over the characters, which its javadoc names as
  the trade — plus an array copy per hard line. It is the ratio the class was
  designed around, and it is 77× cheaper than shaping.
- **No picture moved.** `gallery-canvas.png`, which draws the example's sticky
  through this editor, is byte-identical: one box, one origin, the same rows.
- **`text-area` keeps its own copy of the arithmetic.** `TextAreaState` answers
  the same four questions inline against `TextDocument`, and it is not moved onto
  `TextGeometry`'s new forms here. That is a real cost — two implementations of
  "which row is this offset on" that only agree because both are tested — and the
  reason it is paid is that a `text-area` carries a gutter, a padding, a scroll
  offset and nine golden images, and nothing about this change asks for that risk.
  The forms exist now, which is what a later sweep would need.
- `EditorDocumentTest` holds it in counts, like `TextAreaKeystrokeCostTest` one
  layer up: one line re-shaped per keystroke at both sizes, the untouched lines
  keeping their `Paragraph` instances, a resize shaping nothing, a selection
  measuring nothing. Its second half, `Agreement`, is the part a user would
  notice — carets, presses, selections and `Down` are compared against the
  whole-text shaping they replace, at every `TextAlign`, and land within a
  logical unit of it.
- `EditorKeystrokeBenchmark` is where the milliseconds are, and it asserts
  nothing.
