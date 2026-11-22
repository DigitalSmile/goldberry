# ADR-0740: SVG is drawn by ThorVG, in a module of its own

- **Status:** Accepted
- **Date:** 2026-11-22
- **Relates to:** ADR-0651 (`:media`'s library of its own, the model for this
  one), ADR-0656 (the sanitizer lane and fuzz corpus), ADR-0658 (a pixel cap
  for untrusted input)

## Context

Tessera asked for SVG rendering (T-22): forty library shapes and documents its
users bring. The toolkit draws SVG *path data* (`SvgPath`, the bundled icons)
and nothing more: no `<svg>` document, no gradients, `<use>`, `viewBox` or CSS.
On 2026-11-08 the owner chose D10 (b): `goldberry-vector` on ThorVG, a native
library of its own, with Lottie staying on the pure-Java renderer.

Planning W3.6 on 2026-11-22 (`docs/vector-svg.md`), ThorVG v1.1.4 (commit
`47658a80`, MIT) was built here with its C API, its CPU engine and its SVG,
PNG and JPEG loaders, without threads and without file I/O. The library is
640 KB stripped on linux-x64. Its premultiplied `ARGB8888` is the rasterizer's
`PRGB32`, so it renders into the toolkit's pixel buffers with no conversion.
Its SIMD is chosen at compile time (`-mavx2` on x86) with no run-time
dispatch.

## Decision

The owner answered D1–D7 on 2026-11-22, each as recommended:

1. **D1 (a): a document is rasterized at the device pixels it is drawn at.**
   Parsed once; each draw asks for a raster at the box's size times the
   display scale, cached per size within the image cache's byte budget. During
   a resize the last raster is drawn scaled until the new one arrives.
2. **D2 (a): SVG is accepted wherever an image is.** `ImageFormat` sniffs it,
   and `:core` gains a `VectorDecoder` service that `:vector` provides. An
   `image`, CSS `url()`, `html-view` and `markdown-view` all take SVG once the
   module is on the path. Without it, the error names `goldberry-vector`.
3. **D3 (a): `currentColor` is the view's CSS `color`.** It is given to the
   document's root before parsing and is part of the raster's cache key. A
   document's own colours stay as written.
4. **D4 (a): no text inside documents in this batch.** ThorVG is built without
   its font loaders, so `<text>` draws nothing, and the guide says so.
5. **D5 (a): `data:` URIs are the only references.** ThorVG is built with
   `-Dfile=false` and given no asset resolver, so a document never reads a
   file or the network. Embedded PNG and JPEG are decoded by ThorVG's own
   loaders, the only raster formats built.
6. **D6 (a): SIMD is off on x64 and NEON stays on ARM64.** Every x64 machine
   can load the library. The cost is measured in phase 1 and recorded in the
   phase's ADR.
7. **D7 (a): the four release targets.** Linux x64 and ARM64, macOS ARM64,
   Windows x64 on MinGW-w64 as `:media` is built. A snapshot carries what CI
   builds, and a release needs all four.

The library is `libgoldberry-vector`, built by `vector/src/main/cmake` from the
pinned commit and shipped as `goldberry-vector`'s `natives-<target>`
classifiers. Only the `tvg_*` functions the bindings call are exported.

## Consequences

- An application that draws no SVG carries none of ThorVG. One that adds
  `goldberry-vector` carries about 640 KB per target.
- A document from a mail or a download is parsed by C++. The sanitizer lane
  and a fuzz corpus run over the SVG loader, and documents over 16 MB and
  rasters over 8K on a side or 64 megapixels are refused before ThorVG sees
  them.
- `<text>` and references to other files are the two documented limits.
  Either can be added later (ThorVG's `ttf` loader, a resolver over trusted
  paths) without changing the API.
- ThorVG is not a browser. Where it draws a test document differently from
  one, the guide lists it rather than the toolkit patching ThorVG.
