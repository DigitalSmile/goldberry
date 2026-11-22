# ADR-0743: An SVG image is drawn at the device pixels it covers, wherever it is drawn

- **Status:** Accepted
- **Date:** 2026-11-22
- **Relates to:** ADR-0740 (D1 and D2, which this carries out), ADR-0742 (`Svg`)

## Context

Phase 3 of `docs/vector-svg.md` makes SVG work everywhere an image is: `image`,
CSS `url()` (backgrounds, border images), and the pictures of the HTML and
Markdown views. D2 put a decoder service in `:core`; D1 said each place draws a
document at the device pixels it covers, with rasters cached by pixel size and a
resize drawing the last raster until the new one arrives.

The plan read D1 as work in each loader: `ImageCache` keyed by pixel size, the
`image` widget asking at its box's device size, the CSS painter and the content
views each asking a provider for a `ScalableImage`. Reading the code, every one
of those places ends in the same call, `Frame.drawImage` with a source
rectangle, and every one of them passes `Image`s around: the widget cache, the
stylesheet registry, `content.ImageSource`, a canvas painter.

## Decision

1. **`:core` gains `dev.goldberry.image.ScalableImage`** (a natural size and
   `render(PhysicalSize)`) **and `dev.goldberry.image.spi.VectorDecoder`**,
   exported, with `uses` in `module-info`. `goldberry-vector` provides it
   (`SvgDecoder`, in `module-info` and `META-INF/services`), and `Svg`
   implements `ScalableImage`.
2. **`ImageFormat.SVG`** is an `<svg` root after an optional byte order mark,
   white space, XML declaration, processing instructions, comments and doctype
   (its internal subset included), read for at most 64 KiB. An XML document with
   another root, or an HTML page with an `<svg>` inside, is not SVG.
3. **An SVG decodes to an `Image` that carries its document.** `Image.decode`
   asks the provider and answers a raster at the natural size, rounded up, with
   `scalable()` holding the document; `Image.of(ScalableImage)` does the same
   for an application's own. `decodeInto` and `decodeAnimation` follow. Without
   a provider the bytes are an `ImageDecodeException` naming
   `dev.goldberry:goldberry-vector`; with one that cannot draw on this machine,
   its reason. `scaled` and `cropped` answer pixels only.
4. **`Frame.drawImage` draws such an image from a raster of the device pixels
   it covers**, the frame's scale times the transform's scale on each axis. The
   whole image is drawn from a raster of exactly that size, which the document
   places itself in (`preserveAspectRatio`, ADR-0742). A part of it, a crop or a
   nine-slice piece, is drawn from a raster of the whole at the part's scale,
   the source rectangle scaled outward to whole pixels. Every place an image is
   drawn is sharp without knowing it drew SVG, a canvas painter's included, and
   the caches upstream keep one decode per source as before.
5. **`ScalableRasters`** (`dev.goldberry.image.scalable`, not exported) keeps the
   rasters by document and pixel size, 64 MiB, least recently drawn first. A
   raster is drawn at once outside a running toolkit, or when the document's
   last raster says it takes under 2 ms (256² pixels for a document not drawn
   yet). Otherwise it is drawn on a virtual thread, and until it arrives the
   frame draws the document's most recent raster, or its natural one, scaled.
   A size the document refuses is remembered as the natural raster.
6. **One arrival signal for every late picture.** `PictureArrivals` holds the
   generation `StyleImages` had; `StyleImages.generation()` and `onArrival`
   read it. A raster arriving moves it, and the window paints again. The render
   tree counts a box as changed on an arrival when it names a stylesheet
   picture, as before, or when it has a painter, since its painter may draw a
   raster that has just arrived.

## Consequences

- An `image`, a CSS background or border image, and a picture an HTML or
  Markdown view draws are sharp at any size and scale with the module on the
  path, and refused with the artifact's name without it.
- The raster a frame makes is held until the frame ends, since a threaded
  context reads it after the blit returns.
- An arrival repaints every painted box once. Arrivals are a load finishing or
  a large raster arriving, not every frame, so this is the cheap side of being
  exact about which box drew what.
- A picture that `PicturePainter` repeats into a larger tile (over 1024 tiles)
  is repeated from its natural raster, and is not sharpened.
- `image`'s natural size for an SVG is the document's, so a `@2x` variant of an
  SVG is never needed; a stylesheet's `@2x` lookup for `x.svg` finds none and
  draws the one document.
- `currentColor` in a document drawn this way is black, as a browser draws an
  SVG image. Taking it from CSS is `svg-view`'s (phase 4).
