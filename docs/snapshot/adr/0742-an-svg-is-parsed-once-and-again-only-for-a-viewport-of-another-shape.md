# ADR-0742: An `Svg` is parsed once, and again only for a viewport of another shape

- **Status:** Accepted
- **Date:** 2026-11-22
- **Relates to:** ADR-0740 (the plan's decisions, D1, D3 and D5 above all),
  ADR-0741 (the library and its bindings), ADR-0658 (the cap on a decoded
  picture, which the raster cap follows)

## Context

Phase 2 of `docs/vector-svg.md` puts a type an application can hold over the
bindings of ADR-0741: a document parsed once and drawn at any size into an
`Image`, with `currentColor` given from outside (D3), limits for documents
nobody trusts, and one owner of ThorVG's engine.

Three facts from ThorVG v1.1.4's source shaped it:

- **`tvg_picture_set_size` stretches.** `SvgLoader::resize` scales the parsed
  scene by `w / natural w` and `h / natural h`, independently. The document's
  `preserveAspectRatio` is applied once, at parse time, from its view box to
  its own `width` and `height`. Drawn into a box of another shape, a document
  would be distorted, where a browser drawing an SVG image of that size places
  the view box by the document's rule and distorts it only for `none`.
- **ThorVG without its task scheduler has no locks.** The loader cache and the
  CPU rasterizer's memory pool are process-wide, and `ScopedLock` compiles to
  nothing with `-Dthreads=false`.
- **The C API does not say what a document's root says.** There is no call
  for the view box or `preserveAspectRatio`, and nothing to set `color` before
  the document is parsed.

## Decision

1. **`dev.goldberry.vector.Svg`**, exported: `parse(ByteBuffer)`,
   `parse(String)`, each with or without a `currentColor` (`0xAARRGGBB`,
   black when none is given); `naturalSize()` as a `LogicalSize` (ThorVG's,
   so units and percentages are its reading); `viewBox()`; `render(PhysicalSize)`;
   `render(PhysicalSize, Fit)`; `close()`, with a `Cleaner` behind it.
   `ViewBox` is a public record.
2. **The root tag is read by this module** (`…vector.document.RootTag`, not
   exported): past a byte order mark, the XML declaration, processing
   instructions, comments and a doctype with its internal subset, to an `<svg`
   start tag, whose attributes it reads and can rewrite. Bytes without one are
   an `ImageDecodeException` before ThorVG sees them. Source bytes must also
   be UTF-8 and source text may contain only XML 1.0 characters. This check
   reads literal characters without resolving entities or fetching a DTD.
3. **`currentColor` is a `color` attribute put on the root** as `rgba(r,g,b,a)`,
   the one form with alpha ThorVG reads, unless the root names its own `color`
   as an attribute or in its `style`. A `color` set deeper in the document, and
   every colour named outright, win over it as CSS says.
4. **`render(size)` is a viewport of that size, as a browser draws an SVG
   image.** Where the size has the document's own shape within half a pixel on
   both axes, a copy of the parsed picture is resized, which is exact for every
   `preserveAspectRatio`. Where it does not, and the document has a view box it
   places rather than stretches, the document is parsed again with the
   viewport's `width` and `height` on its root, so that ThorVG's own builder
   places the view box and clips to the viewport. A document with no view box,
   or with `none`, is stretched.
5. **`render(size, Fit)`**: `CONTAIN`, `COVER` and `NONE` place the document's
   natural box (uniformly, centred: a resize and a translation of the parsed
   picture), and `FILL` is the viewport of (4), so the document's own rule
   decides whether it is stretched, as `object-fit: fill` does for an SVG image.
6. **One lock for every call into ThorVG** (`ThorvgEngine.call`). The engine
   is started on the first call and never stopped. The parsed picture is never
   drawn itself: each render draws a duplicate on a canvas of its own over the
   `PixelBuffer`'s memory, so renders of one document from many threads take
   turns and each draws what it would alone.
7. **Limits.** A document over 16 MiB is refused before it is parsed; a raster
   with no area, over 8192 on a side or over 64 million pixels is refused
   (`IllegalArgumentException`) before it is allocated. A document ThorVG will
   not read, or that has no positive, finite size, is an
   `ImageDecodeException` naming ThorVG's result.
8. **The document's bytes are kept** by the `Svg` (ThorVG keeps its own copy as
   well), for the parse of (4). At the 16 MiB cap that is 32 MiB for one
   document; an icon is a few hundred bytes twice.

## Consequences

- A viewport of another shape costs a parse per raster. With D1's cache that
  is once per size, and a box resized continuously is drawn from the last
  raster until the new one arrives (phase 3).
- A document truncated inside its body is parsed and drawn as ThorVG reads it
  (v1.1.4 draws nothing of it); that it neither crashes nor throws is what the
  tests hold, and the fuzz lane of phase 5 holds the rest.
- `ThorvgEngine` registers a `Cleaner`, whose thread must not start while a
  native image is built: the phase 5 configuration initializes it at run time.
- `:vector` now requires `:widgets` (transitively, for `Fit`), as the plan
  had it for `svg-view`.
- Whether parallel canvases are safe without the lock stays a question for a
  benchmark before the lock is narrowed.
