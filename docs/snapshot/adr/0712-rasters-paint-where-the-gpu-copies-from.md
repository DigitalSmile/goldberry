# ADR-0712: Rasters paint where the GPU copies from, where measuring says so

- **Status:** Accepted, as the plan for W3.10 of the downstream gaps plan
  (Tessera's T-30, owner's D16). Nothing is built yet; `docs/zero-copy-tiles.md`
  holds the phases.
- **Date:** 2026-11-21
- **Relates to:** ADR-0478 (the GPU API this extends), ADR-0662 (video
  planes written once into the GPU's upload memory, the same move for
  pictures), ADR-0709 (a frame drawn on the CPU over GPU layers)

## Context

A window composited over a GPU layer, and an application's own tiles drawn by
a `Canvas3dRenderer`, are painted by Blend2D into heap memory, staged into an
SDL upload transfer buffer, and copied into a texture by the GPU. Tessera asked
for real zero-copy between the two (T-30, D16 (c)).

SDL 3.4.18 gives no native handles and imports neither memory nor textures
(SDL#10876), so the GPU sampling Blend2D's own memory is out of reach on every
driver. What is reachable is painting straight into the upload buffer, which
removes the CPU copy. SDL's sources suggest it pays on Vulkan, whose upload
buffers prefer cached memory and stay mapped, and not on Metal or Direct3D 12,
whose upload memory is write-combined while Blend2D reads what it blends onto.
Direct3D 12 drivers without OPTIONS13 also make a hidden third copy when rows
are not padded to 256 bytes.

## Decision

The owner took the plan's decisions with one change:

1. **D1:** "zero-copy" means no CPU copy. Blend2D paints in GPU-visible memory
   and the GPU's copy engine moves the damaged regions into the texture. No
   copy at all is declined until SDL exposes native handles.
2. **D2:** the composited window's frame first, then an exported API.
3. **D3:** no driver is ruled in or out ahead of time; D6 decides, per driver
   and at run time. The plan's fixed table, which kept Metal and Direct3D 12
   staged, was rejected. Direct3D 12's rows are padded either way.
4. **D4:** one raster per window or texture, with the copy submitted in a
   command buffer of its own behind a fence that the next paint waits on. A
   ring of rasters with a buffer age is the answer only if stalls show.
5. **D5:** `RasterTexture` in a new exported `dev.goldberry.gpu.raster`: it owns
   the texture, the raster and the damage, and says which path it took.
6. **D6:** the in-place path is switched on for a driver only if the 4K
   upload stage, with the whole frame damaged, is at least halved, and painting
   into the mapped memory is no more than 5% slower than into heap memory. A
   probe at device creation applies the second number at run time.

## Consequences

- Phase 0 measures every driver in place and staged: the GTX 1660 Ti and
  lavapipe here, Direct3D 12 and Metal on CI. Its numbers go into the plan and
  into this record's amendment.
- The run-time probe is part of the in-place raster (phase 2), not an option,
  because D3 leans on it.
- The path SDL's sources suggest is not a promise: the same-pointer-without-
  `cycle` behaviour is pinned by a test, so an SDL update that changes it fails
  a test rather than a screen.
- Each composited window keeps a frame-sized buffer of pinned system memory in
  place of the heap buffer and the staging buffer it replaces.
