# 481. GPU layers are placed in paint order, and shown through a hole or read back

Date: 2026-09-24

## Status

Accepted. `docs/gpu-plan.md`'s phase 4, D4 as built, and the rest of D5. It
brings the `GpuSurface` ADR-0479 moved to this phase, in a different shape:
there is no `attach` or `detach`, and no `CompositionMode` enum.

## Context

A GPU layer is something the GPU draws into a window among what the CPU paints:
a `canvas3d`, a video on the GPU. Phase 3 composites a window with no layers,
and phases 5 and 6 need layers to exist. The plan's D4 chose how they sit in the
z-order: the layer's element clears its box in the CPU frame, a hole, and the
compositor draws the layer under the frame, so what was painted after the layer
is above it.

Four things the plan left open had to be settled against the code:

- **Who knows the clip.** A layer is scissored by the clips it is painted under,
  a scroll view's viewport above all. Blend2D will not hand its clip back, and a
  `canvas` painter, which is what places a layer, only has the frame.
- **Partial repaint.** A frame that repaints only its damage walks only what the
  damage touches. A video beside a blinking caret is not painted on the caret's
  frames, and places nothing, but it is still on screen. And a video the damage
  cuts across is painted under a clip that includes the damage, which is not a
  clip the video is seen through.
- **The type split.** `:core` places layers and cannot name `:gpu`'s `GpuFrame`,
  which is how a layer renders (ADR-0479).
- **Where a layer renders.** A layer with depth and passes of its own cannot draw
  inside the window's composite pass.

## Decision

**`:core` records opaque layers in paint order and punches their holes; `:gpu`
renders each into a texture of its own, and either composites that texture under
the frame or reads it back into it.**

### `:core`

- **`render.GpuContent`** is an empty interface, an identity. `:gpu`'s
  `GpuLayer` extends it and says how it renders.
- **`Frame.gpuLayer(content, x, y, width, height)`** places one, in logical
  coordinates under the transform in force, and returns false when the frame
  cannot show it, so the painter paints a fallback. The box goes through the
  transform to its bounding box in physical pixels, each edge rounded to the
  nearest pixel. Its scissor is that box cut by the clips in force and the frame.
  What is placed is a `render.GpuPlacement(content, target, scissor)`, and
  `Frame.gpuPlacements()` lists them in paint order.
- **The frame mirrors its clip in Java**, as it already mirrored its transform
  (ADR-0390): `clipTo`, `resetClip`, `save` and `restore` keep a physical-pixel
  rectangle beside Blend2D's. A clip under a rotation is its bounding box, which
  is what Blend2D clips to as well.
- **`Frame.repaintOnly(x, y, width, height, body)`** confines `body` to the
  region being repainted. It is a clip as far as pixels go, it outlasts
  `resetClip` (Blend2D's reset returns to the last saved clip, and the region is
  saved), and it is not in the mirror, so it scissors no layer. `RenderTree`'s
  partial paint uses it, and keeps the damage out of the clips its walk sets:
  the damage is only what the walk culls against.
- **`render.window.GpuSurface`** is sealed, with two non-sealed kinds, which
  are the window's composition mode:
  - `Composited`: the frame clears the layer's box to transparent black
    (`SRC_COPY`, in physical pixels), and the window's next present draws the
    layer there;
  - `ReadBack`: `render(content, size)` gives the layer's pixels, and the frame
    draws them over the box, replacing what was there as the compositor's quad
    would. Heap pixels are copied to direct memory and held until the frame
    ends, since a threaded context blits after the call returns.

  Both are told, once a frame, what is on screen: `placed(layers)`.
- **`BackendWindow.gpuSurface()`** gives the surface for the frame about to be
  painted; `Window.paint` asks for it after `acquireFrame`. Empty by default, with
  `goldberry.gpu=off`, and with no `:gpu` on the module path. Asking makes no
  device.
- **Partial repaint keeps layers it did not reach.** After each frame the window
  merges: a layer from the last frame that the damage does not touch is kept, one
  the damage touches and the frame did not place again is gone, and one placed
  again is where the frame put it. Paint order is kept on both sides.
- **The seam** (`render.composite`, `:gpu` alone) gains `Compositor.readback()`,
  a `ReadbackSurface` per window, and `CompositedWindow.present(frame, damage,
  layers)`.

### The backends

- **sdl3.** A composited window gives a `Composited` surface and passes what the
  frame placed to its present. A window on the CPU gives the compositor's
  read-back surface: a popup, a window with a page in it, one whose claim was
  refused, and every window under `goldberry.gpu.composite=never`.
- **`goldberry.gpu.composite=auto`** now means something: a window is composited
  from the frame after it first shows a layer, and goes back to its surface two
  seconds after it last showed one (D3's hysteresis; leaving costs 6 ms on
  macOS, a visible hitch at 120 Hz). Until then its layers are read back.
  Leaving on a timer is not for good, as leaving on a failure is.
- **`goldberry.gpu=off`** is a policy of its own now, `OFF`, rather than
  `NEVER`: no window is composited and no layer is rendered, where `never` still
  reads layers back.
- **headless** windows give a read-back surface when `:gpu` is on the module
  path. Its device needs a video driver the GPU can use: `offscreen` under
  lavapipe, `cocoa` on macOS.
- **`Offscreen.gpu(surface)`** takes a surface, so a render with a layer in it
  can have the layer.

### `:gpu`

- **`GpuLayer.render(GpuFrame frame, GpuTexture target)`**, public. `target` is
  a `B8G8R8A8_UNORM` colour target exactly the layer's physical size, the
  toolkit's, kept for the layer while it is placed at that size. The layer covers
  it whole, with passes of its own. `GpuLayer.painter(fallback)` is the painter
  that places it, or paints `fallback` where it cannot be shown.
- **A composited present** renders every placed layer, in one frame submitted
  before the composite (one queue runs its submissions in order), then records
  the composite: clear to black, each layer's texture drawn 1:1 at its place,
  scissored, with a replacing pipeline, then the UI texture over them all with
  premultiplied "over". With layers on screen a window is composited on every
  present, damaged or not, since a layer may have changed with no damage at all.
  Layer work is counted in the present's submit time.
- **A read-back surface** renders the layer into the same kind of texture and
  downloads it: the same pixels, bought with a wait.
- **A layer that throws** is logged once and shows as black where it throws.
  Content that is not a `GpuLayer` shows as black too.
- `GpuDevice.wrap` and a texture's SDL handle stay package-private in the public
  package. `gpu.render` reaches them through `ApiAccess`, which `GpuDevice`
  registers when it is initialised.

### The rules

Written into `GpuLayer`'s doc, and each one shown by a golden:

- **Layers are opaque.** A layer replaces what is under it. A translucent one
  does not show the frame through, in either mode.
- **Layers are axis-aligned rectangles of whole pixels.** Clips are rectangles
  too: a rounded clip scissors a layer to its bounding box, and rounded corners
  are drawn by UI painted over the layer.
- **No layer in a group.** A frame nested in an `opacity` group or a promoted
  layer has no surface, so the layer's painter paints its fallback there.
- **Frost blurs the UI only** in composited mode (D4, unchanged).

## Alternatives considered

- **`attach` and `detach` on the surface** (D5's sketch). A layer's presence is
  a fact about the frame just painted, and the frame already records it in paint
  order. A second registry would have to agree with it every frame.
- **Rendering layers straight into the swapchain's composite pass.** It saves
  a texture per layer, but a layer could then have no depth buffer and no pass of
  its own, and read-back would need a different path. The texture is what makes
  the two modes one render.
- **Scissoring by the Blend2D clip as it stands.** It holds the damage, so a
  video half inside the damage would show half.
- **A full repaint whenever a window shows a layer.** It would make the
  keep-or-drop merge unnecessary, at the cost of repainting the whole UI for
  every caret blink in a window with a video.
- **A `CompositionMode` enum beside the surface.** The sealed kinds already say
  it, and an enum could disagree with them.

## Consequences

- **Phase 4's exit is met on Metal.** Six z-order goldens (a popup over a layer,
  a layer in a scrolled list at 100% and 150%, two overlapping layers with
  translucent UI between them, a layer under a rounded clip, and the popup at
  200%) are each rendered composited and read back through the production
  passes. The two ways agree to within two levels in 256, and both match one
  golden. The lavapipe lane has not run them yet.
- **What a layer costs, composited:** a texture of its size and a render every
  present it is on screen. There is no on-demand cache yet: a static `canvas3d`
  re-renders on a caret's frame. Phase 5 decides whether it needs one.
- **What read-back costs:** a blocking download per layer per painted frame. It
  is the mode for what cannot be composited, not the one to choose.
- **Untested here:** leaving `auto`'s composited mode after the hold (it waits
  two seconds of wall time), and layers under Wayland, X11 and Windows.
- `RenderTree` culls against the damage and clips by the tree alone, which
  changed no existing golden or test.
