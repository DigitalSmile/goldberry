# 360. An affix stays inside its container

Date: 2026-09-17

## Status

Accepted. Amends ADR-0119. Closes `book/src/TODO.md`'s "A pinned `affix` is not
pushed out by the next one" and the sticky-header half of "A `table` has no
column resizing and no sticky header".

## Context

An `affix` pinned itself to its viewport's edge once it would have scrolled
past, and stayed pinned for as long as its hole was above the edge. Two
sections with a header each overlapped: the first header stayed pinned while
the second arrived under it. The TODO entry said doing better "needs an affix
to know about its sibling, which is a relationship nothing in the widget tree
expresses".

CSS answered this without siblings. A `position: sticky` element is confined to
its containing block, so a section's header leaves with its section and the
next header takes over by the same rule. What an affix lacked was the rectangle
of the box it is in.

The table's header was blocked on this. It sits above the table's `list`, so a
table inside a page that scrolls lost its column names, and an affix there would
have pinned them over whatever came after the table.

## Decision

**`Located` also reports the painted rectangle of the nearest ancestor with a
box, and an affix never travels past that box's far side.**

- `Located.located(self, clip, container)` is a default method that calls the
  two-argument form. `PointerRouter.notifyLocated` finds the container by
  walking up from the element to the first ancestor that has a hit-test region,
  because a composition node has no box. With none it reports the window. The
  router only notifies again when the container moves.
- `AffixState` limits its shift to the room between the hole's far side and the
  container's: for `top`, the container's bottom minus the hole's bottom; for
  `bottom`, the hole's top minus the container's top; and likewise for the
  horizontal edges. `:affixed` still comes on as soon as the affix lifts.
- An affix directly in the scrolled column has the whole document as its
  container, so it pins exactly as before.
- `Table` wraps its head and rule in an `affix`. At rest the header is where it
  was; on a scrolling page it pins while the table is in view and leaves with
  the table.

## Consequences

- A sticky header per section is two columns, each holding an affix and its
  rows, and needs no code.
- The table's box tree gains `affix` and `affix-content` above `table-head`. The
  table goldens are unchanged.
- `TableHead`'s hit testing goes through the affix's translate, which the router
  already inverts (ADR-0068).
