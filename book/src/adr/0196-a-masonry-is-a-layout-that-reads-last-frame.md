# 196. A masonry is a layout that reads last frame

Date: 2026-08-23

## Status

Accepted, and **outside `core-widgets.md`**. §5's containers are `panel`, `card`,
`group-box`, `tabs`, `split-pane`, `accordion`, `collapse`, `carousel` and
`skeleton`, and the group was complete without this one. Recorded here and in
`ARCHITECTURE.md` §17.1 rather than slipped in as though the canon had asked for
it.

## Context

The Charts screen is a wall of cards, and a wall of cards is where the catalog
had nothing to offer. A `donut-chart` is square, a `statistic` is three lines
tall, a `line-chart` is whatever height it was given. In a `row` of `column`s
built by hand, whichever column got the tall cards hangs off the bottom; in equal
rows, every card is as tall as the tallest beside it and the short ones sit in
acres of empty surface. Both read as a mistake rather than as variety.

CSS has `column-count` and, recently, real masonry. Goldberry has neither: §8's
subset has no `columns`, and **Yoga is a flexbox engine** — flexbox cannot do
masonry, which is precisely why the CSS working group spent years on a separate
specification for it.

## Decision

### It reads the frame before

Nothing can tell a widget how tall a child will be before that child is laid
out. So `masonry` does not try: every card reports what it came out as through
[`Measured`](0117-a-widget-may-be-told-what-it-measured.md), the state banks it,
and the **next** frame puts each card under whichever column is currently
shortest. The first frame is round-robin and the second is a masonry — one frame
of settling, at 60 Hz, that nobody sees.

### Why that is allowed here and not in general

`Measured`'s third rule is that **what it triggers must not change what it
reports**: a widget that resized itself from its own measurement would be told a
new size, resize, and never settle. The rule is why the mechanism has had exactly
one implementation (a scrollbar, which is absolutely positioned and so cannot
affect what it measures).

A masonry passes it, and the argument is one sentence: **the columns are equal
width, so a card's height does not depend on which column it is in.** The number
being reported is stable under the thing it causes. That is also why `columns` is
a *count* and not a list of widths — unequal columns would make this a loop
rather than a layout, and there would be no way to tell from the outside.

There is a test for the property rather than a comment claiming it: four frames,
and the assignment must be identical after the second.

### The column width is written by the widget

`1/n` of the row, where `n` is a number **no selector can count** — which is
[ADR-0099](0099-an-indicator-travels-on-a-grid.md)'s situation exactly and takes
its answer: `restyle` writes the inline value the cascade cannot express.
`flex-grow: 1` alone sizes a column to its *content*, which would break the
equal-width property above; `flex-basis: 0` would have said it in CSS and is the
one §8 property still unimplemented.

### Ties go to the emptier column

Before anything is measured every total is zero, so a plain "strictly shorter"
comparison never fires and the whole first frame stacks into column one. The
tiebreak — equal totals, fewer cards — is what makes an unmeasured frame fill
across. It was found by the test that asserts the first frame is round-robin,
which existed because the two-frame behaviour is the entire widget and had to be
pinned at both ends.

### Reading order is down each column

Which is what masonry means, and is its one real cost: the third card is not
necessarily beside the second. Where that matters — a form, a ranked list — the
answer is a `column` and not this. Said on the class so nobody has to discover it
from a screenshot.

## Consequences

- **The catalog has a widget the design documents do not.** That is a real
  divergence and is recorded in §17.1 with the others rather than being resolved
  by editing the canon, which is not mine to edit.
- **`Measured` has a second implementation**, and it is the first that is not
  absolutely positioned. The rule it has to satisfy is now stated as an argument
  about equal widths rather than as "the one implementation obeys it by
  construction".
- **A card keeps its element when it moves between columns**, because a cell is
  keyed by position. A masonry that reset every card on its second frame — losing
  a chart's animation, a scroll offset, a caret — would be worse than no masonry.
- **It is one frame behind on resize too.** A window drag re-measures every card,
  so the wall re-balances a frame after the width changes. At 60 Hz that reads as
  the layout following the drag; at 5 fps it would read as lag, and the honest
  fix there is fewer cards rather than a cleverer layout.
- **The golden harness had to grow to photograph it**, and that closed something
  else — see the note in `GalleryGoldenTest`: the gallery images never fed
  hit-test regions back between their two frames, so *every* self-measuring
  widget saw a first-frame answer for ever. `text-area` wrapped as though it were
  narrow in the Forms image, and TODO.md said so. Masonry made the gap concrete
  enough to close.
