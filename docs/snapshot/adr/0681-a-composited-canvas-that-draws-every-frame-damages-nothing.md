# ADR-0681: A composited canvas that draws every frame damages nothing

- **Status:** Accepted
- **Date:** 2026-11-09
- **Relates to:** ADR-0479 (a window is composited through a seam core declares and gpu provides), ADR-0671 (render scale)

## Context

A downstream game measured its frame: with the window composited on the GPU,
every frame uploaded the UI layer over the whole `canvas3d`, 5 625 KB a frame
in a 1600 × 900 window (exactly its area at four bytes a pixel), with the HUD
and without it. That is 1.1 to 1.5 ms a frame on the game's machine, and 14 MB
a frame at 2560 × 1440. It grew with the window, not with what changed.

The cause was the painter. `Canvas3dSurface` stamped its `Canvas3dPainter`
with the frame's time while `continuous`, so that the layer would be told the
time when it was painted. The render tree compares painters by equality to
decide what is damaged, so the box was damage on every frame. The CPU UI over
it was rasterised again, and the compositor uploaded the region. Yet the
compositor draws a GPU layer from its own texture on every present, and
re-renders one whose `needsRender()` is true, which a continuous canvas's
always is. Only the time had to reach the layer, and it was riding on the
painter to get there.

## Decision

- **The frame's time reaches the layer as the tree is built**, in
  `Canvas3dSurface.painter(...)`, not from the paint. The painter no longer
  carries the time.
- **A continuous canvas's painter is equal from frame to frame while it is
  composited.** It still differs at a new revision or render scale.
- **Where its pixels are read back into the CPU frame**, the painter still
  differs every frame: the frame is the picture there. That covers offscreen
  renders, a window presenting on the CPU, and a group that bakes it in.
- The layer remembers, from the frame that last placed it, whether that frame
  composited it, through the new `Frame.composesGpuLayers()`. Until its first
  paint, a canvas is treated as read back.

## Consequences

- A composited window with a continuous canvas and a calm UI uploads nothing
  after the first frame. The layer is still rendered every frame. The CPU work
  per frame is now what the UI over the canvas actually changed, as the game
  asked.
- The canvas's notice (`owner.shown`) is updated only on frames that paint the
  box. It reports a state that changes with the surface, and a surface change
  repaints the window.
- `Canvas3dTest` builds painters through `Canvas3dSurface.painter`, not a copy
  of the stamping rule. `continuousReadBack` and `continuousComposited` pin
  the two cases.
