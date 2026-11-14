# ADR-0687: A path knows what its fill contains

- **Status:** Accepted
- **Date:** 2026-11-09
- **Relates to:** ADR-0618 (`clip-path` draws a box as a group)

## Context

A whiteboard draws its items as `Path`s on a `canvas` and has to hit-test
them: a click inside a sticky note's rounded outline, not its bounding box,
and not inside the hole of a ring (Tessera's G-12, here T-12). `Path` could
draw a fill but not say what the fill covers. `ClipShapes.contains`, which
decides what a pointer hits on a box with a `clip-path`, answered a
`path()` by its reference box for the same reason.

## Decision

**`Path.contains(x, y)` and `Path.contains(x, y, evenOdd)`**, in Java, by the
rule a fill uses: `nonzero` unless told otherwise. `PathHitTest`
(`paint.geom`) flattens the path with the existing `Flattener` (a hundredth of
a unit), closes every sub-path as a fill does, and counts a ray's crossings
(evenodd) or their signed sum (nonzero). A vertex at the ray's height belongs
to the edge going up from it, the half-open rule the rasteriser uses, so a
point on a shared edge is counted once.

`ClipShapes.contains` uses it for `path()`, so the pointer finds a box clipped
to a path inside the path, holes left out.

## Consequences

- No native call: hit-testing happens per pointer move and is cheap enough in
  Java, and it works where Blend2D is not loaded.
- Pinch, the other half of T-12, needs a new SDL event layout and so a
  native layout probe; it is a separate item.
- Tested by `PathContainsTest` (rectangles, curves near the circle's edge,
  rings under both rules, open sub-paths) and
  `CompositingPaintTest.hitTestPath`.
