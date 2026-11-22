# ADR-0731: A composited window lends its frame from upload memory

- **Status:** Accepted
- **Date:** 2026-11-22
- **Relates to:** ADR-0712 (the plan, D2 and D4, which this builds), ADR-0478
  (the GPU API), ADR-0732 (the same raster under an application's texture)

## Context

Phase 2 of W3.10 built `MappedRaster`, a BGRA raster in an SDL upload
transfer buffer that stays mapped, with its copy submitted behind a fence. D2
said the composited window's frame comes first. Until now a composited
window's `acquireFrame()` answered empty, the frame loop painted into a heap
buffer of its own, and `present` copied each damaged region into staging
memory before the GPU copied it again.

The frame loop already repaints in part only when it gets the same buffer as
last frame and the backend says the buffer keeps its contents. A raster that
is one buffer per size, and keeps its pixels, satisfies both with no change
to the loop.

## Decision

1. **The SPI gains `CompositedWindow.lendFrame(PhysicalSize)`**, a default
   method that answers empty. `SdlCompositedWindow` answers its raster's
   pixels where the device's check (or `goldberry.gpu.inPlace`) says to
   paint in place, made at the asked size and remade when it changes. Lending
   waits for the last copy out of it.
2. **`Sdl3Window.acquireFrame()` asks the compositor** while composited, and
   falls back to the frame loop's own buffer when it lends nothing. The
   surface is never lent while composited.
3. **`present` knows the lent frame by identity.** That frame's damage is
   copied by `MappedRaster.upload` in a command buffer of its own, submitted
   ahead of the composite, so the queue runs the copy first. Any other frame
   is staged as before, so a caller that paints into its own buffer still
   works.
4. **A refused raster stages for good.** If SDL refuses the memory, the window
   logs it once and stays on the staged path; it does not retry each frame.
5. **A GPU failure copies the frame out first.** The CPU fallback presents the
   frame after the compositor is closed, and the lent frame's memory goes
   with the compositor, so the window copies it into memory of its own before
   letting go. A failed lend leaves the GPU the same way a failed present
   does.

## Consequences

- On Vulkan, and wherever the check says so, a composited window's upload
  stage is recording a copy, not copying the frame: at 4K with the whole frame
  damaged, phase 0 measured 0.17 ms against 8.8 ms on the GTX 1660 Ti.
- `uploadBytes` counts what the driver's copy shape moves: the damage on
  Vulkan, the whole-row bands that hold it on Metal and Direct3D 12.
- Tests that used an empty `acquireFrame()` to mean "composited" now ask the
  window's presentation instead, because a composited window may lend a frame.
- Each composited window holds one frame-sized buffer of pinned memory in
  place of the frame loop's heap buffer; the device's shared staging buffer
  stays for everything else.
