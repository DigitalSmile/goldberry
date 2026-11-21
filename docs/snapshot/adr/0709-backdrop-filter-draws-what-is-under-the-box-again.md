# ADR-0709: `backdrop-filter` draws what is under the box again

- **Status:** Accepted, as W3.2 of `docs/gaps/goldberry-gaps-plan.md` (Tessera
  T-15), with D13 (a): CPU frames only, and the opaque fallback over GPU
  layers.
- **Date:** 2026-11-21
- **Relates to:** ADR-0618 (a box drawn as a group), ADR-0619 (`filter` on
  the group's raster, which kept `backdrop-filter` refused), ADR-0310 (a
  shadow is a stack of rectangles), `docs/design-system.md` §1.5 (the frost
  material and its fallback ladder)

## Context

Tessera wants frosted glass for its call control bar and its board toolbar.
ADR-0619 left `backdrop-filter` unknown because it would read back what was
painted under the box, and that splits the paint walk around every such box.
The owner chose D13 (a) on 2026-11-08: blur what is under the box on CPU
frames, and draw the opaque fallback over a GPU layer.

Reading the frame back turned out to be the wrong way to get what is under
the box, for two reasons:

- **Blend2D cannot be read mid-frame.** A window's frame is rasterized by
  worker threads, and the drawing calls only queue. `bl_context_flush` is not
  exported (`goldberry.symbols` dropped it with no caller), so reading the
  buffer would need a native ABI bump, or ending the context and beginning a
  new one with every saved clip replayed.
- **A partial repaint holds last frame's frost.** Outside the damage, the
  frame holds the previous picture, and under the box that picture is
  already frosted. Blurring it again frosts it twice, more each frame.

## Decision

**What is under the box is drawn again, not read back.** Each painting in
`RenderTree`'s walk carries a `Replay`: how to draw its raster again, shifted.
The raster is the window's frame (replayed from the root), a group's layer
(from the group), or a rounded clip's layer (from its children). Before a box
with a `backdrop-filter` draws itself, the walk replays its raster into a
scratch `Layer` of the box's region. The walk stops at the box, so the region
holds everything painted before the box and nothing after it. The scratch
layer's frame has no GPU surface, so a GPU painter draws its fallback there.

- **The region** (`BackdropRegion`) is the box's border box through its
  matrix, out by the filters' reach (`Compositing.backdropReach`, three radii
  for a blur), on whole logical pixels. It is cut to the raster, and to what
  can be seen out by the same reach. Where the raster's edge cut it, the edge
  pixels are repeated instead (`paint.filter.EdgeExtension`,
  `Layer.filter(…, left, top, right, bottom)`), as CSS reads the backdrop with
  its edges duplicated. Otherwise a frosted bar at the window's edge would
  thin out there.
- **Rasters already drawn are blitted.** A group or a rounded clip that the
  frame drew before the box has a raster that is still good
  (`RenderObject.layerIfCurrent`, `clipLayerIfCurrent`). It is blitted at the
  same shift. Otherwise a scratch raster is drawn for it and closed with the
  backdrop, so the node's own cache is never touched. A rounded clip that
  holds the box draws its children under the rectangle, because no raster of
  all its children can stop halfway.
- **Then**, in this order: the filters run over the scratch raster
  (`Filters.apply` with the edges); the raster is cut to the box's border box
  with its radii (`Frame.keepInside`), through the box's matrix; it is
  composited at the box's opacity. Then the box draws its shadow,
  background, border and children over it, as usual.
- **Over a GPU layer it is the opaque fallback.** If something in the
  region asked for a GPU layer (`gpuLayersAsked`) and the frame can show
  them, the frost would be of the painter's fallback, not of the video. So
  the box is filled with its own background colour at full alpha (faded by
  its opacity) inside its rounded border. Where the frame shows no GPU
  layers, the fallback is what is on screen, and it is what gets frosted.
- **It is not a group.** `Compositing` gains `backdropFilter` (a fifth
  component), `hasBackdrop()` and `backdropReach()`. `needsLayer()` ignores it,
  so a frosted box with no other reason to be a group is drawn on the
  ordinary path. A promoted frosted box draws its backdrop on the raster the
  group is composited onto, just before the blit.
- **Damage is widened.** `RenderTree.damage` collects each frosted box's own
  rectangle and the rectangle its filters read. A damage rectangle that meets
  what a box reads adds the box's whole rectangle, repeated until nothing more
  is added. A change just outside the box reaches in through the blur, and
  inside the box last frame's pixels are already frosted, so the box is never
  repainted in part.
- **The CSS.** `backdrop-filter` takes `filter`'s functions through the same
  `FilterParser`. It does not inherit, and `-webkit-backdrop-filter` is an
  alias. `url()` drops the declaration.

`Frame.drawLayer` now holds the layer's pixels until `end` on a threaded
context, as a read-back GPU layer already was. A raster made for one frame
(the backdrop, and the scratch rasters under it) could otherwise be collected
while the workers still read it. `keepInside` holds its mask for the same
reason, which was a latent hazard for `clip-path` too.

## Consequences

- A frosted box costs, whenever it is in the damage, a replay of its region:
  culled to the region, with the groups and rounded clips under it blitted,
  plus a filter pass over the region's pixels in Java. A still window costs
  nothing, because nothing is in the damage. A box over something animating
  costs a replay every frame.
- **A frosted box under another frosted box is drawn without its own frost**
  in the upper box's backdrop. A replay that frosted again would recurse.
- A rounded clip holding the box clips the backdrop's siblings to its
  rectangle, not its curve. The difference is a corner's few pixels, and it
  is blurred.
- The backdrop is the raster the box is drawn onto. Inside a group or a
  rounded clip, that is what the group or clip drew. CSS's "backdrop root"
  gives the same answer for a group. For a rounded clip it leaves out the
  clip's own background, which then shows sharp through any part the
  filters left transparent.
- Over a translucent raster (a group with a transparent background) the
  filtered picture is drawn over the sharp one rather than replacing it.
  Over an opaque window, which is the case Tessera has, the two are the
  same.
- Not on the GPU compositor: frost over a call's video tiles stays opaque, as
  D13 (a) chose.
- The conformance row moved from UNKNOWN to APPLIED, with an alias row and a
  `url()` row. `ComputedStyleTest` and `FlexAndGapShorthandTest` name
  `mask-image` as the unknown property now.
- `Compositing`'s record pattern has five components. The four-argument
  constructor stays.

## Status

Done 2026-11-21: `Compositing.backdropFilter`/`hasBackdrop`/`backdropReach`,
the `backdrop-filter` arm, longhand and alias; `paint.filter.EdgeExtension`,
`Filters.apply(…, EdgeExtension)`, `Layer.filter` with edges;
`paint.tree.BackdropRegion`; `RenderTree`'s `Replay`, `BackdropWalk`,
`paintBackdrop` and the damage widening; `RenderObject.layerIfCurrent` and
`clipLayerIfCurrent`; `Frame` holding blitted layers and masks until `end`.
Tests: `BackdropFilterPaintTest` (over stripes, its own background, opacity,
radii, a rotation, the colour functions, 2x, threaded against synchronous,
groups and rounded clips under and around it, the widened damage against a
whole repaint, damage cut across the box, the GPU fallback both ways),
`BackdropRegionTest`, `FiltersEdgeTest`, `BackdropFilterStyleTest`, the
conformance rows. The showcase's "Clip, blend and filter" card has a row of
frosted plates.
