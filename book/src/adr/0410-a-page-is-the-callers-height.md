# 410. A page is the caller's height

Date: 2026-09-19

## Status

Accepted. Closes the `book/src/TODO.md` entry "No word-wrap-aware
`PageUp`/`PageDown`" under *Editing text*. Adds one method to the `canvas`
editing seam [ADR-0285](0285-a-caret-is-the-text-stacks-and-not-a-controls.md)
opened; the key map itself
([ADR-0376](0376-one-key-map-three-editors.md)) is untouched, because what a
page *is* was never the map's question.

## Context

`Editor.pageLines()` returned `10`, with a comment admitting it:

```java
/// Lines to a page. Ten, and it is a guess: a page is the height of a
/// viewport, and an editor drawn on a canvas has none. A caller that knows
/// better moves the caret itself.
private int pageLines() {
    return 10;
}
```

The admission is right about the problem and wrong about the remedy.

**Right about the problem.** `EditKeys` turns `PageDown` into
`MoveLine(1, byPage = true, extend)` and leaves "how many lines is a page" to
whoever knows how tall the viewport is — which for `text-area` is
`visibleRows()`, a measured height divided by a line height
([ADR-0297](0297-an-editor-fills-its-pane-and-a-split-knows-its-own-width.md)).
An `Editor` has no box. It is handed a `Frame` and an `(x, top)` and told to
draw; the viewport it is inside belongs to the canvas, at the canvas's transform,
which is the whole point of the class. So ten it was, at every size: `PageDown`
in a six-line sticky ran off the end of it, and `PageDown` in a forty-row pane
moved the caret a quarter of the way down the screen and left the reader looking
for it.

**Wrong about the remedy.** "A caller that knows better moves the caret itself"
costs more than it sounds. Moving the caret by a page means asking
`TextGeometry.moveLine` for a target offset, which means holding the column a run
of vertical movement is keeping — and `desiredX` is private state that
`Editor.verticalBy` sets *after* the move for a reason ADR-0285 argues at length.
A caller doing this itself re-implements that, gets the column-keeping subtly
wrong, and then has to intercept `PageUp`/`PageDown` before `onKey` sees them so
the editor does not also move by ten. The caller knows one number. The editor
knows everything else.

Nothing about `Up` and `Down` was ever wrong, which is why this sat in
`book/src/TODO.md` rather than in `docs/gaps.md`: a page key that moves by the
wrong amount still moves by lines, still keeps its column, and still lands on a
grapheme boundary.

## Decision

**A caller says how tall its viewport is, and a page is the whole lines that
fit.**

```java
public Editor viewportHeight(double height) {
    this.viewportHeight = height;
    return this;
}

private int pageLines() {
    var lineHeight = font.lineHeight();
    if (Double.isNaN(viewportHeight) || viewportHeight <= 0 || lineHeight <= 0) {
        return DEFAULT_PAGE_LINES;
    }
    return Math.max(1, (int) Math.floor(viewportHeight / lineHeight));
}
```

Four things about the shape, and the last one is the decision.

**A height, not a line count.** A height is what a caller has: a `Canvas`'s paint
callback is handed an `Extent`, a sticky on a board is a rectangle, a cell in a
drawing is two corners. A line count is what a caller would have to *derive*, by
dividing by a leading it does not own — this editor's font is this editor's, and
a caller that guessed 16 for a 13-point face would page by the wrong number in a
way no test of theirs could see. One division, done on the side that has both
operands.

**In the text's own space.** Every other number this class takes or hands back is
(ADR-0285's *Coordinates*), so a caller under a scale transform divides once and
this class stays free of the notion that there is a transform at all.

**A page is a screenful, not a screenful less a line.** Editors that *scroll*
page by `rows - 1`, so that the bottom line of the old screen is the top of the
new one and the eye has an anchor. That overlap is a property of the scroll, and
this editor does not scroll — its caller does, if it does at all. Keeping a line
back here would take a line off every caller's page to buy an anchor only some of
them can show.

**Ten is still the answer for a caller that says nothing**, and that is not
inertia. Every alternative default is worse in kind rather than by a factor:

- **Zero** — `PageUp` reports `true`, consumes the key and moves nothing. The
  worst outcome a key can have, because it also stops the application's own
  handler from seeing it.
- **One** — `PageDown` becomes `Down` under a second name, and the key that is
  meant to cover ground covers none.
- **The whole text** — `PageDown` becomes `Ctrl+End`, which the map already has,
  and the selection a `Shift+PageDown` builds becomes select-all.
- **Refusing the key**, so it falls through unhandled — defensible for a *label*
  and wrong for an editor: an editor that has a caret and a text has an answer to
  "move down a screenful", and the one thing it does not know is the screen.

Ten lines is a guess about the box and never a guess about the *meaning*: it
moves by lines, keeps the column, and stops at the ends. It is also exactly what
this editor did before today, so no caller's `PageDown` changed under it —
which is the property that lets the new method be optional rather than a
migration.

## Consequences

- **Nothing is invalidated when a caller says it.** How tall the viewport is
  changes what one key means; it does not change where a line breaks, so the
  shaping and the wrap memo survive being told. `EditorPageTest` asserts that
  with `assertSame` on both, because the cheap thing to write would have been an
  `invalidate()` and nobody would have noticed for a year.
- **A viewport shorter than one line pages by one line.** The caller has said it
  is drawing into something, and a `PageDown` that reports `true` and moves
  nowhere is the outcome ruled out above.
- **The count is in visual lines**, so a wrapped text pages by rows and not by
  paragraphs — which is what "on screen" means, and what
  [ADR-0411](0411-an-editor-shapes-a-line-at-a-time.md) makes cheap to ask.
- **`text-area` and `text-input` are untouched.** A `text-area` already divides
  its measured height by its line height, and a `text-input` is one line, where
  `EditSurface.FIELD` gives the page keys no meaning at all.
- **No picture moved.** `gallery-canvas.png` is the same image: a viewport is a
  key's meaning and not a pixel.
- `EditorPageTest` holds it — a declared viewport in whole lines, in visual lines
  under a wrap, extending with `Shift`, running off the end, and the three ways
  of saying nothing (`NaN`, zero, negative) all meaning ten.
