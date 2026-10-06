# ADR-0571: Strips and sessions show GPU layers through the builder's surface

- **Status:** Accepted
- **Date:** 2026-10-06
- **Relates to:** the Gwent clone's issue list (GB-015),
  [ADR-0564](0564-an-application-renders-a-canvas-offscreen-and-captures-a-window-as-shown.md)

## Context

ADR-0564 gave an application's tests a read-back surface, `OffscreenGpu`, for
`Offscreen.gpu(surface)`, so that `render(root)` draws a `canvas3d` and a
video. `strip(root)` and `session(root)` ignored it. Each builds a
`Filmstrip`, and the strip made its frames with `Frame.over(buffer, scale)`,
which has no surface, so a GPU layer in a session's frame was neither drawn
nor reported as missing. The downstream's match screen is tested in a
session, for the pointer router, the virtual clock and the timers, and its
board is a `canvas3d`: `render` showed the board, and `session(...).frame()`
showed the background where the board was.

## Decision

**The builder's GPU surface travels with the buffer description.**
`Offscreen.Surface`, the package-private record a strip and a session are
made from, carries the optional `GpuSurface` with the size, scale, format and
background, and knows how to make a frame over it and how to tell the surface
what a frame placed. `Offscreen.render` and `paint` use the same two methods,
so there is one place that decides how a frame shows GPU layers.

**`Filmstrip.frame()` paints over the surface and tells it afterwards,** as
`render` does and as a window does after each frame. The layout-only passes,
the mount pass and a session's settle between events, stay surface-less:
they paint nothing, and a surface told about them would hear of layers no
picture shows.

The strip gets the same as the session, since `Session` is built on
`Filmstrip`, and a strip of a `canvas3d` was just as empty.

## Alternatives considered

- **A `gpu(...)` on `Session` itself.** A second place to set what the
  builder already says, and one that a strip would not have.
- **Telling the surface after every pass.** A read-back surface may keep what
  it made for a layer until the layer stops being placed. A layout pass that
  placed nothing would release it between every two events.

## Consequences

- A session's and a strip's frames hold the GPU layers `render` would, and
  the surface hears of each frame's placements.
- `OffscreenGpuSurfaceTest` in `:core` checks the three terminals with a fake
  read-back surface. `OffscreenGpuTest` in `:gpu` checks a session's first
  frame, a frame after the clock moved, and a strip's frame against the still
  render's cube on a real device.
- `Filmstrip`'s package-private constructors take the `Surface` record in
  place of its four fields.
