# Closing the downstream gaps — plan

Written 2026-11-08, before any code, against master at `992c8c21`. Two
applications built on Goldberry sent what they needed and did not get:

- [`goldberry-gaps-1.md`](goldberry-gaps-1.md) is **Lantern**'s, a game, pinned
  at `2026.2-20261011.204446-14`. Twelve entries, G-1 to G-12. Here they are
  **L-1 … L-12**.
- [`goldberry-gaps-2.md`](goldberry-gaps-2.md) is **Tessera**'s, a messenger,
  reviewed on 2026-11-07 against `2026.2-20261025.195555-18`. Its open,
  dropped and closed rows are G-01 to G-32. Here they are **T-01 … T-32**.

Both files are the applications' own documents and stay as they arrived:
their bare `adr/NNNN` links point into the applications' repositories, not this
one. This file is Goldberry's record: what each entry is today, what will be
done about it, in which order, and what the owner has to decide first. §8 is
the status table. Keep it current as work lands.

**How the work is recorded.** Each entry that is built gets an ADR, and so
does each entry that is declined. A refusal is a decision too, in the shape of
ADR-0291. New ADRs are parked in `docs/snapshot/adr/` with lines in
`docs/snapshot/README.md` and `README-lines.md`, numbered from **0676**
(0675 went to the type-scale change on 2026-11-08). List that folder before
taking a number, because numbers must stay contiguous. Guide sections are
parked in `docs/snapshot/<page>.md`. Doc comments link the guide, not ADRs.
Every item ships with its tests in the same commit.

**What goes back to the applications** is kept apart from this plan, one file
each: [`answers-lantern.md`](answers-lantern.md) and
[`answers-tessera.md`](answers-tessera.md). Each says per entry what the
application can do today and what landed. An answer that applies to both
is copied into both files, because each application reads only its own.
They are written as the work lands, not at the end.

**Where the work happens.** On 2026-11-08 another session had a type-scale
change in progress in the main tree, touching 380 files. This batch is built
in a worktree, `../goldberry-gaps` on the branch `gaps-lantern-tessera` from
`992c8c21`, and moved into the main tree as staged patches once the other
change has landed. ADR numbers are settled at that point. That happened on 2026-11-13:
the whole batch is staged in the main tree, uncommitted, and the worktree is
no longer where the work is.

## 1. What the entries are today

All 44 entries were checked against the tree on 2026-11-08 (four read-only
passes, with the claims that change an entry's status checked again by hand).
Since the two pins, master gained `GpuDevice.adapter()`, run-time present
modes and frame caps, render scale and FSR 1, and the ray tracer (5f9e621c …
992c8c21). None of that closes an entry except half of L-10. Size: S is a day
or less, M a few days, L a week or more, XL a plan of its own.

### Lantern

| ID | Gap | Today | Size |
|----|-----|-------|------|
| L-1 | A continuous `Canvas3d` re-uploads the whole UI layer every frame | **Open, cause found.** `Canvas3dSurface.render` (gpu `view/Canvas3dSurface.java:85`) stamps the painter with the frame time. `Canvas3dPainter` is a record, so `RenderObject.sameAppearance` (`paint/tree/RenderObject.java:906-920`) sees a new painting every frame. The box counts as damage, and `SdlCompositedWindow.present` (`:92-114`) uploads it. The compositor already redraws a layer on its own (`Canvas3dLayer.needsRender`, `LayerTextures`). Only `layer.frame(nanos, continuous)` ties the clock to the painter | S–M |
| L-2 | No way to give a widget a computed style value | **Partly closed.** `vw`/`vh`, `min()`, `calc()` and `var()` exist since the CSS batch. `scale` still refuses a length divided by a length (`calc/Folding.java:82-86`, asserted by `MathEverywhereTest:81`), so `min(100vw / 2560px, 100vh / 1440px)` does not fold. Works today: a generated `:root { --hud-scale: X }` sheet from `Application.stylesheets()` plus `Host.restyle()`, as the showcase's `ReadingSize` does | S–M |
| L-3 | Frame statistics are means only | **Open.** `FrameStats` has means and spans; `FrameRing` keeps 60 frames privately with no accessor or listener. `PresentTimings` has no layer-render stage: `Canvas3dRenderer.render` time is folded into `submitNanos` (composited) or raster (read back) | M |
| L-4 | An offscreen filmstrip repaints every frame whole | **Open.** `Filmstrip.frame` (`offscreen/Filmstrip.java:204-224`) allocates a buffer, fills it and paints the whole tree, and throws away `FrameSequence.Stages`. Allocating per frame is documented on purpose: a returned `Image` is a view over its buffer | M |
| L-5 | No capture of a running window | **Closed before the pin.** `Window.capture()` (`Window.java:940`, GPU layers included) and the launcher's `--capture=PATH` landed in 1e3a7515 on 2026-10-06. Only gap left: `--capture` fires when a `--frames=N` run ends, and there is no `Host` shorthand | S |
| L-6 | The natives jar is not a module | **Mostly closed before the pin.** Each classifier jar has had `Automatic-Module-Name: dev.goldberry.natives.<target>` since 2026-10-02 (`natives/build.gradle:592`), and `ClassifierJar` finds the library on the module path. An automatic module cannot go into a `jlink` image, which is probably what keeps Lantern's class-path part | S |
| L-7 | A click is synthesised for the primary button only | **Open.** `PointerRouter.java:1088` tests `button == PRIMARY`. 41 `CLICKED` handlers in 37 files assume the primary button | S + audit |
| L-8 | No click handler a widget can be given | **Partly closed.** `Pressable` (widgets `controls/pressable`) exists since 2026-10-02, but it is an extra box, takes a `Runnable`, handles the primary button only, and is a named Tab stop. `Attributes` has hover hooks only (`PointerRouter.hook`, `:2846-2858`). `PointerEvent.local()` already gives fractions of the box | M |
| L-9 | An image decodes only into its own premultiplied BGRA buffer | **Open.** `PixelFormat` has one entry on purpose. `Image.decode` always allocates (`Image.java:159-163`). The buffer is direct, not heap as the entry says. `GpuFeatures` lists `B8G8R8A8_UNORM`, so Lantern's alternative works today | M |
| L-10 | A GPU device cannot be asked for, or asked about, its adapter | **Half closed.** `GpuDevice.adapter()` returns name, driver, version and info (5f9e621c). Vendor appears only inside the name, and there is no integrated/discrete kind (SDL reports strings only). Low power is **already the default** (`SdlGpuDevice.Options.defaults()`), but nothing public changes it: `DeviceOptions` reads only driver and debug. `OffscreenGpu.toString` prints only the driver | S |
| L-11 | An offscreen render of a tree that paints nothing throws | **Open, on purpose.** `WidgetRenderer.java:540-541` refuses an empty root, and `NothingTest.rootIsRefused` asserts it | S |
| L-12 | Measuring text needs the native library | **Open.** `Font` is HarfBuzz plus Blend2D through `NativeLibrary`. Bidi, itemizing and line breaking are pure Java; shaping and metrics are not. A Java fallback without GSUB/GPOS would not measure "exactly as the game lays it out" | L |

### Tessera, open rows

| ID | Gap | Today | Size |
|----|-----|-------|------|
| T-01 | The weaver is not published | **Open.** Left out of `PublishedModules` on purpose. `PublishedModulesTest:77` asserts it, and `weaver/build.gradle:88` disables publishing. `book/src/ahead-of-time-binding.md:49,57,101` still tells users to depend on `goldberry-weaver`. Any library with `@Markup` widgets needs its `--catalog` half, not only native images. In this repo it runs through the precompiled plugin `goldberry.weave` | S (jar) / M (plugin) |
| T-02 | Rich-text input | **Open.** `Editor`/`TextEdit` hold one font and a plain `String`. `TextDocument.Shaper` sees only a line's text. Parts to build on: `Paragraph.join`, `SpanPaint`, `Editor.selectionRects()`, `Popover`, MIME clipboard | XL |
| T-03 | Rich-text display with entities | **Partly closed** (as reported). `RichText` is a stateless leaf with no input. `content.select` in `:html` cannot be reused from `:widgets`. Core has the geometry: `TextGeometry.offsetAt`/`selectionRects`, `Paragraph.offsetAt`. No spoiler anywhere | M–L |
| T-04 | Virtualized list with sticky section headers | **Partly closed** (as reported). `ListView` has measured rows and keeps the reader's place. An `Affix` inside a row pins only within that row, and the row is virtualized away anyway | M |
| T-05 | Camera and microphone | **Open.** Only playback SDL audio symbols are exported (`goldberry.symbols:330-345`). SDL is built with V4L2 camera support; PipeWire camera is off. `docs/ARCHITECTURE.md` plans `goldberry-camera` and `goldberry-mic` | L |
| T-06 | Encoders and public codecs | **Open.** libvpx is built with `--disable-vp9-encoder`. libopus contains its encoder but exports none of it. No `Encoder` SPI. libyuv has no ARGB→I420. The size gate fails above 6 MB (3.7 MB today) | L |
| T-07 | Video tile fed by app-pushed frames | **Partly closed.** `VideoView` takes a `final` `MediaPlayer`. But `PictureRenderer` (exported since 9710e57e, before the pin) renders an app-built `VideoPicture`/`VideoPlanes` on the GPU from any `Canvas3dRenderer`, so Tessera can drop the CPU `Canvas` now | M |
| T-08 | Web view JS bridge | **Open.** The shim exports 20 webview symbols but no `init`; vendored upstream has `webview_init`. `BackendWebView.eval` exists, but `WebViewController` exposes only `cookies`. Page→app already works through `bind`/`return` | S–M |
| T-09 | Secure credential storage | **Declined, ADR-0291** ("The OS keychain will not be a gap either"). Tessera is the second application to ask, after brd | — |
| T-10 | Deep links and single instance | **Declined, ADR-0291**, but one promise in it is broken. The ADR said a macOS deep link "will fall out of" binding drop events. Drops are bound now, yet `Sdl3Backend.java:1028-1033` drops an event whose window id is unknown, and the URL arrives with window 0 | S |
| T-12 | Pinch, `Path.contains`, tile cache | **Open.** SDL 3.4.18 has `SDL_EVENT_PINCH_*` (Cocoa, Wayland, XInput2); `SdlEventType` maps none. `Path` has no `contains`; `ClipShapes` has a Java winding test for polygons. No tile cache: a canvas is cached whole | M + S + M |
| T-13 | `font-feature-settings` | **Open.** Set aside by the parser. `HarfBuzz.shape` passes `NULL, 0` for features ("until the CSS layer has font-feature-settings"). The `hb_feature_t*` path already exists in the shim | M |
| T-15 | `backdrop-filter` | **Open.** Ignored with a lint warning. `filter` works on the group's own raster. `Frame` cannot read back what is under a box. Over a GPU layer it would need a compositor blur | L |
| T-16 | Layout (FLIP) animation | **Open.** Parts exist: `motion` overlays at paint time, `RenderObject.lastRect`, keyed nodes | M–L |
| T-17 | Screen-reader bridge | **On hold, ADR-0440.** Not rescheduled | — |
| T-18 | Runtime text scaling | **Open.** `WidgetRenderer.textScale` clamps to 0.9–1.5. `HostedWindow.renderer()` (`:627-648`) never sets it, and neither `Application` nor `Host` reaches it | S |
| T-19 | Windows notifications: click and badge | **Open.** `WindowsNotifier` posts a balloon with no `NIF_MESSAGE`, and it is marked unverified on Windows. Badge needs COM `ITaskbarList3`, which would be the first COM call in `:natives` | M |
| T-20 | Drag out to the OS | **Open.** SDL3 has no drag source. It needs four hand-written platform paths (OLE, `NSDraggingSession`, XDND, `wl_data_source`), which ADR-0003/0041 rule out | XL |
| T-22 | SVG rendering | **Open.** Only `SvgPath`. `:assets`' `SvgShapes` is build-time. The pure-Java Lottie renderer (`core/image/lottie`) already paints fills, strokes, gradients, transforms, clips and masks through `Frame`. The architecture names `goldberry-vector` on ThorVG | L |
| T-23 | PDF page rendering | **Parked** on the prebuilt-PDFium licence and provenance question (`docs/TODO.md:202-206`, `content-widgets.md` §2) | XL |
| T-24 | HTML mail: `<style>` and `style=` | **Open, by design.** Parsed into the model and applied by nothing (`HtmlWidgets.java:63-65`). No inline-declaration slot on a widget | L |
| T-25 | Spellcheck | **Open.** Nothing exists. A wavy underline is not built either (`TextDecoration`) | L–XL |
| T-26 | Pluralization and message formatting | **Open.** Nothing. The toolkit's own labels (`TextMenu`'s "Copy") are hard-coded English | M–L |
| T-29 | OS-wide global hotkeys | **Open.** Per-window shortcuts only | L |
| T-30 | Shared memory between `Canvas3d` layers and Board tiles | **Unclear ask.** `GpuLayer` is public, `CopyPass.upload` takes damaged regions, and `PictureRenderer` covers video. `gpu.video` is still exported only to `media`. Zero-copy between Blend2D and GPU textures does not exist | S–L |
| T-31 | Goldberry on the web | **Open.** Two backends, "the complete list". A remote-rendering backend over the headless one would fit the SPI; a wasm port would be a second binding layer | XL |
| T-32 | Web view: private store, cookie delete, user agent | **Open.** One pinned default WebKit context. Delete and user agent can be set after creation (S each per engine). An ephemeral store is creation-time, so it needs the shim to build views itself or a patch on webview/webview | S + M–L |

### Tessera, dropped and closed rows

T-11 (test fixtures), T-21 (window-state helper) and T-27 (command palette)
are dropped by Tessera. T-14 (`cubic-bezier()`) and T-28 (Noto emoji) are
closed. Nothing to do. T-11 is unchanged on purpose: `goldberry.publish`
skips the test-fixture variants, and `GoldberryTestAccess` would split the
`dev.goldberry` package.

## 2. Answers that need no code

These go back to the applications first, because they change what each one
waits for:

- **L-5:** `host.window().capture()` and `--capture=PATH` have existed since
  2026-10-06.
- **L-6:** the classifier jars have had automatic-module names since
  2026-10-02. Ask: is `jlink` what keeps the class-path part? If so, W1.6
  closes it.
- **L-2:** a generated `:root` sheet plus `Host.restyle()` gives an exact
  scale today, without the 35-rule ladder. W1.9 makes it pure CSS.
- **L-9:** a `B8G8R8A8_UNORM` page texture avoids the copy today.
- **L-10:** the window and `OffscreenGpu` already ask SDL for the low-power
  GPU, and `GpuDevice.adapter().name()` says which one ran.
- **T-07:** `PictureRenderer` in a `Canvas3dRenderer` draws pushed I420/BGRA
  frames on the GPU today.
- **T-09, T-10:** ADR-0291 stands unless the owner reopens it (D7). The macOS
  half of T-10 is a bug against that ADR and is fixed in W1.10 either way.
- **T-17:** ADR-0440 stands.
- **T-30:** ask Tessera which of the three it means (D16).

## 3. Decisions for the owner

Each decision becomes an ADR when it is taken. The recommendation is the first
option.

**Answered by the owner on 2026-11-08:**

| # | Chosen | What it means for the waves |
|---|--------|-----------------------------|
| D1 | (a) | `AUX_CLICKED` (W1.2) |
| D2 | (a) | `Attributes.onClick` on any box (W1.2) |
| D3 | (a) | Typed division in `calc()` (W1.9). No inline layer, so W3.3 takes the generated-class route |
| D4 | **(b)** | An empty root renders as nothing **everywhere**: windows too, and `NothingTest.rootIsRefused` is rewritten (W1.4) |
| D5 | **(b)** | The weaver jar **and** a `dev.goldberry.weave` Gradle plugin (W1.8) |
| D6 | (a) | `goldberry.gpu.power` and `OffscreenGpu.open(DeviceOptions)` (W1.3) |
| D7 | **(b)** | ADR-0291 is reopened. A `Keychain` over the three system stores (W2.11). The pure-Java single-instance helper that ADR-0291 sketched goes into W1.10 with the macOS URL delivery. Registering a URL scheme stays packaging, because a library cannot install itself |
| D8 | **(b)** | Separate `goldberry-camera` and `goldberry-mic` modules (W3.4) |
| D9 | **(b)** | No encoders. "Bring your own" is documented, and T-06 is declined by an ADR (§7) |
| D10 | **(b)** | `goldberry-vector` on ThorVG, a new native library (W3.6) |
| D11 | (a) | PDF stays parked (§7) |
| D12 | (a) | The rich-text editor is Goldberry's; `docs/rich-text-input.md` comes first (W3.1) |
| D13 | (a) | `backdrop-filter` on CPU frames; the opaque fallback over GPU layers (W3.2) |
| D14 | (a) | L-12 is declined by an ADR (§7) |
| D15 | **do T-29, T-20, T-19; plan T-31** | Global hotkeys (W3.8), drag-out to the OS on all four platforms (W3.9), Windows notification click and badge (W2.12). T-31 gets its own plan, `docs/web-target.md`: TeaVM, or a GWT-like compiler of the toolkit's own (W3.11) |
| D16 | **(c)** | Real zero-copy between Blend2D rasters and GPU textures (W3.10) |
| D17 | **(b)** | `dev.goldberry.i18n` with generated CLDR plural tables and message formatting (W2.13) |
| D18 | **(b)** | Bundled Hunspell (W3.7) |

The table below is kept as it was when the questions were asked.

| # | Question | Options (recommended first) | Blocks |
|---|----------|-----------------------------|--------|
| D1 | How does a secondary or middle click arrive? | (a) A new `PointerEvent.Kind.AUX_CLICKED` under the same press-and-release rule, as the DOM's `auxclick`; no handler changes. (b) `CLICKED` for every button, and all 41 handlers audited for `button() == PRIMARY` | W1.2 |
| D2 | May any box be clickable without being focusable? | (a) Yes: `Attributes.onClick(Consumer<PointerEvent>)` hears `CLICKED` and `AUX_CLICKED` with the local position. `Pressable` stays the accessible route, and the guide says so. (b) Only through `Pressable`, extended to all buttons | W1.2 |
| D3 | How does a computed value reach a declaration? | (a) CSS Values 4 typed division (length ÷ length gives a number) in `Folding`, so `scale: min(100vw / 2560px, 100vh / 1440px)` is plain CSS. (b) Custom properties set from Java on a node: an inline layer, which bends the rule in `Styled` | W1.9 |
| D4 | What does an empty tree render offscreen? | (a) `Offscreen` and `Filmstrip` answer the background, and a window still refuses. (b) Relax the rule everywhere and rewrite `NothingTest` | W1.4 |
| D5 | Is the weaver published, and how? | (a) A plain `goldberry-weaver` jar now, as a new BOM-only kind in `PublishedModules`; the Gradle plugin after the release, with the natives-picking plugin `docs/TODO.md` already defers. (b) Jar and `dev.goldberry.weave` plugin together | W1.8 |
| D6 | Where does the power preference live? | (a) `DeviceOptions` gains `goldberry.gpu.power=low\|high` for the one process device, and `OffscreenGpu.open(DeviceOptions)` takes it where no device exists yet. (b) Per call, which conflicts with the shared device | W1.3 |
| D7 | Reopen ADR-0291 for the keychain and single instance? | (a) No: keep it, fix the macOS URL delivery (W1.10), and add a guide recipe for the `FileLock` + Unix-socket handoff. (b) Reopen for a `Keychain` over three system APIs (M), now that a second application asks | T-09, T-10 |
| D8 | Camera and microphone: where? | (a) Packages in `:media` (`media.capture`): microphone over SDL recording streams, camera frames as `VideoPlanes`, which also feeds T-07's picture source. (b) Separate `goldberry-camera` and `goldberry-mic` modules, as `ARCHITECTURE.md` says. Also: turn on SDL's PipeWire camera on Linux | W3.4 |
| D9 | Encoders: in scope? | (a) An `Encoder` SPI with Opus only (already linked in, small). VP9 after measuring its size against the 6 MB gate. SVC layers left to applications. (b) Neither; document "bring your own" | W3.5 |
| D10 | SVG: which engine? | (a) Pure Java: a new optional `:vector` module (it requires `java.xml`) that paints through the Lottie painter's primitives and provides an image decoder as a service, as `:emoji` does. (b) `goldberry-vector` on ThorVG, a new native library | W3.6 |
| D11 | PDF? | (a) Stays parked until the PDFium provenance ADR is written. (b) Build PDFium from source with gn | T-23 |
| D12 | Is the rich-text editor Goldberry's? | (a) Yes, as Tessera's ADR 0020 already decided on its side. Plan it in its own document first (`docs/rich-text-input.md`): marks vs tree, entity model, atomic mentions with IME preedit, undo with marks. (b) No | W3.1 |
| D13 | `backdrop-filter`: how far? | (a) CPU frames only: blur what is already painted under the box. Over a GPU layer it draws the opaque fallback, as `ARCHITECTURE.md` plans. (b) A compositor-side blur too | W3.2 |
| D14 | Text without the native library (L-12)? | (a) Decline: a measurement that differs from the game's shaper is worse than shipping the library. ADR in the shape of ADR-0291. (b) A Java fallback over the `sfnt` readers without GSUB/GPOS | L-12 |
| D15 | Platform reach beyond SDL: global hotkeys (T-29), drag-out (T-20), web (T-31), Windows toasts (T-19) | (a) Decline T-20 and T-31 under ADR-0003/0041. Park T-29 until a portal-only Linux version is wanted. Do the T-19 click (S), but only once a Windows lane can drive it. (b) Take some of them on | — |
| D16 | T-30: what is "shared memory"? | Ask Tessera: (a) a documented recipe (tiles as textures in a `Canvas3dRenderer`), (b) a toolkit tile-texture cache, or (c) real zero-copy | — |
| D17 | Localisation (T-26)? | (a) Decline plural rules and message formatting (ICU4J is ~13 MB, and Tessera has its own). Separately, make the toolkit's own labels replaceable. (b) A `dev.goldberry.i18n` package with generated CLDR tables | — |
| D18 | Spellcheck (T-25)? | (a) Later, after T-02, behind a `SpellChecker` SPI with the OS checkers; the wavy underline lands first because CSS wants it anyway. (b) Bundled Hunspell | W3.7 |

## 4. Wave 1 — small, and nothing waits on hardware

Each item is S or small M, and most touch one module. They are ordered by what
they cost the applications today.

| # | Entries | Work | Where | Tests | Exit |
|---|---------|------|-------|-------|------|
| W1.1 | L-1 | Hand the frame time to the layer in `Canvas3dSurface.render()` rather than through the painter. The painter changes only on a new revision or render scale. The read-back path (`Frame.gpuLayer`: Offscreen, no compositor, nested groups) bakes the layer into the CPU frame and still needs damage every frame, so the surface asks whether it is composited | `gpu.view`, `core` `paint.tree` | `Canvas3dTest`, `Canvas3dLayerTest`, `DamageTest`, `CompositorTest`; a new case: a continuous canvas with a static UI uploads 0 bytes after the first frame | `--log-fps`-style upload of 0 KB a frame over a calm continuous canvas, in a test |
| W1.2 | L-7, L-8 | D1 + D2: `AUX_CLICKED` in `PointerRouter.pointerReleased` with the same same-node rule, and `Attributes.onClick` copied through every `with*`, constructor and the KDL `Attributes.of`, run while bubbling | `core` `input`, `widget.attr`; markup | `PointerRouterTest` (buttons), `HoverHookTest` as the model for an attribute hook, a markup inflation case | A right press released off the node is no click; a styled `panel` with `onClick` gets button and fractions |
| W1.3 | L-10 | D6: `goldberry.gpu.power`, `OffscreenGpu.open(DeviceOptions)`, and the adapter name in `OffscreenGpu.toString` | `gpu.composite`, `gpu.offscreen` | `DeviceOptionsTest`, `OffscreenGpuTest` | The bench can print the adapter it ran on, and the preference can be flipped |
| W1.4 | L-11 | D4 (b): `WidgetRenderer` answers an empty root with an empty box instead of throwing, in a window and offscreen alike | `core` `widget` | `NothingTest` (the refusal becomes "renders nothing"), `OffscreenTest`, `FilmstripTest`, a window case | `Offscreen.render(Widget.nothing())` is a background-only image, and a window whose root builds to nothing shows its background |
| W1.5 | L-5 | `--capture` while running (`--capture-at=N` writes frame N and carries on), and `Host.capture(Path)` | `core` `Launcher`, `Host` | `LauncherOptionsTest`, `HeadlessBackendTest` | A running app writes a PNG without exiting |
| W1.6 | L-6 | Only if Lantern confirms `jlink`: compile a real `module dev.goldberry.natives.<target> {}` into each classifier jar | `natives/build.gradle` | `ClassifierJarTest`; `:example:run` on the module path (it is module-path-only) | A `jlink` image holds the natives |
| W1.7 | T-18 | `Host.textScale(double)` (live, marks styles dirty) and `Application.textScale()` (initial), applied in `HostedWindow.renderer()`, popups, `Offscreen` and `Session` | `core` | `TextScaleTest`, `TextScaleLayoutTest`, a host case | A settings row changes text size without a restart |
| W1.8 | T-01 | D5 (b): publish `goldberry-weaver` (sources and javadoc, in the BOM and not the umbrella) **and** a `dev.goldberry.weave` Gradle plugin that does for an application what `goldberry.weave` does here: the catalog after `classes`, model weaving on request or for a native image. The book's dependency snippet gets fixed (it needs `:core` at weave time). The book prose is parked | `build-logic` `publish`, `weaver/build.gradle`, a new published plugin project | `PublishedModulesTest` (flip the weaver assertion), `CentralPomTest`, a Gradle TestKit test that applies the plugin to a sample project | The weaver and the plugin are in the next snapshot |
| W1.9 | L-2 | D3 (a): typed division in `Folding`/`Scalars`, and the viewport in the fold context `Transform` uses | `core` `css.value.calc`, `css.value` | `FoldingTest`, `MathEverywhereTest` (the `calc(1px)` refusal stays; `100vw / 2560px` folds), `LateLengthsTest` | Lantern's ladder becomes one declaration |
| W1.10 | T-10 | D7 (b). A drop with no window becomes `BackendEvent.OpenUrl` → `Application.onOpenUrl(URI)`; that is the macOS delivery ADR-0291 promised. A pure-Java `SingleInstance` (a `FileLock` plus a Unix-domain socket under the application's data directory) hands a second launch's arguments to the first and posts them on the UI thread through the same `onOpenUrl` | `core` `render.backend.sdl3`, `Application`, a new `dev.goldberry.desktop.instance` | `Sdl3BackendTest`, `FileDropTest`, a `SingleInstanceTest` with two instances in one JVM over a `@TempDir` | A windowless `DROP_FILE` reaches the application (headless test; the macOS check by hand), and a second instance's URL reaches the first |
| W1.11 | T-08 | `goldberry_webview_init`, webview ABI 8 → 9, `WebViewSpec.initScript`, and `eval`/`postMessage` on `WebViewController`. **Same ABI bump as W2.8's cookie and user-agent half**, so land them together | `natives` shim + `natives.webview`, `core` `render.web`, `widgets` `core.web` | `WebviewBindingTest`, `WebViewSpecTest`, a `WebView*Test` that reads a value the init script set | A Mini App sees its API before its own scripts run |

## 5. Wave 2 — medium, one module or two each

| # | Entries | Work | Where | Tests | Decision |
|---|---------|------|-------|-------|----------|
| W2.1 | L-3 | A layer-render stage in `PresentTimings`/`FrameRing`, percentiles over the ring, and a `Window` frame listener fed from `Window.paint`. Recording stays allocation-free, so the listener gets a reused view and not a record per frame | `core` `stats`, `gpu.composite` | `FrameRingTest`, `CompositorTest` | Ring length or listener only (the listener covers a 5 s window) |
| W2.2 | L-4 | A measuring strip, `Offscreen.measure(...)`: one reused buffer, `RenderTree.damage` then `paint(frame, damage)`, returning stages and damaged area per frame through W2.1's record. `frame()` keeps its documented per-frame buffer | `core` `offscreen` | `FilmstripTest`, `DamageTest`; a frame with nothing changed paints 0 px | Needs W2.1 |
| W2.3 | L-9 | `Image.decodeInto(ByteBuffer, stride, ChannelOrder)` with a native swizzle (bind Blend2D's `BLPixelConverter`) or a format on `BlendDecodedImage.copyInto`. `PixelFormat` keeps one entry | `core` `image`, `natives.blend2d` (ABI bump) | `CodecTest`, `ImageTest` | — |
| W2.4 | T-13 | `font-feature-settings` (and `font-variant-numeric: tabular-nums`) in `FontValues` → `Typography` → font and paragraph cache keys → `HarfBuzz.shape` with a packed `hb_feature_t` array | `core` `css`, `text.font`; `natives.harfbuzz` | `FontVariationTest` as the model, `CssParserTest`, a `tnum` width golden | Inherited, per the spec |
| W2.5 | T-04 | `ListView` sections: a key per row and a header widget. One pinned header for the first visible row's section, pushed up by the next section's start | `widgets` `panel.list` | `ListMeasuredTest`, `ListVirtualTest`, a sticky golden | Are headers rows (skipped by Home/End and typeahead) or decoration? |
| W2.6 | T-03 | A stateful rich-text view, or state inside `RichText`, that keeps its `TextLayout`. Pointer → offset → run gives a per-`Run` `onPress` and a hover cursor. Drag selection and copy through `TextGeometry`. A spoiler as a `SpanPaint` kind that covers its rects until revealed | `widgets` `text`, `core` `text` | `RichTextTest`, `RichTextGoldenTest`, `TextGeometryTest` | Shared selection model with `content.select`, or not |
| W2.7 | T-07 | A public picture source (`shownPicture()` plus a "next picture due" signal) that `MediaPlayer` implements. `VideoView`, `VideoSurface` and `FollowingState` generalised over it. The borrowing rule (bytes valid for two more pictures) becomes part of its contract | `media` `view`, `picture` | `VideoOnGpuTest`, `VideoPlaybackTest` | Package |
| W2.8 | T-32 | Cookie delete and clear, and the user agent, on all three engines. W1.11 took the web view ABI to 9; while nothing is released in between, W2.8 adds its symbols under the same 9. The ephemeral store goes to its own item once the shim builds its own views or carries a patch | `natives` shim, `core` `render.web`, `widgets` `core.web` | `WebViewSignInTest` | Carry a webview/webview patch? |
| W2.9 | T-12 | `Path.contains(x, y, FillRule)` in Java (flatten, then winding), also fixing `ClipShapes`' path case. Pinch through `SdlEventType` → `Sdl3Backend` → a `PointerEvent.Kind.PINCH` with a scale. The tile cache stays the application's | `core` `paint`, `input`; `natives.sdl.event` | `ClipPathParserTest`, a new `PathContainsTest`, `WheelAndCaptureTest` | — |
| W2.10 | T-16 | Opt-in layout animation: after layout, a keyed node whose rect moved gets an inverted translate and scale in its motion overlay, animated to identity. Damage covers both rects while it runs | `core` `motion`, `paint.tree` | `MotionTest`, `DamageTest`, a filmstrip golden | Opt-in by property or wrapper; identity across a parent change |
| W2.11 | T-09 | D7 (b): `Keychain` (`store`, `load`, `delete` by service and account). Linux: the Secret Service over the existing `Dbus` binding, so no libsecret. macOS: Security.framework `SecItemAdd`/`SecItemCopyMatching`/`SecItemDelete`. Windows: advapi32 `CredWriteW`/`CredReadW`/`CredDeleteW`. All are system libraries through FFM, with no new native dependency. macOS and Windows are UNVERIFIED until a lane runs them | `natives` `desktop.keychain`, `core` `desktop.keychain` | A Secret Service test against a private `dbus-daemon` with a fake service; the platform switch tested like `NotificationCenterTest` | — |
| W2.12 | T-19 | D15: `NIF_MESSAGE` on the balloon, and `NIN_BALLOONUSERCLICK` caught by a message-only window of its own, so it never passes through SDL's window procedure. Then the click runs the pending action, as `postLinux` does. Badge through COM `ITaskbarList3::SetOverlayIcon`, the first COM vtable call in `:natives`, kept in one small `Com` helper | `natives` `desktop.notify`, `core` `render.desktop.notify` | `NotificationCenterTest` (platform case); the Win32 half UNVERIFIED until the Windows lane drives it | — |
| W2.13 | T-26 | D17 (b): `dev.goldberry.i18n`. Plural rules generated from CLDR's `plurals.json` into Java by a `tools/` generator (the cardinal and ordinal rule language compiled to switch code). A `Messages` formatter for the ICU MessageFormat subset `{n, plural, …}`, `{x, select, …}`, `#` and arguments. The toolkit's own labels (`TextMenu` and others) read from a replaceable bundle | `core` `i18n`; `tools/i18n` | `PluralRulesTest` against CLDR's own sample numbers for every locale; `MessagesTest`; the toolkit labels in ru | — |

## 6. Wave 3 — each needs a plan of its own first

These are L or XL. Each starts with a document in `docs/` that finds what is
there and lists decisions and phases, as `gpu-gaming-plan.md` did, and only
then with code.

| # | Entries | First step |
|---|---------|------------|
| W3.1 | T-02 | `docs/rich-text-input.md` (D12): a styled-text model with how ranges move on edit, a span-aware `Shaper` (line offset plus spans, shaped per run and joined), marks in `EditHistory`, toggle keys, a selection popover. A new `rich-input` widget over `Editor` rather than more `TextArea` arguments. T-25's checker plugs into the same document later |
| W3.2 | T-15 | D13 (a): a `backdropFilter` field in `Compositing`, a read-back of the painted region in `Frame`, `Filters.apply` or `CoverageBlur`, and damage widened by the blur's reach. Risks: partial repaints, opacity and promoted groups |
| W3.3 | T-24 | Apply a subset. `style=` becomes a generated class per element plus a rule, since D3 took no inline layer. `<style>` selectors are rewritten into the `html-` namespace and scoped to one view. Theme colours against author colours is the open question |
| W3.4 | T-05 | D8 (b): two new modules, `goldberry-mic` (SDL recording streams) and `goldberry-camera` (SDL camera, frames as `VideoPlanes` for W2.7's picture source). Plus the SDL symbols, device hot-plug events and a native ABI bump, as audio-out (ADR-0462) and the tray (ADR-0191) did. Turn on SDL's PipeWire camera on Linux |
| W3.6 | T-22 | D10 (b): `goldberry-vector` on ThorVG, a pinned commit in its own superbuild (as `:media` has), with `svg-view` and an `image/svg+xml` decoder provided as a service. Lottie stays on the pure-Java renderer. The licence (MIT) and size go in the ADR |
| W3.7 | T-25 | D18 (b): the wavy `text-decoration-style` (core CSS) first. Then Hunspell built from a pinned commit into a native library of its own, a `SpellChecker` SPI, checks off the UI thread per `TextDocument` line, and suggestion rows in `TextMenu`. Dictionaries come from the application: their licences differ per language, so none ships in a jar |
| W3.8 | T-29 | D15: `GlobalShortcuts`. Wayland: `org.freedesktop.portal.GlobalShortcuts` over `Dbus`, with press and release from Activated and Deactivated. X11: `XGrabKey` on the root window. Windows: `RegisterHotKey`, plus a low-level keyboard hook for the release push-to-talk needs. macOS: Carbon `RegisterEventHotKey` (pressed and released). A shortcut is granted, not assumed: on Wayland the user approves it |
| W3.9 | T-20 | D15: drag out to the OS, behind `BackendWindow.startDrag(DragOffer)`, which defaults to refusing. `PointerRouter` offers it when an in-app drag leaves the window. Windows: OLE `DoDragDrop` with `IDataObject`/`IDropSource` as FFM vtables. macOS: `beginDraggingSessionWithItems:` through `ObjC`. X11: XDND as source. Wayland: `wl_data_device.start_drag` on SDL's display and seat. This goes against ADR-0003/0041's "no hand-written platform backends" for one capability, and its ADR says so |
| W3.10 | T-30 | D16 (c): zero-copy between a Blend2D raster and a GPU texture. Where the device allows it, a raster's pixels live in memory the GPU samples (Vulkan `VK_EXT_external_memory_host` or a persistently mapped upload buffer the texture is copied from on the GPU, Metal shared storage). Elsewhere it falls back to staging. Plan first: `docs/zero-copy-tiles.md` |
| W3.11 | T-31 | D15: `docs/web-target.md`, a plan only. The options to weigh: TeaVM (Java bytecode → JS/Wasm, which already exists) against a GWT-like source compiler of the toolkit's own. In both cases the FFM layer needs a browser replacement: Blend2D, HarfBuzz and Yoga built to Wasm, or Canvas2D/WebGPU drawing. No code until the plan is read |

## 7. Declined or parked, each with an ADR

Written as decisions, the way ADR-0291 and ADR-0440 were. Since 2026-11-08:
**L-12** is declined (D14), **T-06** is declined, with "bring your own encoder"
documented (D9), and **T-23** stays parked until the PDFium provenance record
(D11). T-17 needs no new record (ADR-0440). Everything else is built. The
three records are ADR-0688 (L-12), ADR-0689 (T-06) and ADR-0690 (T-23),
written on 2026-11-09.

## 8. Status

Status: `todo`, `asked` (waiting for the owner or an application), `doing`,
`done`, `declined`, `parked`, `answered` (no code; §2). Update the row and the
log in the same commit as the work.

| Entry | Item | Status | ADR | Commit |
|-------|------|--------|-----|--------|
| L-1 | W1.1 | done | 0681 | |
| L-2 | W1.9 | done | 0679 | |
| L-3 | W2.1 | done | 0691 | |
| L-4 | W2.2 | done | 0693 | |
| L-5 | W1.5 | done | 0682 | |
| L-6 | W1.6 | asked (Lantern: is it `jlink`?) | | |
| L-7 | W1.2 | done | 0680 | |
| L-8 | W1.2 | done | 0680 | |
| L-9 | W2.3 | done | 0694 | |
| L-10 | W1.3 | done | 0676 | |
| L-11 | W1.4 | done | 0677 | |
| L-12 | — | declined (D14) | 0688 | |
| T-01 | W1.8 | done | 0685 | |
| T-02 | W3.1 | done: phases 1–7 (marks, `Editor` with marks, `rich-input`, the formatting bar and link editor, mentions, Markdown and HTML, the showcase's Rich input screen); the guide is parked until release (`docs/rich-text-input.md` §5) | 0703–0708 | |
| T-03 | W2.6 | done | 0697 | |
| T-04 | W2.5 | done | 0696 | |
| T-05 | W3.4 | in progress (`docs/camera-and-mic.md`): D1–D9 taken (a), D8 on a measurement; phase 1 (the SDL surface, native ABI 29), phase 2 (`goldberry-mic`: recording, the synthetic source, levels and spectra) and phase 3 (`level-meter`, `waveform-view`, `spectrum-view`) done; phases 4–6 (`goldberry-camera`, PipeWire in CI, the showcase) open | 0719, 0720, 0721 | |
| T-06 | — | declined (D9) | 0689 | |
| T-07 | W2.7 | done | 0698 | |
| T-08 | W1.11 | done | 0684 | |
| T-09 | W2.11 | done (macOS and Windows unverified) | 0700 | |
| T-10 | W1.10 | done | 0683 | |
| T-12 | W2.9 | done: `Path.contains` and pinch; the tile cache stays Tessera's | 0687, 0692 | |
| T-13 | W2.4 | done | 0695 | |
| T-15 | W3.2 | done: `backdrop-filter` on CPU frames, the opaque background over GPU layers | 0709 | |
| T-16 | W2.10 | done (`view-transition-name`; the opt-in is the implementer's choice, see the ADR) | 0702 | |
| T-17 | — | parked (ADR-0440) | | |
| T-18 | W1.7 | done | 0678 | |
| T-19 | W2.12 | done (Win32 half unverified) | 0701 | |
| T-20 | W3.9 | in progress (`docs/drag-out.md`): decided D1 (b) the platform's drag from the threshold, D2–D8 as recommended. Phase 1 (API, router, window, headless), phase 2 (X11, XDND over SDL's hook, native ABI 27; no picture yet) and phase 3 (Wayland, `start_drag` on SDL's own data device with SDL's serial, native ABI 28; hand check on GNOME open) done; phase 4 (Windows, OLE `DoDragDrop` with COM objects in Java and a loop turn inside it) and phase 5 (macOS, an `NSDraggingSession` on SDL's view with a source class of the toolkit's own) built, not yet run on Windows or a Mac; phase 6 (the showcase's drag-out card, the guide section parked in `docs/snapshot/`) done. The Windows and macOS lanes are green on it (Snapshot #86). Open: hand drags on all four | 0714, 0716, 0717, 0718 | |
| T-22 | W3.6 | todo | | |
| T-23 | — | parked (D11) | 0690 | |
| T-24 | W3.3 | done: a page's `<style>`, `style=`, `bgcolor` and `<font color>` in a `StyleScope` of the view's own; `AuthorStyles` with `ADAPTED` the default (a text colour with no background of the mail's under it takes the theme's) | 0710 | |
| T-25 | W3.7 | todo | | |
| T-26 | W2.13 | done | 0686 | |
| T-29 | W3.8 | done: `GlobalShortcuts` (portal, X11 grab, `RegisterHotKey`, Carbon); Windows and macOS written, not run; the Windows release is polled while held, not hooked | 0711 | |
| T-30 | W3.10 | planned and decided (`docs/zero-copy-tiles.md`): D1 (a), D2 (a), D3 D6 decides per driver, D4 (a), D5 `RasterTexture`, D6 the gate. Phases 0–4 not started | 0712 | |
| T-31 | W3.11 | done: plan written (`docs/web-target.md`, D1–D8 asked); no code until it is read | | |
| T-32 | W2.8 | done: delete, clear, user agent; the private store waits for the shim to build its own views | 0699 | |

## 9. Risks

- **W1.1 touches every continuous canvas.** If the read-back path loses its
  per-frame damage, an offscreen or nested `canvas3d` freezes. The GPU goldens
  (`Canvas3dGoldenTest`, `:example:gpuTest`, which is local only) are the net.
- **W1.2 is a public input API.** D1 (a) adds a kind that exhaustive
  `switch`es over `PointerEvent.Kind` must handle. Count them before choosing.
- **Natives ABI bumps.** W2.3 and W2.4 may each move `libgoldberry`'s ABI,
  and W1.11 and W2.8 move the webview ABI. Batch them, and run
  `:example:run` with the dummy driver after any module-info or resource
  change.
- **Platform code that cannot be run here.** The macOS half of W1.10, all of
  W2.8 outside GTK, and T-19 are written against documentation. Mark them
  UNVERIFIED in the code, as `MacMenuBarProjection` is.
- **Publishing (W1.8)** changes what the next snapshot ships. It is not a
  release bump. Versions, CHANGELOG sections and tags stay the user's.

## 10. Log

- **2026-11-08.** Both gap files read. All 44 entries checked against
  `992c8c21`. L-5 found closed before Lantern's pin, L-6 mostly closed, L-10
  half closed, and T-07 answerable with `PictureRenderer`. ADR-0291 already
  declines T-09 and T-10, but its macOS deep-link promise is broken by
  `Sdl3Backend` skipping windowless drops. Plan written; no code yet. Next ADR
  0675.
- **2026-11-08, later.** The owner answered D1–D18 (§3). The waves were
  rewritten to match: W2.11–W2.13 and W3.8–W3.11 added, and W3.5 dropped
  (T-06 declined). `answers-lantern.md` and `answers-tessera.md` started. Work
  moved to the `../goldberry-gaps` worktree because a type-scale change was in
  progress in the main tree. ADRs from 0676.
- **2026-11-09.** Wave 1 is built on the branch (ADRs 0676–0685, uncommitted
  in `../goldberry-gaps`). The type-scale change has landed on master
  (5b80dd1c, ADR-0675), so 0676 follows it without a gap and the branch can
  move into the main tree. Then the easy rest: W2.13 (`dev.goldberry.i18n`:
  CLDR 49 plural rules read from one extracted resource rather than
  generated into switch code, `IcuMessage`, `Labels` for every toolkit word,
  ADR-0686), the `Path.contains` half of W2.9 (ADR-0687; pinch waits for a
  layout probe), and the three records of §7 (0688–0690). W2.1 is started.
  Next ADR 0691.
- **2026-11-09, later.** W2.1 done (ADR-0691): `FrameStats.layers()`,
  `percentile(FrameStage, p)` by nearest rank, and `Window.onFrame` with one
  reused `FrameSample` view; the ring stays sixty frames. Pinch done
  (ADR-0692), so W2.9 is closed: `PointerEvent.Kind.PINCH` with `scale()` and
  `pinchPhase()`, the SDL pinch layout and constants in the shim's table with
  no new function, so the native ABI stays 26. The worktree has its own native
  build now (`natives/.deps` copied from the main tree, paths rewritten).
  W2.2 (a measuring strip) is unblocked. Next ADR 0693. Against that build
  the whole `:natives` suite is green (676 tests, `LayoutVerificationTest`
  with the pinch layout, and W1.11's `WebviewInitTest` against a webview
  library at ABI 9 for the first time), and the showcase runs on the module
  path.
- **2026-11-13.** The worktree moved into the main tree as one staged
  `--binary` patch (153 files; no file overlapped the type-scale commit, so
  it applied without a conflict). The batch's rows and ADR lines were appended
  to `docs/snapshot/README.md` and `adr/README-lines.md`, and its notes file
  deleted. Nothing is committed. Status `done` means in the main
  tree's index.
  The full `check` afterwards found two things of the batch's: `plurals.txt`
  had no native-image declaration (added to `:core`'s
  `reachability-metadata.json`), and the showcase's Input golden lists
  `AUX_CLICKED` and `PINCH` now (re-taken). Two failures are not the batch's:
  `BookTest.Pictures` (picture widths left behind by the type-scale commit
  5b80dd1c) and both GPU lanes (SIGSEGV in the NVIDIA driver; this machine
  runs them on lavapipe).
- **2026-11-14.** Committed to master with the plan and both answers files.
  `done` in §8 means on master; nothing is pushed or in a snapshot yet.
- **2026-11-14, later.** Wave 2 continued in the main tree, uncommitted.
  W2.2 (ADR-0693): `Offscreen.measure(root)`, a `MeasuringStrip` over one
  buffer that repaints only its damage, clearing it to the background first
  (`RenderTree.repaint`), and reports a `MeasuredFrame` per frame and
  `stats()` as a window's. W2.3 (ADR-0694): `Image.decodeInto` with
  `ChannelOrder` and `PixelTarget`. The swap is done in the existing Java row
  copy, not by binding Blend2D's converter, so no ABI bump. W2.4 (ADR-0695):
  `font-feature-settings` and `font-variant-numeric` through `FontFeatures`,
  a `Typography` component, one font per feature set, and `hb_feature_t` as
  a row of the layout table. No new symbol, so the ABI stays 26. W2.5
  (ADR-0696): `ListView.sections`. Headers are not rows: in a measured list
  they count into their section's first block, a pinned copy sits over the
  viewport, and a list at one height measures once it has sections. W2.6
  (ADR-0697): `RichText` is stateful over a `rich-text` leaf, with
  `Run.onPress`, `Run.spoiler`, `RichText.selectable` and
  `SpanPaint.background`. The selection is not shared with `content.select`.
  The first full `:widgets` run caught `Decoding` using `MemorySegment`
  outside `:natives` (`BoundaryTest`); it uses `ByteBuffer` now. W2.7
  (ADR-0698): `PictureSource`, implemented by `MediaPlayer`, `PushedPictures`
  and `PictureView`. `VideoView` stays the player's view. W2.8 (ADR-0699):
  `deleteCookies`, `clearCookies` and `userAgent` in the web view shim on all
  three engines, verified on WebKitGTK only and still under web view ABI 9.
  The private store stays open, and the new upcall shape is the fifteenth
  (`ForeignSurfaceTest`). Not started: W2.10, which needs an opt-in property
  carried from the cascade through `Box` to the render tree and a paint-time
  clock; W2.11, which needs object paths, byte arrays, structs and prompts in
  the D-Bus binding and a fake Secret Service to test against; and W2.12,
  which is Windows only. `MediaWidgetsTest$Controls.arrowsSeek` failed once in
  three runs under load; the seek it reads is asynchronous, and nothing here
  touched it. Next ADR 0700.
- **2026-11-14, evening.** W2.2–W2.8 committed to master with their ADRs
  (0693–0699), the plan and both answers files. `done` in §8 still means on
  master; nothing is pushed or in a snapshot yet.
- **2026-11-14, night.** W2.11 (ADR-0700): `dev.goldberry.keychain.Keychain`,
  over a `SecretStore` per platform in `natives.desktop.keychain`. The Secret
  Service is spoken directly over D-Bus with a `plain` session, and a locked
  keyring is unlocked through its prompt. The macOS Keychain goes through
  `SecItem`, and the Windows Credential Manager through `Cred*`; those two are
  UNVERIFIED. The D-Bus binding moved to `natives.desktop.dbus` (not
  exported). It learned object paths, byte arrays, structs, variants, error
  replies, waiting for a signal and serving, so the fake notification daemon
  uses it now instead of raw handles. `SecretServiceTest` runs against a fake
  Secret Service on a private `dbus-daemon`, prompts included. Next ADR 0701.
- **2026-11-14, night, later.** W2.12 (ADR-0701): the Windows balloon's
  click and a taskbar badge. The notification icon belongs to a message-only
  window of its own, whose window procedure (the sixteenth upcall shape) queues
  `NIN_BALLOONUSERCLICK`. `PlatformNotifier` polls it on the Linux click's
  timer. The badge is `ITaskbarList3::SetOverlayIcon` through a new `Com`
  helper (`natives.desktop.windows`, not exported), over a picture
  `BadgeIcon` draws with the toolkit's painter. The Win32 half is UNVERIFIED;
  structure offsets, vtable slots, GUID bytes and the message handling are
  tested on Linux. Guide corrections parked. Next ADR 0702.
- **2026-11-14, night, last.** W2.10 (ADR-0702): FLIP layout animation. The
  opt-in was put to the owner and not answered, so the recommended one was
  taken: `view-transition-name`, timed by the box's own `transform`
  transition or View Transitions' 250 ms `ease`, instant under reduced
  motion. The name rides on `Transform` (a fifth component,
  `ViewTransition`), so nothing new crosses `ComputedStyle` or `Box`.
  `LayoutMotion` runs after layout over `LayoutTracks` (a new internal
  package, `motion.layout`). Its flip matrix goes through `RenderTree.compose`,
  so painting, hit testing, damage and culling agree, and only the moved
  paths settle again. `FrameSequence` hands the renderer's clock to
  `RenderTree.update` and keeps the loop awake while a box travels. A tree
  with no names skips the pass. Wave 2 is closed. Next ADR 0703.
  Verified together: `:natives`, `:core` and `:widgets` green (711, 5824 and
  3228 tests, one natives skip), javadoc clean, and the showcase runs on the
  module path with the dummy driver. `spotlessApply` also rewrapped twelve
  files from c93b6537 that had missed the formatter. Nothing is committed.
- **2026-11-14, night, after Wave 2.** W3.1 started with its plan,
  [`docs/rich-text-input.md`](../rich-text-input.md), written against
  `6741de31`. What it found: `TextEdit` and `EditHistory` are values over one
  string, so marks can ride in the state; `TextDocument.Shaper` sees only a
  line's text; `Paragraph.join` and `ParagraphCache` already shape mixed
  faces; `Paragraph` has no inline objects, so a mention is an atom of text;
  `:widgets` cannot see `:html`'s Markdown model. It proposes flat marks over
  a string (D1), a fixed set of kinds plus custom ones (D2), looks from the
  cascade (D3), `Editor` with marks under a new `rich-input` (D4), a generic
  `EditHistory<S>` (D5), a clipboard type of the toolkit's own (D6), mentions
  reported for the application to suggest (D7), a stock formatting bar (D8),
  and no input rules yet (D9). Seven phases. No code until the owner answers.
- **2026-11-14, night, rich text.** The owner took D1–D9 of
  `docs/rich-text-input.md` as recommended. Phases 1–3 built, uncommitted:
  `text.mark` (marked text and its edit rules, ADR-0703), the canvas
  `Editor` with marks, formatting keys and a clipboard type of its own
  (ADR-0703), and `rich-input` styled by `mark` nodes in the cascade
  (ADR-0704). `EditHistory` is generic now (a breaking-changes note). The
  formatting bar, mentions, Markdown and HTML, and the showcase composer are
  phases 4–7. Next ADR 0705.
- **2026-11-14, night, rich text phase 4.** The formatting bar over a settled
  selection, the link editor (`Ctrl+K`), the text menu's Format submenu,
  `RichInput.onSubmit` and a blinking caret (ADR-0705), and a composer card
  on the showcase's Forms screen. Uncommitted. Next ADR 0706.
- **2026-11-15, rich text phase 5.** Phases 1–4 are committed as `93a1ee62`.
  Mentions (ADR-0706): the field reports a query typed after a trigger
  (`MentionQuery`, `Mentions`, `MentionPrompt`), offers the list keys to the
  application first, and puts the chosen person in as an atom. The showcase
  composer mentions somebody from the keyboard. Uncommitted. Next ADR 0707.
- **2026-11-15, rich text phase 6.** Phase 5 was committed as `84f1f44d`.
  Markdown and HTML (ADR-0707): clipboard formats in `core`, `MarkedMarkdown`
  and `MarkedHtml` in `:html`, and the `text/html` format a `rich-input`
  installs. Uncommitted. Next ADR 0708.
- **2026-11-15, rich text phase 7.** Phase 6 was committed as `04f0dbac`.
  The owner asked for every option shown with an example, so `rich-input` got
  its own showcase screen, Rich input, with eleven cards (ADR-0708). The
  composer moved there, now with a conversation in `list` sections. The
  mention picker became `ui.richinput.suggest`, answering `@`, `#`, `:` and
  `/` with a different kind each. Tessera's G-02 answer points at the screen.
  T-02 is done; the guide moves into the book at release. Uncommitted. Next
  ADR 0709.
- **2026-11-21.** Everything through `5998561b` is pushed (Snapshot #81 and
  Qodana #85 are its first CI runs). The owner asked to close W3.2, W3.3, W3.8,
  W3.10 and W3.11. Three are being built side by side: W3.2 `backdrop-filter`
  (ADR-0709), W3.3 author styles in `html-view` (ADR-0710) and W3.8
  `GlobalShortcuts` (ADR-0711). W3.10 and W3.11 get their plan documents,
  `docs/zero-copy-tiles.md` and `docs/web-target.md`; W3.10's code waits for
  its plan to be read. Next ADR 0712.
- **2026-11-21, later.** W3.2, W3.3 and W3.8 are built and integrated,
  uncommitted. W3.2 (ADR-0709) draws what is under a frosted box again
  instead of reading the frame back: Blend2D's flush is not exported, and a
  partial repaint would frost twice. W3.3 (ADR-0710) needed a core scope
  (`ScopedRules`, `StyleScope`) because D3 took no inline layer; a page's
  rules beat the window's normal ones and lose to `!important`, and its
  `<style>` is matched on the document, not the folded widget tree. W3.8
  (ADR-0711) polls the Windows release while a hotkey is held instead of a
  low-level hook, which Windows drops after a slow upcall. Both plan
  documents are written; W3.10 waits for its D1–D6. Next ADR 0712.
- **2026-11-21, evening.** The owner answered W3.10's plan: D1 (a), D2 (a),
  D4 (a), D5 `RasterTexture`, D6 the gate as written, D3 rejected. Phase 0 can
  start; phases 1–3 wait for D3's replacement. Next ADR 0712.
- **2026-11-21, last.** D3 of W3.10 taken: D6's gate decides per driver, at
  run time, with no driver ruled out ahead of time. All six decisions are in
  ADR-0712. Phase 0 (measurements) is next for W3.10. Next ADR 0713.
- **2026-11-21, W3.9.** `docs/drag-out.md` written from the tree at
  `b3752f37` and SDL 3.4.18's sources: SDL is a drop target everywhere and a
  drag source nowhere, but gives what a source needs (the X11 event hook,
  OLE up on Windows, `wl_display` and `wl_surface`, the `NSWindow`). Six
  phases, API and router first, then X11, Wayland, Windows, macOS. D1–D8 are
  asked; no code. Next ADR 0714.
- **2026-11-21, W3.9 answered.** D1 (b): a draggable with an offer is the
  platform's drag from the threshold, not from the window's edge; D2–D8 as
  recommended. ADR-0714 records them, parked. Phase 1 (API and router) next.
  Next ADR 0715.
- **2026-11-21, W3.9 phase 1.** The API and the router, in `:core` only:
  `input.dragout` and `render.dragout`, `Attributes.dragOut`,
  `BackendWindow.startDrag` (refuses by default), the hand-off at the
  threshold, `DragFlight` driving in-app targets from the platform's reports,
  and the echoed drop swallowed. No platform starts a drag yet, so nothing
  changes for an application until phase 2. Uncommitted. Next ADR 0715.
- **2026-11-21, W3.9 phase 2.** X11: XDND 5 on SDL's own `Display` through
  `SDL_SetX11EventHook` (native ABI 27). A drag with an offer leaves an X11
  window for any XDND target, and over the application's own windows it is an
  in-app drag. No picture under the pointer yet. Next ADR 0715.
- **2026-11-21, W3.9 phase 3.** Wayland. A second `wl_data_device` would
  unlink SDL's on mutter, so `start_drag` goes out on SDL's own, with the data
  device and serial read from SDL's seat by one C file compiled against SDL's
  private headers (native ABI 28, ADR-0716). The source and its icon are Java
  over libwayland-client (`natives.wayland`). Over the application's own
  windows SDL's drop events are the position. Not driven by hand yet. Next
  ADR 0717.
- **2026-11-21, W3.9 phase 4.** Windows: `DoDragDrop` with an
  `IDataObject`, `IEnumFORMATETC` and `IDropSource` written in Java
  (`ComServer`), the picture through the shell's drag image helper, and one
  guarded turn of the loop on each of OLE's questions (ADR-0717). No native
  ABI bump. Written and tested through the objects' vtables everywhere, not
  yet run on Windows. Next ADR 0718.
- **2026-11-21, W3.9 phase 5.** macOS: an `NSDraggingSession` begun on SDL's
  content view, with `GoldberryDraggingSource` as the dragging source and the
  pasteboard item's lazy data provider, files as file URLs, and the ghost as
  an `NSImage`. `ObjC` passes `NSPoint`/`NSSize`/`NSRect` by value
  (ADR-0718). Not yet run on a Mac. Next ADR 0719.
- **2026-11-21, W3.9 phase 6.** The showcase's Input screen has a drag-out
  card: a picture and a note to drag into other applications, and a tray
  that keeps them in the window. The guide's `## Dragging out of the window`
  is parked in `docs/snapshot/`. An offscreen `Session.drag` now carries a
  draggable with an offer in-app instead of dropping it. Every phase is built;
  what is left is running it: the Windows and macOS lanes, and a hand drag
  on each desktop. Next ADR 0719.
- **2026-11-21, W3.4.** `docs/camera-and-mic.md` written from the tree at
  `258b836f`. Found: SDL already compiles its camera (V4L2 here, Media
  Foundation, CoreMedia) and its recording; PipeWire is off for want of
  headers, and compiling it in moves playback to PipeWire too; hot-plug and
  camera approval arrive only through `SDL_PumpEvents`; SDL never converts
  MJPG; and `VideoSurface` draws `VideoPlanes` on the GPU only, so a camera
  (or pushed planes) shows nothing on the CPU path. D1–D9 asked. Next ADR 0719.
- **2026-11-21, W3.4 decided, phase 1.** D1–D9 (a). D8 (PipeWire) taken on a
  loopback measurement: with `SdlAudioSink` counting two pulls on Linux,
  playback through PulseAudio and through PipeWire is heard within 0.2 ms of
  each other against the sink's clock (one pull counted, PipeWire was 16 ms
  late). Phase 1: sixteen SDL symbols, native ABI 29, recording, cameras and
  device events in `:natives`, run against the C922 here. ADR-0719. Next ADR
  0720.
- **2026-11-22, W3.4 phase 2.** `goldberry-mic` (optional, Java only):
  `Microphones` and `Recording` over SDL through an unexported backend, a
  `SampleSource` the widgets will read, a deterministic `SyntheticSource`, and
  levels, FFT, spectrum and envelope trackers. ADR-0720. Next ADR 0721.
- **2026-11-22, W3.4 phase 3.** The microphone's widgets: `level-meter`,
  `waveform-view` and `spectrum-view`, each a stateful `@Markup` widget that
  feeds a tracker from its source and redraws once a frame, over exported
  leaves that also draw a recorded clip's outline with its played share.
  `--gb-mic-*` tokens in `mic.css`; goldens on both themes from the synthetic
  source. `answers-tessera.md` G-05 corrected: SDL's PipeWire camera does not
  go through the portal. ADR-0721. Next ADR 0722.
