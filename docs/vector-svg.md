# SVG on ThorVG — implementation plan

Written 2026-11-22, before any code. This is W3.6 of
[`gaps/goldberry-gaps-plan.md`](gaps/goldberry-gaps-plan.md), Tessera's T-22:
"SVG rendering (40 library shapes, custom SVG)". On 2026-11-08 the owner chose
D10 (b): `goldberry-vector` on ThorVG, a new native library pinned to a commit
and built by a superbuild of its own, as `:media` is. The module provides
`svg-view` and an `image/svg+xml` decoder as a service. Lottie stays on the
pure-Java renderer. The ADR records the licence (MIT) and the size.

The document has the same shape as `spell-checking.md`: what was found, the
decisions with a status, phases with exit criteria, risks, and a log. Each
decision becomes an ADR when it is taken, parked in `docs/snapshot/adr/` from
0740. **All seven were taken on 2026-11-22, each as recommended** (ADR-0740).

## 1. Where things stand

Read from the tree on 2026-11-22 (master at `56c6f33a`).

| Area | State |
|------|-------|
| Vector shapes | `icon.SvgPath` parses SVG path data (`d=`) into a `paint.Path`, and the bundled icons are path data. There is no document reader: no `<svg>`, `<g>`, `<use>`, gradients, `viewBox` or CSS |
| `:assets`' `SvgShapes` | Build time only. It reads shapes for the icon set and writes them out as path data |
| Lottie | `core/image/lottie` is a pure-Java renderer over `Frame`. It handles fills, strokes, gradients, transforms, clips and masks. D10 keeps it |
| Decoding | `Image.decode(ByteBuffer)` sniffs the format with `ImageFormat.of` (magic bytes, never a name). GIF is the toolkit's own decoder, WebP goes to libwebp, and the rest goes to Blend2D. There is **no decoder SPI**, and an `Image` is pixels at one size |
| Where images come from | `widgets.core.image.ImageSource` (file, resource, bytes, decoded, supplied, region) behind `ImageLoader`/`ImageCache`, which decodes on a virtual thread. In CSS, `css.image.StyleImages` is a `ServiceLoader` provider with `@2x` variants. `html-view` and `markdown-view` use their own `content.ImageSource` |
| A native library of its own | `:media` is the model: `media/src/main/cmake` (ExternalProjects, `meson` already driven for dav1d, `checkout.cmake` commit checks, an exports list, `package.cmake` writing a NOTICE), `natives-<target>` classifiers on the publication, its own loader, FFM bindings by hand, an ASan lane and a fuzz corpus (ADR-0656), a pixel cap (ADR-0658), and CI on four targets (Windows on MinGW-w64) |
| Native image | `NativeLibrary` carries `libgoldberry` as a resource and unpacks it on first use. `:media`'s library travels the same way |

**ThorVG, checked and built here on 2026-11-22.**

| Fact | Value |
|------|-------|
| Release | v1.1.4, 2026-11-19, commit `47658a80842f3b3182aded036190edf459beabe7`. Monthly releases since v1.1.0 (July) |
| Licence | MIT. Its bundled lodepng (zlib) and jpgd (public domain or MIT) carry their own notices |
| Build | meson. Options used: `-Dengines=cpu -Dloaders=svg,png,jpg -Dbindings=capi -Dthreads=false -Dfile=false -Dpartial=false -Dextra= -Dstatic=true` |
| Size | **640 KB** stripped, `.so` on linux-x64 with the options above (GCC, release). The SVG loader and XML parser are about 210 KB of objects, and the CPU rasterizer about 230 KB |
| API | A C API (`thorvg_capi.h`) of opaque handles and enums. No struct crosses the boundary, so there is nothing for a layout probe to check; a version check is enough |
| Pixels | `TVG_COLORSPACE_ARGB8888` is premultiplied, `0xAARRGGBB` per `uint32_t`. That is Blend2D's `PRGB32` on every target the toolkit builds, so ThorVG can render straight into a `PixelBuffer` with no conversion |
| File I/O | `-Dfile=false` compiles out every file read. A document can reach only bytes it was handed (and `data:` URIs inside them) |
| Threads | `-Dthreads=false` removes the task scheduler. `tvg_engine_init` and `tvg_engine_term` must pair on one thread |
| SIMD | `-Dsimd=true` (the default) compiles the CPU engine with `-mavx2` on x86 and NEON on ARM. **There is no run-time dispatch**: an x64 build with it crashes with SIGILL on a CPU without AVX2 (see D6) |

## 2. What this plan delivers

- `:vector`, a new optional module (`goldberry-vector`, `dev.goldberry.vector`),
  and `libgoldberry-vector`, a shared library holding ThorVG, shipped as
  `natives-<target>` classifiers of that module.
- `dev.goldberry.vector.Svg`: a parsed document with its natural size and
  `viewBox`, rendered at any pixel size into an `Image`.
- SVG accepted wherever an image is, once the module is on the path (D2):
  `image`, CSS `url()`, `html-view` and `markdown-view`. Each is rasterized at
  the device pixels it is drawn at (D1).
- `svg-view`, a widget for a document given as a source or inline, with `fit`,
  `alt` and `currentColor` taken from CSS `color` (D3).
- Limits for untrusted documents (size, pixels, references), an ASan build, and
  a fuzz corpus run.
- A showcase card, the guide section (parked until release), KDL, and
  THIRD-PARTY-NOTICES rows.

**Out of scope:** animation (SMIL, CSS animations inside SVG), scripting and
events inside the document, `<foreignObject>`, editing, saving, Lottie through
ThorVG, and the GPU engines. Tessera draws its 40 library shapes itself; this
plan gives it a way to draw them as SVG files.

## 3. Decisions

### D1. The size a document is rasterized at (taken: (a))

- **(a) The device pixels of the box it is drawn into (recommended).** A
  document is parsed once and kept as a `Svg`. Each draw asks for a raster at
  the box's size times the display scale, rounded up to a whole pixel. Rasters
  are cached per (document, pixel size) in the image cache's byte budget, and a
  resize that is still running redraws the last raster scaled until the new one
  arrives. Sharp at every scale and every size, which is why SVG is wanted.
- (b) Once, at the natural size times the scale, and scaled by Blend2D after
  that. Simpler, and blurry as soon as a box differs from the natural size.

### D2. Where SVG is accepted (taken: (a))

- **(a) Everywhere an image is, through an SPI in `:core` (recommended).**
  `ImageFormat` learns to sniff SVG (an `<svg` root after an optional BOM, XML
  declaration, comments and doctype). `:core` gains
  `dev.goldberry.image.spi.VectorDecoder` (`uses` in `module-info`), which
  `:vector` provides. `Image.decode` on SVG bytes asks the provider for a
  raster at the natural size. The size-aware loaders (`ImageCache`, the CSS
  painter, the HTML and Markdown views) ask it for a `ScalableImage` and use
  D1. Without the module, the error names `goldberry-vector`. This is what
  `answers-tessera.md` promised.
- (b) Only `svg-view` draws SVG, and an `image` with an SVG source fails as it
  does today.

### D3. Colour from the theme (taken: (a))

- **(a) `currentColor` is the view's CSS `color` (recommended).** The root
  `<svg>` is given the colour as its `color` before ThorVG parses it, so an
  icon drawn in `currentColor` follows the theme and `:hover`. The colour is
  part of the raster cache's key. A document that names its own colours keeps
  them.
- (b) The file's colours only.

### D4. Text inside a document (taken: (a))

- **(a) Not in this batch (recommended).** ThorVG is built without its font
  loaders, so `<text>` draws nothing, and the guide says so. Icons and shapes
  rarely carry text, and a document's text would not go through the toolkit's
  shaper anyway: no fallback fonts, no bidi.
- (b) ThorVG's `ttf` loader, with the toolkit's bundled faces registered with
  it. Adds about 100 KB and a second text stack whose output differs from the
  toolkit's own.

### D5. References out of a document (taken: (a))

- **(a) `data:` URIs only (recommended).** Built with `-Dfile=false`, so no
  file is ever read, and with no asset resolver, so nothing is fetched. An
  embedded PNG or JPEG is decoded by ThorVG's own small loaders (lodepng,
  jpgd), and those are the only raster formats built. `<use>` inside the
  document works, and `<use href="other.svg#x">` draws nothing. A mail, a
  download or a paste can be drawn without reaching the disk or the network.
- (b) As (a), and also files beside a document read from a trusted `Path` or
  resource, through ThorVG's resolver calling back into Java.

### D6. SIMD on x64 (taken: (a))

- **(a) Off on x64, NEON on ARM64 (recommended).** `-Dsimd=false` on x64
  until ThorVG dispatches at run time. Every x64 machine the toolkit runs on
  can load it. The cost is measured in phase 1 and recorded in the ADR.
  ARM64 always has NEON, so it keeps it.
- (b) AVX2 on x64, and AVX2 becomes the floor for x64 machines (roughly
  Haswell, 2013, and later). A load on an older CPU has to be refused cleanly
  with a check before `dlopen`, not a SIGILL.
- (c) Two x64 builds, with and without AVX2, chosen at load time. Twice the
  library on x64.

### D7. The targets (taken: (a))

- **(a) The four the toolkit releases on: linux-x64, linux-aarch64,
  macos-aarch64, windows-x64 (recommended).** A snapshot carries what CI
  builds, and a release refuses to go without all four, as `:media`'s natives
  do. Windows is built with MinGW-w64 in MSYS2's UCRT64 shell, like `:media`,
  so the two superbuilds share one toolchain and one set of steps.
- (b) Windows with MSVC. ThorVG supports it, but it would be a second Windows
  toolchain in CI.

## 4. Design

**The module.** `:vector` holds `dev.goldberry.vector` (exported: `Svg`,
`SvgView`, `VectorCapabilities`), `dev.goldberry.vector.thorvg` (internal:
FFM bindings, the library loader, the engine's one owner) and
`dev.goldberry.vector.decode` (internal: the `VectorDecoder` provider). It
`requires dev.goldberry.core` and `dev.goldberry.widgets`, and nothing in
`:core` or `:widgets` requires it. It goes into the umbrella as optional, as
`:html`, `:media` and `:spell` do.

**The library.** `vector/src/main/cmake` builds ThorVG as an ExternalProject
(meson, static, options in §1), links it into `libgoldberry-vector` with an
exports list of the `tvg_*` functions used and nothing else, writes
`vector-NOTICE.txt`, and checks the commit as `:media`'s `checkout.cmake`
does. The pin lives in `gradle/libs.versions.toml`. The library is stripped
and size-gated.

**One owner of the engine.** `tvg_engine_init`/`term` pair on one thread, and
ThorVG's loader cache is process-wide. Phase 2 starts with every parse and
render behind one lock and the engine initialised once for the life of the
process. A benchmark measures whether parallel canvases are safe without the
lock before the lock is narrowed.

**Rendering.** `Svg.render(PhysicalSize)` allocates a `PixelBuffer`, makes a
`tvg_swcanvas` targeting its memory in `ARGB8888`, pushes a duplicate of the
parsed picture sized and placed by `preserveAspectRatio`, draws and syncs. The
canvas lives only for that one call. The pixels are Java's again when it
returns.

As built (phase 2): ThorVG's `tvg_picture_set_size` stretches a parsed document
on each axis, and the document's `preserveAspectRatio` is applied only at
parse time. So a viewport with the document's own shape (within half a pixel)
resizes a duplicate, and a viewport of another shape, for a document with a
view box it does not stretch, parses the document again with the viewport's
`width` and `height` on its root. `RootTag` (`…vector.document`) reads and
rewrites the root; `currentColor` is an `rgba()` `color` put there. A `Fit`
other than `FILL` places the natural box with a resize and a translation.

**Limits.** A document over 16 MB is refused before parsing. A raster over 8K
on a side or 64 megapixels is refused (as ADR-0658 caps video). A document that
fails to parse is an `ImageDecodeException` with ThorVG's result code, and the
`image` shows its error state as for any other bad file.

## 5. Phases

### Phase 0. This plan (done)

Exit: D1–D7 taken; ADR-0740 records them with the licence and the size.

### Phase 1. The library

- `:vector` module skeleton, settings, BOM, the umbrella's optional list.
- `vector/src/main/cmake`: ThorVG pinned to `47658a80`, the exports list,
  NOTICE, size gate, `:vector:nativesBuild` for the host, the toolchain check.
- `natives-<target>` jars and their publication.
- `licenses/thorvg.txt` (with lodepng's and jpgd's notices), a
  THIRD-PARTY-NOTICES row.
- `VectorLibrary` loader (resource from the classifier jar, the
  `goldberry.vector.library` override), a version check.
- Measured: size per target, and D6's cost in a render benchmark.

Tests: the library loads from its classifier jar (`natives-jar` tag), the
version matches the pin, the exports list matches what the bindings use
(`check_exports.cmake` as `:media` has), and a one-rect document renders the
expected pixels.

Exit: the library builds and loads on linux-x64 here. CI is phase 5.

### Phase 2. `Svg`

- `Svg.parse(ByteBuffer)` and `Svg.parse(String)`, natural size, `viewBox`,
  `render(PhysicalSize)`, `render(PhysicalSize, Fit)`, `close()` and a cleaner.
- `currentColor` (D3) as a parse parameter.
- The limits in §4, and errors as `ImageDecodeException`.

Tests: pixels of rects, paths, strokes, gradients, `<use>`, clips, masks and
opacity against goldens; `preserveAspectRatio` in all three kinds; a document
with no size; malformed and truncated input; over-size refusal; `data:` PNG;
an external `href` drawing nothing; `currentColor`; renders from many virtual
threads at once.

Exit: an SVG becomes an `Image` at any size.

### Phase 3. SVG wherever an image is

- `ImageFormat.SVG` and the sniff; `dev.goldberry.image.spi.VectorDecoder` and
  `ScalableImage` in `:core`; `Image.decode` of SVG through the provider, and
  the error naming `goldberry-vector` without it.
- `ImageCache` keys rasters by pixel size; `image` asks at its box's device
  size (D1).
  As built: the frame draws an SVG image at the device size, so `ImageCache`
  keeps one decode per source and `ScalableRasters` the sizes (ADR-0743).
- CSS `url()` (`StyleImages`, `background-image`, `list-style-image`, border
  images) at the painted size.
- `html-view` and `markdown-view` image sources.

Tests: the sniff on real files and look-alikes (an XML document that is not
SVG, HTML with an `<svg>` inside); `:core` with a fake provider; an `image` at
1× and 2× drawing a sharp edge; CSS background at two sizes; a mail with an
inline SVG `<img>`.

Exit: an application that adds `goldberry-vector` sees SVG work in every place
an image is accepted.

### Phase 4. `svg-view`

- `@Markup svg-view` with `src=`, inline content, `fit=`, `alt=`,
  `decorative=`, and `color` from CSS.
- Loading and error states as `image` has them.

Tests: markup inflation, fit modes, theme colour changing on `:hover`, the
error state, semantics (alt text), and a golden.

### Phase 5. CI, native image, robustness

- The superbuild on the four targets in `media.yml` (or a `vector.yml` beside
  it), the classifiers published with the snapshot.
- An ASan build and a fuzz corpus run over the SVG loader, as ADR-0656 does
  for `:media`.
- A native-image run of the showcase with the library as a resource.
  `ThorvgEngine` registers a `Cleaner`, so it is initialized at run time.

Exit: Snapshot green on all jobs, with the vector library on four targets.

### Phase 6. Showcase and guide

- A card on the showcase: a few documents of the project's own drawing at
  three sizes, one tinted by the theme, one broken.
- The guide section, parked in `docs/snapshot/components-vector.md`, and the
  catalogue line.
- `answers-tessera.md` G-22, `docs/status.md`, `ARCHITECTURE.md` §11.1's row
  (Lottie stays Java).

## 6. Risks

- **Malformed documents.** ThorVG's SVG loader fixes crashes on malformed
  input in most releases (v1.1.4 lists several). The ASan lane, the fuzz
  corpus and the 16 MB cap are the answer. A crash in the loader takes the
  process down, so a document from a mail is the case to fuzz hardest.
- **Thread safety.** ThorVG's loader cache and memory pools are process-wide.
  The single lock is the starting point, not an assumption that it is safe
  without one.
- **Rendering differences.** ThorVG is not a browser. Where it differs from
  Firefox on a test document, the guide lists the difference rather than the
  toolkit patching ThorVG.
- **Release cadence.** ThorVG releases monthly and the C API grows (v1.1.4
  changed the asset resolver's signature). The pin moves only with the
  dependency updates, and the exports list catches a removed function.
- **Cache size.** A raster per size multiplies during an animated resize. The
  cache keeps the last raster per document while a new size is pending and
  evicts by bytes, as it does now.

## 7. Documents kept in step

| Document | What | Status |
|----------|------|--------|
| `docs/gaps/goldberry-gaps-plan.md` §8, §10 | T-22's row and the log | plan written |
| `docs/gaps/answers-tessera.md` | G-22's answer when it lands | |
| `docs/snapshot/adr/` | ADR-0740 (D1–D7), ADR-0741 (phase 1), ADR-0742 (phase 2), ADR-0743 (phase 3), then one per phase | doing |
| `docs/snapshot/` guide pieces | `components-vector.md` | |
| `docs/status.md` | a line per phase | doing |
| `THIRD-PARTY-NOTICES.md`, `NOTICE`, `licenses/` | ThorVG, LodePNG, jpgd | done (phase 1) |

## 8. Status and log

| Phase | Status |
|-------|--------|
| 0. Plan | done (this document, ADR-0740) |
| 1. The library | done 2026-11-22 (ADR-0741) |
| 2. `Svg` | done 2026-11-22 (ADR-0742) |
| 3. SVG wherever an image is | done 2026-11-22 (ADR-0743) |
| 4. `svg-view` | todo (next) |
| 5. CI, native image, robustness | todo |
| 6. Showcase and guide | todo |

- **2026-11-22.** Plan written from the tree at `56c6f33a`. Found: no decoder
  SPI and no vector reader beyond `SvgPath`; `:media`'s superbuild already
  drives meson. ThorVG v1.1.4 (`47658a80`) built here with the C API, the CPU
  engine and the SVG, PNG and JPEG loaders, no threads and no file I/O: 640 KB
  stripped on linux-x64. Its premultiplied `ARGB8888` is `PRGB32`, so it renders
  into a `PixelBuffer` as it is. Its SIMD is a compile-time `-mavx2` with no
  dispatch (D6). D1–D7 are asked.
- **2026-11-22, later.** The owner took D1–D7, each as recommended
  (ADR-0740). Phase 1 starts.
- **2026-11-22, phase 1 done.** `:vector` and `libgoldberry-vector`: the
  superbuild (ThorVG as a meson ExternalProject, its hidden static API made
  visible with `-fvisibility=default` so the export list decides), 17 exported
  symbols, `vector.properties` beside the library, the 1.5 MB gate (486 KB on
  linux-x64), the natives jars and their publication, `VectorNatives` with its
  ABI and target checks, `VectorCapabilities`, and 35 tests including a
  document drawn through the bare bindings and the natives jar on the class
  path. D6 measured on an i7-4790K: SIMD off costs about 35% at 1024² on a
  400-path document with gradients (288–295 against 215–219 ms) and 17% at
  256²; it stays off (ADR-0741). Next ADR 0742.
- **2026-11-22, phase 2 done.** `Svg` (ADR-0742): `parse` from bytes or text
  with an optional `currentColor`, `naturalSize`, `viewBox`, `render(size)` as
  a browser's SVG viewport and `render(size, Fit)`, `close` and a cleaner;
  `ViewBox`; `RootTag`, which finds the root past a prologue and rewrites its
  attributes; `ThorvgEngine` (one lock, the engine started once and never
  stopped) and `ThorvgPicture`. Found in ThorVG's source: a resize stretches,
  so a viewport of another shape is a parse of its own; `rgba()` with commas is
  the only colour with alpha it reads. Limits: 16 MiB, 8192 a side, 64 million
  pixels. Tests: the root scanner, placement in all three kinds and every
  `Fit`, `currentColor`, a `data:` PNG and five references out of the
  document drawing nothing, a truncated body, the limits, 32 virtual threads
  sharing and parsing documents, and two goldens (every feature, and contained
  in a square) with the 2× and 1.5× sweep. The phase 1 draw test now runs in
  the engine's lock, since the cleaner's thread may release a picture at any
  time. `:vector` requires `:widgets` for `Fit`. Next ADR 0743.
- **2026-11-22, phase 3 done.** SVG wherever an image is (ADR-0743). Read in
  the code: every place an image is drawn ends in `Frame.drawImage` with a
  source rectangle, and every loader passes `Image`s. So rather than each
  loader asking a provider for a `ScalableImage` and `ImageCache` keying by
  size, an SVG decodes to an `Image` that carries its document, and the frame
  draws it from a raster of the device pixels it covers (`ScalableRasters`,
  64 MiB, drawn at once under 2 ms and otherwise on a virtual thread with the
  document's last raster drawn until it arrives, as D1 asks). `ImageFormat.SVG`
  sniffs past the prologue; `ScalableImage` and `VectorDecoder` are in `:core`,
  `SvgDecoder` in `:vector`; `PictureArrivals` is the one signal for every late
  picture, and a painted box is repainted on one. Tests: the sniff on documents
  and look-alikes, `:core` with a decoder of its own (a one-pixel checkerboard,
  sharp at 2×, larger, cropped, kept, refused), the raster cache's bound, and
  with ThorVG an `image` at 1× and 2×, a CSS background at two sizes and a
  Markdown picture each drawing an edge on a device pixel; with the frame's
  hook switched off, eight of them fail. Next ADR 0744.
