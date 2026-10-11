# ADR-0619: `filter` is applied to the group's raster, in Java

- **Status:** Accepted
- **Date:** 2026-10-11
- **Relates to:** `docs/css-plan.md` (CSS-40), `docs/css-support-2026-10-10.md`
  §7; ADR-0618 (a box drawn as a group)

## Context

CSS-40 asked whether a blur and colour-matrix pass on the GPU compositor was
worth building for `filter` and `backdrop-filter`, and what the CPU raster
path should do: the same thing more slowly, or refuse. The user decided on
2026-10-11: `filter` on both paths, with the CPU drawing the same picture more
slowly, and `backdrop-filter` stays refused.

Building it settled the question differently from how it was asked. A box's
group raster (`paint.Layer`) is always the rasterizer's, painted by Blend2D
and composited with a blit. The GPU here places video and `canvas3d` surfaces;
it does not composite box layers. So there is one path for a filter to run on,
and it is the same on every backend.

ADR-0618 already draws a box with a `clip-path` or a blend mode as a group.
`CoverageBlur` (from `text-shadow`) blurs a coverage mask with three box passes
that approximate a Gaussian.

## Decision

**`filter` makes a box a group. The functions run, in the order written, over
the group's raster in Java, before the clip is cut and before the raster is
composited with its opacity and blend mode, which is CSS's order.**

- `css.compositing.FilterFunction` is sealed: `Blur`, `Brightness`, `Contrast`,
  `Grayscale`, `HueRotate`, `Invert`, `Opacity`, `Saturate`, `Sepia` (the
  colour functions share `ColourFunction`) and `DropShadow`. `FilterParser`
  reads them. Amounts are numbers or percentages, defaulting to 1; `grayscale`,
  `invert`, `opacity` and `sepia` stop at 1; negatives and `url()` drop the
  declaration. A `drop-shadow()` with no colour takes the element's `color`.
  The list lives on `Compositing` beside the clip and the blend. It does not
  inherit, and `-webkit-filter` is an alias.
- `paint.filter.Filters` reads the raster as premultiplied ARGB. The colour
  functions are CSS Filter Effects' matrices on unpremultiplied sRGB values
  (`ColourMatrix`), the space browsers draw them in. `blur()` runs
  `CoverageBlur` on each premultiplied channel with σ the radius. A
  `drop-shadow()` blurs the alpha, colours it, offsets it and draws it under the
  picture. `opacity()` scales every channel.
- A blur and a drop shadow reach past what the group drew, by three times
  their radius and the shadow's offset. `FilterFunction.reach` sums it.
  `BoxInk` counts it for the box, `RenderObject` around its whole subtree's
  ink, and `RenderTree.bounds` around the layer, so culling keeps the box and
  the layer has room for the fade.
- `Layer.filter` applies the list after the subtree is painted; `Layer.amend`
  then cuts the clip-path into the same raster.
- `backdrop-filter` stays unknown (a `by-design` row): it would read back what
  was painted under the box, which splits the paint walk around every such
  box.

## Consequences

- A filtered box costs a layer, and, when its subtree changes, a pass over the
  layer's pixels in Java: four channel blurs for `blur()`, one for a
  `drop-shadow()`, one loop for each colour function. A still filtered box is
  a blit.
- The colour functions work in sRGB rather than linear light. That is what
  browsers draw for the shorthand functions, and differs from an SVG filter's
  default.

## Status

Done 2026-10-11: `FilterFunction`, `FilterParser`, `Compositing.filter` and
`reach`, the `filter` arm, longhand and alias, `paint.filter.Filters` and
`ColourMatrix`, `Layer.filter`/`amend`, `BoxInk`, `RenderObject`'s ink,
`RenderTree.bounds` and `compositeThroughLayer`; `FilterParserTest`,
`FilterPaintTest`, `PropertiesTest`, the conformance rows.
