# ADR-0564: An application renders a canvas offscreen, and captures a window as shown

- **Status:** Accepted
- **Date:** 2026-10-05
- **Relates to:** the Gwent clone's issue list (GB-011),
  [ADR-0481](0481-gpu-layers-are-placed-in-paint-order-and-shown-through-a-hole-or-read-back.md),
  [ADR-0482](0482-canvas3d-is-a-gpu-layer-an-application-renders-into.md),
  [ADR-0050](0050-goldens-render-through-offscreen-at-one-tolerance.md)

## Context

`Offscreen.gpu(GpuSurface)` has taken a read-back surface since ADR-0481, and
the toolkit's own tests hand it one: `CompositeHarness`, a test fixture over
the compositor's `SdlReadbackSurface`, in a package the module does not
export and a variant the publishing skips on purpose. An application had no
way to either. Its test of a screen with a `canvas3d` rendered the canvas's
"no GPU" notice where the scene should be, and its reference captures of a
window had to come from the desktop's screenshot tool, which GNOME refuses to
a script. The GPU device is "not made by an application" (ADR-0478), and that
still holds: what was missing was a door for a picture with no window.

## Decision

**`OffscreenGpu`, in the exported `dev.goldberry.gpu.offscreen`.** `open()`
makes a compositor and its device on the calling thread; `surface()` is the
read-back surface for `Offscreen.gpu`; `device()` is the device, for a test
that makes resources ahead; `close()` closes both. It initialises SDL's video
where nothing has, under `goldberry.gpu.videoDriver`, SDL's default, or
`offscreen` when the default cannot start, and quits what it started. With no
device, `open` throws with the driver's reason: a test that asked for the GPU
and did not get it fails, and does not pass on the notice.

**`Window.capture()`, and `--capture=PATH`.** A `BackendWindow` may answer
`capture()` with its last frame as the screen shows it. The headless backend
answers from the frame it keeps. The sdl3 backend answers from the window
surface's pixels, which SDL keeps between frames, while the window presents
on the CPU; while it is composited, the composited window draws the same
composite again, the UI texture over the layers last drawn, into a texture
of its own, and reads it back, because a swapchain texture is not readable.
`Window.capture()` hands the frame out as an `Image`. The launcher writes it
as a PNG after the last frame of a `--frames=N` run when `--capture=PATH` is
given, which is how a reference set is taken on a machine whose desktop will
not take one.

The fixture stays a fixture. It also needs the device for the composited
picture, which `OffscreenGpu` does not give, and a test of the toolkit is not
a downstream.

## Alternatives considered

- **Publishing `goldberry-gpu`'s test fixtures.** The fixture reaches into
  the compositor's package and the natives, and would have made both API.
- **An `Offscreen` that makes its own device.** `Offscreen` is `:core`'s and
  knows no GPU; the GPU is a module an application may not have, found by
  `ServiceLoader` at a window. A surface the caller opens and closes keeps the
  device's lifetime in the caller's hands, which is what a test wants.
- **Capturing on the CPU side only, from the painted buffer.** The painted
  frame of a composited window has a hole where each layer is; the picture
  the user sees is made on the GPU. Capturing there is the one way to get
  what is on screen.
- **Keeping a copy of every frame for `capture()`.** A full-frame copy per
  present, for a call made once in a run. The window surface and the UI
  texture already hold the last frame; capture reads them.

## Consequences

- `BackendWindow.capture()` and `CompositedWindow.capture()` are new SPI
  methods with a default of empty; a backend that cannot read its window back
  says so by answering nothing.
- A composited capture composites the frame a second time and waits for the
  GPU: a cost on that call, none on a frame.
- `SdlCompositor.api()` and `unavailable()` are public now, for the offscreen
  package of the same module; the package stays unexported.
- The GPU tests gained `OffscreenGpuTest`, which renders the harness's scene
  through the public path and compares it with the same golden, and
  `CompositorTest` a capture that is the presented frame made opaque.
- `TestCube` is public, so a test in another package of the module can mount
  it.
