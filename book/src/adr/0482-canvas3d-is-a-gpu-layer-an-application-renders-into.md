# 482. `canvas3d` is a GPU layer an application renders into

Date: 2026-09-25

## Status

Accepted. `docs/gpu-plan.md`'s phase 5, built on ADR-0481's GPU layers.

## Context

Phase 4 made GPU layers: content the GPU draws, placed in paint order, and
composited under a window's frame or read back into it. `canvas3d` is the first
layer an application writes, and the consumer D5 was waiting for.

Five questions came with it:

- **What the application writes.** It needs a device to make pipelines and
  buffers on, a frame to record into, a target to draw into, and a place to
  release what it made.
- **When it is drawn.** A spinning model is drawn every frame. A model viewer
  whose camera moves once a minute should cost nothing between moves. Phase 4
  rendered every layer on every present, so a still 3D view was drawn again
  for a caret blinking beside it.
- **How a frame is asked for.** The render tree repaints a `canvas` box only
  when its painter changes, and compares painters with `equals`. A read-back
  canvas has to be repainted to be drawn again. A composited one does not, but
  its window must still present.
- **What it shows with no GPU.** Plan D5 said "the widget paints
  `--gb-canvas3d-unavailable` and the reason, as `web-view` does." `web-view`
  gives its reason as a `message` child, which is chosen at build time. A
  canvas learns it has no GPU only when it is painted.
- **Where its module sits.** `:gpu` had no widgets and did not depend on
  `:widgets`.

## Decision

**`canvas3d` is a stateful widget that keeps one GPU layer per renderer. The
layer drives the application's `Canvas3dRenderer` through its lifecycle, and
says when it has nothing new to show.**

### The renderer

`gpu.view.Canvas3dRenderer`:

- `init(GpuDevice)`: once, before the first render, on the window's device.
- `resize(PhysicalSize)`: before the first render, and whenever the canvas's
  physical size changes.
- `render(GpuFrame, Canvas3dTarget)`: for each frame the canvas is drawn.
- `dispose()`: when the canvas leaves the tree, and before `init` on a new
  device.

All four run on the UI thread. `Canvas3dTarget` holds three things:

- the colour texture, which is the layer's texture at the canvas's size and
  belongs to the toolkit;
- the depth texture, when the canvas asked for one (`depth=d16|d32`), kept by
  the canvas and remade on resize;
- the frame's time in nanoseconds since the canvas was first drawn, taken from
  the window's clock. With a virtual clock, such as `Offscreen`'s, a frame is
  therefore exact.

### Drawing only what changed

- **`GpuLayer.needsRender()`**, true by default. When a layer says false and
  the compositor still holds its texture at the same size, the compositor
  shows the texture again without rendering. A new texture is always rendered
  into, and so is one whose last render threw. False is therefore never wrong;
  it only promises that the last picture is still right.
- **A canvas is `continuous` or on demand.**
  - `continuous`: the canvas needs a render on every frame, and its leaf
    reports `isAnimating`, which keeps frames coming.
  - On demand: it needs one on its first frame and when `revision` changes.
    `Canvas3d.revision(n)` is what an application rebuilds with, and markup's
    `revision=` sets it.
- **The painter is a record**, `Canvas3dPainter(layer, stamp, nanos, …)`, so
  the render tree's `equals` decides the damage:
  - on demand, the stamp is the revision, so an unchanged canvas is not
    repainted;
  - continuous, the stamp is the frame's time, so the canvas's box is repainted
    every frame. That is what read-back needs; composited, it costs a hole's
    upload.

### No GPU

- The painter fills the box with `--gb-canvas3d-unavailable`, a token in both
  Nord themes.
- In a running window, a deferred rebuild adds a `message` over the canvas
  (class `canvas3d-notice`) saying why. This is `web-view`'s zero-delay rebuild
  from inside a paint. There are three reasons:
  - the GPU is off (`goldberry.gpu=off`);
  - there is no GPU here: an `opacity` group, or a window with none;
  - the GPU failed.
- `Frame.hasGpu()` is what tells the first two cases from the third.
- `Offscreen` has no host to rebuild through, so an offscreen canvas without a
  GPU shows the fill alone. Its golden, `canvas3d-unavailable`, runs on every
  lane.

### The module

`:gpu` now does what `:media` does for its widgets:

- applies the catalog weaver;
- takes `:widgets` as `api`;
- `requires transitive` it;
- exports `gpu.view`;
- `uses WidgetCatalog`.

The woven catalog is `gpu.view.GoldberryCatalog`, one widget.

### The showcase

The **GPU** tab has three cards:

- a spinning cube in a continuous canvas, with a chip painted over it;
- the same cube on demand, turned by a slider that bumps the revision;
- a `hud` with the present readings.

The cube is written as an application's renderer. Its HLSL is the showcase's
own (`example/src/main/shaders`), compiled by `:gpu:compileShaders` into the
showcase's resources, loaded with `ShaderCode.load`, and checked fresh by
`ShowcaseShadersTest`.

## Alternatives considered

- **`init(Canvas3dHost)` with `device()` and `requestRedraw()`.** A renderer
  asking for its own redraw is imperative. A revision on the widget is how
  every other widget here changes: rebuild with a new value.
- **A painter that is a new lambda every build**, as `video-view`'s is. That
  repaints an on-demand canvas on every rebuild of anything around it.
- **The reason drawn as text by the painter.** The painter would need a font
  and a paragraph of its own, and would draw words the stylesheet cannot style.
  The `message` widget is the toolkit's.
- **A runtime switch between composited and read-back in the showcase** (the
  plan's row). The mode is the window's, set at launch. Switching it at run
  time needs a window-level composition API, and its only consumer would be
  this switch (ADR-0019). The tab instead says which launch properties show
  which mode.

## Consequences

- **Phase 5's exit, on Metal:**
  - the cube at a fixed angle is a golden in `:gpu`, the same composited and
    read back;
  - the GPU screen is a gallery golden in `:example`: drawn on the GPU through a
    headless window's read-back surface (`gallery-gpu-drawn`, on the new
    `:example:gpuTest`), and without a GPU on every lane (`gallery-gpu`);
  - the showcase runs composited on Metal with both cubes.
- **A new GPU lane.** `:example:gpuTest` runs on the first thread like
  `:natives`' and `:gpu`'s, and is part of `check`.
- **A continuous canvas's box is uploaded every frame** when composited: it is
  a hole, but it is damaged. A signal of "layer only, no UI damage" could skip
  that. It is not needed at the sizes measured here.
- **The GPU tab's notice needs a window**, so no golden shows it.
- **Not tested:** the device-loss path. A renderer disposed and initialised
  again on a new device is tested; a device lost mid-run is phase 7's.
