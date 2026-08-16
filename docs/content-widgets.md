# Goldberry — Content Widgets

Companion to `ARCHITECTURE.md`. Covers the optional content modules: HTML/markdown, PDF, charts, scientific plotting, code, terminal, vector/animation, media, and the parked web-engine module.

**The module pattern.** `goldberry-core` stays lean and license-flat (Apache-2.0 + permissive notices). Anything that brings a heavy native dependency or an attribution/copyleft obligation is quarantined in its own artifact with its own natives jars. Apps opt in per module; nothing here is a core dependency.

| Module              | Engine                  | Native size | License in                    | Status |
|---------------------|-------------------------|-------------|-------------------------------|--------|
| `goldberry-html`    | litehtml + md4c         | < 1 MB      | BSD-3-Clause, MIT             | planned (M3-adjacent) |
| `goldberry-pdf`     | PDFium                  | tens of MB  | BSD-3 + bundled permissive    | planned (M4-adjacent) |
| `goldberry-charts`  | first-party (canvas)    | none        | Apache-2.0                    | planned (M3) |
| `goldberry-plot`    | first-party (on charts) | none        | Apache-2.0                    | post-v1 |
| `goldberry-code`    | Tree-sitter             | small       | MIT                           | candidate |
| `goldberry-terminal`| libvterm                | tiny        | MIT                           | candidate — strong for Scarlet |
| `goldberry-vector`  | ThorVG                  | small       | MIT                           | candidate — resolves SVG deferral |
| `goldberry-media`   | libVLC                  | large       | LGPL-2.1+ (**dynamic link**)  | engine decided; module gated on media doc |
| `goldberry-camera`  | SDL3 camera subsystem   | none new    | zlib (SDL3, already in core)  | planned |
| `goldberry-mic`     | SDL3 audio recording    | none new    | zlib (SDL3, already in core)  | planned |
| `goldberry-web`     | Servo                   | large       | MPL-2.0                       | **parked** |
| `goldberry-emoji`   | OpenMoji                | font only   | CC BY-SA 4.0 (attribution)    | planned (M2) |

---

## 1. `goldberry-html` — `html-view` and `markdown-view` (litehtml + md4c)

**Scope.** Rendering *authored content*: documentation, help pages, changelogs, HTML email display, and markdown. Explicit non-goal: web browsing. The README promise is "renders your HTML content, beautifully and offline" — never "renders websites."

### 1.1 Architecture

litehtml is C++ with a virtual-class embedding interface (`document_container`), which FFM cannot implement directly. The container is therefore implemented **natively**, in C++, inside `libgoldberry-html`:

- Font callbacks (`create_font`, `text_width`, `draw_text`) route to the toolkit's HarfBuzz + Blend2D text stack — HTML renders in the embedded Inter/JetBrains Mono through the same shaping pipeline as every widget.
- Paint callbacks (`draw_background`, `draw_borders`, list markers) are direct Blend2D calls.
- Image decoding uses Blend2D's built-in codecs (PNG/JPEG/QOI); SVG images route to `goldberry-vector` when present (§7).

A thin **C API** faces Java: create/destroy document, set viewport + media parameters, layout at width, paint into a context with a clip, forward pointer events (for `:hover` and link hit-testing). Exactly **two upcalls** cross into Java:

1. `fetch(url, kind)` — images and linked stylesheets. All networking happens in Java (`HttpClient`), under the app's policy. The native layer never touches the network.
2. `anchorClicked(url)` — navigation is always an app decision.

The hot path (thousands of draw calls per page) never crosses the FFM boundary.

### 1.2 Markdown

`markdown-view` = **md4c** (tiny, MIT, CommonMark + tables/strikethrough/task-list extensions) parsing to HTML, rendered by the same litehtml pipeline with the theme master stylesheet. One extra dependency measured in kilobytes; markdown inherits theming, fonts, and golden-image testing for free. Code fences hand off to `goldberry-code` highlighting when that module is present, else render as plain `code` blocks.

### 1.3 Widget API

```kdl
scroll {
  html-view id="doc" src="help/getting-started.html"
}
scroll {
  markdown-view src="CHANGELOG.md"
}
```

```java
var view = HtmlView.of(Path.of("help/getting-started.html"))
    .onLink(url -> app.navigate(url))
    .resources(new ClasspathResourceResolver("/help"));
```

`html-view` is a leaf render object; Yoga sizes it (measure = layout-at-width), the host `scroll` provides scrolling. Re-layout on width change; paints as a repaint-boundary layer.

### 1.4 Theming

litehtml requires the embedder to supply the **master stylesheet** (default styling of `h1`, `p`, `a`, `code`, `table`…). Goldberry generates it from the active theme: `--gb-*` colors, the typography scale, `--gb-selection`, link colors from `--gb-accent`. Regenerated on theme switch — HTML content is Nord-native in light and dark automatically. Media parameters fed honestly: logical viewport, device scale, and `prefers-color-scheme` mapped from the active theme.

### 1.5 Supported / not supported

Supported (current litehtml): HTML5 parsing; CSS selectors and matching with media queries; block/inline/table layout; **flexbox**; floats; positioned elements (relative/absolute); backgrounds incl. linear/radial gradients; borders and `border-radius`; lists; `:hover` and interactive pseudo-classes.

Not supported, by engine or by v1 decision:

- JavaScript, network, navigation — engine has none; app owns all three. (Feature, not gap.)
- CSS Grid, CSS animations/transitions, web components.
- Interactive form widgets (`<input>` etc.) — out of scope for a content renderer.
- Text selection/copy — deferred to v1.x (character-quad work, same bucket as text-editing depth).

### 1.6 Testing & license

Deterministic by construction (embedded fonts, CPU raster) → golden-image tests of full documents in CI. litehtml BSD-3-Clause, md4c MIT; entries in `THIRD-PARTY-NOTICES`. Statically linked into `libgoldberry-html` via the CMake superbuild.

---

## 2. `goldberry-pdf` — the `pdf-view` widget (PDFium)

**Scope.** A real PDF viewer widget: continuous page view, zoom, selection, search, links, thumbnails. Chrome/Edge's rendering engine, so fidelity expectations are "what users already trust."

### 2.1 Architecture

PDFium exposes a **C API** (`fpdfview.h`, `fpdf_text.h`, …) — bound directly via jextract/FFM, no shim layer at all.

- **Rendering:** `FPDF_RenderPageBitmap` rasterizes into a caller-owned BGRA buffer → wrapped zero-copy as `BLImage` → composited as a repaint-boundary layer. Pages render at `zoom × window physical scale`, so text is re-rastered crisp on zoom rather than bitmap-scaled.
- **Threading:** PDFium is not thread-safe. All PDFium calls are serialized on **one dedicated PDF worker thread** per process; rendered `BLImage`s are handed to the UI thread. Page renders are therefore async by construction — the widget shows a placeholder fill until the raster lands.
- **Memory:** LRU cache of page rasters (budgeted in bytes, not pages); thumbnails are the same render call at small size, cached separately.

### 2.2 Widget features (v1)

- Virtualized continuous page list inside `scroll` (only visible ± prefetch pages are rendered).
- Zoom: fit-width / fit-page / percentage; re-render on settle, scaled preview during pinch/drag.
- Text selection from `FPDF_Text*` character quads, drawn with `--gb-selection`; copy to clipboard.
- Search with highlight quads; next/previous navigation.
- Link regions (`FPDFLink_*`) hit-tested through normal event dispatch — internal destinations scroll, external URLs raise `onLink`.
- Encrypted documents via a password callback.
- Document outline (bookmarks) exposed as a model the app can bind to a tree/list.

Deferred: form filling, annotation authoring, printing.

```kdl
pdf-view id="manual" src="manual.pdf" zoom="fit-width"
```

```java
var pdf = PdfView.open(Path.of("manual.pdf"))
    .onLink(app::openExternal);
pdf.search("flexbox");
```

### 2.3 Natives & license

PDFium's own build uses Google's gn/depot_tools — deliberately **not** vendored into the superbuild. The `:natives-pdf` module consumes the community **pdfium-binaries** prebuilts (pinned version, checksum-verified) and repackages them as `goldberry-pdf-natives-{platform}-{arch}` jars. License: BSD-3-Clause plus bundled permissive components (e.g. FreeType under FTL) — all listed in the module's `THIRD-PARTY-NOTICES`. The size cost (tens of MB) is the reason this module exists separately from core.

Rejected engines, for the record: **MuPDF** (best-in-class, but AGPL/commercial — unshippable inside an Apache-2.0 library) and **Poppler** (GPL — same problem).

---

## 3. `goldberry-charts` — first-party, and staying that way

**Decision: no third-party chart engine.** The survey is short and conclusive:

- Java chart libraries (JFreeChart, XChart, Orson) all render through Java2D/AWT — a heavyweight dependency Goldberry deliberately does not have, with weak GraalVM native-image support.
- ImPlot is the only serious native-side candidate, but it is welded to Dear ImGui's immediate-mode draw-list model — embedding it means hosting a second, philosophically opposite UI framework inside the render tree.
- GR framework has a real C API but brings its own device model and font rendering — charts would fight the theme and bypass the text stack.
- The genuinely great chart systems (ECharts, Vega, Observable Plot) are JavaScript and require exactly the browser engine core refuses to carry.

Charts aren't an *engine* problem the way PDF is: the importable surface is a few hundred lines of published algorithms, while the mismatch surface (fonts, theming, events, testing) is the entire library. So: build on the `canvas` primitive and inherit everything.

### 3.1 Borrow algorithms, not engines

- **Wilkinson's extended tick-labeling algorithm** for "nice" axis ticks.
- **LTTB downsampling** (Largest-Triangle-Three-Buckets) so a 100k-point series draws as a faithful ~1k-point polyline.
- **OKLCH color ramps** derived from the Nord aurora palette: categorical series colors from aurora + frost hues; sequential/diverging ramps interpolated in OKLCH (shared with the system-accent derivation utility).
- **`java.time`-driven time axes** (tick stepping across sec/min/hour/day/month/year boundaries).

### 3.2 Scope (guardrail)

`sparkline`, `line-chart`, `bar-chart`, `area-chart`, `donut-chart`; shared axis/legend/tooltip primitives; hover tooltips and crosshair via normal dispatch; enter/update animation via the ticker (reduced-motion aware). Data via a small `Series` model in Java, or inline KDL for small static data:

```kdl
bar-chart title="Releases" {
  series name="downloads" {
    point "0.1" 1200
    point "0.2" 3400
  }
}
```

**Deliberately not a plotting library.** Dashboard-grade, five widgets. Science-grade needs go to `goldberry-plot` (§4).

---

## 4. `goldberry-plot` — scientific plotting (first-party, post-v1)

Layered on `goldberry-charts` primitives (axes, legend, tooltip, ramps) — same substrate, bigger vocabulary. Still no third-party engine, for the same reasons as §3.

### 4.1 Scope

- **Plot types:** `scatter` (large-N), `histogram`, `box-plot`, `heatmap`, `contour`, `error-bar` decorations on line/scatter, step/stairs plots.
- **Scales:** log and symlog axes with correct log tick labeling (extension of the Wilkinson pass), dual y-axes, shared/linked axes across plots (crosshair sync).
- **Large data:** spatial index (grid/quadtree) for hover/selection hit-testing on 10⁵–10⁶ point scatters; LTTB for series; density fallback (auto-switch scatter → heatmap above a point threshold).
- **Colormaps:** the perceptual scientific families (viridis/magma-class, public-domain colormap data) alongside Nord-derived ramps — science needs perceptual uniformity more than brand fidelity; both offered, viridis-class default for heatmaps.
- **Algorithms to implement:** Freedman–Diaconis binning for histograms, marching squares for contours, Welford/quantile passes for box plots.

### 4.2 Deferred / non-goals

Math-notation labels (LaTeX-style) — deferred; plain text + unicode only. 3D plotting — non-goal (that's a `canvas3d` application, not a plot widget). Maps/geo — non-goal. Interactive pan/zoom on plots is v1 of this module (it's table stakes for scientific use), implemented on normal pointer events.

### 4.3 Export

Because everything is Blend2D, `Plot.renderTo(image)` gives publication-quality PNG export at arbitrary DPI for free; SVG export deferred.

---

## 5. `goldberry-code` — syntax-highlighted code view (Tree-sitter)

**Why:** docs, changelogs, diff views, and any developer-facing app need highlighted code; and md4c code fences want a highlighter.

- **Engine: Tree-sitter** — C library, MIT, incremental parsing, the highlighting engine behind Neovim/Zed/GitHub. Grammars are themselves small generated C libraries; the module ships a curated set (Java, Kotlin, C, Rust, Python, JS/TS, JSON, KDL, CSS, HTML, Markdown, Bash) with an SPI to register more.
- **Widget:** `code-view` — read-only v1: highlighting via Tree-sitter queries mapped to theme token colors (a Nord highlight scheme ships in light and dark), line numbers, soft wrap toggle, selection + copy reusing the toolkit selection machinery.
- **Explicitly not v1:** editing. A code *editor* is text-editing depth (ARCHITECTURE §17) plus incremental re-highlighting — Tree-sitter's incrementality is designed for exactly that, so the seam exists, but the commitment doesn't.
- Native cost small; grammars statically linked into `libgoldberry-code`.

---

## 6. `goldberry-terminal` — terminal widget (libvterm)

**Why:** first-class for Scarlet Macaw OS (the system terminal app becomes a Goldberry widget) and broadly useful for dev tools.

- **Engine: libvterm** — tiny MIT C library implementing the terminal state machine (the one under Neovim's `:terminal`). It parses escape sequences and maintains the cell grid; Goldberry renders the grid.
- **Rendering fit is ideal:** a damage-rect-driven monospace cell grid in JetBrains Mono — the exact strengths of the existing text/raster pipeline. Truecolor maps through the theme (a Nord 16-color ANSI palette ships by default).
- **The real platform work is the PTY, not the terminal:** `forkpty`/`openpty` via FFM on Linux/macOS, **ConPTY** on Windows. This lands in the Backend SPI as an optional capability (`Optional<Pty> openPty(cmd, env, size)`) — the same pattern as `gpuSurface()`.
- v1: vt100/xterm-class emulation as provided by libvterm, scrollback buffer, selection + copy, clickable URLs (OSC 8 + heuristic). Deferred: sixel/kitty image protocols, ligatures.

---

## 7. `goldberry-vector` — SVG + Lottie (ThorVG)

**Why:** resolves two deferrals at once — SVG images in `html-view`/`image`, and animated vector illustrations (Lottie) for modern empty-states/onboarding polish.

- **Engine: ThorVG** — MIT, small, has a C API, renders SVG (a practical subset) and Lottie animations. CPU rendering into a buffer → `BLImage`, or its paints traversed to Blend2D.
- **Widgets:** `svg-view` (static, DPI-crisp re-raster on scale) and `lottie-view` (driven by the toolkit ticker; respects reduced-motion by rendering the final frame).
- Interaction with core: the `image` widget and litehtml's image callback route `image/svg+xml` here when the module is on the classpath; otherwise SVG is unsupported (as core documents).
- Note: Lucide icons do **not** move here — they remain precompiled paths in core with zero dependencies. This module is for arbitrary/user-supplied vector content.

---

## 8. `goldberry-media` — audio/video playback (libVLC) — engine decided, module still gated

- **Engine: libVLC.** Chosen over libmpv on three grounds: official builds are **LGPL by default** (mpv is LGPL only via custom `-Dgpl=false` builds you'd have to own); the libVLC 3.x C API has been effectively stable for a decade; and the Java precedent is overwhelming — vlcj has shipped VLC-rendered video inside Java desktop apps for ~15 years via the same `vmem` callback path Goldberry uses (decoded frames in a requested pixel format → `BLImage` layers). Subtitles are rendered onto frames by the engine.
- **Packaging (the known tax):** libVLC is `libvlc` + `libvlccore` + a **plugins directory** of many shared objects. The natives jars ship the plugin tree and the loader sets the plugin path explicitly at init. Dynamic linking throughout — which LGPL requires anyway.
- **License:** LGPL-2.1+, dynamically linked (relink requirement satisfied). Apps that ship it owe the LGPL notice; documented loudly in the module README. Codec/patent exposure is per-app and gets its own document before this module gets code.
- **Scope if built:** `video-view` (playback, seek, tracks, subtitles) and an `AudioPlayer` API — music, podcasts, and streams go through the same engine; audio-only playback is just libVLC without a video output.
- **UI sound effects do not belong here.** Short, low-latency effect sounds (clicks, notification chimes) should not drag in an LGPL media stack. SDL3 — already inside `libgoldberry` — has a full audio subsystem: a small `Sound` API in core plays WAV effects through SDL audio, with a tiny public-domain decoder (stb-class) if OGG effects are wanted. Zero new dependencies; the earlier miniaudio idea is retired.

---

## 9. `goldberry-camera` — camera capture (SDL3)

**Zero new natives.** The module calls SDL3's camera subsystem directly (V4L2/PipeWire on Linux, Media Foundation on Windows, CoreMedia on macOS) — SDL3 is already statically linked in `libgoldberry`. A separate artifact so that apps that don't use the camera never link camera code, and privacy review scopes to one small module.

- `camera-view` widget: device selection, live preview as a repaint-boundary layer updated at camera cadence (decoupled from the UI frame rate), format negotiation with YUY2/NV12 → BGRA conversion into `BLImage`, mirror option, `snapshot()` returning an image.
- **Permission states are widget states:** the OS prompt flow (SDL surfaces approval/denial events) renders as themed placeholders — `awaiting-permission`, `denied` with guidance — never a black rectangle. macOS app bundles need the camera usage-description plist key; documented in the module README.
- Testing: a synthetic color-bar/moving-pattern camera source ships for CI — capture UIs stay golden-image-testable with no hardware.
- Non-goals: encoding/recording-to-file (media territory), video-call transport (app domain). The frame-stream API is public precisely so apps can feed encoders or QR scanners themselves.

## 10. `goldberry-mic` — microphone capture & audio UI (SDL3)

**Zero new natives**, same reasoning: SDL3's audio API supports recording devices, and it's already in the binary.

- `AudioCapture`: device enumeration + PCM chunk stream (sample rate/format negotiated), delivered off-thread, exposed to Java as `MemorySegment`s with zero copies.
- Visualization widgets, canvas-based and theme-colored: `level-meter` (peak/RMS with proper decay ballistics), `waveform-view` (rolling buffer), `spectrum-view` (small first-party Java FFT — Vector API candidate).
- Same permission-state treatment as camera (microphone usage-description key on macOS).
- Testing: synthetic sources (sine, noise, sweep) for CI.

### 10.1 Audio mixer — three meanings, three answers

1. **In-app mixing** (multiple sounds at once, per-sound volume): already free — SDL3 mixes all audio streams bound to a device; the core `Sound` API and `AudioPlayer` expose per-stream gain. Nothing to build beyond the API surface.
2. **Mixer UI**: `knob` and the vertical `fader` slider variant live in core's control catalog; this module adds the meters, and a `channel-strip` composite (fader + knob + meter + mute/solo) ships as a gallery example rather than a widget — apps compose their own strips.
3. **System mixer** (other apps' volumes, output routing): out of toolkit scope — that's OS-specific session APIs, app territory.

---

## 11. Parked: `goldberry-web` (Servo)

Full web embedding stays out of scope for core; CEF-OSR remains the documented escape hatch for apps that truly need Chromium. Servo is the tracked future option: MPL-2.0 (file-level copyleft — clean next to Apache-2.0), a `SoftwareRenderingContext` that fits the CPU pipeline, and AccessKit alignment with Goldberry's own a11y plan. Blocker: libservo is Rust-only (a custom `cdylib` C shim would be ours to maintain) against a deliberately unstable API. Revisit when libservo ships with semver guarantees or Verso-style embedding stabilizes.

---

## 12. License summary (modules)

| Module    | Engine license           | Downstream obligation for apps |
|-----------|--------------------------|--------------------------------|
| html      | BSD-3 (litehtml), MIT (md4c) | notice file only |
| pdf       | BSD-3 + FTL etc.         | notice file only |
| charts    | Apache-2.0 (ours)        | none beyond core |
| plot      | Apache-2.0 (ours)        | none beyond core (colormap data public-domain) |
| code      | MIT (Tree-sitter + grammars) | notice file only |
| terminal  | MIT (libvterm)           | notice file only |
| vector    | MIT (ThorVG)             | notice file only |
| media     | LGPL-2.1+ (libVLC, dynamic) | LGPL notice + relinkability (dynamic linking satisfies); codec/patent review per app |
| camera    | zlib (SDL3, already shipped) | none new; OS permission prompt + macOS usage-description key |
| mic       | zlib (SDL3, already shipped) | none new; OS permission prompt + macOS usage-description key |
| web       | MPL-2.0                  | notice; publish modifications to engine files only |
| emoji     | CC BY-SA 4.0             | **visible attribution required** (about box / credits) |