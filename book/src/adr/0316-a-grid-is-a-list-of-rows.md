# 316. A grid is a list of rows

Date: 2026-09-14

## Status

Accepted. Reverses the layout half of
[ADR-0309](0309-a-sheet-of-icons-reflows-and-pays-for-it.md) and keeps everything
it decided about reflow. Builds on
[ADR-0213](0213-a-virtual-list-is-two-spacers-and-a-window.md).

## Context

ADR-0309 replaced the icon sheet's virtualized `list` with a `masonry`, because a
masonry is what reflows and the list did not — it chunked names into rows of a
**fixed** seven, so a wide window left a band of empty space and a narrow one
clipped the last column. It priced the trade honestly and published the numbers:

> placing a card under the shortest column is a decision about **every** card. So
> all 1544 tiles are in the tree

4709 elements, and a settled frame that styled in 3.5 ms where every other screen
in the gallery was under one.

Two records later the rest of the bill arrived.
[ADR-0313](0313-a-frame-pays-for-what-is-on-screen.md) found 18 ms of raster
going into 1504 tiles nobody could see, and
[ADR-0315](0315-a-rebuild-is-not-a-restyle.md) found 66 ms of cascade going into
the same tiles on every wheel notch. Both are fixed and both are toolkit-wide
fixes that this screen merely *found*. What was left was the part that is
genuinely about having 4709 elements: 5 ms of box-building, 2.5 of layout and 2.5
of hit-test snapshot, on every frame, whatever is on screen.

And ADR-0309 had already written down the sentence that undoes it:

> A masonry of **equal-height** tiles is a reflowing grid in reading order

A grid of equal-height rows is a **list** of equal-height rows. `list`
virtualizes. The masonry was never doing the reflowing — `IconsScreen.columnsFor`
was, and still is, from a width the screen measures itself
([ADR-0119](0119-a-widget-may-be-told-where-it-is.md)). The masonry was doing the
*chunking*, which is four lines.

## Decision

**The sheet is a virtualized `list` whose items are rows of `columns` names.**

```java
var grid = new ListView<>(rows(), IconRow::id, this::rowOf)
        .selection(Selection.NONE)
        .virtualized(ROW_PITCH)
        .id("icon-wall");
```

- `rows()` chunks `matching` into slices of `columns` — 221 records where the
  masonry was handed 1544 widgets.
- `rowOf` builds one `row` of tiles, **padded** to `columns` with empty cells: a
  tile grows over a 152pt basis so a full row divides the width evenly, and a
  part-full last row would divide the same width between fewer tiles and draw
  them half again as wide.
- `ROW_PITCH` is 76 — a 68pt tile plus `TILE_GAP` — and `showcase.css` pins
  `#icon-wall list-row` to the same number. `ListRow` complains in the log if the
  two ever disagree, which is the guard ADR-0213 built for exactly this.

Reading order is left to right down the page. It always was; it is now true by
construction rather than by an argument about which column is shortest.

### The `list`'s furniture is taken back off

`#icon-wall list-row` drops the padding, the pointer cursor and the hover wash.
This row is not a row of a list a reader picks from — nothing is selectable and
nothing is pressable — and the hover highlight belongs to the *tile* under the
pointer rather than to the whole row of seven. `Selection.NONE` is the model:
§10's own reading of it is *"for a list that is a view — a set of things being
browsed rather than picked from"*, which is this sheet exactly.

## Alternatives considered

**Hand-roll the virtualization in the screen.** Two spacers and a window is not
much code, and it is code `ListState` already has — with the overscan, the
`Located` window that settles in one frame, the pitch check and the focus that
can reach a row outside the window. `table` composes a `ListView` rather than
copying it (ADR-0214) and this is the same call.

**Keep the masonry and make it virtualize.** The masonry cannot, and ADR-0309 is
right about why: a card's column is a function of every card before it. That is
true of a masonry and false of a grid, which is the whole of this record.

**Leave it.** The sheet was down from 78 ms a notch to 15 after ADR-0313 and
ADR-0315, which is a screen that works. It is also a screen whose every frame
costs 4709 elements for the 80 a reader can see, and the fix was four lines and a
stylesheet rule.

## Consequences

**A settled frame**, 1280×900, one Blend2D thread:

| | ADR-0309 | now |
|---|---|---|
| elements | 4709 | **711** |
| opens in | 414 ms | **106 ms** |
| style | 3.5 ms | **0.72 ms** |
| layout | 0.70 ms | **0.20 ms** |
| raster | 18.0 ms | **4.2 ms** |

**And a wheel notch**, which is the frame a reader actually feels — the whole
arc, across all three records:

| wheel frame | before ADR-0313 | now |
|---|---|---|
| flush | 2.0 ms | 0.4 ms |
| style | 66.6 ms | 2.2 ms |
| layout | 2.7 ms | 8.4 ms |
| paint | 4.8 ms | 5.2 ms |
| hit-test | 2.6 ms | 0.5 ms |
| **total** | **78.6 ms** | **16.8 ms** |

**Layout went up, and that is the honest cost of virtualizing.** A window that
moves is a tree that changes: the sheet used to hold still and Yoga skipped it
entirely, and now one row leaves at the top and one arrives at the bottom on
every notch. It is 8.4 ms rather than the ~1 ms the change ought to cost, and
**the reason is known**: `RenderObject.reconcileChildren` matches children by
position, so a window that shifts by one row mismatches every row after it and
closes and rebuilds the lot — a fresh `YGNode` and a fresh measure callback per
text node, at the 11 µs apiece ADR-0037 measured.

Matching by `Box.owner()` instead — the element, which the element tree has
already reconciled by key — fixes it and was measured at **9.9 ms → 3.7 ms**. It
is not in this record because it moved a card's bottom border by one pixel on the
Panels screen, deterministically, and a one-pixel layout change in the render tree
that nobody can account for is not a thing to ship on a performance argument. The
measurement and the artefact are written down here so the next person starts from
where this stopped rather than from the beginning.

**The search field is a convenience again.** ADR-0309 made it the performance
story — *"two letters take about three quarters of the cost back"* — and that was
true of a wall that built every tile. A virtualized list builds its window, so a
filtered sheet and a whole one are the same tree and the same frame.
`FrameBudgetTest` asserts the new property in place of the old one: filtering
changes the model by a factor of ten and changes the cost by nothing.

**The sheet has no budget of its own any more.** It had 8 ms of style and 4 of
layout where every other screen had 1, because it did not meet the wall's and
pretending otherwise would have been a test that fails or a test that checks
nothing. It is 2 ms and 2 ms now — three times the measurement, which is this
file's own doctrine — and the raster is on the wall's own number.

**What the picture cost.** The two icon goldens moved by 4 points: a masonry
divides its width into *n* columns and writes each one's width inline, where a
flex row of *n* growing tiles divides the same width by flexing. The row fills to
the content edge and the masonry stopped 5 points short of it. Both are correct
arrangements of the same tiles; the new one is the one with no slack in it.

**And the element count is asserted, not only the time.** `IconsScreenTest` checks
that fewer than a tenth of the 1544 tiles exist and that the sheet is nonetheless
as tall as all of them — the spacers adding up to the model is what keeps the
thumb still while a reader scrolls, and it is the property that fails first if
this screen ever stops virtualizing.
