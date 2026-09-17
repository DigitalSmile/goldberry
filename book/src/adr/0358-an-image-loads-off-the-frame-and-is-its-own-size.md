# 358. An image loads off the frame, and is its own size

Date: 2026-09-17

## Status

Accepted. Builds `docs/core-widgets.md` §1's `image`, and answers
`book/src/TODO.md`'s "There is no `img` widget".

## Context

§1 specifies `image`: sources of "path, classpath, bytes, or async supplier
(placeholder fill until loaded)", fit modes `contain | cover | fill | none`,
DPI-aware raster selection, and "image with alt text (required attribute for
non-decorative use)". Until now a `canvas` was how an application drew a
picture, and the TODO entry named what a widget would need: `object-fit`, a
natural size that takes part in layout, loading and error states, and a decode
off the UI thread.

Three facts decide the shape:

- `Image.decode` is synchronous, and a large JPEG is tens of milliseconds. A
  build runs every frame.
- `Box` has no measure hook for an image and no `aspect-ratio`.
  `content.image.Picture` in `:html` already sizes a picture by arithmetic
  against its style.
- `Frame.drawImage` takes a crop in image pixels and a destination in logical
  units, so any fit can be drawn without a clip.

## Decision

**`ImageView` is stateful, and loads through an `ImageLoader` that answers a
future completing on the UI thread. Its styled part sizes itself from the
picture unless the stylesheet sizes it. Fit is a crop and a rectangle.**

### Sources and loading

- `ImageSource` is sealed: `File`, `Resource` (an anchor class, or a class
  loader for markup's `classpath:`), `Bytes` (copied, keyed by SHA-256),
  `Decoded` (an `Image` in hand) and `Supplied` (an application's own work
  under its own key).
- `ImageLoader.shared()` is one process-wide `ImageCache`. It holds **futures**,
  so two views of one file in one frame share one decode. It forgets a failure,
  so a file that appears later is read again. It is bounded at 256 MiB of
  pixels, least recently used first, counted in bytes because a count treats an
  icon and a photograph alike.
- A load runs on a virtual thread through `Goldberry.async` when there is a UI
  thread to come back to, and in the caller when there is not: a test, or an
  offscreen render, where a picture that arrived later would never be
  photographed. A `Decoded` source never leaves the caller.
- `ImageState` reads a future that is already done during the build, so a
  cached or decoded picture is drawn on the first frame with no placeholder.
  Each request carries a generation, and an answer to a question the view no
  longer asks is dropped. That covers a changed `src` and a window moved to a
  display of another scale.

### Variants

`Variant(scale, source)`; `srcset="logo.png 1x, logo@2x.png 2x"` in markup. The
view draws the smallest variant at least as dense as the window's scale, or the
densest there is. The natural size is the pixels over the variant's scale, so
`logo@2x.png` is drawn at the size of `logo.png`.

### Size

In `ImagePaint`, per axis:

- neither axis given: the natural size, capped in proportion by a `max-width`
  or `max-height` in points;
- one axis in points: the other follows the picture's shape;
- a percentage width with an auto height: the height follows the width the
  last frame laid out, through `Measured`, one frame late;
- both given: the stylesheet's box.

### Fit

`Fit.place` returns a crop in image pixels and a rectangle in the box. `cover`
and `none` crop the source to a centred region rather than drawing outside the
box, so neither needs a clip or `overflow: hidden`, and neither can paint over
a neighbour. Crops are whole pixels and at least one.

### States and semantics

- `image.loading` and `image.error` take `--gb-surface-2`, the skeleton's
  placeholder fill. An error shows Lucide's `image-off` and the alt text, as a
  browser does.
- Alt text is required unless `decorative=#true`. A meaningful image builds an
  `ImageFigure` (`Role.FIGURE`, named by its alt); a decorative one builds an
  `ImageBox`, which does not implement `Semantics` at all. The role set has no
  image of its own, and `canvas` answers `FIGURE` too.

## Consequences

- SVG is not decoded. §1 routes it to `goldberry-vector`, which does not exist,
  so an SVG shows the error state.
- An image with no size of its own has nothing to fill while it loads. A list of
  known shape should size its images.
- `Image.decode` stays synchronous and uncached for a `canvas` that calls it by
  hand. `ImageLoader.shared()` is public, and is the seam a painter can use.
- The Canvas screen gains an "image widget" card over the existing
  `canvas-sample.jpg`, and `gallery-canvas` is re-blessed. `image-dark` and
  `image-light` are new goldens.
