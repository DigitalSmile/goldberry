# ADR-0609: `z-index` orders siblings, and every box is its own stacking context

- **Status:** Accepted
- **Date:** 2026-10-11
- **Relates to:** `docs/css-plan.md` (CSS-28, CSS-29),
  `docs/css-support-2026-10-10.md` §4, ADR-0603

## Context

A box's paint order was its document order, with one exception: a box a
widget marked `elevated` was drawn after its siblings. That bit is what keeps
a pinned `affix` header above the rows scrolling under it. It was "one bit
meaning draw me last", and its documentation was explicit that it was not a
z-order and that there was no ordering among elevated siblings.

`z-index` was an unknown property, and the support analysis counted it among
the layout gaps a sheet written elsewhere meets first: a badge over an avatar,
a dropdown over the rows beside it.

CSS's full model has two parts: siblings ordered by `z-index`, and stacking
contexts, which let a deep descendant's `z-index` lift it above boxes outside
its parent unless an ancestor seals it in. The render tree paints a box and
then its children, and composites a subtree through a layer for `opacity` and
rounded clips; a descendant escaping its ancestors would need a paint walk
that interleaves subtrees, which nothing here does.

## Decision

**`z-index` orders a box among its siblings, and only among them: every box is
its own stacking context.**

- `z-index` is `auto`, read as 0, or a whole number, negative allowed. It is a
  component of `Presence`, beside `display`, `aspect-ratio` and the three
  CSS-28 added, and it does not inherit. Every box is a flex item here, and
  CSS gives a flex item's `z-index` effect without `position`.
- `Box` carries its whole `Presence` in place of the `aspectRatio` and
  `elevated` components it had. `RenderTree` draws, culls and visits children
  in one order: a stable sort by `z-index`, so equals keep document order. A
  list already in order, which is nearly every list, is used as it is with no
  allocation. The hit test reads the same walk, so the box drawn on top is the
  one the pointer finds.
- `Box.elevated(true)` stays, as `z-index: 1` for a box whose rule said less.
  A stylesheet's higher `z-index` is kept, so a rule can still put a popup over
  a pinned header. `Box.elevated()` answers whether the `z-index` is above
  `auto`.
- A retained node compares its whole presence when deciding whether its ink
  changed, so a change of `z-index`, `visibility` or `pointer-events` repaints.

## Consequences

- A `z-index` cannot lift a box above its parent's siblings. A dropdown that has
  to cover the rest of a window is an overlay, as it always was.
- Negative `z-index` draws a box before its siblings, not behind its parent's
  background: the parent is drawn before any child.
- `elevated` boxes among themselves are now ordered too, by document order, as
  equal `z-index` values are; nothing that relied on it changes.

## Status

Done 2026-10-11: `Presence.zIndex`, the `z-index` arm and longhand, `Box`'s
presence component, `RenderTree.paintOrder`, `RenderObject.sameAppearance`,
and tests (`ZIndexTest`, `ElevationTest`, `RecordWitherTest`, the
conformance table).
