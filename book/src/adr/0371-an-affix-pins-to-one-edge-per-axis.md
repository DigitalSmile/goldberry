# 371. An affix pins to one edge per axis

Date: 2026-09-17

## Status

Accepted. Closes `book/src/TODO.md`'s "An `affix` pins on one axis".

## Context

An `affix` took one `edge=`. The TODO entry described the case for two: "a header
that is both sticky at the top and held against the left of a horizontally
scrolling table", which "needs two shifts and a rule about which wins".

There is nothing for either to win. A vertical edge's shift is a `translateY` and
a horizontal edge's is a `translateX`, and each is computed from the same three
rectangles on its own axis: the hole, the clip and the container (ADR-0360).

## Decision

**An affix has an edge and an optional cross edge on the other axis, and each
axis's shift is computed independently.**

- `Affix.alsoPinnedTo(Edge)` and `edge="top left"` in markup. A second edge on the
  same axis is refused in Java; in markup it is ignored, as an unknown edge word
  already is.
- `AffixState.shiftFor(edge, offset, self, clip, container)` is the per-edge
  arithmetic, lifted out unchanged. The state keeps a shift per axis, and
  `:affixed` is on when either is non-zero.
- `AffixSlot` and `AffixContent` carry `shiftX` and `shiftY` instead of an edge
  and a shift, and the content translates by both.

## Consequences

- One `offset` applies to both edges.
- The table's own sticky header (ADR-0360) is unchanged; a table in a
  horizontally scrolling viewport that wants its header held on the left too says
  `alsoPinnedTo(Edge.LEFT)` on an affix of its own.
