# ADR-0596: A box with overflow and a border-radius clips its children to the curve

- **Status:** Accepted
- **Date:** 2026-10-10
- **Relates to:** docs/gaps.md (G58)

## Context

`overflow: hidden` ended at `Frame.clipTo(x, y, width, height)`, a rectangle,
in `RenderTree.clipFor`. The same box's background, border and shadow were
already drawn round through `RoundRect.addTo` and the box's `Corners`.
So a picture in `border-radius: 17px; overflow: hidden` was drawn square inside
a round box. Tessera's faces are 34px circles of initials, and the one face
with a photo in it was the one face that was not round.

Blend2D's context clips to rectangles only (`BlendContext.clipTo`), and no
other clip is exported. This change was to make no native changes.

## Decision

**The children of such a box are drawn into a layer, the corners are cut off
the layer, and the layer is composited under the rectangle clip.**

- `RenderTree.paintChildren` is now the one place a node's children are drawn,
  from both `paint` and `paintIntoLayer`. For a box whose `overflow` is not
  `visible` and whose corners are not square, it asks `RoundedClip.of(box,
  layout)` for the shape. If there is one it goes to `paintRounded`; otherwise
  it takes the walk it always took. **A box with square corners costs one
  comparison and allocates nothing.** No layer, no record, no change of
  context state; `RoundedClipTest` asserts it composites no layer.
- `RoundedClip` is two shapes. The **outline** is CSS's padding box: inside the
  border, each corner's radius less the border it meets. The toolkit's corners
  are circles, so each shrinks by the wider of its two sides, where CSS would
  draw an ellipse. The **rectangle** is the toolkit's content box, where
  `clipFor` has always clipped (inside the padding). It is kept inside the
  border too, because the
  toolkit lays children out over the border and CSS's clip never lets them
  cover it.
- **Only a clip the curve can reach takes a layer.** When the content
  rectangle stays clear of every corner the curve cuts off, `RoundedClip.of`
  answers null and the rectangle is the whole clip. That happens when the
  padding is wider than the radius. A `text-input` (`TextField` forces
  `overflow: hidden`, and the field is padded 12px in from a 6px radius) and a
  padded card keep the walk they always had. An unpadded rounded `scroll`, a
  `progress` track and an avatar take the layer.
- `paintRounded` draws the children untransformed into a layer the size of
  that rectangle. That is `compositeThroughLayer`'s arrangement: the node's
  matrix goes on the blit. The layer is cut with **`Frame.cutCorners(x, y,
  width, height, Corners)`**, new and public, and blitted under the same
  rectangular clip the children would have had. The rectangle clip still
  confines everything, and the curve takes off what the rectangle lets through.
- `cutCorners` fills four wedges with the cubic `Path.roundRect` draws, so the
  cut follows the curve the background was filled with, antialiasing
  included. The fill is a **copy of transparent**, and Blend2D blends a copy by
  coverage. `DST_OUT` with opaque black was tried first, and on this build of
  Blend2D it painted the corners opaque black.
- The layer is kept on the `RenderObject` (`clipLayerFor`) and drawn again
  only when something under the node changed, its bounds changed, or the
  opacity baked into it changed. A still avatar is a blit. The opacity
  accumulated above the children is drawn into the layer rather than applied to
  the blit, so a rounded clip does not turn per-box alpha into group opacity.
- Nested rounded clips nest layers: the inner box's layer is composited into
  the outer box's, and the outer's corners are cut from the result.

## GPU layers

**A subtree that places a GPU layer keeps the rectangle clip** on a frame that
can show GPU layers. A layer's frame has no GPU surface, so drawn through the
corner-cutting raster a `canvas3d` would fill its unavailable colour and a
`video` would fall back to the CPU. `Frame` counts the GPU layers a painter asks
for (`gpuLayersAsked`), placed or not. When the children ask for one inside the
raster, and the window's frame has a GPU surface, the raster is thrown away and
the children are drawn straight onto the frame under the rectangle clip, as
before this record: the GPU layer is scissored to the clip's bounding box
(`GpuPlacement.scissor`), and UI painted over it rounds it. The render object
remembers the answer until something under it changes, so later frames do not
try the raster again. On a frame with no GPU surface (an offscreen frame
without one, or the GPU off) the painters' own drawing is what shows either
way, and it is cut to the curve. Clipping the GPU layer itself to the curve
needs the compositor to take a rounded scissor or a mask, which it does not.

This corrects the first version of this change, which sent every GPU layer
under a rounded clip to its no-GPU drawing. `LayerZOrderTest`'s rounded-clip
golden in the GPU lane caught it, and `GpuLayerPaintTest` now holds both cases.

## Hit testing

**Not changed.** CSS hit-tests a child by the clipped shape, so a press in the
cut-off corner of a round avatar button misses it. Here it still lands, as the
rectangle says. Following CSS needs the rounded shape on `HitTest.Region` and
`BoxPainter.Placed`, both public records, so that `Region.contains` can test
the corners as it tests the clip. That is a change to two public types for a
corner a few pixels across. It is left for when a control needs it, and no
test asserts either answer.

## Where reality differed from the entry

The entry expected the rounded outline to be "a few lines apart" from the
rectangle clip, as `RoundRect.addTo` is. It is a few lines in `BoxPainter`, and
a raster in `RenderTree`, because Blend2D has no clip that is not a rectangle.

## Consequences

- New: `Frame.cutCorners`, `RoundedClip` (package-private), and
  `RenderObject.clipLayerFor`. `RenderTree.edge` and `Path.KAPPA` became
  package-private so the two shapes share one arithmetic.
- `RoundedClipTest` (core): a child that fills a round box is cut to the
  circle, and the page shows in the corners. Square corners clip to the
  rectangle with no layer. A still round box is a blit, and a changed child is
  drawn and cut again. A ringed box cuts at the inner radius. Two clips nest
  with a layer each.
- `RoundedClipGoldenTest` (widgets): `rounded-clip.png` at 1x and
  `rounded-clip-2x.png` at 2x hold a 34px face (`image` in `border-radius:
  17px; overflow: hidden`), the same face with a 2px border, a square box
  clipping the same picture, and two nested rounded clips. Each also passes
  the 1.5x and 1.25x scale sweep.
- Goldens that moved, because a box in them is rounded, clips, and has
  children reaching its corners:
  - `progress` (`border-radius: 2px; overflow: hidden`) now rounds its fill's
    ends inside the track, which its stylesheet always asked for:
    `progress-determinate`, `progress-light`, `progress-reduced`,
    `progress-sweeping`, `progress-sweeping-end`, `controls-on-surface-dark`
    and `-light` (widgets); `progress-dark.webp` and `progress-light.webp`
    (book); and `gallery-values` and `gallery-application` (example), which
    show a progress bar.
  - The showcase's `scroll.tall-list` (`border-radius: 8px`, no padding):
    its rows are composited through the layer, which moves text antialiasing
    by a few levels and rounds a row's wash where it meets a corner.
    `gallery-collections` (example) and `screen-collections-dark.webp` and
    `screen-collections-light.webp` (book) moved.
  - `gallery-drawing` (example) gained the round face the image card now
    shows.
- A `text-input` (forced `overflow: hidden`, padded clear of its radius)
  takes no layer. Before that rule was added, every field picture in the
  book moved by a few levels of text antialiasing. After it, none did, and
  neither did any `textarea` picture.
