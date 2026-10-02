# ADR-0537: A shadow is a list, and may be cast inside

- **Status:** Accepted. Supersedes the "one shadow, not a list" and "`inset`
  refused" parts of [ADR-0310](0310-a-shadow-is-a-stack-of-rectangles.md).
- **Date:** 2026-10-02
- **Relates to:** [ADR-0427](0427-the-shadow-is-cut-out-of-its-box.md),
  [ADR-0536](0536-a-border-is-dashed-dotted-or-double-as-written.md),
  `docs/gaps.md` G58, `docs/goldberry-gaps.md` #14

## Context

ADR-0310 took one `box-shadow` and refused `inset`. A list was read as its
first entry because no rule here wrote one, and an inner shadow was "a
different drawing that needs the clip this one was careful not to need".
Deploy Orc wrote both. Its current-release accent was `inset 3px 0 0` and
became a `border-left`. Its `--shadow` token was two layers and was cut to
one.

ADR-0427 then added the piece ADR-0310 lacked, an even-odd fill. It noted
that `inset` is the same band stack with the hole and the shape swapped. One
part was still missing. Blend2D clips to rectangles only, so a hole that an
offset pushes past a rounded box's curve cannot be clipped back at fill time.
Under even-odd the part that pokes out would be filled instead of cut.
G58 (a rounded clip) is the general answer to that.

## Decision

**`Decoration` holds a `List<Shadow>`, and `Shadow` has an `inset` flag.
Inner shadows are drawn with the even-odd band stack, and the hole is
clipped to the padding box geometrically. G58 stays open.**

- `Shadow.parse` returns the whole list, first on top, or empty for `none`.
  `inset` may come anywhere in an entry, once. One bad entry drops the
  declaration. `Decoration.shadows()` is the list, and `shadow()` is the
  first or `Shadow.NONE`. A constructor and a wither still take one
  `Shadow`.
- **Paint order** is CSS's. Outer shadows are drawn before the background,
  last first. Inner shadows are drawn after the background and before the
  border, last first.
- **An inner shadow's band** is the padding box (the border box less each
  side's border width, with its radii pulled in as the border painter pulls
  them) plus a hole. The hole is the padding box moved by the offset and inset
  by the band's `grow`, which is the same number `ShadowRamp` gives an outer
  band, read inwards. Both are flattened to polygons, and the hole is
  intersected with the padding box (`paint.geom.ConvexClip`, Sutherland–Hodgman
  over two convex shapes). The pair is filled even-odd. Using the same
  polygon for the box and for the clip edge is what makes the shared boundary
  cancel exactly. A band whose hole covers the padding box paints nothing,
  and `ShadowGeometry.coveredAt` already answers that.
- An inner shadow's outsets are zero, so the damage rectangle does not grow
  for one. `BoxInk` takes the largest outset of the outer shadows on each
  side.
- `transition: box-shadow` moves two lists pair by pair. The shorter list is
  padded with transparent copies of the longer one's shadows, so an arriving
  shadow fades in at full size. A pair of an inner and an outer shadow makes
  the whole list swap half-way, CSS's rule for a list that cannot
  interpolate.

## Consequences

- `inset 3px 0 0 var(--gb-accent)` is a stripe inside the box, under its
  border, and a two-layer elevation token draws both layers.
- An inner shadow on a rounded box follows the inside of the curve. The
  flattening is to a twentieth of a logical pixel.
- A box with no shadows pays two empty loops.

## Alternatives considered

- **Implement G58 and clip to a rounded rectangle.** A rounded clip in
  Blend2D means rendering into a mask layer and compositing it, per shadowed
  box per frame. It would close G58 for `overflow: hidden` as well, and it is
  the right answer there, but it is a different change with its own costs.
  Inner shadows need only the intersection of two convex shapes, which costs
  a few dozen points.
- **A reversed sub-path under non-zero.** Rejected for ADR-0427's reason. It
  fills the parts of the hole outside the shape.
