# 372. A tab is dragged, and the strip asks where

Date: 2026-09-17

## Status

Accepted. Closes `book/src/TODO.md`'s "Nothing reorders tabs", as an option.

## Context

The TODO entry: "a reorder would need a different animation from an arrival: a
tab that moves has two positions and nothing to interpolate between them, which
is ADR-0097's missing geometry again. §5 does not ask for drag-to-reorder; the
model shape would take it without a change, since the strip draws the list it is
given."

Two things make a drag possible without that geometry. The dragged tab is the
only one that needs to move smoothly, and it follows the pointer 1:1, which §1.7
asks of every drag and which needs no interpolation. And tabs already report
their painted rectangles through `Located` (ADR-0120), so the strip can tell
where among the other tabs a drop landed.

## Decision

**`Tabs.onReorder((value, index) -> …)` makes a strip's tabs draggable. A drag
draws the tab at the pointer, and a drop asks the application to move the tab to
an index among the others.**

- `TabDrag` is a composition node with no CSS type around each header of a
  reorderable strip. It hears the pointer after the `Tab`, which consumes only the
  click. Past 4 points of travel along the row a press is a drag: each move
  reports the travel and the pointer, and the release reports the drop.
- `TabsState` keeps every header's painted rectangle while the strip is
  reorderable, in strip order, dropping rectangles for tabs that have gone. The
  drop index is the number of other tabs whose centre is before the pointer.
  Nothing is asked when that is where the tab already was.
- While dragged, the `Tab` carries a `dragOffset` that its `restyle` turns into a
  `translateX`, so the tab is drawn where the pointer took it and its slot in the
  row stays put.
- The strip does not reorder anything. On the drop the tab returns to its slot,
  and the application's new list puts it in its new one on the next build.
- A press and release that did not travel still select.

## Consequences

- The other tabs jump to their new places when the application's list changes.
  Animating them is still ADR-0097's missing geometry.
- There is no markup: two values do not fit a valued action. The showcase's
  chapter strip, which is Java for the list's sake already, is reorderable.
- `gallery-navigation` is re-blessed for the caption that says so.
