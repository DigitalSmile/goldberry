# Goldberry — Architecture

**A fast and modern UI toolkit for Java.** Linux, Windows, and macOS from day 1.

Status: design document. Nothing here is stable.

**Which document wins.** `design-system.md` (the visual and interaction language)
and `core-widgets.md` (the per-widget contracts) are the **authority**; this
document is the architecture that serves them and a summary of what they specify.
Where the two disagreed, they have disagreed silently — §10.1 carried a typography
table that contradicted the design system's for months, down to a weight no
shipped font can draw. So this document now **references** the design system's
tables rather than copying them, and where it still states a number, it says which
section it mirrors.

`charts.md` is the fifth, and the narrowest: it is the chart widgets' contract —
the derived series palette (ADR-0194) and which of Grafana's features belong in a
desktop toolkit at all.

`content-widgets.md` is the fourth: it specifies the **optional content modules**
— HTML/markdown, PDF, plotting, code, terminal, vector, media, camera,
microphone — none of which is a core dependency and none of which is built. §11.1
is its summary, ADR-0190 is the shape they all share, and `TODO.md` records what
each is waiting on.

What is *built* is tracked in `book/src/status.md`, and what is deferred or
known-broken in `book/src/TODO.md`, not here. A line in this
document is a design, not a claim that it exists — the places where the
implementation has since answered a question, or refused one, are marked inline.

---

## 1. Positioning

Goldberry is a retained-mode, declarative desktop UI toolkit written in pure Java over a small set of native C libraries bound via the Foreign Function & Memory API (FFM). No JNI, no bundled web engine, no platform widget wrapping.

What "fast and modern" means concretely:

- **Startup in milliseconds, small memory footprint.** CPU rasterization, no GPU context for plain UI, GraalVM native-image as a first-class target.
- **Declarative.** Immutable widgets with pure `build()` (Flutter model), expressed as Java records or as KDL markup. Markup + stylesheets are hot-reloadable at runtime.
- **Real layout and real styling.** Flexbox via Yoga, a genuine CSS subset with variables, cascade, and transitions — not a proprietary styling DSL.
- **Cross-platform from the first commit.** Linux (Wayland/X11), Windows, and macOS are peer platforms behind one SPI.

Non-goals for v1: mobile/touch profiles, embedded HTML, RTL text layout, full IME (see §17).

## 2. Layer map

```
┌────────────────────────────────────────────────────────┐
│  Application (Java records / KDL documents / CSS)      │
├────────────────────────────────────────────────────────┤
│  Widget layer      immutable widgets → elements →      │
│                    render objects (three trees)        │
├──────────────┬──────────────┬──────────────────────────┤
│  Style       │  Layout      │  Text                    │
│  CSS engine  │  Yoga        │  HarfBuzz + JDK          │
│  (pure Java) │  (flexbox)   │  Bidi/BreakIterator      │
├──────────────┴──────────────┴──────────────────────────┤
│  Paint & raster    display lists, layers, damage,      │
│                    Blend2D (CPU, JIT, banded threads)  │
├────────────────────────────────────────────────────────┤
│  Backend SPI       window, present, input, clipboard,  │
│                    cursor, tray, popup, GpuSurface     │
├────────────────────────────┬───────────────────────────┤
│ SDL3                       │ Headless                  │
│ the desktop backend        │ CI / golden-image tests   │
│ Linux, Windows, macOS      │                           │
└────────────────────────────┴───────────────────────────┘
```

### 2.1 Package map

A package is named for the **part its contents play**, not for the library or
the file they came from (ADR-0172). Read down a module's package list and you
should be reading the pipeline above.

**`:core`** — 35 packages. The root `io.github.digitalsmile.goldberry` holds the
running shell and nothing else: `Goldberry`, `Application`, `Host`, `Launcher`,
`GoldberryRuntime`, `Window`, `Popup`, `Placement`, `Overlay`,
`ContextMenuHandler`. Those ten are one role — `Launcher` and `GoldberryRuntime`
make twenty-one calls into `Window`'s package-private event intake, and splitting
them would publish it (ADR-0172 records the count and the decision).

| group | packages |
| --- | --- |
| style | `css` (the sheet and the computed result) · `css.parse` · `css.select` · `css.cascade` · `css.value` |
| widgets | `widget` (the three trees and the renderer over them) · `widget.attr` · `widget.style` · `widget.root` |
| input | `input` (the router) · `input.event` · `input.key` · `input.hit` · `input.handler` |
| binding | `bind` (what a model declares) · `bind.registry` · `bind.runtime` |
| paint | `paint` (the frame, the layer and the box painter) · `paint.tree` (the retained render tree) · `stats` |
| backend | `render` (the SPI) · `render.model` · `render.event` · `render.window` · `render.popup` · `render.backend.sdl3` · `render.backend.headless` |
| text | `text` (paragraphs and lines) · `text.font` |
| the rest | `kdl` · `motion` · `icon` · `assets` · `reload` |

**`:common`** — one package, `io.github.digitalsmile.goldberry.log`, and the
lowest module in the graph. `Logs` and `Startup` are used by the FFM bindings and
by the widget catalog alike, and neither layer owns them; before ADR-0174 they
had to live inside `:natives`, because that is the lower of the two modules that
need them.

**`:natives`** — 15 packages, split where the foreign memory stops. Each
library's **wrappers that hold a handle** stay beside the binding class they are
the only callers of; the enums and values, which touch no foreign memory at all,
get packages of their own. So `blend2d` keeps `BlendContext` next to `Blend2D`,
and `blend2d.enums` holds the tables of C constants; `yoga` keeps `YogaNode` and
`MeasureCallback`, `yoga.style` holds the flexbox vocabulary and `yoga.measure`
the rest of the measure protocol; `sdl` keeps `SdlVideo`, `SdlWindowHandle` and
the event plumbing, with `sdl.event`, `sdl.window` and `sdl.desktop` beside it.
`natives` itself and `natives.layout` stay unexported, per §3.1.

**`:widgets`** — 39 packages, one per control since ADR-0091 and ADR-0065. The
module root holds the four pieces of furniture an application wires a window up
with (`Widgets`, `Controls`, `Icons`, `Density`); `widgets.markup` holds the
contract a widget *author* names — `@Markup`, `Inflatable`, `WidgetCatalog`,
`Wiring`.

Two rules keep this from decaying, and both are tests rather than prose:
`ExportedSurfaceTest` reads `:natives`' own descriptor and fails if anything
reachable from outside it mentions a `MemorySegment` (§3.1), and
`WrittenNamesTest` resolves every class name the weaver writes into bytecode as
text — the one kind of package reference no compiler checks.

## 3. Native core

| Library      | Role                          | Notes |
|--------------|-------------------------------|-------|
| Blend2D      | 2D vector rasterization       | JIT pipelines, banded multithreading, built-in PNG/JPEG/QOI codecs. Renders glyph runs directly from font outlines — no FreeType. |
| libwebp      | WebP decoding                 | The decoder half only (`webpdecoder`): no encoder, no muxer, no animation demuxer. Blend2D ships no WebP codec and a screenshot in 2026 is one (ADR-0329). |
| Yoga         | Flexbox layout                | Flexbox-only in v1. Layout engine hidden behind an internal interface (Taffy or grid support possible later). |
| HarfBuzz     | Text shaping                  | UTF-16 in (matches Java strings), glyph IDs + positions out. |
| SDL3 (≥3.2)  | Desktop backend               | Windowing, input, per-monitor DPI, clipboard, cursors, popup windows, tray icons, SDL_GPU. The permanent desktop windowing layer — Goldberry ships no hand-written platform backends. |

### 3.1 Binding rules (FFM)

- Bindings are **hand-written**, not generated. jextract was the original plan and was dropped (ADR-0006 superseded by ADR-0010): the surface actually needed is small, and a generated binding for one platform's headers is not a binding for another's. What replaces the guarantee jextract would have given is a **layout probe** — a table of struct layouts and constants checked against the compiled library at run time on every target, which is what catches `long` = 32-bit on Win64 and an enumerator that drifted (ADR-0029).
- **A bound function is a holder, and its handle is a constant.** Each of the 134 bound C functions has a small final class holding its address, with its unbound `MethodHandle` as a `private static final FD_<symbol>` and a `call` taking ordinary Java types; the holders of one library are grouped in a `…Calls` record, which is what a binding class keeps. This is not a style preference: a handle bound to an address cannot be a compile-time constant in a GraalVM native image, a handle in an *instance* field is not one either, and a `MethodHandle` that is not a constant is an interpreted lambda form rather than a call — a factor of 450 per crossing (ADR-0161, ADR-0173). The constant is therefore read by the method that invokes it, and the holders live in packages that contain nothing else, because `--initialize-at-build-time` can name a package but naming a holder's *enclosing class* does not reach it — measured at 4538 ns/call against 8.
- Raw `MemorySegment` never escapes the `io.github.digitalsmile.goldberry.natives` module (`native` is a reserved word in Java, hence `natives`). Every native object gets a thin Java wrapper with explicit ownership.
- **Arena discipline:** one shared `Arena` per window for long-lived objects (fonts, Yoga config); a confined per-frame arena for scratch (HarfBuzz buffers, glyph arrays, damage lists) — dies at frame end, zero GC pressure. Long-lived wrappers expose `close()`; a `Cleaner` is the safety net, never the mechanism.
- Upcalls (Yoga measure functions) use **one stub per measured node**, not one shared stub dispatching on a context pointer: a callback already belongs to exactly one node, and passing the node pointer on would put a raw `MemorySegment` in front of code outside the module (ADR-0017, ADR-0029). The returned `YGSize` segment is allocated once per callback rather than per call. Upcall cost is benchmarked in the M1 vertical slice.
- **Yoga's tree is owned, not merely referenced.** A root owns its subtree; inserting a child transfers ownership; closing a root frees the subtree child-first and marks every Java wrapper in it dead, so a stale reference throws instead of reading freed memory. Yoga's `abort()`-on-precondition checks are all reproduced in Java first (ADR-0029).

### 3.2 Native build & packaging

- A CMake superbuild — driven from Gradle by the `:natives` module — statically links Blend2D + Yoga + HarfBuzz + SDL3 into **one shared library `libgoldberry`** per platform. One artifact, one version matrix.
- Distribution follows the LWJGL pattern: classifier jars — `goldberry-natives-linux-x64`, `-linux-aarch64`, `-windows-x64`, `-macos-aarch64` — extracted or `System.load`-ed at startup. Four rows, not every OS × arch pair: Windows on ARM and macOS on Intel are not built (ADR-0041).
- CI: GitHub Actions matrix, every artifact built on a native runner — four runners, four artifacts, no cross-targeting. The Linux legs build inside a `manylinux_2_28` container, which pins the glibc floor at 2.28 (RHEL 8) — a stock runner would link against glibc 2.39 and refuse to load on older distributions.
- **Java 25 LTS is the floor** (FFM final, Vector API for the blur path). GraalVM native-image builds run in the same CI matrix.
- **The build's Linux desktop integrations are declared, and the library reports them.** SDL compiles its D-Bus, IBus and udev support in only where the development headers were installed on the build machine, and out — silently, into calls that succeed and answer nothing — where they were not. So the superbuild probes for them, stops rather than shipping a library that cannot ask the desktop anything, cross-checks its own answer against SDL's generated `SDL_build_config.h`, and compiles what it found into the binary: `Goldberry.capabilities()` is how an application tells *"this desktop has no such setting"* from *"this build cannot ask"* ([ADR-0325](../book/src/adr/0325-a-build-says-what-it-can-ask-the-desktop.md), `docs/gaps.md` G32).

## 4. Backend SPI

**A file dropped on a window is a gesture, not an event.** A desktop reports one as a
beginning, a moving position, one event per file and an end; the SPI keeps that shape
(`BackendEvent.FileDropped`, `FileDropCompleted`) and `Window` reassembles it into one
`input.drop.FileDrop` carrying every path **and the point they landed on**
([ADR-0330](../book/src/adr/0330-a-dropped-file-arrives-somewhere.md)). The position is
half the feature: a board needs to know *where* something was dropped, not merely that it
was. The assembly is above the SPI so it is written once and testable without a desktop;
what is left in the backend is one `switch` arm per event type.

**The clipboard has two halves.** Text is a value and is copied; everything else is a *transfer negotiation* — `has`/`read`/`write` over a MIME type, where a write advertises what this application can produce and the platform asks for the bytes when somebody pastes. That laziness is the protocol rather than SDL's choice, so it reaches the SPI: `Clipboard.write(Map)` offers several types in order, and the bytes stay in the toolkit's arena until the offer is replaced. What turns those bytes into an image is `image.Image`, not the SPI (ADR-0286).

**The desktop's appearance is a session fact with no window in it.** `systemTheme()` answers `Optional<SystemTheme>` — `LIGHT`, `DARK`, or empty where the platform has no such setting, which is a different answer from light and has to stay one: an application needs to tell a *setting* from a *default*. It is a `default` method returning empty, so a backend that cannot ask is correct without saying so, and the change arrives through the ordinary pump as one event per open window, the way `QUIT` becomes one `CloseRequested` per window — because a `Host` is per window and that is where an application listens. The toolkit chooses nothing with the answer: which theme to use, and whether to follow the desktop at all, are the application's ([ADR-0322](../book/src/adr/0322-the-desktop-says-light-or-dark-or-says-nothing.md)). Empty has one more source than it looks: a `libgoldberry` built without the D-Bus headers *cannot* ask, on any Linux desktop, and reports that separately through `Goldberry.capabilities()` rather than leaving an application to guess ([ADR-0325](../book/src/adr/0325-a-build-says-what-it-can-ask-the-desktop.md)).

The only platform-facing interface. Everything above it is platform-agnostic.

```java
public interface Backend extends AutoCloseable {
    BackendWindow createWindow(WindowSpec spec);
    Optional<BackendPopup> createPopup(BackendWindow owner, PopupSpec spec); // menus, tooltips — built (ADR-0102)
    Optional<TrayIcon> createTrayIcon(TraySpec spec);
    Clipboard clipboard();                    // text; built (ADR-0167)
    Optional<SystemTheme> systemTheme();      // light or dark, or the desktop does not say (ADR-0322)
    void pumpEvents(EventSink sink);          // blocks until events or frame callback
    void wakeup();                            // cross-thread event-loop wake
}

public interface BackendWindow extends AutoCloseable {
    void present(PixelBuffer frame, List<DamageRect> damage);  // CPU path
    Optional<GpuSurface> gpuSurface();        // GPU composition path — in the SPI from day 1
    float scale();                            // per-monitor, fractional (125%, 150% must work)
    void setCursor(Cursor shape);             // standard set; custom image still to come (§7.3)
    void setTitle(String title); void setDecorated(boolean serverSide);
    void setMinimumSize(LogicalSize minimum); // the floor a user may drag to (ADR-0304)
    void textInput(boolean active);           // off by default, follows focus (ADR-0167)
    void requestFrame();                      // vsync-aligned frame callback
}
```

Rules baked into the SPI:

- **macOS main thread.** `Goldberry.launch(app)` takes over the calling thread as the UI thread (AppKit requires the first thread). It does not spawn one.
- **HiDPI:** layout in logical px, raster at physical px, per window. Fractional scales are day-1 correct, not retrofitted.
- **Geometry is the user's, within a floor the application declares.** `WindowSpec.minimumSize` and `Window.minimumSize(...)` name the smallest a window may be dragged to and the *window manager* enforces it — the pointer stops at the edge, rather than a resize handler clamping a frame after it has been painted. No minimum by default: a toolkit does not know what a window holds (ADR-0304).
- **Decorations:** native/server-side by default on every platform (SDL negotiates Wayland SSD). Client-side "frost" decorations are an opt-in `titlebar` widget (`core-widgets.md` §8) using the `frost` material (`design-system.md` §1.5), same widget code on every backend.
- **Overlays, two places.** In-window overlays — a toast, a scrim, a `hud` — are a widget-tree facility and need nothing from here: they are children of the window's own root node, out of flow at a corner (ADR-0100). `createPopup` is for the ones that must escape the window's bounds — a `menu`, a `tooltip`, a `select`'s list — and **it is built**: a popup is a `BackendWindow` with an owner, a kind and a position in the owner's coordinates, on both backends. It returns an `Optional` because popup support belongs to the video driver, not to the request: all four desktop drivers have it and SDL's `dummy` does not, so the refusal is a branch CI runs (ADR-0102).
- **Backends shipped:** `sdl3` (the desktop backend on all three OSes), `headless` (renders to `BLImage` for golden-image tests). That is the complete list — no hand-written Win32/Cocoa/Wayland backends and no AWT bridge, ever. SDL3 is a permanent dependency on desktop; the SPI exists to serve `headless` and to keep the platform boundary in one place, not as an invitation to grow new desktop backends.

## 5. Rendering pipeline

Three trees, Flutter-style:

1. **Widgets** — immutable Java records, pure `build()`. Cheap to rebuild, diffed by type + key.
2. **Elements** — the mutable instantiation; holds state, owns lifecycle.
3. **Render objects** — one per visual node; owns a `YGNode` and the `Box` last applied to it, and is **retained across frames**. Reconciled against the per-frame box tree a widget describes rather than mutated by widgets, so the declarative contract is untouched; every Yoga setter is guarded by a comparison, because Yoga dirties a node when a style is *set* and not when it changes. Layout and the walk cost 7 µs against 190 µs for the tree that was rebuilt every frame (ADR-0069).

**Frame loop** (single UI thread; raster bands are Blend2D worker threads):

```
input events → dispatch (hit-test on render tree)
→ rebuild dirty widgets → diff → update elements/render objects
→ style resolution (invalidated nodes) → Yoga layout (incremental)
→ paint recording (dirty layers only) → Blend2D raster (banded)
→ present(buffer, damage) / GPU composite
```

- **The frame is a borrowed buffer.** When the platform lends its own surface, Blend2D's `BLImage` is a view over it — `bl_image_init_as_from_data` with no destroy callback — so rasterization happens directly in the memory that will be presented, and a frame costs no blit. The context is scaled by the display factor once per frame, which is what keeps painting in logical coordinates without rounding them: a fractional edge is antialiased across the physical pixels it covers rather than snapped to one. Colours are straight-alpha `0xAARRGGBB`; the buffer is premultiplied and Blend2D converts (ADR-0031).
- **Shapes are values, and a turned shape is one too.** `paint.Path` is an immutable outline and `Frame` takes it directly (ADR-0277); the geometry over it lives in `paint.geom` — `Flattener` and `Dasher` for a dashed stroke (ADR-0278), and `Transformer` for an affine map, reached as `Path.transformed(Affine)` with `rotated`, `translated` and `scaled` beside it. A transformed path is the answer for a painter that does not own the frame's matrix, because an arc keeps being an arc: its ellipse is decomposed and its sweep flag flips under a mirror, which is the arithmetic an application would otherwise write again. The frame's own matrix is the other half — `Frame.transform` states it and **`Frame.concat` multiplies by it**, so a `canvas` painter can turn what it draws without discarding the translation that put the canvas on screen ([ADR-0390](../book/src/adr/0390-a-turned-shape-is-a-path-and-the-frame-can-compose.md)).
- **Layers & damage.** Render objects marked as repaint boundaries cache their raster in a `Layer`. Promotion is `opacity < 1` with children today — which is where CSS group opacity and a per-box alpha multiply disagree, and is what makes `opacity` mean what CSS means (ADR-0071, closing ADR-0064's open question). A promoted subtree is rasterized at full strength and *untransformed*, so the alpha and the transform are applied to the blit and a subtree that did not change is a blit and nothing else. Damage rects flow to the backend: each render object remembers where it was, and a node that changed damages the union of where it was and where it is. **The frame is painted only inside the damage**, where the backend promises the buffer it lends back holds last frame's pixels — `BackendWindow.retainsFrameContents()`, false by default so a backend that says nothing gets a full repaint (ADR-0072). A clipped repaint is asserted pixel-identical to a full one. Scroll containers, the frost material and the 3D canvas are not promoted yet.
- **A rebuild is not a restyle.** `Element.update` used to throw a node's whole subtree's cached styles away on every re-description, on the grounds that a rebuild was already the expensive path. It is not, once anything cascades down it: a `scroll` moving by one notch re-describes **two** nodes and was invalidating everything under them. Three guards now ([ADR-0315](../book/src/adr/0315-a-rebuild-is-not-a-restyle.md)) — an **identical** widget is not a description at all and the walk stops; a re-description that leaves `type`, `id` and `classes` alone cannot change what a selector matches anywhere below it, so the subtree keeps its styles; and what is left is `Styled.restyle`, which two widgets in the catalog override, asked once per class through a `ClassValue`. A wheel notch on the icon sheet went from re-resolving **1556** elements to **4**, and from 66.6 ms of cascade to 4.2.
- **Culling.** The walk no longer visits every box. Each render object carries the `Ink` its **subtree** draws — its border box grown by the focus ring, the drop shadow's four asymmetric outsets and an icon larger than the slot it is centred in, unioned over its children and through their transforms — measured once per layout pass by `RenderObject.settle`. A subtree whose ink cannot overlap the clip in force is not drawn and not walked ([ADR-0313](../book/src/adr/0313-a-frame-pays-for-what-is-on-screen.md)). The **subtree's** ink rather than the box's own, because flexbox lets a child overflow its parent and a `transform` moves one out from under it. This is what makes a viewport cost what is **on screen** rather than what is in it: the showcase's icon sheet is 1544 tiles of which forty are visible, and its settled frame went from 22.2 ms to 9.0 ms, the raster from 18.0 to 4.4. Scrolling re-measures nothing, because a viewport moves by a `transform` on one box and the thousand under it are skipped at the first rectangle that held. `RenderTree.boxesPainted()`/`boxesCulled()` expose the outcome, for `layersRepainted`'s reason: a culler that quietly stopped working draws the same frame four times as slowly.
- **Images.** Encoded bytes become an `image.Image` — PNG, JPEG, QOI, WebP and GIF; the first three through the codecs compiled into Blend2D, the last two through the two decoders `docs/gaps.md` G35a added ([ADR-0329](../book/src/adr/0329-two-more-codecs-one-fetched-and-one-written.md)): **libwebp**, fetched by the superbuild and bound with no C glue, and a **GIF decoder this toolkit wrote**, in `:core` beside the PNG encoder. The split is the point — VP8 is a video codec and there is no Java answer worth writing; GIF is a palette, a few block headers and LZW, and a second native dependency for that costs more than owning it. The format comes from the bytes (`image.ImageFormat`), never from a name — and a `Frame` draws one with a crop, a destination rectangle and an alpha. An image is a **value**: the decoder is the one thing allowed to allocate pixels the toolkit did not, because the size of a PNG is inside the PNG, and its allocation is copied into a Java-owned `PixelBuffer` and destroyed inside `decode`. So there is nothing to close and an image can live in a field for the life of an application. Drawing one at its *natural* size is one image pixel per **device** pixel, which is the same reconciliation of physical raster with logical coordinates that a `Layer` needs (ADR-0157). Writing a PNG back out is `java.base`'s `Deflater` in `image.png`, not a native call (ADR-0283).
- **Rendering with no window.** `offscreen.Offscreen` takes a painter or a widget tree and returns an `image.Image` — the frame loop above, minus the window: no backend, no SDL, no compositor, because `Frame` paints into memory. The widget form runs three passes (two measuring and drawing nothing, then the one that is painted), because a widget is told what size it came out as *after* a frame is laid out and may rebuild in response; and its clock is virtual, so the same scene is the same bytes twice. The golden-image harness is a consumer of it, which is what keeps it in step with `Launcher`'s copy of the same sequence (ADR-0284).
- **Blur/frost.** Blend2D has no filter effects. Gaussian-approximate blur = 3-pass separable box blur implemented in Java with the **Vector API**, applied to downscaled layer copies for the frost material; drop shadows are nine-slice cached blurred rects. Frost automatically falls back to opaque per the design system when the backdrop is unavailable.
- **Animation.** A frame `Clock` (system, or virtual so a golden image can snapshot a mid-animation frame); CSS transitions over a **closed** whitelist — `opacity`, `background-color`, `border-color`, `color`, `transform` — resolved by the cascade like any other property. Animated values live in a per-node overlay applied at paint and are never written back into computed style, so recomputation and animation cannot fight; retargeting starts from the current animated value. A declaration naming a layout property is refused with a warning, not ignored. Colours interpolate in OKLCH. `prefers-reduced-motion` collapses every duration to zero and keeps the declarations, so the same states are reached by the same route. A `transform` interpolates function by function rather than through its matrix, because the midpoint of two matrices a half-turn apart is a collapsed box (ADR-0068). **Layer promotion**, the overlay enter/exit lifecycle and the imperative `AnimationController` are **not** implemented — see ADR-0067.

## 6. Text stack

- One font buffer feeds both `hb_face_t` and `BLFontFace` — metrics cannot disagree.
- Pipeline: segment (script/direction via `java.text.Bidi`; line-break candidates via `BreakIterator` — both JDK built-ins, no ICU4C) → `hb_shape` per run → `BLGlyphRun` → `blContextFillGlyphRun`.
- **Paragraph cache** keyed by (text, resolved text style, width bucket). Yoga measure callbacks hit this cache; it is the hot path.
- **A paragraph is a label, and `text.document` is a document.** `Paragraph` shapes a whole string at once and keeps two prefix sums over it, an `int` per character each — right for a label, and wrong for half a megabyte somebody is typing into, because every keystroke makes a different string and pays all of it again. `TextDocument` shapes one *hard line* at a time, compares a new text against the last from both ends, and re-shapes only the lines an edit touched; `DocumentLines` is that document wrapped at one width, as a computed `List<TextLine>` in the whole text's offsets. Wrapping was already per hard line, so nothing is lost. `text-area` is its one caller today ([ADR-0388](../book/src/adr/0388-a-note-is-shaped-a-line-at-a-time.md)).

**Editing** is `text.edit`, beside the shaping rather than inside a control: `TextEdit` (a string, a caret and an anchor, every movement a pure function), `EditHistory` (undo, with a typing run folded into one step), `TextGeometry` (where a caret is on a *wrapped* paragraph, what `Up` means when lines differ in length, what shape a selection is across a break) and `Editor`, which is the three of them wired to a key map an application can drive from a `canvas`. The widget catalogue's `text-input` is a control *around* this, not the owner of it (ADR-0285). IME preedit is not bound yet — committed text is.

### 6.1 Fonts

- **Embedded by default:** Inter (UI) + JetBrains Mono (code) ship inside the jar. Deterministic rendering on every machine; the design system's metrics are authored against them. Inter ships as **four faces** — 400 and 600, upright and italic — because a weight is a face here (ADR-0066) and so is an italic: Inter's italic is a different *drawing*, not a slant, and the alternative without a file is shearing the upright glyphs, which is a type-design decision rather than a workaround ([ADR-0323](../book/src/adr/0323-an-italic-is-a-face-and-the-matrix-closes.md)). Two weights × two styles is a matrix with no wrong corner: `font-weight: 600; font-style: italic` resolves to a face rather than to the nearest of three. `font-style: oblique` is refused, for the same reason the shear is.
- **`Fonts` is the book that joins a resolved style to a `Font`**, caching faces by family+weight+style and fonts by (face, size). Owned and closed by the application, never global: these are thread-confined and hold native memory, and a process-wide cache of them would have no hook that ever frees it (ADR-0044, ADR-0066).

```java
try (var fonts = Fonts.bundled()) {
    var renderer = new WidgetRenderer(stylesheets, fonts);
}
```

- **System fonts are a planned opt-in and are not implemented.** The design was a `FontSource.system("Segoe UI", "SF Pro")` resolved through the backend via fontconfig / DirectWrite / CoreText, plus `FontSource.file(…)`; `Fonts.bundled()` is the only source that exists. Switching to system fonts would void pixel-exact design-system metrics — documented and intentional — which is why the bundled path is the default and was built first.
- **Fallback chain is exactly two slots:** primary family → emoji font, and **both of them are reached**. Text Unicode draws as a picture is split out during itemization and shaped in the emoji face; everything else stays in the family the cascade chose ([ADR-0393](../book/src/adr/0393-an-emoji-is-routed-by-the-text-and-drawn-in-layers.md)). There is still no general fallback cascade: the split is by emoji *presentation*, not by script, so a Han character in a Latin face is still `.notdef` deliberately. `font-family: Inter, sans-serif` therefore takes the first name and discards the rest, rather than pretending to a mechanism that does not exist.
- **A weight is a face, not an axis.** `design-system.md` §1.4 ships two weights, 400 and 600, so Inter's SemiBold static instance is bundled beside the variable file. Instancing `wght` at runtime needs symbols in both HarfBuzz and Blend2D and therefore three new export branches — the machinery that has caught the same local-symbol bug three times. A CSS weight no file provides resolves to the nearer one that does, the way CSS's own font matching works (ADR-0066).

### 6.2 Emoji

- Emoji font: **OpenMoji**, the **COLRv0 colour build**. It was the monochrome build until the text stack could draw layered outlines; it can now, so an emoji is a picture rather than a silhouette ([ADR-0393](../book/src/adr/0393-an-emoji-is-routed-by-the-text-and-drawn-in-layers.md)). 2.5 MB against 1.4 MB, paid only by an application that adds the artifact. The palette is OpenMoji's own and is not re-themed: a derivative would carry a statement of changes under the licence, for a gain nobody has asked for.
- COLRv0 is layered outlines plus a CPAL palette — `text.font.sfnt.ColorLayers` reads both tables in Java, `GlyphFace` reads them once per typeface, and `GlyphPen` draws each layer glyph with its palette colour. A layer is an ordinary glyph in the same face, so nothing new rasterizes anything. No bitmap emoji formats (CBDT/sbix): they are 6 MB of fixed-resolution strikes that blur at 150%.
- Segmentation: emoji sequences (ZWJ, VS-16, modifiers) are detected during itemization by `text.itemize.Itemizer` and routed to the emoji slot. The rules are UTS #51's, read out of `java.lang.Character`, so they move when the JDK's Unicode version moves.
- OpenMoji is CC BY-SA, and **it is not in `goldberry-core`**: the face ships as `goldberry-emoji`, an optional artifact an application adds on purpose, because CC BY-SA asks for attribution where the work is *seen* and no file inside a jar gives that. `:core` names the slot and loads the face through an `EmojiFont` service; with no provider, `Font.bundled(EMOJI, …)` fails with a sentence naming the artifact ([ADR-0384](../book/src/adr/0384-the-emoji-face-is-an-artifact-an-application-opts-into.md)). What ships is the unmodified COLRv0 colour build; a re-themed derivative would be published with attribution and a statement of changes, per the licence.

### 6.3 Icons

- Icon set: **Lucide** (ISC license), compiled at build time from SVG into a compact binary path table (Lucide is uniform 24×24 stroke paths). Rendered as `BLPath` strokes — crisp at any scale, tinted by the CSS `color` property like text.
- **An icon is a `Box`, and markup *names* one rather than building it.** An `Icon` owns native memory and must be closed exactly once, while a widget is a value rebuilt every frame — so a document reloaded on every keystroke would leak one per reload. Markup therefore resolves `icon="plus"` against an `Icons` registry the application owns, the same indirection `action` and `bind` use (ADR-0043, ADR-0059). An icon is built at a size and that size *is* its intrinsic size, so unlike text it needs no measure function.
- The standalone `icon` widget of `core-widgets.md` §1 — `icon name="check"`, sized in `em` — is **not implemented**; what ships is the registry and `button icon="…"`.
- Apps can register custom icon packs (same SVG-path pipeline) without forking the toolkit.

## 7. Input

### 7.1 Events

Backend-neutral event types; backends translate.

- **Pointer:** enter/leave/move/down/up/wheel, with button, modifiers, click count, logical position; hit-testing against the render tree respects clips and transforms. Pointer capture on drag.
- **Keyboard:** `KeyEvent{ key, physicalCode, modifiers, repeat }` — separated from **text input**: `TextEvent{ committedText }`. The translation is SDL's on all three platforms: it uses libxkbcommon on Linux (`xkb_state` + `xkb_compose` for dead keys) and the platform's own on Windows/macOS, and delivers the result as `SDL_EVENT_TEXT_INPUT`. Goldberry binds no xkbcommon of its own (ADR-0055). This split keeps the door open for IME preedit later.
- **Wheel/scroll:** deltas in **lines**, fractional, positive down and right. SDL exposes no pixel-precise axis, so the original "pixel-precise with a line-based fallback" is not reachable through it; the fractions a touchpad sends are what precision there is. The "natural scrolling" inversion and SDL's away-from-the-user sign are both undone at the boundary, so a widget never sees them (ADR-0056).
- Dispatch: capture → target → bubble, `consume()` stops propagation. Synthetic events (`:hover` enter/leave) derive from pointer flow.
- **Pointer capture:** a press takes it implicitly and the release gives it back, so a drag that leaves a widget still reaches it; `capturePointer` takes one that outlives the release. `:hover` keeps following the pointer regardless — capture decides who is told, not what is highlighted (ADR-0058).

### 7.2 Focus

- One focus owner per window; focus travels by pointer press and `Tab`/`Shift+Tab` traversal (document order, `focusable`/`tab-index` overridable). **Arrow-key group navigation inside composites** — radio groups, menus, lists (`design-system.md` §2.2) — is specified and **not implemented**; it arrives with `radio-group`, which is the first composite that needs it.
- `:focus` and `:focus-visible` are distinct — the **focus ring** (2 px, `--gb-focus`, offset 2 px, following the control's radius) renders only for keyboard focus, per `design-system.md` §2.2. It is one `outline` rule over a type list in the toolkit-base layer rather than one rule per control, because "the ring is the same everywhere" is the actual requirement and a copy is how it stops being true (ADR-0064).
- Keyboard shortcuts: a per-window accelerator map (`router.shortcut("Ctrl+S", action)`), fired **after** the focused chain declines the key so a text field keeps its own `Ctrl+A`; menu items will declare accelerators and register automatically.
- **Open, and a live conflict with the design system.** `design-system.md` §2.3 says accelerators use the *platform primary modifier* — `Cmd` on macOS, `Ctrl` elsewhere — "via one `Shortcut` abstraction". The shipped `Shortcut` deliberately does **not** remap: its javadoc argues that silently translating them would make `Ctrl+C` mean two different things depending on where it ran. Both positions are defensible and they are not compatible; the design system is the authority, so this is the code that has to move or the design that has to be amended on the record. See §17.

### 7.3 Cursor

Standard shape set (`default, pointer, text, move, ew/ns/nesw/nwse-resize, wait, progress, crosshair, not-allowed, grab/grabbing`) mapped to native cursors by the backend; custom image cursors still to come, and `grab`/`grabbing` fall back to `move` until they arrive because no platform has a system cursor for them. Widgets set the cursor via CSS (`cursor: pointer`) or code. The shape is carried on the **painted box** and read off the rectangle under the pointer, so inheritance is the stack of rectangles rather than the element tree, and it freezes during a drag (ADR-0057).

## 8. CSS subset

Pure-Java tokenizer/parser (css-syntax-compatible). No native code.

- **Selectors:** type, `.class`, `#id`, descendant, child (`>`), and pseudo-classes `:hover :active :focus :focus-visible :disabled :checked :indeterminate :invalid` — the last added with §4's `field`, which is the one addition `core-widgets.md` §1 asks for by name (ADR-0169). Standard specificity. Nothing else in v1 — notably no `:not()`, which is why a disabled control is kept from lighting up in the router rather than in a stylesheet (ADR-0064).
- **Cascade layers (fixed):** toolkit base → theme → application → inline. Later layer wins at equal specificity. The `inline` layer has no `style=` attribute behind it and is instead reached by `Styled.restyle`, which lets a widget write the values a selector cannot express — a segmented control's indicator is `1/n` wide and `k` cells along, and nothing in CSS can count `n`. Applied after the cascade and *before* the frame's animations observe the style, so what a widget writes transitions like anything else ([ADR-0099](../book/src/adr/0099-an-indicator-travels-on-a-grid.md)).
- **Custom properties + `var()`** are the theming mechanism (prefix `--gb-*`). `calc()` deferred.
- **Property split** (already a design invariant):
    - *Layout properties* compile directly to Yoga: `display:flex, flex-direction, flex-wrap, flex-grow/shrink/basis, justify-content, align-items/self/content, gap, padding, margin, width/height/min/max, position: relative|absolute, inset, aspect-ratio, overflow`. `flex-wrap` arrived last and had exactly one consumer waiting — `select multiple`'s chips, which shrank rather than wrapping — and the half nobody predicted was *where* it goes: on the field it wraps the chevron under the chips, so the chips needed a box of their own ([ADR-0192](../book/src/adr/0192-a-row-of-chips-wraps-and-the-chevron-does-not.md)). `align-content` is still absent, and matters only to a wrapped row in a box with a fixed height. `align-self` was listed here while only `align-items` resolved, which a tab strip's `+` found — a child shorter than its row sat at the top of it with no per-child way to say otherwise ([ADR-0244](../book/src/adr/0244-a-child-may-say-where-it-sits.md)). `flex-basis` and `aspect-ratio` are the ones still unimplemented; `flex-shrink` arrived late and its absence had made every fixed metric in §3 negotiable ([ADR-0076](../book/src/adr/0076-a-glyph-does-not-negotiate.md)), and `position` / `inset` arrived with the first box that had to sit *over* its siblings rather than beside them ([ADR-0099](../book/src/adr/0099-an-indicator-travels-on-a-grid.md)). **`margin` arrived last of them** and its binding had been in place since ADR-0029; what it adds that nothing else in the subset could say is `auto` — `margin: 0 auto` centres a box on the **main** axis, where `align-self` only ever reached the cross one and `justify-content` is the container's decision about every child at once ([ADR-0311](../book/src/adr/0311-margin-is-room-outside-and-auto-is-the-half-that-mattered.md)). That record also carries two older defects it turned up: `padding: auto` threw inside the layout pass rather than being dropped, and the cascade returned its winners in hash order, so a shorthand and a longhand over one value were applied in whichever order their property names' buckets fell — `inset: 8px; left: 20px` lost its `left`.
    - *Paint properties* resolve into an immutable `ComputedStyle`: `background, color, border(+radius, per-side), opacity, box-shadow, backdrop-filter: blur() (frost), font-family/size/weight, line-height, letter-spacing, text-align, cursor, transition, transform (translate/scale/rotate), outline (focus ring)`.
    - Of that list, `ComputedStyle` resolves **`background`, `color`, `opacity`, `border-radius` / `border` / `outline` (CSS's 1-4 corner shorthand over four circular radii; `border` and `outline` are one width and one colour, not per-side), `box-shadow` (one shadow, not a list), `font-family` / `font-size` / `font-weight` / **`font-style`** / `line-height`, `cursor`, `transition` and `transform` / `transform-origin` (the 2D functions)** — plus, from the layout half, `position` and `inset`. `backdrop-filter` and `letter-spacing` are absent, because `Box` cannot express them and a property that resolves into nothing is a property with no test that means anything. **`box-shadow` was on that list too and its reason had expired**: it needed no `Box` field — it is a component of `Decoration`, beside the radius its geometry is derived from — and "nothing paints outside a box's own rectangle" stopped being true the day the focus ring did. What was left was the drawing, and a rasterizer with no blur can still fade an edge out of the primitive it is fastest at: a stack of nested rounded-rectangle fills, one band per logical pixel, with the band alphas solved for the fact that nested fills composite ([ADR-0310](../book/src/adr/0310-a-shadow-is-a-stack-of-rectangles.md)). Each theme ships `--gb-elevation-1/-2/-3` as whole shadow values, because the alpha that reads as a shadow on nord-light is invisible on nord-dark and an application should not be choosing it. **`text-align` was on that list and did not belong on it**: nothing about it needs `Box` at all, because `Paragraph.paint` is already handed the box's width and every line has already measured itself — the two numbers an alignment needs were in the same method the whole time, and what was missing was a keyword saying what to do with them ([ADR-0256](../book/src/adr/0256-a-line-is-placed-by-the-paint-not-by-the-box.md)). `start | center | end` resolve; `left` and `right` are refused for ADR-0247's reason, and `justify` because a paragraph shaped once has nowhere to put the extra advance. Each arrives with the thing that paints it. `transform` is the one whose computed value is **not** finished by the cascade: percentages in it are proportions of a box that has no size until Yoga has run, so what is carried is the function list and the painter resolves it (ADR-0068).
    - *Text-flow properties* are a **third** group the split did not anticipate, the way `cursor` was: `white-space: normal|nowrap`, `text-overflow: clip|ellipsis` and `text-align: start|center|end` compile to neither engine on their own — they modify the *paragraph*, which is read by Yoga through the measure function and by the painter through the draw. `white-space` is the mechanism and `text-overflow` is the marking: under `nowrap` a paragraph reports the width it wants rather than the width it was offered, so a box may finally be laid out narrower than its own content, which is the state `overflow: hidden` and an ellipsis were always waiting for and which the toolkit could not previously reach. Four widgets had been overflowing their cells for want of it — a clamped menu row, an `option`, a `select-value` and an autocomplete suggestion — and three attempts at clipping instead are recorded in [ADR-0235](../book/src/adr/0235-a-cut-label-needs-nowrap-not-text-overflow.md), which diagnosed it, and [ADR-0255](../book/src/adr/0255-a-label-that-does-not-fit-is-cut-not-wrapped.md), which built it. `text-overflow` is read **only** at paint time: an ellipsised line is drawn short and measured long, or the mark would decide the width that caused it. CSS's other three `white-space` values are statements about collapsing whitespace, which this toolkit never does, so `pre-wrap` is what `normal` already means here and `pre` is what `nowrap` already means. `text-align` is the same group's answer to the opposite question — where a line **narrower** than its box sits in it — and it arrived the same way, as a keyword over numbers the paint already had ([ADR-0256](../book/src/adr/0256-a-line-is-placed-by-the-paint-not-by-the-box.md)); `slider-value` is its consumer, a readout in a `width: 40px` box that had been left-aligned since the control shipped. **`text-decoration-line: none | underline | line-through`** is the fourth of the group and the first that is a *mark on* the glyphs rather than a placement of them: where a rule sits and how thick it is come from the face's own `underlinePosition`/`underlineThickness`, which nothing above `:core` can reach, so a painter outside the toolkit would be wrong at every size and every family ([ADR-0321](../book/src/adr/0321-a-rule-under-text-belongs-to-the-face.md)). It is a **set** — CSS allows both rules at once — and the shorthand's colour and style (`wavy`, `dotted`) are refused rather than half-applied, because a rule that asked for a wavy red underline and got a straight one in the text's colour is a property that lies. Four of `BLFontMetrics`' sixteen floats crossed the boundary for it and no new native symbol did.
- **Units:** logical `px` (scaled per window), `%`, `em`, `rem`. Window-scoped media queries: `@media (min-width)`, `(prefers-color-scheme)`, `(prefers-reduced-motion)` — **media queries are not implemented**; a theme is chosen by swapping a stylesheet, and reduced motion is a switch on the renderer (§5). `em` and `rem` resolve against a fixed context rather than the node's own resolved `font-size`, which is wrong and has no effect today because no shipped stylesheet uses them (ADR-0066).
- **`transition`** is a property like any other: resolved by the cascade, so `button` and `button:hover` can declare different ones and an application can turn one off by overriding a rule. It does **not** inherit — a panel that faded its background must not make every label inside it fade too (ADR-0067).
- **At-rules:** `@media` is parsed and not evaluated (above). **`@starting-style`** (the block form) holds rules that describe the style an element transitions *from* on its first styled frame; they never join the style an element has ([ADR-0352](../book/src/adr/0352-an-element-enters-from-its-starting-style.md)). **`@keyframes`** names a sequence that `animation` and its seven longhands run, as a second layer of the animation overlay beneath transitions and under the same whitelist; a later block with a name replaces an earlier one whole, and reduced motion drops them ([ADR-0353](../book/src/adr/0353-a-stylesheet-may-name-keyframes.md)). Every other at-rule is refused.
- **Resolution is invalidation-driven**, which is what the frame loop above has always claimed: a node's `ComputedStyle` is cached on its element and re-resolved only when the resolver changes (a theme swap or a hot reload builds a new one), when the style it inherited changes, or when its subtree is invalidated by a pseudo-class or a rebuild. Invalidation is a **subtree** because a descendant combinator makes a node's match depend on an ancestor's state. This took the cascade from 135 µs to about 2.5 µs on a 15-element tree (ADR-0070).
- **Inheritance** is CSS's: `color`, the font properties — the family, the size, the weight, the **style** and the line height, which travel as one `Typography` because they are only ever read together — `white-space`, `text-align` and `text-decoration` pass down the element tree — the last of those is CSS's *propagation to in-flow descendants* rather than inheritance strictly, and reads as inheritance here for `text-align`'s reason: a control's text is usually an anonymous child box, so a rule that stopped at the node it was written on would decorate nothing ([ADR-0321](../book/src/adr/0321-a-rule-under-text-belongs-to-the-face.md)); the layout half, `background`, `opacity`, `transform`, `text-overflow` and the decoration do not — and the `white-space` / `text-overflow` split is CSS's own, which is the only reason those two are separate components of `ComputedStyle` rather than one value ([ADR-0255](../book/src/adr/0255-a-label-that-does-not-fit-is-cut-not-wrapped.md)) — though `opacity` and `transform` both have an *effect* that reaches the whole subtree, which the painter accumulates rather than the cascade. `cursor` is the one exception to CSS — it inherits through the stack of *painted rectangles* instead, so hit testing reads it off whatever the pointer is over (ADR-0057, ADR-0066).
- **Invalidation** is coarse in v1: pseudo-class or class change recomputes the subtree.
- Stylesheets are runtime-loadable → hot reload with preserved state.

## 9. KDL markup

KDL 2.0. The markup schema is the **stable contract**; the Java builder API is generated to stay in lockstep.

Mapping rules: node name = widget type; string arguments = primary content; properties = attributes; children = children; `class`/`id`/`style` behave as in HTML.

```kdl
window title="Settings" width=720 height=480 {
  menubar {
    menu "File" {
      item "Save" icon="save" accel="Ctrl+S" action="save"
      separator
      item "Quit" accel="Ctrl+Q" action="quit"
    }
  }
  row class="root" {
    column class="sidebar" {
      button id="apply" icon="check" "Apply"
      checkbox id="frost" bind="prefs.frost" "Enable frost"
      progress id="scan" max=100
    }
    scroll {
      form {
        field label="Name"  { text-input id="name" placeholder="…" }
        field label="Theme" { select id="theme" options="light;dark;system" }
      }
    }
  }
}
```

- **Inflater:** a runtime registry `widget name → factory`. Built-ins and app widgets register identically; unknown nodes are hard errors with source positions.
- **Wiring:** `id` lookup + `action` names bound against a controller object explicitly (`Kdl.inflate(doc).bind(controller)`); no reflective `#handler` magic.
- **`bind`** is **one-way** binding against an observable model: data flows down into the tree, and what the user did flows back up as an `action`. Markup is handed the read-only `Observable` half, so a control cannot write to the model — `checkbox bind="prefs.frost" change="toggleFrost"` (ADR-0063, amending this bullet's original "one/two-way"). A `bind` value is a **dotted path and nothing else** — `frost`, `prefs.frost` — resolved against a `Bindings` registry, the same way `action` is resolved against a controller (ADR-0062). What a path names is a **plain field** of a `@Model` class, so `gain++` moves a slider and a model mentions no container at all. A jar binds those fields reflectively and a **native image** has them woven — the build rewrites the assignments into stores that notify — and the two publish the same registries either way (ADR-0125, ADR-0155). The built-in `Property<T>` remains for a value with no class to live in, and the registry holds either.
- **Parity invariant (enforced by test):** every built-in widget is constructible in all three forms — Java builder, KDL, and styleable via CSS. A widget that can't is a build failure.

## 10. Theming — Nord by default

Two theme files ship: `nord-light` (default) and `nord-dark`, both derived from the [Nord palette](https://www.nordtheme.com). Themes are just CSS custom-property layers; switching is one stylesheet swap and honors `@media (prefers-color-scheme)` for `system` mode.

Raw palette tokens (theme-invariant): `--nord0…--nord15`.

Semantic tokens (what widgets actually consume):

| Token             | Light                | Dark                 |
|-------------------|----------------------|----------------------|
| `--gb-bg`         | nord6 `#ECEFF4`      | nord0 `#2E3440`      |
| `--gb-surface`    | `#FFFFFF`            | nord1 `#3B4252`      |
| `--gb-surface-2`  | nord5 `#E5E9F0`      | nord2 `#434C5E`      |
| `--gb-text`       | nord0 `#2E3440`      | nord6 `#ECEFF4`      |
| `--gb-text-muted` | nord3 `#4C566A`      | nord4 `#D8DEE9`      |
| `--gb-border`     | nord4 `#D8DEE9`      | nord3 `#4C566A`      |
| `--gb-accent`     | nord10 `#5E81AC`     | nord8 `#88C0D0`      |
| `--gb-focus`      | nord8 `#88C0D0`      | nord8 `#88C0D0`      |
| `--gb-danger`     | nord11 `#BF616A`     | nord11               |
| `--gb-warning`    | nord13 `#EBCB8B`     | nord13               |
| `--gb-success`    | nord14 `#A3BE8C`     | nord14               |
| `--gb-info`       | nord9 `#81A1C1`      | nord9                |
| `--gb-selection`  | nord8 @ 30%          | nord10 @ 40%         |

Contrast for text-on-semantic-color pairs is validated in CI against WCAG AA — **specified, not implemented**: no contrast check runs yet (`design-system.md` §1.2, §4).

**Three tiers of token, not two.** Above the raw palette and the semantic aliases sit **component tokens** — `--gb-button-bg`, `--gb-checkbox-mark-checked` and their kin. `design-system.md` §3 puts the rule plainly: "app stylesheets may override component tokens, never structure". A control's *metrics* live in the theme-invariant toolkit-base layer; its *colours* live in the theme, because only a theme knows what a hover looks like on its own background — a hover lightens on Nord dark and darkens on Nord light, which is the whole reason there are two files and not one shared rule.

**Motion tokens** (`--gb-motion-fast` / `-base` / `-overlay`) and the **typography tokens** below are in the theme layer for the same reason, though neither varies between the two shipped themes: it is what keeps a reduced-motion or large-text theme an alias swap rather than a code path (§4 of the design system asks for exactly that shape from the high-contrast theme).

**Two design-system sections have no counterpart here and are not duplicated on purpose:** §1.3's 4 px spacing ramp and `--gb-density`, and §1.5's radii, elevation levels and the three materials. `--gb-density` **is implemented**: every control sizes itself from `--gb-control-height`, and `Density.COMPACT` is a three-token stylesheet in the theme layer — the `regular` column lives in `controls.css` and `Density.REGULAR` ships no stylesheet, because a default is the absence of an override ([ADR-0074](../book/src/adr/0074-density-is-a-token-swap-and-regular-is-no-stylesheet.md)). §1.3's "hit targets ≥ 32×32" is therefore the **regular** default rather than an invariant: a compact control is 28 tall by specification, and the glyph inside it stays 16 so the trade is margin rather than target. The radii are implemented as values in `controls.css`; elevation and the `frost`/`veil` materials are not (§5 describes the blur path that would serve them).

### 10.1 Typography tokens

**The table lives in `design-system.md` §1.4 and is deliberately not copied here.**

It used to be, and the two drifted: this section said body 14/20 where §1.4 says
13/18, heading 16/24 against 15/20, code 13/20 against 13/18 — and it carried a
`label` token at **weight 500**, which §1.4 does not have and which no shipped
face can draw, because the system ships two weights. Nobody noticed until a
control was built to the numbers. Duplicating a table is how a design system
acquires two answers, so what remains here is only how the one answer is spelled
in CSS (ADR-0066).

- **Sizes and line heights** are `--gb-font-*` and `--gb-line-*`, in the **theme**
  layer — so a large-text theme moves all of them at once without touching a rule.
- **The two weights** are `--gb-weight-regular` (400) and `--gb-weight-strong`
  (600). A weight is a *face*, not a variable-font axis; see §6.1 — and so is an
  italic, which is why `font-style: italic` has two more files behind it and no
  token: a slant is not a scale.
- **The mapping** is classes in the toolkit-base layer: `.display`, `.title`,
  `.heading`, `.body`, `.body-strong`, `.caption`, `.mono`. The mapping is
  theme-invariant — a heading is `heading` in every theme — while the numbers
  behind it are the theme's.
- `text style="title"` (`core-widgets.md` §2) and `Text.title("…")` are **not
  implemented**: what ships is `class="title"`, which is the same thing spelled
  the way CSS already spells it. Whether a second spelling earns its keep is a
  question for when `field` and `form` need labels.

## 11. Widget catalog

Every widget: Java record + KDL node + CSS-styleable, per the parity invariant. `core-widgets.md` is the per-widget contract — behaviour, states, keyboard, semantics — and this is the inventory and where each lives.

**Module split, and a disagreement with `core-widgets.md`.** That document opens by saying every built-in lives in "the single `goldberry-core` Gradle module — separated by *package*, not by artifact". What is built, and what this section describes, is a **two-module split**: primitives in `goldberry-core`, controls and containers in `goldberry-widgets` (ADR-0014). The split is load-bearing rather than incidental — `:widgets` is what makes the parity test and the golden-image corpus a thing `:core` does not carry — so the code is not moving to match the sentence. `core-widgets.md`'s *package* names still describe the intended internal grouping. Recorded in §17 rather than silently reconciled, because the design documents are the authority and this is the one place the architecture knowingly departs from one.

Grouped by module:

**Parts are the one exception.** A control with two surfaces a theme must style differently — `checkbox`'s 32px hit target and its 16px glyph — gives the inner surface a cascade node of its own, because one `ComputedStyle` carries one background and one radius. A part (`check-indicator`, and the equivalents `radio`, `slider`, `select` and `tabs` will need) is a **CSS type selector and is not registered in the KDL inflater**: the invariant is about widgets in the catalog, which an author picks from a list and puts in a document, and a part has no meaning outside its parent. Restyling it is what an author wants, and a type selector is the whole of that (ADR-0065).

**Primitives (`goldberry-core`):** `text`, `icon`, `image` (Blend2D codecs), `qr-code` (ISO/IEC 18004, encoded in `:core` beside the image codecs — ADR-0391), `spacer`, `row`, `column`, `stack` (z-layering), `scroll` (design-system scrollbar spec: overlay, hover-widening), **`canvas` is built** (immediate-mode Blend2D painting surface — the escape hatch and the substrate for charts): a `Painter` is a content slot on `Box`, handed the frame translated to the box's content corner and clipped to it, inside the `save`/`restore` pair the export list grew for it (ADR-0193), `focus-scope`.

**Controls (`goldberry-widgets`):** `button` (primary/secondary/ghost/danger variants via classes), `toggle`, `checkbox` (tri-state), `radio` + `radio-group`, `slider`, `select` (popup-backed dropdown), `text-input` (single-line) and `text-area` — see §17 for the editing-subsystem caveat, `progress` (determinate + indeterminate), `spinner`, `badge`, `tooltip`.

Of those, **`button` and `checkbox` are built**; the other eleven are not. `radio` + `radio-group` is the next one that matters architecturally, because it is the first composite and therefore the first thing to need §7.2's roving arrow-key focus.

**Containers & surfaces (`goldberry-widgets`):** `panel` / `card` (surface + border + radius tokens), `group-box`, `tabs`, `split-pane`, `form` / `field` (label + control + validation message layout, consistent label column), `dialog` (modal, backend popup or in-window layer), `toast`.

**Menus & shell (`goldberry-widgets`):** `menubar`, `menu`, `item`, `context-menu` (any widget: `context-menu=` reference), all rendered in backend popup windows (`SDL_CreatePopupWindow`) so they can escape the window bounds; **`tray-icon` is built** (SDL3 tray API, ADR-0191) with menu + tooltip — and is a *value* rather than a widget, because the desktop's shell draws its rows; **window decorations** — native by default, optional client-side decorated `titlebar` widget (frost material, window buttons per-platform ordering).

**Data (`goldberry-widgets`):** built on `canvas` with the theme palette: **All five are built** — `sparkline` takes `color` because it is one series; `line-chart`, `area-chart` and `bar-chart` are three modes of one `chart-plot` sharing its axes and gutter measurement; `donut-chart` refuses fewer than three slices and more than eight. They read `--gb-chart-1…8` from the theme (ADR-0194, ADR-0195) and pair a canvas plot with a legend of real widgets. `charts.md` is the contract, and the interaction layer in its §3.1 — tooltip, crosshair, thresholds, log scales, time axes — is not built. Axes/legend/tooltip primitives shared; deliberately small — not a plotting library. (Charts shipped as their own module in the original design; see ADR-0014.)

**3D (`goldberry-gpu`):** `canvas3d` — see §12.

### 11.1 Content modules (`content-widgets.md`)

The catalog above is what `goldberry-core` and `goldberry-widgets` owe. Everything
that renders *authored content* — a document, a page, a video, a camera frame —
is an **optional module**, specified in `content-widgets.md` and adopted as a plan
by ADR-0190. **One of them is half built**: `goldberry-html` renders Markdown
(ADR-0294, ADR-0295) and does not yet render HTML. The rule is one sentence: core stays lean
and licence-flat, so anything with a heavy native dependency or an
attribution/copyleft obligation is quarantined in its own artifact with its own
natives jars and its own `THIRD-PARTY-NOTICES`, and an application opts in.

| Module | Widgets | Engine | Where it attaches |
|---|---|---|---|
| `goldberry-html` | **`markdown-view` and `html-view` built** | **md4c (MIT)** for Markdown, a Java parser for HTML; litehtml still open | both halves landed **without an engine** (ADR-0298); litehtml waits on the paint surface below, for inline layout and text selection |
| `goldberry-pdf` | `pdf-view` | PDFium (BSD-3, prebuilt) | M4-adjacent |
| `goldberry-code` | `code-view` | Tree-sitter (MIT) | candidate |
| `goldberry-terminal` | `terminal-view` | libvterm (MIT) + a PTY in the backend SPI | candidate |
| `goldberry-vector` | `svg-view`, `lottie-view` | ThorVG (MIT) | candidate — closes the SVG deferral in §6.3 |
| `goldberry-media` | `video-view`, `AudioPlayer` | libVLC (LGPL-2.1+, **dynamic**) | last; gated on a codec/patent note |
| `goldberry-camera` | `camera-view` | SDL3 camera | needs `SDL_OpenCamera*` on the export list, as `tray-icon` needed `SDL_Tray*` |
| `goldberry-mic` | `level-meter`, `waveform-view`, `spectrum-view` | SDL3 audio recording | needs the SDL audio symbols |
| `goldberry-plot` | scatter/histogram/heatmap/contour | first-party, on the chart primitives | post-v1 |
| ~~`goldberry-web`~~ | `web-view`, **in `:widgets`** | webview/webview (MIT), over the desktop's own WebKitGTK/WebView2/WKWebView | **not a module** (ADR-0441): nothing heavy or encumbered to quarantine, so it is §9's second `widget.shell` member. A page cannot rasterize into a buffer and Wayland allows no reparenting, so it is a **window** rather than a box — and its native library is separate and optional so that GTK and WebKit are not load-time dependencies of the toolkit |

Two rows of that document's table are not modules here. **Charts** ship inside
`:widgets` (ADR-0014) — its §3 argument, that there is no third-party chart
engine worth hosting and the algorithms are the importable part, stands
unchanged. **Emoji** ships inside core's text stack (§6.2), which means core
carries the CC BY-SA attribution the document wanted quarantined; recorded in
§17.1.

**What `goldberry-html` actually landed**, and where it departs from the plan
above (ADR-0294, ADR-0295). md4c is compiled **into `libgoldberry`** rather than
into a native of its own: the quarantine rule is for heavy or encumbered natives,
and md4c is one MIT C file of tens of kilobytes, so a second superbuild with four
CI legs would cost more than it isolates. What is not relaxed is the dependency
direction — `:natives` exports md4c's wrapper to `:html` alone, and neither
`:core` nor `:widgets` knows Markdown exists. The parse crosses the boundary
**once**, as an encoded event buffer rather than as thousands of upcalls, which is
the first rule below applied to a parser; and `markdown-view` renders into
`column`, `row` and `text` under the ordinary cascade rather than into litehtml,
so the Markdown half needed no widening of the paint surface at all. litehtml,
when it comes, still gets its own library — every clause of ADR-0190's argument
applies to it.

Three rules the modules share, from ADR-0190:

- **The hot path never crosses FFM.** litehtml gets exactly two Java upcalls —
  `fetch(url, kind)` and `anchorClicked(url)` — so all networking is the
  application's `HttpClient` under the application's policy, and the thousands of
  per-page draw calls stay native. Every other engine is bound the same way.
- **A module's native library links against `libgoldberry`**, and never
  statically links a second copy of Blend2D or HarfBuzz — two Blend2D runtimes in
  one process make a `BLContext` handed across them undefined behaviour. Its
  symbols go on the one export list, which is why `goldberry-html` starts by
  *widening the toolkit's own paint surface* rather than by compiling litehtml.
  Two of the three things that sentence used to name have since been added by
  widgets that needed them first — the nested state stack by `canvas` (ADR-0193)
  and **gradients by a chart's fill** (ADR-0207), which is the shared work
  arriving from the other direction. Rounded geometry is still not on it.
- **Everything here rasterizes on the CPU into a buffer**, so a document, a PDF
  page and a terminal grid stay golden-image testable in CI on three OSes;
  camera and microphone ship synthetic sources so their widgets are too. **This
  is the rule `web-view` could not keep**, and the reason it is not in this table
  any more: `webview/webview` has no offscreen surface at all, so a page is a
  platform window and no golden image can ever contain one (ADR-0441). It is the
  second thing in the catalog, after `tray-icon`, that §14's coverage rule cannot
  reach.

## 12. 3D canvas (day 1 in the SPI)

- `canvas3d` is a leaf render object: Yoga sizes it like an image; it owns an SDL_GPU texture instead of pixels. The app receives a command-buffer scope per frame (SDL_GPU: Vulkan/D3D12/Metal underneath; shaders authored once, cross-compiled via shadercross).
- **Composition modes**, chosen per window automatically:
    1. *CPU-only* (no `canvas3d` present): Blend2D buffer → `present()` — the default cheap path.
    2. *GPU composition*: UI still rasterized by Blend2D on CPU, uploaded as a texture (damage-rect regions only); `canvas3d` textures and the UI texture composited in z-order in a trivial SDL_GPU pass. UI over 3D, 3D under frost — all works.
    3. *Readback* (headless/tests): GPU scene → CPU image → Blend2D composite.
- `BackendWindow.gpuSurface()` is present in the SPI from day 1; day-1 backends may return empty without breaking anything.
- Escape hatch: backends expose raw native window handles for apps embedding external renderers.

## 13. Accessibility

Day-1 in the design, and **not implemented**: a **semantics tree** parallel to the render tree — role, name, value, state, actions per node, populated automatically by built-in widgets. Day-1 consumers were to be keyboard traversal and the test framework (query by role/name, not pixel). Keyboard traversal exists and walks the *element* tree instead; interaction tests drive the router directly and golden images assert pixels. The tree is M5 work, and every control shipped so far records what its role and state would be in its own javadoc so that building it is transcription rather than archaeology.

`design-system.md` §4's accessibility baseline is met in part: contrast is authored to WCAG AA but **not validated in CI**; text scale to 150% is **implemented and not gallery-enforced** — `renderer.textScale` is §13's fourth switch beside reduced motion, and it scales the text and deliberately not the boxes, which is the condition §1.4 asks every component to survive ([ADR-0267](../book/src/adr/0267-a-text-scale-scales-the-text-and-not-the-layout.md)); what the enforcement half now waits on is *what to assert*, since `text-overflow: ellipsis` makes some cutting correct; reduce-motion is implemented (§5); reduce-transparency and "always show scroll bars" have nothing to switch yet; hit targets are ≥ 32 for both shipped controls.
Screen-reader bridging (UIA / NSAccessibility / AT-SPI) **is on hold and no milestone owns it** ([ADR-0440](../book/src/adr/0440-the-accessibility-bridge-is-on-hold-and-the-semantics-tree-stays.md)). It was planned via **AccessKit** (C ABI, fits the FFM stack), and that remains the shape it would take if it were reopened: nothing has asked for it, two of its three platforms are behind the same missing Windows and macOS machines §12 of `book/src/TODO.md` already blocks half a dozen items on, and the cost is a standing four-platform obligation in `:natives` — a Rust toolchain in the superbuild or vendored prebuilt binaries. **The semantics tree stays**, and is the reason the sentence above is a statement about schedule rather than about architecture: the data is in the tree, the gallery sweep enforces that every interactive node has a role and a name, and a bridge would still be an adapter rather than a rearchitecture. What is *not* provided, on any platform, is anything that reads it.

## 14. Testing

- `headless` backend renders to `BLImage`; **golden-image tests** run identically in CI on all three OSes (deterministic: embedded fonts, no platform rendering).
- Semantics-tree queries for interaction tests (`click(byRole(BUTTON, "Apply"))`), synthetic event injection through the normal dispatch path.
- Native layer: a **layout probe** — struct layouts and constants checked against the compiled library on every target, which is what replaces the guarantee generated bindings would have given (§3.1); upcall/shaping micro-benchmarks tracked over time.
- The widget showcase in `:widgets` doubles as the visual regression corpus — a widget with no screen there has no pixel coverage. `core-widgets.md` puts it more strictly: "a widget isn't done until it's in the gallery". There is no gallery *app* yet; what exists is a golden image per control per theme per state, which is the same coverage without the screen to browse it on.
- **Animation is testable because the clock is injectable.** A golden image of a mid-transition frame is impossible against a wall clock — the test would have to sleep and would be asserting on whatever the scheduler gave it. A virtual clock makes `clock.advance(50)` the exact frame at 50 ms, on every machine (ADR-0067).

## 15. Distribution

- Build: **Gradle** (Groovy DSL) multi-module — `:common` (what both halves need and neither owns), `:core`, `:widgets` (the widget catalog, charts included), `:html` (the first optional content module: Markdown, §11.1), `:gpu`, `:natives` (wraps the CMake superbuild) — with a version catalog and convention plugins; CI runs the same Gradle tasks on all three OSes. (The original design specified the Kotlin DSL, a separate `:charts`, and a `:gallery` module; see ADR-0013 and ADR-0014.)
- **Optional content modules** (§11.1) are published beside these when they exist — `goldberry-html`, `-pdf`, `-code`, `-terminal`, `-vector`, `-media`, `-camera`, `-mic`, each with its own notice file and none of them a dependency of `-core` or `-widgets` (ADR-0190). **`goldberry-html` exists**, and it has no natives jar of its own: md4c rides `libgoldberry` (ADR-0294), which is the one clause of that record measured and departed from. The other seven do not exist.
- Published to **Maven Central** under group `io.github.digitalsmile` (base package `io.github.digitalsmile.goldberry`): artifacts `goldberry-common`, `-core`, `-widgets`, `-html`, `-gpu`, plus `goldberry-natives` with `{platform}-{arch}` classifier jars (consumable from Gradle and Maven alike). **Versions are calendar versions** — `2026.1`, `2026.2`, `2026.2.1` — declared once in `gradle.properties` as the line being worked towards; every push to master publishes it as a `-SNAPSHOT` to the Central Portal's snapshot repository, and a matching `v*` tag publishes the release. One workflow uploads, once per run, after all three platforms are green (ADR-0333, ADR-0334; `docs/releasing.md`). Beside them, **`goldberry-bom`** pins every artifact's version and **`goldberry`** is the one dependency an application starts from: `-common`, `-natives`, `-core`, `-widgets` as dependencies and `-html`, `-gpu` — and every future content module — as `<optional>` (ADR-0336). The showcase is not published to a repository: a release tag builds it as a GraalVM native image on all three platforms and attaches the binaries to the tag's GitHub Release (ADR-0337, ADR-0340).
- Single-jar quick start (fat natives) for tinkering; GraalVM native-image config shipped in the jars (`META-INF/native-image`).
- License: toolkit Apache-2.0. Bundled assets — Inter (OFL), JetBrains Mono (OFL), Lucide (ISC) in `goldberry-core`; OpenMoji (CC BY-SA, attribution in the application's About screen) in the optional `goldberry-emoji` (ADR-0384). The statically linked native libraries also redistribute in object form: Blend2D, AsmJit, SDL3 (Zlib), Yoga, HarfBuzz. On Linux SDL loads the system libxkbcommon at run time; it is not redistributed. Full disclosure in `THIRD-PARTY-NOTICES.md` and `licenses/`, verified by `./gradlew checkLicenses`; see ADR-0015.

## 16. Milestones

- **M0 — Skeleton:** superbuild → `libgoldberry` on 3 OSes; FFM bindings + layout checks; SDL3 + headless backends; blank window at correct fractional DPI.
- **M1 — Vertical slice:** styled wrapped paragraph, resized at 60 fps on Linux, macOS and Windows; paragraph cache + upcall benchmarks green. *Built, unproven: the pipeline is done and the budget is met with 3.9x of headroom (3.13 ms median, 4.28 ms p95 against 16.67), but on one machine — and that machine is a VirtualBox VM. The "on Linux, macOS and Windows" half is a CI job rather than toolkit work and is scheduled at M5; `book/src/status.md` has the shape of it.*
- **M2 — Widgets & style:** CSS engine, KDL inflater + hot reload, core controls, Nord light/dark, focus/cursor/shortcuts, golden-image CI. *Done: **every control in `core-widgets.md` §3 is built**, `select` included — it came last because its list is a platform window and a widget had no way to ask for one until `BuildContext.host()` (ADR-0140, ADR-0141). `segmented` closed the catalog and cost two amendments to `design-system.md`: its specified drawing needs per-corner radii that §8's subset does not have, and its specified motion needs the geometry of a box a widget cannot see (ADR-0097). Plus §7.2's roving arrow-key focus with an axis — which `segmented` is the first non-menu to use — §1.3's density, disabled propagating through a subtree, §1.2's contrast floor now checked in CI, and a gesture anchor for controls whose drag is a rate. Custom image cursors are the other gap. `book/src/status.md` has the detail.*
- **M3 — Shell:** menus/popups, tray, dialogs, scroll, forms, decorations opt-in CSD, charts, widget showcase. *Started: the **in-window overlay layer** ships and `hud` is its first occupant — every window's tree is rooted at a `window-root` that floats overlays out of flow at a corner, which is what `toast`, a `dialog`'s scrim and `hud` need and what none of them needed a platform window for (ADR-0100, ADR-0101). The **backend popup window** is built too — `createPopup` on both backends, a popup being a window with an owner and a position in its coordinates, refusable by a driver that has none (ADR-0102). **The widget layer over it is built as well**: a `Popup` is an element tree, a render tree and a router of its own in that window, painted by the owner's renderer, anchored to a node's painted rectangle and light-dismissed by a press or `Escape` below it — the showcase opens one from a button (ADR-0103). **`popover` is built** — the panel as a widget, and measure/flip/shift/open as `host.popup(content, anchor, placement)` against the display's *work area* rather than its bounds, with the keyboard forwarded into whatever popup is open (ADR-0104). `menu` is built and so, now, is **`select`** — the control that was M2's leftover, and the one that turned `Overlay.of(context)` from a note into `BuildContext.host()` (ADR-0140, ADR-0141). `tooltip` is ordinary widget work. **§4's `field`, `form` and validation model are built too** (ADR-0169): a field is silent until the keyboard has left it once and live from then on, blur is `Handles.onFocusWithin` — the `:focus-within` notification, whose second consumer is the `carousel` brake §5 asked for and ADR-0165 could not build — and the fields find the form rather than the other way round, through `BuildContext.findAncestorState`'s first real use. **§4's `text-input` opens the forms group** (ADR-0167): the field owns its caret, its selection and its undo stack and reports each value up, the editing rules are a value tested without a window, and the caret blinks on a timer rather than through `isAnimating`. Two things it needed did not exist — the **clipboard**, which was §4's last named SPI hole, and `SDL_StartTextInput`, which nothing had called, so committed text had never arrived on a real SDL window. **§7's `message` is built too** (ADR-0175) — the group's one member that never floats, and the first widget whose job is to draw §1.2's aurora hues as a *glyph and a border on a surface* rather than as a fill under text. Measuring that found the theme's own claim untrue in five of eight pairs, so a semantic hue has a third rank now (`--gb-danger-line`) with a 3:1 CI sweep under it, and §4's error summary is finally drawn by something. **And §7's `dialog`** (ADR-0176), which is the widget M3 named that was actually *blocked* rather than merely unbuilt: it needed a focus trap and a way to focus something by name, and three TODO entries were waiting on the second. Modality turned out to be two different things — the pointer's is geometry (a filling overlay takes every press) and the keyboard's is a declaration the router reads as "while something modal is mounted, the focused node is inside it". **And `toast`** (ADR-0177), which closes §7: the one widget in the catalog whose *value is not a widget* — a `Toast` is a record raised through a controller, because §7's own line between "part of the layout" and "something that just happened" means nobody writes one anywhere. The stack owns the queue, and owning it is what lets a toast outlive its own dismissal. **And §3's sibling reflow** (ADR-0178), which finishes §7: when a toast goes from the middle of a stack the survivors travel to their new places rather than jumping there, and *which* of them travel turned out to be a fact about the overlay layer rather than about the widget — a `toaster` is pinned to a corner with its newest member against it, so the older half closes the hole and a stack that loses its oldest first moves nothing. The `AnimationController` §3 names for it did not appear: `Phase` was already the start and the end, and the interruption is three lines of arithmetic, which is ADR-0081's finding one level up. **And the popup facility says what it measured** (ADR-0179), which is the measure step `Host` had always claimed was "separately observable" and was not: a `Host.Fit` sits between the measure and the place, so `menu` has stopped capping itself from an assumed row height and `select` has stopped losing the bottom of a list longer than the screen. The facility reports and the caller decides, because whether long content scrolls or is clamped is a fact about the content and `:core` has no viewport to wrap it in either way. **And §7's "restores focus on close"** (ADR-0180), which turned out to sit on top of a bug: `Element.unmount` tells the element tree and nothing else, so a closed dialog left the router holding an unmounted element that still received keys. Two rules now — the router never holds an element that has left the tree, which is right for a switched tab and a shortened list as much as for a dialog, and then the keyboard goes back to whatever had it before the modal opened, ring and all. The remembered element is the first state the focus trap has ever held; everything else about the trap is still a question about the tree, asked fresh. The popup half needed nothing: a popup's router is its own, and a probe through the real launcher recorded no focus loss in the owner at all. **And §8's subset gained `min-width` / `max-width` / `min-height` / `max-height`** (ADR-0181), which was filed as "a gap in the style engine rather than a decision about dialogs" and had five consumers waiting — three of them widgets that had written a *width* where they meant a maximum. One value rather than four components, because they are the same question asked four ways and a caller handling three of them would have a bug nobody finds. `dialog` has §2's min 320 and max 80% now, the second of which works because a percentage resolves against the containing block and a dialog's is a scrim that fills the window — so the scrim gave up its padding across, and the trick is that nothing has to measure a window. **And §3's `select multiple=` with §4's free-text autocomplete** (ADR-0182), which turned out to share one shape: a control that offers a set of things has to be able to give one back. `change` is a *toggle* when a select holds many, because the set is the application's; a suggestion panel is the select's own list with `Option.inAList()`, so arrows move the focus and `Enter` commits rather than rewriting the field under a user who is only looking; and both needed something the toolkit did not have — **a popup whose content can change while it is open**, because a popup is an element tree with its own build schedule and showing it something new used to mean closing and reopening it. A toast can be dismissed by clicking it now, which is the way out §7's shape left missing. **And §3's combobox** (ADR-0183), which finishes `select` bar `tree=`: the closed control holds a real `text-input`, because everything an editable field needs already lives there with rules in it and a second editor would be a second copy of them. The field stops being a Tab stop and delegates focus, so a combobox is one stop; and `Esc`-restores plus the refusal of a free-typed value both fall out of a single nullable string of offered text, because `TextInputState.follow` already overwrites only when the offered value changes. **And a first cut of §3's `tree`** (ADR-0184), which `select tree=` was waiting on and which had to define the node model rather than inherit it — §3 says a tree shares `list`'s item-factory and `list` is not built, so `list` will now have to agree with what shipped here. The id is the whole model: expansion is retained by it, the row is keyed on it, and a branch survives its model being re-sorted underneath it. `Right` on an open row deliberately does nothing, because the rows are flattened depth-first and the next row already *is* the first child. **And §9's `tray-icon` opens the shell group** (ADR-0191), which is the first thing M3 owed that starts in `goldberry.symbols` rather than in a widget: eleven symbols and two binding classes took the export list from 192 to 203, and the five `SDL_TRAYENTRY_*` values went into the constant probe with everything else. It is also the first entry in the catalog that Goldberry **does not draw** — a tray menu is a GTK menu, an `NSMenu` or a Win32 popup, themed and clicked by the shell — so the parity invariant's "CSS-styleable" has nothing to attach to and a `TrayIcon` is a value like a `Toast`. The menu it holds is an ordinary `Menu`, the same value a `menubar` holds, for ADR-0163's reason; what the platform has no vocabulary for — an icon, an accelerator, a widget that is not an item — is dropped with a warning rather than in silence. Absence is the answer on a desktop with no notification area, and unlike a popup's no error string is read to decide it: the Linux failure is `Could not load AppIndicator libraries`, which is an absence wearing the words of a failure. **And `charts.md` §3.1 is complete** (ADR-0207), which took the second thing off §11.1's list of what `goldberry-html` would have to add first: a gradient is not a colour, so every fill on the export list was the wrong shape for one, and the six symbols that fix that are the ones a CSS gradient and an SVG one both need. The sixth is `bl_context_fill_path_d` — the plain fill with no `_rgba32` suffix, the only styleless drawing call bound, and the only way a ramp reaches a path; putting a colour back afterwards is not optional, because a gradient left set would be drawn by whatever filled next, somewhere else in the frame. The `OKLCH` the deferred entry asked for turned out to be vacuous — a fade between two alphas of one hue is the same curve in every space — and what actually matters is repeating the colour at the far stop, since `0x00000000` is transparent *black* and a green fading to it goes through grey. `Fill.NONE` is the default, so no existing picture changed, and a ramp is anchored to the **data** rather than to the plot: two series of different magnitudes are drawn at the same strength, and a stack's lower band is not half gone before it starts. **And two keyboard gaps closed behind it**: a context menu opens from `Key.MENU` and `Shift+F10` now (ADR-0208), anchored to the *focused* element's painted rectangle because there is no pointer to anchor to — which was the half of ADR-0108 that left one catalog entry a pointer was the only way into, in a toolkit whose §2.2 says everything must be reachable; and `tree` has the rest of §3's keyboard (ADR-0209) — `Home`/`End` over the flattened list, `*` over the siblings and not the descendants, and type-to-select over the visible rows only. All three of the tree's needed rows the focused one cannot see, which is why they waited and why each is a callback the tree hands down. **And `tree` owes §3 nothing further** (ADR-0210): `checkable=` and the selection models are built, and the one recorded as *blocked* on `list` was not — `tree` defined the node model itself for the same reason and wrote down that `list` will have to agree, so the selection models are the same debt taken knowingly in the same place. Selecting and checking are **two values through two callbacks**, because a file manager where the highlighted row and the ticked rows were one thing could not copy six files; a `cascade` parent is derived from what is under it rather than stored, so it cannot drift out of step with its own children; and a lazy branch nobody has opened reads its own membership, because fetching a model to draw a checkbox is the one thing a lazy tree must not do.**And §10's `list` is built** (ADR-0212), which was owed twice over: `tree` had defined its selection models under ADR-0184's rule and written down that `list` would have to agree, so building it is the debt being paid rather than a new widget arriving. `Selection` moved and nothing about its shape changed, which is the evidence that the promise was small. An item is the **application's** type described by three functions — what it is, what it looks like, what it reads as — and the third is optional, because §10 makes type-to-select conditional on items exposing text and a row that consumed a keystroke it could not use would starve a field elsewhere. Item context menus are named on the **row**, which is the only node both ways in walk up through: a right-click from the pointer and the menu key from the focus (ADR-0208). **And §10 is complete** (ADR-0213, ADR-0214): the virtualization §10 promised for v1.x is built — a row height and two spacers, the spacers being what keeps it from oscillating, since the column adds up to the same total however the window moves — and `table` turned out to be waiting for `list` rather than for the recycler. A table's rows *are* a list's rows with more than one thing in them, so it composes a `ListView` and inherits the selection, the typeahead, `Home`/`End` and the ten-thousand-row window rather than copying them. Sorting is the application's, and the click reports what the sort would *become* rather than which column was hit. A caret slot is kept on every sortable header whether or not it is drawn, which the **golden image** found and no assertion had: without it, sorting a column takes 16px from that column's own label at the moment the reader clicks it.*
- **M4 — GPU:** `canvas3d`, GPU composition path.
- **M5 — Hardening:** text editing depth, IME preedit, docs, the first release (`2026.1`, ADR-0333), and the three-platform frame evidence M1 is waiting on. *The AccessKit bridge was this milestone's last toolkit item and is [on hold](../book/src/adr/0440-the-accessibility-bridge-is-on-hold-and-the-semantics-tree-stays.md), so what is left under M5 is the release half alone — blocked on an account and a tag rather than on code. The frame evidence is here because it is a CI job and because this is where every other "prove it on hardware nobody has run it on" item already lives. It needs a window that can be resized from outside — `SDL_SetWindowSize` is bound and the SPI never exposes it — a run that reports what its frames cost, and a ceiling under the three `showcase.yml` legs that already open a real window on each platform.*

**The content modules are not on this ladder.** §11.1 says where each would
attach — `goldberry-html` beside M3, `goldberry-pdf` beside M4, the rest as
candidates — and that is a statement about order, not about schedule. Nothing in
`content-widgets.md` is scheduled while M3 still owes client-side
decorations, charts and the rest of §4.

## 17. Known deferrals & open questions

Deliberately *not* in v1, with the seams that keep them addable:

- **Text editing depth.** `text-input`/`text-area` v1 = caret, selection, clipboard, undo stack, word ops. **`text-input` ships with all of it** (ADR-0167). Full IME **preedit** (CJK composition), RTL editing, and rich text are deferred; the `KeyEvent`/`TextEvent` split and the semantics tree are the seams. Committed text from an IME already works — the platform hands over finished characters and a field takes them like any others — so what preedit adds is the underlined in-progress string a field draws and does not hold, plus `SDL_SetTextInputArea` to say where the candidate window goes.
- **Drag & drop** (in-app first, platform DnD later — SPI gains two methods).
- ~~**List virtualization** (recycling for 10k+ rows; `scroll` is designed to host it).~~ **Built** ([ADR-0213](../book/src/adr/0213-a-virtual-list-is-two-spacers-and-a-window.md)): `list` and `table` take a row height and build only the rows their viewport can see, standing the rest off with two spacers — which is what makes it terminate, since the column adds up to the same total however the window moves. `scroll` did host it, through `Located` rather than through anything the viewport had to add. *Rows of varying height are still out: the arithmetic is index × height.*
- ~~**Tables/trees**~~ **both built** — `tree` at ADR-0184/0209/0210 and `table` at [ADR-0214](../book/src/adr/0214-a-table-is-a-list-with-columns.md), which is a `list` composed rather than a widget reimplemented. Still deferred: rich text display, notifications API, and multi-window state management beyond the basics.
- ~~**Open:** KDL `bind` expression scope (dotted paths only vs. mini-expressions)~~ — **settled: dotted paths only**, enforced by the registry, so an expression fails at inflation rather than resolving to nothing (ADR-0062). Negation and formatting stay in Java.
- **Open:** whether `--gb-selection` alpha compositing forces a color-mix() subset; whether client-side decorations are the default on Linux or opt-in everywhere.

### 17.1 Where this document and the design documents disagree

Recorded rather than reconciled, because the design documents are the authority
and each of these needs a decision rather than an edit. `book/src/TODO.md`
tracks them alongside the implementation's own gaps.

**Five were taken on 2026-09-17**, and each went the way this section says it
should — the design documents won, and where a reading was genuinely open the tie
went to the row that wrote a number down. They are struck through below rather
than deleted, because the argument each of them was up against is the part worth
keeping.

- ~~**The platform primary modifier.**~~ **Settled by
  [ADR-0378](../book/src/adr/0378-the-desktops-own-modifier-has-a-name.md).**
  §2.3 wanted accelerators against a platform-primary modifier — `Cmd` on macOS,
  `Ctrl` elsewhere — "via one `Shortcut` abstraction"; the shipped `Shortcut`
  refused to remap, because silently translating them makes `Ctrl+C` mean two
  different things depending on where it runs. They were never incompatible: §2.3
  wanted a way to *say* "whatever this desktop uses" and the code was refusing to
  *guess* it. `Primary+S` says it, `Ctrl+S` still means `Ctrl`, and the toolkit's
  own editing accelerators are on the primary modifier. (§7.2)
- **Pixel-precise wheel deltas.** *Settled by ADR-0115, and left here because
  the resolution is a difference rather than an agreement.* §2.4 asks for
  "pixel-precise wheel/trackpad deltas with line fallback"; SDL exposes no pixel
  axis and going around it to the platform is what ADR-0056 declined. What ships
  is **lines with the touchpad's fraction preserved, plus the platform's
  accumulated detents beside them** — which delivers what §2.4 wanted (scrolling
  that does not quantize) without the mechanism it named. What a line is worth in
  pixels is the toolkit's number rather than the compositor's, and it lives on
  the widget that scrolls. (§7.1)
- **A `text-area` that fills.** `core-widgets.md` §4 describes a field that grows
  to fit its text between `rows` and `max-rows`, which is what a form wants. An
  **editor** wants the other thing — the height its container has, with the text
  scrolling inside it — so `fill=#true` is an attribute §4 does not have
  ([ADR-0297](../book/src/adr/0297-an-editor-fills-its-pane-and-a-split-knows-its-own-width.md)).
  It is an addition rather than a disagreement: an area without it behaves exactly
  as §4 says. The sentence to amend is the one that assumes a multi-line field is
  always a form control. (§11)
- ~~**One module or two.**~~ **Already amended, and this entry outlived it.**
  `core-widgets.md` now says "the single `goldberry-widgets` module (ADR-0014),
  separated by package", which is what ships. The sentence this recorded was
  edited on 2026-08-18 and nobody struck the record. (§11)
- ~~**A `tooltip`'s radius and its type rank.**~~ **Settled by
  [ADR-0380](../book/src/adr/0380-the-tooltip-row-is-what-ships.md): the code
  follows the row.** §3 says radius 4 and `caption`; the sheet wrote 8 and `body`,
  each with an argument beside it — §1.5 groups radii as `4` · `8` · `12` and
  names no tooltip in any of them, so neither number *follows*, and §1.4 gives
  `caption` to secondary text under a control where a tooltip is the only text on
  screen. Where a reading is open, the tie goes to the row that wrote a number
  down; the `body` argument stays in `controls.css` because it is §1.4's to
  answer. `TooltipMetricsTest` pins all four numbers, so a fifth departure is a
  failing test ([ADR-0263](../book/src/adr/0263-three-numbers-in-one-row-and-nothing-watching.md)). (§10.1)
- **`checkable` names two different things.** `core-widgets.md` §3 spends the
  word twice. On `select tree=` it is a rule about which rows are an **answer** —
  `checkable="leaf|any"`, "leaf-only by default, because 'Europe' is usually a
  heading and not an answer". On a standalone `tree` it "adds a **checkbox** per
  node", `checkable="none|leaf|any|cascade"`, and a checkbox is a second value
  beside the selection rather than a rendering of it. What ships implements both
  under two names — `Tree.anyNode` for the first and `Tree.checkable` for the
  second — because a file manager where the highlighted row and the ticked rows
  were one thing could not copy six files (ADR-0210). Recorded rather than
  reconciled: both sentences describe something real, and it is the *word* that
  is doing two jobs. (§10.1)
- ~~**A disabled container disabling its descendants.**~~ **Built by
  [ADR-0379](../book/src/adr/0379-a-disabled-container-reaches-the-cascade.md)**,
  for input (ADR-0077) and now for the cascade: `:disabled` reaches every element
  under a disabled one, and one rule — `:disabled :disabled { opacity: 1 }` —
  keeps the fade on the outermost of them, which is what kept it out before. The
  **semantics** half is still owed by something that does not exist: there is no
  semantics tree, so nothing reports a role as unavailable yet.
- ~~**`goldberry-emoji` is not a module, and core carries its attribution.**~~
  **It is a module** (2026-09-17,
  [ADR-0384](../book/src/adr/0384-the-emoji-face-is-an-artifact-an-application-opts-into.md)).
  The table was right and §6.2 was wrong: CC BY-SA wants *visible* attribution —
  an about box or a credits screen, not a notice file — so an obligation every
  application inherited is now one an application takes on by adding an artifact.
  The fallback chain is still a text-stack decision and the slot stays in
  `:core`; the *face* is a packaging one and arrives through an `EmojiFont`
  service. (§6.2, §11.1)
- ~~**`goldberry-charts` is not an artifact.**~~ **The table says so now**
  (2026-09-17). ADR-0014 merged it into `:widgets` before that table was written,
  the code was never moving, and §3's argument about engines is untouched by where
  the widgets live. (§11.1)
- ~~**"Zero new natives" is not zero new work.**~~ **Both sentences say so now**
  (2026-09-17). They were true of the *binary* and not of the *surface*:
  `tray-icon` met this first and paid eleven symbols and two binding classes for
  it (ADR-0191), and of the 76 `SDL_*` entries on the export list today none is
  audio and none is camera. Each of those modules is the same widening plus a
  binding apiece (ADR-0190). (§3.2, §11.1)
- ~~**Dual y-axes.**~~ **`charts.md` wins, and §4.1 says so now** (2026-09-17).
  `content-widgets.md` listed "dual y-axes" among `goldberry-plot`'s scales;
  `charts.md` §3.4 refuses them outright: two measures
  at different scales are two charts, small multiples, or one indexed to a common
  base, and a second y-scale is the single most reliable way to make a chart say
  something untrue. The design document is the authority and this is a refusal
  rather than an omission, so it needs a decision. It is post-v1 either way —
  `goldberry-plot` is not scheduled — but the note belongs here rather than in the
  module that would inherit the argument. (§11.1)
- **`masonry` is a widget the design documents do not have.** §5's containers are
  complete without it, and the catalog has it anyway: a wall of cards of unequal
  height had no answer, `column-count` is not in §8's subset, and Yoga is a
  flexbox engine — flexbox cannot do masonry, which is why CSS specified it
  separately. It is an addition rather than a reading of the canon, and it is
  here rather than in `core-widgets.md` because that document is the authority
  and editing it is not the implementation's to do (ADR-0196). (§11)
- **`Tab` does not move between a chart's series.** `charts.md` §3.5 asks for
  keyboard operation of a chart and spells part of it "`Tab` moves between
  series". `Tab` is this toolkit's focus traversal and a composite is one Tab stop
  with roving *arrow* keys inside it (ADR-0073), so a control cannot also claim
  `Tab` — and a chart is one node rather than a group of focusable series. What
  ships is `Left`/`Right` walking the crosshair, `Home`/`End` for the ends and
  `Escape` to let go, with the readout naming **every** series at the point
  rather than one at a time, which is what the request was for. The vertical
  arrows are deliberately left alone: a focused chart inside a `scroll` must not
  swallow the keys that move the page. A refusal of one sentence of an authority
  document, so it is recorded here (ADR-0199). (§11)
