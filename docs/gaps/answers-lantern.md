# Answers to Lantern

Goldberry's reply to [`goldberry-gaps-1.md`](goldberry-gaps-1.md), entry by
entry. It is written while the work happens, and each entry ends with what has
landed and in which commit. Ids are Lantern's own, G-1 to G-12. Goldberry's
plan calls them L-1 to L-12 ([`goldberry-gaps-plan.md`](goldberry-gaps-plan.md)).

Checked against Goldberry master `992c8c21` on 2026-11-08. Lantern pins
`2026.2-20261011.204446-14`, so some of this already existed in the snapshot
Lantern builds on, and the entry says so.

| # | Gap | Answer | Status |
|---|-----|--------|--------|
| G-1 | Continuous `Canvas3d` re-uploads the UI layer | Fixed in Goldberry | on master, not yet in a snapshot |
| G-2 | Computed style value | Typed division in `calc()`; a workaround today | on master, not yet in a snapshot |
| G-3 | Frame statistics are means only | Per-frame records, percentiles, a layer stage | on master, not yet in a snapshot |
| G-4 | Filmstrip repaints whole | `Offscreen.measure(...)`, a measuring strip | on master, not yet in a snapshot |
| G-5 | No capture of a running window | **Already there**; a while-running flag added | answered; on master, not yet in a snapshot |
| G-6 | Natives jar not a module | **Automatic module already**; a question back | question |
| G-7 | Click for primary only | `AUX_CLICKED` for the other buttons | on master, not yet in a snapshot |
| G-8 | No click handler on a widget | `Attributes.onClick` | on master, not yet in a snapshot |
| G-9 | Decode only into BGRA | `Image.decodeInto` with a channel order | on master, not yet in a snapshot |
| G-10 | GPU adapter | `adapter()` exists; a power preference added | on master, not yet in a snapshot |
| G-11 | Empty tree throws | An empty tree renders as nothing | on master, not yet in a snapshot |
| G-12 | Text needs the natives | Declined | declined, ADR-0688 |

## G-1. A continuous `Canvas3d` re-uploads the whole UI layer every frame

**Confirmed, and the cause is what the entry says.** `Canvas3dSurface` stamps
its painter with the frame's time while `continuous`. The painter is a record,
so the render tree sees a new painting every frame and damages the box.

**Goldberry's fix.** The frame time goes to the GPU layer directly, and the
painter changes only when the revision or the render scale does. A continuous
canvas under a calm UI then uploads nothing after its first frame. Where the
layer is read back into the CPU frame instead of composited (offscreen
renders, a window without the GPU compositor, a canvas inside an opacity
group), the box is still damaged every frame, because there the CPU frame is
the picture.

**Today.** Nothing in Lantern helps; wait for the snapshot.

**Landed.** Built on Goldberry's master (not yet in a snapshot), ADR-0681. A continuous `canvas3d` in a composited window no longer damages its box: the frame's time goes to the layer as the tree is built, so `--log-fps` should show the upload drop to what the HUD itself changed. Unit-tested on the painter, and the GPU lane is green on lavapipe. Lantern's own `--log-fps` on the dev machine is the check that matters.

## G-2. No way to give a widget a computed style value

**Today, without a ladder.** Build the sheet in Java and restyle when the
window resizes:

```java
@Override
public List<String> stylesheets() {
    return List.of(":root { --hud-scale: " + hudScale + "; }");
}
// on resize: hudScale = Math.min(w / 2560.0, h / 1440.0); host.restyle();
```

The showcase's reading-size setting works this way. A restyle rebuilds the
renderer's styles, so do it on a resize, not every frame.

**Goldberry's fix.** CSS Values 4 typed division: a length divided by a length
is a number. With it, the whole ladder becomes

```css
.hud { scale: min(100vw / 2560px, 100vh / 1440px); }
```

and the M8 UI-scale setting multiplies it with `calc(var(--ui-scale) * …)`.
Goldberry will not let Java set declarations on a node: a widget writes only
what a stylesheet could not.

**Landed.** Built on Goldberry's master (not yet in a snapshot), ADR-0679. `.hud { scale: min(100vw / 2560px, 100vh / 1440px); }` replaces the ladder, and a resize re-resolves it. The UI-scale setting: `scale: calc(var(--ui-scale) * min(100vw / 2560px, 100vh / 1440px))`, with `--ui-scale` in a generated `:root` sheet.

## G-3. Frame statistics are means only

**Goldberry's plan.** Three things:

- A layer-render stage in the frame timings, so `Canvas3dRenderer.render` is
  measured on its own.
- Percentiles over the frame ring.
- A frame listener on `Window` that hears each frame's record. Recording stays
  allocation-free, so the listener is handed a reused view.

Once these land, `FrameTimes` and `FrameReport` can read Goldberry's numbers
instead of keeping their own.

**Landed.** On Goldberry's master, not yet in a snapshot, ADR-0691:

```java
host.window().onFrame(frame -> times.add(frame.intervalNanos(), frame.paintNanos(), frame.layerNanos()));
var p99 = host.frames().percentile(FrameStage.INTERVAL, 99);   // ms, nearest rank
var layers = host.frames().layers();                            // min / mean / max ms
```

- `Window.onFrame` is called once per presented frame, after the present,
  with the interval, paint, the four stages, late refreshes and, on a
  composited window, upload, acquire, submit, layer time and bytes. The
  listener gets **the same `FrameSample` object every frame** (a view, no
  allocation); `frame.copy()` keeps one as a `FrameRecord`.
- `FrameStats.percentile(FrameStage, p)` over the last sixty frames.
- `FrameStats.layers()` and the `layers` hud reading: the GPU layers' render
  time (`Canvas3dRenderer.render`), no longer folded into `submit`.

`FrameTimes` and `FrameReport` can be fed from `onFrame` instead of
`FrameDriver.drawn`. A five-second window is the listener's to keep; the
ring stays sixty frames.

## G-4. An offscreen filmstrip repaints every frame whole

**Goldberry's plan.** `Offscreen.measure(...)` is a strip that keeps one buffer
and repaints only its damage, as a window does. It reports each frame's stages
and damaged area. `strip(...).frame()` stays as it is: each `Image` it returns
is a view over its own buffer, which a golden test relies on.

**Landed.** `Offscreen.of(w, h).measure(root)` returns a `MeasuringStrip`.
It paints into one buffer, repaints only the damage, and clears the damaged
region to the background first, so a HUD with nothing under it measures
correctly. `frame()` returns a `MeasuredFrame`: `timings()` (the same
`FrameRecord` a window's `onFrame` listener keeps, with build, style, layout,
raster and paint in nanoseconds) and `damage()` with `damagedPixels()`. A
frame with nothing changed paints 0 px. `stats()` reads as `Host.frames()`
does, including `percentile(FrameStage.PAINT, 99)` over the last sixty
frames. `picture()` copies the buffer for a check beside the numbers.

```java
try (var meter = Offscreen.of(2560, 1440).stylesheets(sheets).measure(new Hud(model))) {
    for (var i = 0; i < 600; i++) {
        model.tick();
        var frame = meter.frame();
        log(frame.timings().paintNanos(), frame.damagedPixels());
        meter.advance(16);
    }
}
```

The numbers are the toolkit's work only. Nothing is uploaded or presented,
so the present readings are zero. ADR-0693.

## G-5. No capture of a running window

**This was already in Lantern's snapshot** (Goldberry 1e3a7515, 2026-10-06):

- `host.window().capture()` returns an `Optional<Image>` of the composed
  window, GPU layers included. `Image.encodePng()` writes it.
- The launch flag `--capture=PATH` writes the last frame of a `--frames=N`
  run.

**Added on top.** `--capture-at=N` writes frame N and lets the application
keep running, and `host.capture(Path)` is the one-line form.

**Landed.** Built on Goldberry's master (not yet in a snapshot), ADR-0682: `--capture-at=N --capture=PATH` and `host.capture(path)`.

## G-6. The natives jar is not a module

**Mostly there already.** Since 2026-10-02 every classifier jar carries
`Automatic-Module-Name: dev.goldberry.natives.<target>` (for example
`dev.goldberry.natives.linux_x64`). It can sit on the module path, and
Goldberry finds the library there.

**Question back.** An automatic module cannot go into a `jlink` image. Is that
what keeps Lantern's class-path part? If it is, Goldberry will compile a real
descriptor into each classifier jar.

**Landed.** Nothing to build: Lantern ships a native image, not a `jlink`
image. Give `native-image` the modular jars on `--module-path` and the
classifier jar on `-cp`. The image carries `libgoldberry` as a resource and
unpacks it on first use, so the class-path part never reaches the user. The
showcase's own `nativeImage` task is built that way. A descriptor would not
help, because no module requires the natives and resolution would drop them.
Tests keep the natives as `testRuntimeOnly`, which is the usual arrangement.

## G-7. A click is synthesised for the primary button only

**Goldberry's fix.** A secondary or middle press and release on the same node
becomes `PointerEvent.Kind.AUX_CLICKED`, under the same rule as `CLICKED`, and
`button()` says which button. `CLICKED` stays primary only, as the DOM's
`click` does, so no existing handler starts hearing right clicks. A right
press dragged off the target is no click.

**Then.** `ui.widget.Clickable` can listen for `AUX_CLICKED` instead of
building a click from press and release, or go away entirely (G-8).

**Landed.** Built on Goldberry's master (not yet in a snapshot), ADR-0680. `PointerEvent.Kind.AUX_CLICKED` for secondary and middle, by the press-and-release-on-the-same-node rule; a right press dragged off is no click.

## G-8. No click handler a widget can be given

**Goldberry's fix.** `Attributes.onClick(Consumer<PointerEvent>)` on any widget.
It hears `CLICKED` and `AUX_CLICKED`, and the event's `local()` gives the
position as a fraction of the box. The box that is styled is the box that is
clicked.

Such a box is not a Tab stop. For a control a keyboard must reach, `pressable`
is still the accessible route.

**Then.** `ui.widget.Clickable` can be deleted.

**Landed.** Built on Goldberry's master (not yet in a snapshot), ADR-0680. `anyWidget.onClick(event -> …)`: `event.button()`, `event.local().fractionX()`/`fractionY()` in the node's own box. It consumes the click; a control inside the node keeps its own. Not a Tab stop. `ui.widget.Clickable` can go.

## G-9. An image decodes only into its own premultiplied BGRA buffer

**Today.** Upload the page as `B8G8R8A8_UNORM`. Goldberry's GPU supports that
format everywhere it runs, and it saves both the copy and the swap. The cost
is the one the entry names: two page formats in the batcher.

A correction: the decoder's buffer is direct memory, not heap.

**Goldberry's plan.** `Image.decodeInto(ByteBuffer, stride, ChannelOrder)`
decodes straight into the caller's memory in RGBA or BGRA, swizzled in native
code. `PixelFormat` keeps its one entry, because every backend's present reads
it.

**Landed.** `Image.decodeInto` writes straight into your memory, in
`ChannelOrder.RGBA` or `BGRA`, premultiplied, and returns the size. The
memory is asked for once the size is known, so a transfer buffer can be
mapped for it:

```java
var size = Image.decodeInto(pageBytes, ChannelOrder.RGBA,
        decoded -> PixelTarget.packed(transfer.map(decoded), decoded.width()));
```

`Image.decodeInto(bytes, destination, stride, ChannelOrder.RGBA)` does the
same into a buffer you already have, and refuses one that is too small before
writing anything. The swap happens in the one copy out of the decoder (not
in native code as planned, but in the same single pass), and no `Image` or
buffer is left behind. Every format `decode` reads works, GIF and WebP
included. ADR-0694.

## G-10. A GPU device cannot be asked for, or asked about, its adapter

**Half there since 2026-10-25** (5f9e621c). `GpuDevice.adapter()` returns the
adapter's name, driver name, driver version and driver info as SDL reports
them. SDL reports no vendor id and no integrated/discrete kind, so neither
does Goldberry, but the name says it ("AMD Radeon 780M", "llvmpipe").

**Also already true.** Goldberry asks SDL for the **low-power** GPU by default,
for windows and `OffscreenGpu` alike. On a laptop with both GPUs the
integrated one is what runs, unless the driver decides otherwise.

**Added.**

- `goldberry.gpu.power=low|high` chooses the preference for the process's
  device.
- `OffscreenGpu.open(DeviceOptions)` takes it where no device exists yet.
- `OffscreenGpu.toString()` names the adapter, so `--hull-bench` can print
  which GPU it measured.

**Landed.** Built on Goldberry's master (not yet in a snapshot), ADR-0676: `GpuPower`, `goldberry.gpu.power`, `OffscreenGpu.open(GpuPower)`, `OffscreenGpu.adapter()`, and `toString()` naming the adapter. `--hull-bench` can print `gpu.adapter().name()`.

## G-11. An offscreen render of a tree that paints nothing throws

**Goldberry's fix.** A tree that builds to `Widget.nothing()` renders as
nothing, in `Offscreen` and in a window alike: the background, and no
exception. `EventDialogTest` can then render the empty dialog on its own.

**Landed.** Built on Goldberry's master (not yet in a snapshot), ADR-0677. `Offscreen.of(w, h).render(Widget.nothing())` is the background; a strip and a window behave the same. `EventDialogTest` can drop its backdrop.

## G-12. Measuring text needs the native library

**Declined.** Goldberry measures text with HarfBuzz and Blend2D. A pure-Java
fallback would skip GSUB and GPOS (ligatures, kerning, contextual forms), so
its widths would differ from the game's, and a `text-fits` rule would pass
text that overflows in the game. Measuring exactly as the game lays out means
measuring with the same library. The workaround Lantern has, natives on the
tools' run-time class path, is the answer.

**Landed.** The refusal is recorded as ADR-0688 (2026-11-09, on Goldberry's master, not yet in a snapshot). Nothing changes in the code.
