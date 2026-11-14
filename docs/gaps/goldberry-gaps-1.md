# Goldberry gaps

What Lantern needed from Goldberry and did not get, found while building it: a missing feature, a behaviour that
costs us, or a test hook that is not there. Each entry says what was seen, the evidence, what it costs Lantern, how
Lantern works around it (or why it cannot), and what would close it. This is the list to take upstream.

Pinned Goldberry: `2026.2-20261011.204446-14` (`gradle/libs.versions.toml`). When the pin moves, re-check the open
entries and close the ones the new snapshot fixes.

**Adding a gap.** Any work package that hits one adds an entry here in the same branch, with the next number, and
links it from the code or ADR that works around it. Status is `open`, `worked around` (Lantern has a workaround that
stays until Goldberry changes), or `closed` (fixed upstream; say in which snapshot).

| # | Area | Gap | Status | Found in |
| --- | --- | --- | --- | --- |
| [G-1](#g-1-a-continuous-canvas3d-re-uploads-the-whole-ui-layer-every-frame) | GPU canvas | A continuous `Canvas3d` re-uploads the whole UI layer every frame | open | M1-13 |
| [G-2](#g-2-no-way-to-give-a-widget-a-computed-style-value) | Styling | No way to give a widget a computed style value | worked around | M1-13 |
| [G-3](#g-3-frame-statistics-are-means-only) | Diagnostics | Frame statistics are means only | worked around | M1-13 |
| [G-4](#g-4-an-offscreen-filmstrip-repaints-every-frame-whole) | Testing | An offscreen filmstrip repaints every frame whole | open | M1-13 |
| [G-5](#g-5-no-capture-of-a-running-window) | Testing | No capture of a running window | open | M1-13 |
| [G-6](#g-6-the-natives-jar-is-not-a-module) | Packaging | The natives jar is not a module | worked around | M0-07 |
| [G-7](#g-7-a-click-is-synthesised-for-the-primary-button-only) | Input | A click is synthesised for the primary button only | worked around | M1-14 |
| [G-8](#g-8-no-generic-click-handler-on-a-widget) | Input | No click handler a widget can be given | worked around | M1-14 |
| [G-9](#g-9-an-image-decodes-only-into-its-own-premultiplied-bgra-buffer) | Images | An image decodes only into its own premultiplied BGRA buffer | worked around | M1-15 |
| [G-10](#g-10-a-gpu-device-cannot-be-asked-for-or-asked-about-its-adapter) | GPU device | A GPU device cannot be asked for, or asked about, its adapter | open | M1-16 |
| [G-11](#g-11-an-offscreen-render-of-a-tree-that-paints-nothing-throws) | Testing | An offscreen render of a tree that paints nothing throws | worked around | M3-04b |
| [G-12](#g-12-measuring-text-needs-the-native-library) | Text | Measuring text needs the native library | worked around | M3-06a |

## G-1. A continuous `Canvas3d` re-uploads the whole UI layer every frame

**Seen.** With the window composited on the GPU, every frame uploads the UI layer over the canvas's whole box:
5 625 KB a frame in a 1600 × 900 window (exactly 1600 × 900 × 4), with the battle HUD, without it (`--no-hud`) and
with no battle at all (only the tick readout over the canvas).

**Evidence.** `--log-fps` on the dev machine (100 Hz, 2026-11-08): `present … upload 1.3/2.2 5625 KB` in every
5 s window; up to 15 ms of upload in a window where the machine was slowed. In Goldberry's source,
`Canvas3dSurface` (`goldberry-gpu`, `dev.goldberry.gpu.view`) stamps its painter with the frame's time while
`continuous`, and the render tree compares painters by equality to decide what was damaged, so the canvas's box is
damage on every frame and the CPU UI over it is rasterised and uploaded again, though the canvas itself is a GPU
layer the compositor draws on its own.

**Costs Lantern.** 1.1 to 1.5 ms a frame when calm on the dev machine; at 2560 × 1440 it is 14.4 MB a frame, a risk to
M1-11's 8 ms frame budget on the integrated GPU reference machine. It grows with the window, not with what changed.

**Workaround.** None that helps: a non-continuous canvas redrawn by a new `revision` every frame is damage the same
way. The HUD itself keeps its own damage small (weapon charge in 2% steps, M1-13).

**Would close it.** A GPU layer whose content changes should not damage the CPU frame: the compositor redraws the
layer from its own texture, and only UI damage (the readout's text, a HUD region that rebuilt) is rasterised and
uploaded. In short: separate "the layer has a new picture" from "the box was repainted".

## G-2. No way to give a widget a computed style value

**Seen.** The HUD must scale by `min(width / 2560, height / 1440)` of the window, as the scene does. Widgets take no
inline style and an `Attributes` carries only id, classes, key and handlers, so a value computed in Java cannot reach
a declaration; media queries see only the window's logical width and height.

**Evidence.** ADR-0020; `ui.theme/lantern.css` ends with a ladder of 35 `@media (min-width) and (min-height)` rules, one
per 0.05 step from 0.3 to 2.

**Costs Lantern.** The HUD scales in steps of 0.05, so between steps it is slightly smaller than would fit; the user's
UI-scale setting (M8) will have to multiply a ladder, not a number.

**Workaround.** The ladder (worked around).

**Would close it.** A way to set a custom property (`--hud-scale`) or a declaration on a node from Java, or a
`calc()`/`min()` over the viewport in `scale`.

## G-3. Frame statistics are means only

**Seen.** `FrameStats` gives means (and min/mean/max spans) over the last few dozen frames. A 5 s log of those cannot
show a hitch: a window read 64.6 fps with a median frame of 10.8 ms. There is no per-frame access, no percentile, and
the time a `Canvas3dRenderer` spends recording its passes is not one of the stages.

**Evidence.** The M1-13 frame-time investigation (`docs/implementation/m1-vertical-slice.md`, M1-13 exit check).

**Costs Lantern.** Its own per-frame bookkeeping.

**Workaround.** `app.loop.FrameTimes` and `FrameReport`: per-frame intervals from the frame driver's clock, the
loop's and the renderer's CPU (`FrameDriver.drawn`), percentiles and the longest frame per window, beside
Goldberry's means (worked around).

**Would close it.** A per-frame callback or ring of frame records, percentiles over a window, and the canvas
renderer's time as a stage.

## G-4. An offscreen filmstrip repaints every frame whole

**Seen.** `Offscreen.strip(…).frame()` rasterises the whole buffer each call, even when nothing changed: a 2560 × 1440
HUD frame cost p50 9 ms with nothing changed and 7.6 ms with the weapons bar rebuilding every frame.

**Evidence.** The throwaway probe of the M1-13 frame-time investigation (not kept).

**Costs Lantern.** A widget tree's frame cost cannot be measured headlessly, so every UI cost question needs a run in
a window on someone's screen.

**Workaround.** None in tests; `--log-fps` in the game.

**Would close it.** A filmstrip that repaints only its damage as a window does, and reports the damaged area and the
stage times of each frame (the window's `FrameStats` for a strip).

## G-5. No capture of a running window

**Seen.** Nothing in Goldberry saves what a running window shows; with no screenshot tool on the machine the
battle with its HUD could only be seen offscreen, piece by piece (the scene through `OffscreenGpu`, the HUD through
`Offscreen`), never composed as the player sees it.

**Evidence.** M1-11 and M1-13 exit checks: the composed window is a manual check.

**Costs Lantern.** Every check of the composed picture is someone looking at the screen.

**Workaround.** None.

**Would close it.** A host call or launch flag that writes the next composited frame to a PNG
(`host.capture(Path)` or `--goldberry-capture=<file>`).

## G-6. The natives jar is not a module

**Seen.** `goldberry-natives` (the classifier jar) has no module descriptor, so it cannot sit on the module path
beside the modular Lantern and Goldberry jars.

**Evidence.** ADR-0007 (M0-07): natives load from the class path.

**Costs Lantern.** The runtime image keeps a class-path part; `render` and `ui` tests need the natives as a
`testRuntimeOnly` dependency.

**Workaround.** The natives stay on the class path (worked around).

**Would close it.** An automatic-module name or a descriptor in the natives jar.

## G-7. A click is synthesised for the primary button only

**Seen.** `PointerRouter` dispatches `PointerEvent.Kind.CLICKED` only when the released button is `PRIMARY`; a
secondary or middle press and release on the same node is a `PRESSED` and a `RELEASED`, never a click.

**Evidence.** M1-14: a right-click on a system row (take a bar of power away) never arrived as a click in
`BattleHudInputTest`; Goldberry's `PointerRouter.pointerReleased` tests `button == PointerEvent.Button.PRIMARY`
before it synthesises the click.

**Costs Lantern.** The UI doc gives the secondary button meanings everywhere (take power, toggle a door, cancel
targeting); without a click, a right press dragged off the target still counts.

**Workaround.** `ui.widget.Clickable` takes a secondary or middle press as the click (worked around).

**Would close it.** `CLICKED` for every button, with `button()` saying which, under the same press-and-release-on-
the-same-node rule.

## G-8. No generic click handler on a widget

**Seen.** `Attributes` offers `onPointerEnter`, `onPointerExit`, drag and drop, but no `onClick`; a panel, a row or
a `canvas3d` cannot be made clickable without writing a widget that implements `Handles`.

**Evidence.** M1-14: the weapon slots, system rows, crew tiles and the scene's canvas all needed clicks.

**Costs Lantern.** One extra box in the tree per clickable thing, and a widget of our own to keep in step with
Goldberry's input API.

**Workaround.** `ui.widget.Clickable`: a leaf around one child that implements `Handles.onPointer` and hands a
`Click` (button, modifiers, position as a fraction of the box) to a callback (worked around).

**Would close it.** `Attributes.onClick(Consumer<PointerEvent>)` (or `onPointer`) on any widget, with the local
position, so the box that is styled is the box that is clicked.

## G-9. An image decodes only into its own premultiplied BGRA buffer

**Seen.** `Image.decode` always allocates a heap `PixelBuffer` in `BGRA32_PREMULTIPLIED` and hands that back; there
is no way to ask for RGBA, or to decode into a buffer the caller owns (a GPU transfer buffer, a page being built).

**Evidence.** M1-15: the packed atlas pages (`render.atlas.PageDecoder`) are uploaded as `R8G8B8A8_UNORM` textures,
like every page the batcher samples.

**Costs Lantern.** One page-sized copy per atlas page at load (16 MB for a 2048 px page), with the red and blue
swap done texel by texel in Java; it grows with the art (M5 and M6 bring dozens of pages), and the decoder's buffer is
garbage straight after.

**Workaround.** `PageDecoder` copies into an RGBA buffer, swapping the channels (worked around). Uploading the BGRA
page as a `B8G8R8A8_UNORM` texture would avoid it, at the price of two page formats in the batcher and its tests.

**Would close it.** `Image.decode(bytes, PixelFormat)` with an RGBA premultiplied format, or a
`decodeInto(bytes, ByteBuffer, stride, format)` that writes into the caller's memory.

## G-10. A GPU device cannot be asked for, or asked about, its adapter

**Seen.** `OffscreenGpu.open()` and the window's device take no options, and `GpuDevice` tells only its `driver()`
(`vulkan`, `metal`, `direct3d12`): not the adapter's name, vendor or whether it is integrated. SDL 3 has both halves
(`SDL_PROP_GPU_DEVICE_CREATE_PREFERLOWPOWER_BOOLEAN` when creating; the device's name and driver info in
`SDL_GetGPUDeviceProperties`).

**Evidence.** M1-16, the M1 exit check "hull recompose p99 < 16 ms on the integrated GPU": `--hull-bench` prints
`OffscreenGpu[vulkan]` whichever GPU ran it. Forcing Mesa's lavapipe with `VK_ICD_FILENAMES` changed the timings but
not the report, so even that could only be told from the numbers.

**Costs Lantern.** On a laptop with an integrated and a discrete GPU (the common case for the reference machine) the
bench cannot be pointed at the integrated one, and its report cannot prove which one it measured; the same holds for
the frame-time log (`--log-fps`) and for bug reports from players.

**Workaround.** None in code: run the bench where the integrated GPU is the only one, or check with the driver's
tools (open).

**Would close it.** A `prefer low power` (and `prefer high performance`) option on `OffscreenGpu.open` and on the
window's device, and `GpuDevice.adapter()` with the name, vendor and kind SDL reports.

## G-11. An offscreen render of a tree that paints nothing throws

**Seen.** `Offscreen.render(widget)` throws `IllegalStateException: nothing in this widget tree paints; the root
described only composition` when the widget builds to `Widget.nothing()`, instead of answering the background.

**Evidence.** M3-04b, `EventDialogTest`: the event dialog with no event open builds to nothing, and the test that it
draws nothing could not render it on its own.

**Costs Lantern.** An overlay that is often empty (the dialog, the panels, the result) cannot be rendered alone in
its empty state, so its "shows nothing" test needs a backdrop it would not have in the game.

**Workaround.** The test renders the dialog over a backdrop that paints (a corner label), as the HUD layer is in the
game (worked around).

**Would close it.** An empty frame rendered as the background, or an option to allow it in tests.

## G-12. Measuring text needs the native library

**Seen.** `Paragraph.of(font, text).layout(width)` and `Font.bundled(...)` fail with `UnsatisfiedLinkError: No
libgoldberry for linux-x64 on the classpath` when `goldberry-natives` is not on the run-time class path, though
nothing is drawn: only lines are counted and widths measured.

**Evidence.** M3-06a: the content linter's `text-fits` rule (`ui.dialog.fit.DialogTextFit`) in `lantern-tools`, with
the natives dependency removed from `tools/build.gradle.kts`, fails `ContentLinterTest.textFits` with that error.

**Costs Lantern.** A command-line content tool, with no window and no GPU, must ship the platform's native library
(and `--enable-native-access`) to count lines, so `lantern-tools lint` runs only where a natives build exists, and
the tools image grows by it.

**Workaround.** `tools` depends on `goldberry-natives` for the machine's classifier at run time, as the game does
(worked around).

**Would close it.** Shaping and measurement that work without the native library (a pure-Java fallback for
measurement, or the measuring parts split from the native renderer), so a headless tool can measure text exactly as
the game lays it out.
