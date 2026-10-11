# ADR-0618: `clip-path` and `mix-blend-mode` draw a box as a group

- **Status:** Accepted
- **Date:** 2026-10-11
- **Relates to:** `docs/css-plan.md` (CSS-46), `docs/css-support-2026-10-10.md`
  §7; ADR-0596 (the kept raster a rounded clip is cut from); ADR-0609 (every
  box its own stacking context)

## Context

The rasterizer clips to rectangles only. ADR-0596 rounded a box's clip by
drawing its children into a kept raster and cutting the corners out of it.
Opacity already draws a subtree as a group: `RenderTree.compositeThroughLayer`
paints it into a layer at full strength, keeps the layer while nothing under
it changes, and composites it once. Blend2D has every separable blend mode as
a compositing operator.

CSS-46 asked whether to generalise the kept raster to paths and images. The
user decided on 2026-10-11: `clip-path` with basic shapes and reference boxes,
`mix-blend-mode` and `isolation`; masks (`mask-image` and its longhands) stay
out.

## Decision

**A box with a `clip-path`, a `mix-blend-mode` other than `normal`, or
`isolation: isolate` is drawn as a group: its subtree goes into a layer, the
clip is cut out of the layer, and the layer is composited with the blend
mode.**

- `dev.goldberry.css.compositing` holds the values: `ClipPath` (sealed:
  `None`, `Shaped(shape, box)`, `Boxed(box)`, with `Inset`, `Circle`,
  `Ellipse`, `Polygon` and `PathData` shapes), `ReferenceBox`, `BlendMode` and
  `Compositing`, with `ClipPathParser`. They ride on `layout.Presence` as one
  `Compositing` component, so `ComputedStyle` and `Box` carry them without a
  component of their own. None of the three inherits. The package is
  exported, since `Presence` is.
- Lengths and percentages resolve at paint against the reference box. A
  circle's percentage radius is of the diagonal over √2, `closest-side` and
  `farthest-side` measure from the centre, and `path()` is in the reference
  box's coordinates, unscaled. The padding box is the border box less the
  border's width, and the content box the border box less the padding, since a
  border here is painted over the padding. `margin-box` and the SVG boxes read
  as the border box. A reference box alone clips to that box with the box's
  own rounded corners, brought in.
- `RenderObject.isPromoted()` is true for such a box, leaf or not.
  `compositeThroughLayer` paints the subtree into the kept layer, and then
  `Frame.keepInside(path, evenOdd)` fills the outline into a mask the layer's
  size with its own fill rule and composites the layer by it (`DST_IN`). Edges
  are antialiased, and a self-crossing `nonzero` path keeps what its rule says
  is inside. The layer is composited through `Frame.drawLayer(…, BlendMode)`,
  which maps each mode to its Blend2D operator. `plus-lighter` is `PLUS`.
- `hue`, `saturation`, `color` and `luminosity` are dropped with a reason:
  they mix colour channels across each other, which no operator here does.
  `url(#…)` clips are dropped. `background-blend-mode` and `mask*` are not
  read.
- `isolation: isolate` makes the group with nothing else to make it one, so a
  child's blend mode combines with the group and not with the page under it.
  Since every box is its own stacking context here, an un-isolated child
  blends with whatever its nearest group, or the frame, holds.
- The hit test honours the clip on the box itself: `HitTest.Region` gains a
  `Shape`, which `ClipShapes.contains` answers for insets (by their rectangle),
  circles, ellipses, polygons by their fill rule, and `path()` by its
  reference box.

## Consequences

- A clipped or blended box costs a layer and, when its subtree changes, a
  mask the layer's size. A still one is a blit, as an opaque group is.
- A descendant of a clipped box is still found by the pointer outside its
  ancestor's shape. Only the box that writes the `clip-path` tests it.
- A GPU layer inside a group is drawn by its fallback painter, as it is inside
  an opacity group.

## Status

Done 2026-10-11: `css.compositing` (`ClipPath`, `ClipPathParser`,
`ReferenceBox`, `BlendMode`, `Compositing`), `Presence.compositing`, the three
arms and longhands, `Corners.parse`, `paint.clip.ClipShapes`,
`Frame.keepInside` and the blended `drawLayer`, `RenderObject.isPromoted`,
`RenderTree.compositeThroughLayer`, `HitTest.Region.shape`; `ClipPathParserTest`,
`CompositingPaintTest`, `PropertiesTest`, the conformance rows.
