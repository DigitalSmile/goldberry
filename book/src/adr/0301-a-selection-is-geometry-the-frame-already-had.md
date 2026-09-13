# 301. A selection is geometry the frame already had

Date: 2026-09-13

## Status

Accepted. Makes both content views selectable — drag, double-click, triple-click,
`Ctrl+A`, `Ctrl+C` — and closes the last item on `docs/gaps.md` G17 that was not
an engine's.

Completes [ADR-0300](0300-a-document-is-read-and-the-application-answers.md),
whose "Text selection is not here, and this is what it needs" section is the
design this carries out. Depends on
[ADR-0119](0119-a-widget-may-be-told-where-it-is.md)'s `Located` and
[ADR-0299](0299-a-cache-smaller-than-one-frame-is-worse-than-no-cache.md)'s
paragraph cache, and it could not have been built cheaply without either.

## Context

Both views rendered documents a reader could not take a copy of, and ADR-0300
listed three obstacles rather than building it:

1. **Hit-testing a point to a word and an offset.** `build` and `render` run
   before Yoga, so a word has no idea where it is (ADR-0080), and a widget can see
   its own size but not its children's rectangles.
2. **Painting the highlight without a rebuild.** A `selected` class per word means
   a rebuild of six hundred widgets per pointer move — the frame shape ADR-0299
   had just removed.
3. **Character granularity.** A selection that snapped to whole words is not a
   selection anybody uses twice.

Each of those had an answer already in the toolkit; what was missing was the
observation that they fit together.

## Decision

### Every word says where it landed

`Located` — the third geometry facility, and the only one that carries a
**position** — tells a widget where the last frame painted it and what clips it,
in window coordinates, once a frame and only when it changes. That is exactly
what a selection needs, and *scrolled* is the position it reports, which is what
"where the reader sees the words" means.

So a document's words are no longer `text` widgets from the catalog but
`content.select.Word`:

- it draws what `text` draws, through the same measured-leaf box and the same
  paragraph cache — **one widget per word either way**, which is the whole reason
  this is affordable after ADR-0299;
- it is `Located`, so it reports its rectangle into a `WordGeometry`;
- it hands its shaped `Paragraph` to that geometry, which is what turns an **x**
  into a character offset — `Paragraph.offsetAt`, the same arithmetic a caret in
  a `text-input` uses.

A wrapper node per word was the obvious alternative and is the one that costs:
twice the elements for a document, which is precisely the cost the previous
record paid down.

### A drag repaints; it does not rebuild

The selection is **mutable state** — two carets — that the pointer handler writes
and an overlay's painter reads at paint time. A pointer move therefore costs one
`Host.repaint()`: no build, no cascade, no layout. That is the decision the rest
of the design hangs off, and it is why the highlight is a painter rather than a
class.

The overlay is `selection-layer`, absolutely positioned and inset to nothing, so
it fills the document's padding box and contributes no layout. It is the **first**
child, because paint order is document order: the wash goes behind the words
rather than over them. It is `Located` too, which is how window rectangles become
its own coordinates with no assumption about padding or borders.

Its colour is `var(--gb-selection)` from the cascade, so a theme decides what a
selection looks like.

### What a reader can do

Drag to select; double-click for a word; triple-click for a block — a paragraph,
a heading, a cell, a line of a fence; `Ctrl+A` for the document; `Escape` to let
it go; `Ctrl+C` to copy. Links and images are part of a selection: their
rectangles are washed and a link's label is in what gets copied, because a
selection that skipped them would copy "Read first." out of "Read the help
first."

**The copied text carries the separators the document implies** — a space between
words, a newline between blocks — and that information is the *fold's*, because a
wrapping row draws its spaces as gaps between boxes rather than as characters.
Each word therefore carries the separator that belongs in front of it, and
`WordMinter` is where the fold says so. Without it a copy pastes
`Thequickbrownfox`.

Two things it deliberately is **not**: an editor — no caret, nothing blinks,
nothing can be typed — and a drag that starts on a link or a task box, because
the router captures on press and that press belongs to the control.

### A selection is dropped when the document changes under it

A preview re-parses on every keystroke. The geometry notices when a build
registers different words from the last one and the selection is cleared, because
keeping it would highlight whatever is now at those indices. A rebuild that
changes nothing keeps it, which is the half that makes the rule worth having: a
frame that re-parsed the same text must not take a reader's selection away.

## Consequences

**The last non-engine item on G17 is closed.** What litehtml would still buy is
real inline layout — a line of mixed faces as one shaped run, and with it
justification and hyphenation. Selection is no longer on that list.

**A document's words are a `word` CSS type rather than `text`.** No stylesheet in
the repository styled a bare `text` type, and both content sheets style the
`md-word` / `html-word` classes, which are unchanged — so nothing moved visually,
which the goldens say. An application that styled `text` **inside a document**
would have to say `word` now.

**Every word is `Located`, so the router notifies six hundred nodes a frame.** It
is a map write and a small record each, measured at no change to the document's
style (0.64 ms) or layout (0.82 ms) pass. Worth knowing it is there: the notify
walk is now proportional to the words on screen rather than to the handful of
widgets that used to ask.

**The view is stateful now**, through one `SelectableDocument` node between the
view and the fold's column. `MarkdownView` and `HtmlView` stay the same records
they were — the state is in a node they build, which is also what lets both
halves share every line of this.

**A `selection-host` node wraps every rendered document.** It hears the pointer
and the keyboard, is focusable — `Ctrl+C` goes to whatever has the focus — and
carries `cursor: text`. It draws nothing. One consequence is visible: a
background on `.markdown` stops where the words do rather than filling the pane,
because the pane's child is now the host. The golden tests frame their documents
on the host for that reason.

**Auto-scroll while dragging past the edge is not implemented.** Dragging to the
bottom of a viewport stops selecting rather than scrolling on; the design for it
is the same one a `text-area` wants and neither has it yet.

## Alternatives considered

**A `selected` class on each covered word.** The obvious implementation, rejected
on arithmetic: a class is part of a widget's description, so every pointer move
would rebuild the document and re-resolve every style — 600 elements at 60 Hz, to
change a colour. The painter reads mutable state instead and the rebuild never
happens.

**Wrapping each word in a reporting node.** Correct, and twice the elements: a
document is already one widget per word, and ADR-0299 is the record about what
that costs when it is multiplied.

**Selection in the text stack**, by making a paragraph of mixed faces one
selectable run. That *is* the engine — it needs the inline layout ADR-0298 parked
— and it would have to wait for it.

**Word granularity only**, snapping a selection to whole words. Cheaper, and
wrong: the offsets come free from `Paragraph.offsetAt`, which the editor already
uses for the same purpose.

**Copying through the model instead of the rendered words** — walk the document
tree between two carets and serialise it. Rejected: the carets are positions in
what was *drawn*, the mapping back to the model is a second thing to keep
correct, and what a reader selected is what they can see. The words already carry
their own separators.
