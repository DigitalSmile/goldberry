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

What is *built* is tracked in `book/src/status.md`, not here. A line in this
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

## 3. Native core

| Library      | Role                          | Notes |
|--------------|-------------------------------|-------|
| Blend2D      | 2D vector rasterization       | JIT pipelines, banded multithreading, built-in PNG/JPEG/QOI codecs. Renders glyph runs directly from font outlines — no FreeType. |
| Yoga         | Flexbox layout                | Flexbox-only in v1. Layout engine hidden behind an internal interface (Taffy or grid support possible later). |
| HarfBuzz     | Text shaping                  | UTF-16 in (matches Java strings), glyph IDs + positions out. |
| SDL3 (≥3.2)  | Desktop backend               | Windowing, input, per-monitor DPI, clipboard, cursors, popup windows, tray icons, SDL_GPU. The permanent desktop windowing layer — Goldberry ships no hand-written platform backends. |

### 3.1 Binding rules (FFM)

- Bindings are **hand-written**, not generated. jextract was the original plan and was dropped (ADR-0006 superseded by ADR-0010): the surface actually needed is small, and a generated binding for one platform's headers is not a binding for another's. What replaces the guarantee jextract would have given is a **layout probe** — a table of struct layouts and constants checked against the compiled library at run time on every target, which is what catches `long` = 32-bit on Win64 and an enumerator that drifted (ADR-0029).
- Raw `MemorySegment` never escapes the `io.github.digitalsmile.goldberry.natives` module (`native` is a reserved word in Java, hence `natives`). Every native object gets a thin Java wrapper with explicit ownership.
- **Arena discipline:** one shared `Arena` per window for long-lived objects (fonts, Yoga config); a confined per-frame arena for scratch (HarfBuzz buffers, glyph arrays, damage lists) — dies at frame end, zero GC pressure. Long-lived wrappers expose `close()`; a `Cleaner` is the safety net, never the mechanism.
- Upcalls (Yoga measure functions) use **one stub per measured node**, not one shared stub dispatching on a context pointer: a callback already belongs to exactly one node, and passing the node pointer on would put a raw `MemorySegment` in front of code outside the module (ADR-0017, ADR-0029). The returned `YGSize` segment is allocated once per callback rather than per call. Upcall cost is benchmarked in the M1 vertical slice.
- **Yoga's tree is owned, not merely referenced.** A root owns its subtree; inserting a child transfers ownership; closing a root frees the subtree child-first and marks every Java wrapper in it dead, so a stale reference throws instead of reading freed memory. Yoga's `abort()`-on-precondition checks are all reproduced in Java first (ADR-0029).

### 3.2 Native build & packaging

- A CMake superbuild — driven from Gradle by the `:natives` module — statically links Blend2D + Yoga + HarfBuzz + SDL3 into **one shared library `libgoldberry`** per platform. One artifact, one version matrix.
- Distribution follows the LWJGL pattern: classifier jars — `goldberry-natives-linux-x64`, `-linux-aarch64`, `-windows-x64`, `-macos-aarch64` — extracted or `System.load`-ed at startup. Four rows, not every OS × arch pair: Windows on ARM and macOS on Intel are not built (ADR-0041).
- CI: GitHub Actions matrix, every artifact built on a native runner — four runners, four artifacts, no cross-targeting. The Linux legs build inside a `manylinux_2_28` container, which pins the glibc floor at 2.28 (RHEL 8) — a stock runner would link against glibc 2.39 and refuse to load on older distributions.
- **Java 25 LTS is the floor** (FFM final, Vector API for the blur path). GraalVM native-image builds run in the same CI matrix.

## 4. Backend SPI

The only platform-facing interface. Everything above it is platform-agnostic.

```java
public interface Backend extends AutoCloseable {
    BackendWindow createWindow(WindowSpec spec);
    Optional<BackendPopup> createPopup(BackendWindow owner, PopupSpec spec); // menus, tooltips — built (ADR-0102)
    Optional<TrayIcon> createTrayIcon(TraySpec spec);
    Clipboard clipboard();
    void pumpEvents(EventSink sink);          // blocks until events or frame callback
    void wakeup();                            // cross-thread event-loop wake
}

public interface BackendWindow extends AutoCloseable {
    void present(PixelBuffer frame, List<DamageRect> damage);  // CPU path
    Optional<GpuSurface> gpuSurface();        // GPU composition path — in the SPI from day 1
    float scale();                            // per-monitor, fractional (125%, 150% must work)
    void setCursor(Cursor shape);             // standard set; custom image still to come (§7.3)
    void setTitle(String title); void setDecorated(boolean serverSide);
    void requestFrame();                      // vsync-aligned frame callback
}
```

Rules baked into the SPI:

- **macOS main thread.** `Goldberry.launch(app)` takes over the calling thread as the UI thread (AppKit requires the first thread). It does not spawn one.
- **HiDPI:** layout in logical px, raster at physical px, per window. Fractional scales are day-1 correct, not retrofitted.
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
- **Layers & damage.** Render objects marked as repaint boundaries cache their raster in a `Layer`. Promotion is `opacity < 1` with children today — which is where CSS group opacity and a per-box alpha multiply disagree, and is what makes `opacity` mean what CSS means (ADR-0071, closing ADR-0064's open question). A promoted subtree is rasterized at full strength and *untransformed*, so the alpha and the transform are applied to the blit and a subtree that did not change is a blit and nothing else. Damage rects flow to the backend: each render object remembers where it was, and a node that changed damages the union of where it was and where it is. **The frame is painted only inside the damage**, where the backend promises the buffer it lends back holds last frame's pixels — `BackendWindow.retainsFrameContents()`, false by default so a backend that says nothing gets a full repaint (ADR-0072). A clipped repaint is asserted pixel-identical to a full one. The *traversal* is still full: the clip saves rasterization while the walk still visits every box, which is why one small box changing costs 117 µs against 367 µs rather than the 400× its 0.23% area would suggest. Scroll containers, the frost material and the 3D canvas are not promoted yet.
- **Blur/frost.** Blend2D has no filter effects. Gaussian-approximate blur = 3-pass separable box blur implemented in Java with the **Vector API**, applied to downscaled layer copies for the frost material; drop shadows are nine-slice cached blurred rects. Frost automatically falls back to opaque per the design system when the backdrop is unavailable.
- **Animation.** A frame `Clock` (system, or virtual so a golden image can snapshot a mid-animation frame); CSS transitions over a **closed** whitelist — `opacity`, `background-color`, `border-color`, `color`, `transform` — resolved by the cascade like any other property. Animated values live in a per-node overlay applied at paint and are never written back into computed style, so recomputation and animation cannot fight; retargeting starts from the current animated value. A declaration naming a layout property is refused with a warning, not ignored. Colours interpolate in OKLCH. `prefers-reduced-motion` collapses every duration to zero and keeps the declarations, so the same states are reached by the same route. A `transform` interpolates function by function rather than through its matrix, because the midpoint of two matrices a half-turn apart is a collapsed box (ADR-0068). **Layer promotion**, the overlay enter/exit lifecycle and the imperative `AnimationController` are **not** implemented — see ADR-0067.

## 6. Text stack

- One font buffer feeds both `hb_face_t` and `BLFontFace` — metrics cannot disagree.
- Pipeline: segment (script/direction via `java.text.Bidi`; line-break candidates via `BreakIterator` — both JDK built-ins, no ICU4C) → `hb_shape` per run → `BLGlyphRun` → `blContextFillGlyphRun`.
- **Paragraph cache** keyed by (text, resolved text style, width bucket). Yoga measure callbacks hit this cache; it is the hot path.

### 6.1 Fonts

- **Embedded by default:** Inter (UI) + JetBrains Mono (code) ship inside the jar. Deterministic rendering on every machine; the design system's metrics are authored against them.
- **`Fonts` is the book that joins a resolved style to a `Font`**, caching faces by family+weight and fonts by (face, size). Owned and closed by the application, never global: these are thread-confined and hold native memory, and a process-wide cache of them would have no hook that ever frees it (ADR-0044, ADR-0066).

```java
try (var fonts = Fonts.bundled()) {
    var renderer = new WidgetRenderer(stylesheets, fonts);
}
```

- **System fonts are a planned opt-in and are not implemented.** The design was a `FontSource.system("Segoe UI", "SF Pro")` resolved through the backend via fontconfig / DirectWrite / CoreText, plus `FontSource.file(…)`; `Fonts.bundled()` is the only source that exists. Switching to system fonts would void pixel-exact design-system metrics — documented and intentional — which is why the bundled path is the default and was built first.
- **Fallback chain is exactly two slots:** primary family → emoji font. No general fallback cascade in v1 (no itemization across arbitrary fonts). Missing glyphs render `.notdef` deliberately. `font-family: Inter, sans-serif` therefore takes the first name and discards the rest, rather than pretending to a mechanism that does not exist.
- **A weight is a face, not an axis.** `design-system.md` §1.4 ships two weights, 400 and 600, so Inter's SemiBold static instance is bundled beside the variable file. Instancing `wght` at runtime needs symbols in both HarfBuzz and Blend2D and therefore three new export branches — the machinery that has caught the same local-symbol bug three times. A CSS weight no file provides resolves to the nearer one that does, the way CSS's own font matching works (ADR-0066).

### 6.2 Emoji

- Emoji font: **OpenMoji** — monochrome (Black) variant as the default, matching the toolkit aesthetic; the **COLRv0 color variant** (palette re-themed toward Nord) is opt-in per text style (`emoji: color`).
- COLRv0 is layered outlines + CPAL palette — implemented in the text stack by drawing each layer glyph with its palette color through Blend2D. No bitmap emoji formats (CBDT/sbix) in v1.
- Segmentation: emoji sequences (ZWJ, VS-16, modifiers) detected during itemization and routed to the emoji slot.
- OpenMoji is CC BY-SA; the re-themed derivative is published with attribution per license.

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

- **Selectors:** type, `.class`, `#id`, descendant, child (`>`), and pseudo-classes `:hover :active :focus :focus-visible :disabled :checked :indeterminate`. Standard specificity. Nothing else in v1 — notably no `:not()`, which is why a disabled control is kept from lighting up in the router rather than in a stylesheet (ADR-0064).
- **Cascade layers (fixed):** toolkit base → theme → application → inline. Later layer wins at equal specificity. The `inline` layer has no `style=` attribute behind it and is instead reached by `Styled.restyle`, which lets a widget write the values a selector cannot express — a segmented control's indicator is `1/n` wide and `k` cells along, and nothing in CSS can count `n`. Applied after the cascade and *before* the frame's animations observe the style, so what a widget writes transitions like anything else ([ADR-0099](../book/src/adr/0099-an-indicator-travels-on-a-grid.md)).
- **Custom properties + `var()`** are the theming mechanism (prefix `--gb-*`). `calc()` deferred.
- **Property split** (already a design invariant):
    - *Layout properties* compile directly to Yoga: `display:flex, flex-direction, flex-wrap, flex-grow/shrink/basis, justify-content, align-items/self/content, gap, padding, margin, width/height/min/max, position: relative|absolute, inset, aspect-ratio, overflow`. `flex-basis` is the one of these still unimplemented; `flex-shrink` arrived late and its absence had made every fixed metric in §3 negotiable ([ADR-0076](../book/src/adr/0076-a-glyph-does-not-negotiate.md)), and `position` / `inset` arrived with the first box that had to sit *over* its siblings rather than beside them ([ADR-0099](../book/src/adr/0099-an-indicator-travels-on-a-grid.md)).
    - *Paint properties* resolve into an immutable `ComputedStyle`: `background, color, border(+radius, per-side), opacity, box-shadow, backdrop-filter: blur() (frost), font-family/size/weight, line-height, letter-spacing, text-align, cursor, transition, transform (translate/scale/rotate), outline (focus ring)`.
    - Of that list, `ComputedStyle` resolves **`background`, `color`, `opacity`, `border-radius` / `border` / `outline` (one radius, not per-side), `font-family` / `font-size` / `font-weight` / `line-height`, `cursor`, `transition` and `transform` / `transform-origin` (the 2D functions)** — plus, from the layout half, `position` and `inset`. `box-shadow`, `backdrop-filter`, `letter-spacing` and `text-align` are absent, because `Box` cannot express them and a property that resolves into nothing is a property with no test that means anything. Each arrives with the thing that paints it. `transform` is the one whose computed value is **not** finished by the cascade: percentages in it are proportions of a box that has no size until Yoga has run, so what is carried is the function list and the painter resolves it (ADR-0068).
- **Units:** logical `px` (scaled per window), `%`, `em`, `rem`. Window-scoped media queries: `@media (min-width)`, `(prefers-color-scheme)`, `(prefers-reduced-motion)` — **media queries are not implemented**; a theme is chosen by swapping a stylesheet, and reduced motion is a switch on the renderer (§5). `em` and `rem` resolve against a fixed context rather than the node's own resolved `font-size`, which is wrong and has no effect today because no shipped stylesheet uses them (ADR-0066).
- **`transition`** is a property like any other: resolved by the cascade, so `button` and `button:hover` can declare different ones and an application can turn one off by overriding a rule. It does **not** inherit — a panel that faded its background must not make every label inside it fade too (ADR-0067).
- **Resolution is invalidation-driven**, which is what the frame loop above has always claimed: a node's `ComputedStyle` is cached on its element and re-resolved only when the resolver changes (a theme swap or a hot reload builds a new one), when the style it inherited changes, or when its subtree is invalidated by a pseudo-class or a rebuild. Invalidation is a **subtree** because a descendant combinator makes a node's match depend on an ancestor's state. This took the cascade from 135 µs to about 2.5 µs on a 15-element tree (ADR-0070).
- **Inheritance** is CSS's: `color` and the font properties pass down the element tree; the layout half, `background`, `opacity`, `transform` and the decoration do not — though `opacity` and `transform` both have an *effect* that reaches the whole subtree, which the painter accumulates rather than the cascade. `cursor` is the one exception to CSS — it inherits through the stack of *painted rectangles* instead, so hit testing reads it off whatever the pointer is over (ADR-0057, ADR-0066).
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
- **`bind`** is **one-way** binding against an observable model (small built-in `Property<T>` type; no framework dependency): data flows down into the tree, and what the user did flows back up as an `action`. Markup is handed the read-only `Observable` half, so a control cannot write to the model — `checkbox bind="prefs.frost" change="toggleFrost"` (ADR-0063, amending this bullet's original "one/two-way"). A `bind` value is a **dotted path and nothing else** — `frost`, `prefs.frost` — resolved against a `Bindings` registry, the same way `action` is resolved against a controller (ADR-0062).
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
  (600). A weight is a *face*, not a variable-font axis; see §6.1.
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

**Primitives (`goldberry-core`):** `text`, `icon`, `image` (Blend2D codecs), `spacer`, `row`, `column`, `stack` (z-layering), `scroll` (design-system scrollbar spec: overlay, hover-widening), `canvas` (immediate-mode Blend2D painting surface — the escape hatch and the substrate for charts), `focus-scope`.

**Controls (`goldberry-widgets`):** `button` (primary/secondary/ghost/danger variants via classes), `toggle`, `checkbox` (tri-state), `radio` + `radio-group`, `slider`, `select` (popup-backed dropdown), `text-input` (single-line) and `text-area` — see §17 for the editing-subsystem caveat, `progress` (determinate + indeterminate), `spinner`, `badge`, `tooltip`.

Of those, **`button` and `checkbox` are built**; the other eleven are not. `radio` + `radio-group` is the next one that matters architecturally, because it is the first composite and therefore the first thing to need §7.2's roving arrow-key focus.

**Containers & surfaces (`goldberry-widgets`):** `panel` / `card` (surface + border + radius tokens), `group-box`, `tabs`, `split-pane`, `form` / `field` (label + control + validation message layout, consistent label column), `dialog` (modal, backend popup or in-window layer), `toast`.

**Menus & shell (`goldberry-widgets`):** `menubar`, `menu`, `item`, `context-menu` (any widget: `context-menu=` reference), all rendered in backend popup windows (`SDL_CreatePopupWindow`) so they can escape the window bounds; `tray-icon` (SDL3 ≥ 3.2 tray API) with menu + tooltip; **window decorations** — native by default, optional client-side decorated `titlebar` widget (frost material, window buttons per-platform ordering).

**Data (`goldberry-widgets`):** built on `canvas` with the theme palette: `sparkline`, `line-chart`, `bar-chart`, `area-chart`, `donut-chart`. Axes/legend/tooltip primitives shared; deliberately small — not a plotting library. (Charts shipped as their own module in the original design; see ADR-0014.)

**3D (`goldberry-gpu`):** `canvas3d` — see §12.

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

`design-system.md` §4's accessibility baseline is met in part: contrast is authored to WCAG AA but **not validated in CI**; text scale to 150% is neither implemented nor gallery-enforced; reduce-motion is implemented (§5); reduce-transparency and "always show scroll bars" have nothing to switch yet; hit targets are ≥ 32 for both shipped controls.
Screen-reader bridging (UIA / NSAccessibility / AT-SPI) is planned via **AccessKit** (C ABI, fits the FFM stack) in a post-v1 milestone — but the semantics tree exists from the start precisely so this is an adapter, not a rearchitecture.

## 14. Testing

- `headless` backend renders to `BLImage`; **golden-image tests** run identically in CI on all three OSes (deterministic: embedded fonts, no platform rendering).
- Semantics-tree queries for interaction tests (`click(byRole(BUTTON, "Apply"))`), synthetic event injection through the normal dispatch path.
- Native layer: a **layout probe** — struct layouts and constants checked against the compiled library on every target, which is what replaces the guarantee generated bindings would have given (§3.1); upcall/shaping micro-benchmarks tracked over time.
- The widget showcase in `:widgets` doubles as the visual regression corpus — a widget with no screen there has no pixel coverage. `core-widgets.md` puts it more strictly: "a widget isn't done until it's in the gallery". There is no gallery *app* yet; what exists is a golden image per control per theme per state, which is the same coverage without the screen to browse it on.
- **Animation is testable because the clock is injectable.** A golden image of a mid-transition frame is impossible against a wall clock — the test would have to sleep and would be asserting on whatever the scheduler gave it. A virtual clock makes `clock.advance(50)` the exact frame at 50 ms, on every machine (ADR-0067).

## 15. Distribution

- Build: **Gradle** (Groovy DSL) multi-module — `:core`, `:widgets` (the widget catalog, charts included), `:gpu`, `:natives` (wraps the CMake superbuild) — with a version catalog and convention plugins; CI runs the same Gradle tasks on all three OSes. (The original design specified the Kotlin DSL, a separate `:charts`, and a `:gallery` module; see ADR-0013 and ADR-0014.)
- Published to **Maven Central** under group `io.github.digitalsmile` (base package `io.github.digitalsmile.goldberry`): artifacts `goldberry-core`, `-widgets`, `-gpu`, plus `-natives-{platform}-{arch}` classifier jars (consumable from Gradle and Maven alike).
- Single-jar quick start (fat natives) for tinkering; GraalVM native-image config shipped in the jars (`META-INF/native-image`).
- License: toolkit Apache-2.0. Bundled assets — Inter (OFL), JetBrains Mono (OFL), Lucide (ISC), OpenMoji derivative (CC BY-SA, attribution in About/NOTICE). The statically linked native libraries also redistribute in object form: Blend2D, AsmJit, SDL3 (Zlib), Yoga, HarfBuzz. On Linux SDL loads the system libxkbcommon at run time; it is not redistributed. Full disclosure in `THIRD-PARTY-NOTICES.md` and `licenses/`, verified by `./gradlew checkLicenses`; see ADR-0015.

## 16. Milestones

- **M0 — Skeleton:** superbuild → `libgoldberry` on 3 OSes; FFM bindings + layout checks; SDL3 + headless backends; blank window at correct fractional DPI.
- **M1 — Vertical slice:** styled wrapped paragraph, resized at 60 fps on Linux, macOS and Windows; paragraph cache + upcall benchmarks green.
- **M2 — Widgets & style:** CSS engine, KDL inflater + hot reload, core controls, Nord light/dark, focus/cursor/shortcuts, golden-image CI. *Engines done; **every control in `core-widgets.md` §3 is built except `select`**, whose popup window belongs with M3's overlays. `segmented` closed the catalog and cost two amendments to `design-system.md`: its specified drawing needs per-corner radii that §8's subset does not have, and its specified motion needs the geometry of a box a widget cannot see (ADR-0097). Plus §7.2's roving arrow-key focus with an axis — which `segmented` is the first non-menu to use — §1.3's density, disabled propagating through a subtree, §1.2's contrast floor now checked in CI, and a gesture anchor for controls whose drag is a rate. Custom image cursors are the other gap. `book/src/status.md` has the detail.*
- **M3 — Shell:** menus/popups, tray, dialogs, scroll, forms, decorations opt-in CSD, charts, widget showcase. *Started: the **in-window overlay layer** ships and `hud` is its first occupant — every window's tree is rooted at a `window-root` that floats overlays out of flow at a corner, which is what `toast`, a `dialog`'s scrim and `hud` need and what none of them needed a platform window for (ADR-0100, ADR-0101). The **backend popup window** is built too — `createPopup` on both backends, a popup being a window with an owner and a position in its coordinates, refusable by a driver that has none (ADR-0102). What `menu`, `tooltip`, `popover` and M2's leftover `select` now wait on is the widget layer above it: painting a widget subtree into a second window's frame, routing input to it, and light-dismiss.*
- **M4 — GPU:** `canvas3d`, GPU composition path.
- **M5 — Hardening:** text editing depth, AccessKit bridge, IME preedit, docs, 0.1 release.

## 17. Known deferrals & open questions

Deliberately *not* in v1, with the seams that keep them addable:

- **Text editing depth.** `text-input`/`text-area` v1 = caret, selection, clipboard, undo stack, word ops. Full IME **preedit** (CJK composition), RTL editing, and rich text are deferred; the `KeyEvent`/`TextEvent` split and the semantics tree are the seams.
- **Drag & drop** (in-app first, platform DnD later — SPI gains two methods).
- **List virtualization** (recycling for 10k+ rows; `scroll` is designed to host it).
- **Tables/trees**, rich text display, notifications API, multi-window state management beyond the basics.
- ~~**Open:** KDL `bind` expression scope (dotted paths only vs. mini-expressions)~~ — **settled: dotted paths only**, enforced by the registry, so an expression fails at inflation rather than resolving to nothing (ADR-0062). Negation and formatting stay in Java.
- **Open:** whether `--gb-selection` alpha compositing forces a color-mix() subset; whether client-side decorations are the default on Linux or opt-in everywhere.

### 17.1 Where this document and the design documents disagree

Recorded rather than reconciled, because the design documents are the authority
and each of these needs a decision rather than an edit. `book/src/status.md`
tracks them alongside the implementation's own open questions.

- **The platform primary modifier.** `design-system.md` §2.3 wants accelerators
  expressed against a platform-primary modifier — `Cmd` on macOS, `Ctrl`
  elsewhere — "via one `Shortcut` abstraction". The shipped `Shortcut` refuses to
  remap, arguing that silently translating them makes `Ctrl+C` mean two different
  things depending on where it runs. Both are defensible; they are not
  compatible. The design system wins by default, which makes this a change to the
  code — but it should be an ADR, not a quiet edit, because the counter-argument
  is a real one and is currently the only thing written down. (§7.2)
- **Pixel-precise wheel deltas.** `design-system.md` §2.4 asks for them "with line
  fallback". SDL exposes no pixel axis at all — Wayland and macOS both have one
  underneath and SDL does not surface it — so what ships is lines with a
  touchpad's fractions preserved. Here the *implementation* is the newer finding
  and the design system is the aspiration; reaching it means going around SDL to
  the platform (ADR-0056). (§7.1)
- **One module or two.** `core-widgets.md` says every built-in lives in a single
  `goldberry-core` module, separated by package. The build ships `:core` and
  `:widgets` as separate modules and artifacts (ADR-0014). The split is
  load-bearing and is not moving; the sentence is what needs amending. (§11)
- **`text style="body"`.** `core-widgets.md` §2 gives `text` a `style=` attribute
  for the typography tokens. What ships is `class="body"` — the same thing spelled
  the way CSS already spells it. A second spelling may still earn its keep when
  `field` and `form` need labels. (§10.1)
- **A disabled container disabling its descendants.** `core-widgets.md`'s widget
  contract says disabled propagates down the tree for input *and* semantics.
  What works is a control passing the flag to the parts it builds itself, which
  is enough for `checkbox` and will not be enough for `form` or `group-box`
  (ADR-0065).