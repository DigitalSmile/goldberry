# ADR-0597: A list whose rows vary measures them as it builds them, and keeps the reader's line

- **Status:** Accepted
- **Date:** 2026-10-10
- **Relates to:** docs/gaps.md (G59),
  [ADR-0392](0392-a-timeline-opens-at-its-end-and-keeps-the-readers-line.md)

## Context

`ListView.virtualized(double)` and `virtualized()` build only the rows a
viewport can see and stand the rest off with two spacers. The arithmetic is
index × pitch, so every row has to be one height, and the method's own note
told a list whose rows vary not to virtualize at all. A chat timeline is that
list: a message is one line or twelve, with a picture or an album or a quote,
and no row's height is known until it has been laid out. Tessera built every
message of a conversation on every rebuild, 77 ms at two thousand messages,
and paging history in only makes the conversation longer.

G48 (ADR-0392) gave `scroll` the three things a timeline wants of its viewport:
open at the end, stay at the end while the reader is there, and keep the
reader's line when rows arrive above. A virtualized timeline must keep all
three.

## Decision

**`ListView.virtualized(RowHeights.estimating(64))`.** `RowHeights` is a record
holding one number, the height counted for a row that has not been built. The
entry proposed `Measured.estimating(64)`; the name is `RowHeights` because
`dev.goldberry.input.handler.Measured` is the geometry interface the rows
themselves now implement, and two `Measured` types in one file is a misread
waiting to happen. `ListView` gains a twelfth component, `@Nullable RowHeights
rowHeights`. The eleven-argument constructor every caller wrote stays as an
overload, and both fixed forms clear the field again.

**What the list keeps** (`MeasuredWindow`, package-private):

- The height each row came out as, keyed by the item's **identity**. Rows are
  keyed by identity already (`ListRow.key()`), so a prepend leaves every
  measured height where it was.
- Where every row begins: an array one longer than the model, the sum of
  measured heights and the estimate for the rest. It is recomputed in one pass
  when the model, the estimate or a height changed, and not before it is read.
  The spacers are its differences, so the list's height, and so the scroll
  extent, is *Σ measured + unmeasured × estimate* and converges as rows are
  measured.
- The window, by index, carried across a model change by the identity of its
  first row. Without that, a page of history prepended above would leave the
  window on the indices it had and build rows the reader is not looking at.
- **The reader's line**: the first row that begins at or below the viewport's
  top edge, by identity. That is the node `scroll`'s preservation anchors to,
  for the same reason.

**How a row says its height.** The window's rows are `MeasuredRow`s: a
`ListRow` and a callback, implementing `Measured`. The router tells a
`Measured` node its border box when it changes, which is on the frame it is
first laid out and whenever it changes after. A separate type and not a flag
on `ListRow`, because being `Measured` puts a node in the router's per-frame
walk, and a fixed-height list must not pay for a height per row. The rows
carry the class `measured`, and `controls.css` gives `list-row.measured`
`height: auto` with the row token as its minimum.

**Keeping the reader's line when an estimate is wrong.** A row above the
reader's line that comes out taller or shorter than it was counted moves the
line by the difference. The list moves the enclosing `scroll` by the same
amount through two new `ScrollScope` members: `shift(dx, dy)` (the
`ScrollState.shiftBy` that `preserve-on-prepend` already used: no glide, no
woken bars, ignored by an `END` viewport at its end) and
`preservesOnPrepend()`. **A viewport that preserves its line does the
correcting itself**: its `Anchored` walk sees the reader's row move and
shifts, so the list leaves it alone and nothing is counted twice. A `START`
viewport without preservation gets the list's correction. A row below the line
moves nothing on screen and corrects nothing.

The correction lands on the frame after the row was laid out. That is
`Measured`'s bargain and ADR-0392's, unchanged. While a correction is pending,
the window is fitted to where the viewport is about to be, not where it was
painted. The same goes for a prepend under a preserving viewport. Otherwise
the frame after a prepend builds the rows that slid under the old offset, and
the row the viewport anchors to is not built when it is needed. The reader's
line is re-read only on a frame that corrected nothing. That is the rule
`PointerRouter.notifyAnchored` follows.

**A width change forgets the heights**, apart from the rows built at that
moment. The frame that changed the width has just measured those again. The
reader's line is corrected for what forgetting moved.

**Reaching a row** (`Home`, `End`, the typeahead) scrolls. The fixed-height
path widens its window to take in the row reached, which on a measured
timeline would build every row between the viewport and the target. A
measured list instead moves its window to start at the row and nudges the
enclosing `scroll` so the row's top is at the top edge. Where the row begins is
known, measured or estimated, without building anything above it. The row
becomes the reader's line, so the rows built above it as the window settles
move the viewport and not the row. Once the row's own height is known, the
list brings it the rest of the way into view by the least distance. Without
that, the last row placed at an estimate of 64 and measured at 120 would hang
56 pixels below the viewport.

## Where reality differed from the entry

- The entry asked for `Measured.estimating(64)`; it is
  `RowHeights.estimating(64)`, for the name clash above.
- "Re-anchor when a measured height differs from its estimate" needed no new
  mechanism under a preserving `scroll`: G48's `Anchored` walk already sees
  the row move. The list's own correction is for a viewport that does not
  preserve.
- The fixed-height `End` was found to widen its window from wherever it is to
  the last row. On ten thousand rows that builds ten thousand rows for a frame,
  and nothing scrolls to the row, because focusing a row does not reveal it.
  That path is unchanged here, by the brief's rule that the fixed path stays as
  it is. The measured path does not widen and does scroll.

## Not done

- A public "scroll to item" call. Reaching a row is the keyboard's (`Home`,
  `End`, typeahead), as it was. An application that wants to jump to a message
  has no API for it yet.
- A benchmark. None exists for `ListView` in a `src/benchmark` source set, and
  the tests count builds rather than time them.
- Margins between rows, or a `gap` on `list`, are not counted. A row's height
  is its border box, and `RowHeights` says to pad rows rather than space them.

## Consequences

- New: `RowHeights`, `MeasuredRow`, `MeasuredWindow`, `ListView.rowHeights()`,
  `ListView.virtualized(RowHeights)`, `ScrollScope.enclosing(BuildContext)`,
  `ScrollScope.shift`, `ScrollScope.preservesOnPrepend`; `ScrollState.shiftBy`
  is package-private rather than private.
- `ListMeasuredTest` (17 cases): five thousand rows of five heights build a
  screenful, and a frame while scrolling builds at most a window. The extent is
  the measured rows plus the estimate for the rest. A still list rebuilds
  nothing. A wrong estimate (20 against rows of 40 to 120) does not move the
  reader's line as rows above it are measured. A row above the line that grows
  moves the viewport, and one below moves nothing. A typeahead lands its row at
  the top edge and builds a window. `End` ends the viewport on the last row.
  A timeline opens at its end, stays there when a message is appended, and
  keeps the reader's line when two hundred rows are prepended and when the rows
  above turn out another height. With the list's own correction turned off,
  five of them fail.
- The fixed-height path, `ListVirtualTest`, `ScrollTimelineTest` and the
  table's virtualization are unchanged and green.
