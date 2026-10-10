# ADR-0588: A painter draws a picture cut in nine, by the stylesheet's rules

- **Status:** Accepted
- **Date:** 2026-10-10
- **Relates to:** the Gwent clone's issue list (GB-032),
  [ADR-0585](0585-a-border-may-be-a-picture-cut-in-nine.md)

## Context

ADR-0585 gave a box `border-image`. `NineSlice` placed the nine pieces and
`NineSlicePainter` drew them, and both served the stylesheet only:
`NineSlicePainter` was package-private in `dev.goldberry.paint`, and
`NineSlice.pieces` took a `BorderImage` and a `Border`, not insets. A
`StyledPainter` on a `canvas` had `Frame.drawImage` and its source-rectangle
form, and nothing that kept a picture's corners while it stretched its
edges.

The downstream paints three plates on canvases because they follow its 3D
scene: a card's name plate, its ability box and a coin's caption. Its UI kit
has their sprites, each with its slice insets in a manifest. Nine
`drawImage` calls per plate would be the nine-slice written again in the
application.

## Decision

**A painter draws a picture cut in nine through `Frame.drawNineSlice`, and
the cut is a value, `dev.goldberry.paint.slice.NinePatch`.**

```java
private static final NinePatch PLATE = NinePatch.of(22, 64, 22, 64);
frame.drawNineSlice(namePlate, PLATE, 0, 0, 300, 40);
```

- `NinePatch(Insets slice, Insets widths, boolean fill, Repeat across,
  Repeat down, int density)`. `slice` is in the picture's pixels at
  `density` (points) or a percentage. Each of `widths` is a length in logical
  pixels, a percentage of the rectangle, or `auto` for the slice's own size.
  `Repeat` is `BorderImage.Repeat`: `stretch`, `repeat` or `round`.
- `NinePatch.of(top, right, bottom, left)` is the common case: widths
  `auto`, the middle drawn, everything stretched, density 1. `withWidths`,
  `withFill`, `withRepeat` and `atDensity` change one part.
- **One arithmetic.** `NinePatch.pieces` turns the cut into a `BorderImage`
  with no source and no outsets, on a box with no border, and asks
  `NineSlice.pieces`. So the CSS rules hold as they do for a box: when two
  opposite widths do not fit, every width shrinks by the same factor and a
  corner keeps its shape.
- **One painter.** `NineSlicePainter.draw(frame, image, pieces, x, y,
  alpha)` is the loop `paint` used to inline. The box's border image and
  `Frame.drawNineSlice` both call it, so every piece's edges sit on whole
  device pixels and a corner shares its seam with the edge beside it.
- `Frame.drawNineSlice` refuses a rectangle with no area and an alpha above
  1, as `drawImage` does, and draws nothing at alpha 0.

`NinePatch` gets a package of its own, `paint.slice`, for `paint.stroke`'s
reason: a value a `Frame` draws, with no native handle. `paint.slice` reads
`css.image`, and nothing there reads back.

## What differed from the entry

The entry suggested `drawNineSlice(Image, Insets slice, x, y, w, h, Insets
drawn)`. A value holds the cut instead, because a plate is drawn every frame
from one manifest entry, and the repeat, the fill and the density belong to
the cut rather than to the call.

Its example, a 995 × 113 plate cut `22 64 22 64` and drawn 40 high with
`auto` widths, shows the CSS rule: 22 + 22 is 44 of 40, so the sides shrink
to 40/44, the two rows of corners fill the height, and the side edges and
the middle have nothing left to draw. `NinePatchTest` pins both that case and
the same plate with narrower widths, which keeps a middle band.

## Consequences

- Tests: `NinePatchTest` (9, by numbers) and `DrawNineSliceTest` (4, by
  pixels at 100% and 200%). `NineSliceTest` and the border-image goldens are
  unchanged.
- The guide's painter section gains a paragraph, parked in
  `docs/snapshot/components-drawing-nine-slice.md` until the release.
