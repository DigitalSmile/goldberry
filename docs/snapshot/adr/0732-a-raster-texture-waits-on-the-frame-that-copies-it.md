# ADR-0732: A raster texture waits on the frame that copies it

- **Status:** Accepted
- **Date:** 2026-11-22
- **Relates to:** ADR-0712 (the plan, D4 and D5, which this builds), ADR-0731
  (the composited window's frame), ADR-0478 (the GPU API this extends)

## Context

D5 asked for `RasterTexture` in a new exported `dev.goldberry.gpu.raster`:
paint, then `frame.copyPass(tile::upload)`, then sample `tile.texture()`.
Painted in place, the upload is a copy the GPU makes out of the raster later,
so the next paint must wait for it (D4 (a)). The composited window submits
its copy in a command buffer of its own behind a fence. An application's
upload, though, is recorded into the application's own frame, which the
toolkit or the application submits whenever it likes, so there is no fence
of the raster's own to wait on.

## Decision

1. **The copy goes into the application's copy pass, and waits on that
   frame.** A frame keeps one `FrameCompletion`, made the first time a copy
   pass asks for it and counted as one holder of the frame's fence, which its
   readbacks already share. The next paint waits on it.
2. **A frame still recording is not waited for.** Until the frame is
   submitted the GPU has not begun, so a paint goes ahead; the frame then
   copies what was painted since, and the damage is kept for the next upload
   too. `MappedRaster` keeps every pending copy that answered "not submitted"
   and asks again at the next paint, so a fence is neither leaked nor skipped.
   A discarded frame counts as done.
3. **The module's way in is `ApiAccess`.** It already bridged the compositor
   to the GPU API's insides; it now also hands `…gpu.raster` the SDL device,
   the device's in-place decision, and a copy pass's SDL pass with its frame's
   completion. Nothing of `:natives` reaches the exported surface.
4. **The device's decision is kept on the `GpuDevice`.** The compositor sets
   it when it makes the device, and a device it did not make runs the check
   the first time a raster texture asks.
5. **The surface is small:** `create`, `staged`, `paint` (whole, by region,
   and by region at a scale), `damage`, `upload`, `texture`, `inPlace`,
   `close`. A raster's damage is the regions painted, collapsed to their
   bounds past 32 and to the whole when one covers it.
6. **The showcase draws its board with texture-to-texture copies.** The GPU
   screen's card paints a board's squares as raster textures and copies them
   into the `canvas3d` target, so it needs no shader of its own; a move
   repaints two squares, and the card says how many tiles went up and how.

## Consequences

- Tessera's Board tiles get the same no-CPU-copy path as the window, with
  one call per tile per frame and no fence handling of their own.
- `GpuFrame.submit` asks for a fence whenever a raster was uploaded in it,
  where it used to only when it had readbacks.
- A raster texture closed while the frame of its last upload is still
  recording leaves that frame's copy to SDL, which keeps the transfer buffer
  until the command buffer is done with it.
- The guide section is parked until the release.
