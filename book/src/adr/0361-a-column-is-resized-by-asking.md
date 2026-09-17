# 361. A column is resized by asking

Date: 2026-09-17

## Status

Accepted. Closes `book/src/TODO.md`'s "A `table` has no column resizing".

## Context

`docs/design-system.md` §3.1's table row says "column resize: 1:1, like
`split-pane`'s drag". The TODO entry proposed a `split-pane` between the headers,
but a split divides one box between two panes and a table has any number of
columns, some fixed and some weighted.

`Table` is a stateless leaf, and everything about its rows is the application's:
the order, the selection, and the widths through `Column.fixed` and
`Column.weight`. Sorting already works by asking: a header click reports what the
sort would become, and the application hands back rows in that order.

A 1:1 drag has to be measured from the width the column had when the drag
started. For a weighted column only the layout knows that.

## Decision

**A resizable column's header carries a grip, and a drag on it asks the
application for a width in pixels, anchored at the width the header last came out
as.**

- `Column.resizable(true)` and `Table.resized((key, width) -> …)`. The
  application answers by making the column `fixed(width)`; a weighted column that
  is dragged becomes a fixed one.
- A resizable header is built inside `TableHeaderCell`, a stateful composition
  node with no CSS type, which keeps the header's last laid-out width through
  `Measured`. The header answers `gestureAnchor()` with it.
- `table-grip` is a 6px part over the header's trailing edge, positioned
  `right: -12px` because an absolute box here is placed against the content edge
  and §3's cell padding is 12. It handles the drag: on each move it asks for
  `anchor + dragX`, with a floor of 32px so a column cannot be dragged shut. The
  router asks the pressed chain deepest-first for an anchor, so the grip reads the
  header's width. It consumes the press, release and click, so a drag never sorts.
- `table-header` loses `overflow: hidden`. That clips at the content box and hid
  the half of the grip over the padding; the label still clips itself.

## Consequences

- The table goldens are unchanged: a column that is not resizable builds no cell
  node and no grip.
- A resize is a rebuild per pointer move, the same cost as a `split-pane` drag.
- There is no keyboard resize. A header is focusable only when it sorts, and a
  second meaning for its arrow keys is a decision for when something asks.
- The showcase's Name column is resizable, with its width kept in the card's
  state; `gallery-collections` is re-blessed for the caption that says so.
