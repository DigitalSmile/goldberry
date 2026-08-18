# ADR-0113: A submenu is placed beside its menu, and a tick column is a menu's

- **Status:** Accepted
- **Date:** 2026-08-18
- **Relates to:** `docs/core-widgets.md` §8, corrects
  [ADR-0106](0106-a-menu-is-a-widget-and-opening-one-is-not.md)'s two remaining
  geometry mistakes

## Context

Two things about a menu's geometry, reported together, and both are the same kind
of mistake: **a decision made per row that belongs to the menu.**

1. A submenu opened *on top of* the right-hand border of the menu it came from.
2. Every row in every menu was indented by a tick column, whether or not anything
   in that menu could be ticked — and, once that was fixed, a row with an icon was
   still indented further than the rows above it, because the icon was drawn after
   the column rather than in it.

## Decision

### A submenu is placed beside the menu, level with the row

[ADR-0106](0106-a-menu-is-a-widget-and-opening-one-is-not.md) anchored a submenu
to its **item**, which is right for one axis and wrong for the other. An item's
right edge is a few pixels inside the menu's — the panel's padding, and its
border — so `Placement.AFTER` put the submenu's left edge inside the parent's
frame, overlapping the border it should have cleared.

The anchor is two rectangles now: **x and width from the popup**, y and height
from the row. `Popup.bounds()` is the popup's own rectangle in the owner window's
coordinates, beside `Popup.anchor(id)` which is a node inside it.

The gap is 2px: far enough that the two panels do not share an edge — which reads
as one panel with a seam — and near enough that a pointer crossing it does not
leave both menus and put the submenu away.

### One leading column, holding a tick *or* an icon

The indent was reported twice, and the second time was the interesting one.

The first fault: the column was built for every row, always, on the argument that
a column appearing with the first tick would shift every label sideways. That
argument is right *within a menu* and was being applied to every menu in the
toolkit — so a File menu with nothing checkable in it indented every label for a
tick none of its rows could have.

The second, still visible after that was fixed: a row's icon was drawn **after**
the tick column rather than in it, so a row with an icon sat further in than the
rows above it. The showcase's own menu has both an icon row and a checkable row,
which is why it still read as an unexplained gutter with a ragged left edge.

So there is one leading part, `item-lead`, with one width and three possible
contents — a tick, an icon, or nothing. A menu reserves it when **anything in it**
has something to put there, and then every row has one. That is what every desktop
menu does, and what keeps the labels in a line.

### A tick column is a menu's decision, so `checked` has three states

The column was built for every row, always, on the argument that a column
appearing with the first tick would shift every label sideways. That argument is
right *within a menu* and was applied to every menu in the toolkit — so a File
menu with nothing checkable in it indented every label by fourteen pixels and
eight of gap, for a tick none of its rows could ever have.

The scope was wrong, not the rule. A menu reserves a column when **anything in it
is checkable**, and then every row in that menu has one.

Which needs a third state, because "unchecked" and "not a checkbox" had been the
same value. `Item.checked` is a `Boolean`: `TRUE` and `FALSE` are a checkable row
that is on and off, `null` is a row that is not checkable at all. In markup,
`checked=#true` and `checked=#false` are the first two and no attribute is the
third.

### A row that leads somewhere says so

While looking at the images: a submenu row was drawn exactly like a command. The
chevron is a painter mark — `CHEVRON_END`, beside `CROSS` and `PLUS` — rather than
Lucide's `chevron-right`, for the reason those two are marks and one more: an icon
owns native memory that must be closed exactly once
([ADR-0043](0043-icons-are-stroked-paths.md)), and a menu is built and
thrown away every time it opens.

## Alternatives considered

- **Anchoring the submenu to the item and adding a bigger gap.** It works until
  the panel's padding changes, at which point the gap is wrong and nobody knows
  why — the number would be compensating for a different number in a stylesheet.
- **Overlapping the parent deliberately**, which Windows does by a few pixels. It
  needs the parent's border drawn *over* the child to look intentional, which two
  platform windows cannot do.
- **A `checkable` flag beside `checked`.** Two booleans with three legal
  combinations, and the fourth silently meaningless.
- **An icon column beside the tick column.** What the code did by accident, and it
  is what a row with both would need — except that no menu anywhere shows a tick
  and an icon on one row, because the tick *is* the row's state and the icon *is*
  its identity, and a row has one leading thing.
- **Reserving the column only on rows that are checkable.** The labels step in and
  out down the list, which is the thing the always-on column was avoiding.

## Consequences

- **`Item.checked()` returns `Boolean`.** An author reading it has to know that
  null means "not a checkbox", which is the price of the three states being
  distinguishable at all.
- **`Popup.bounds()` is public**, and is what any widget anchoring to a popup
  rather than to something in one will want — a submenu today, a `tour` stop
  later.
- **The menu has golden images**, five of them, where it had none: a menu is drawn
  in a window of its own and appears in no other picture in the corpus. Both faults
  here were visible the moment there was one.
- **A submenu still has no keyboard `Left` to close it**, and a chevron does not
  yet rotate or highlight when its submenu is open — the row is `:focus-visible`
  when the keyboard is on it and nothing marks "this one is showing".
