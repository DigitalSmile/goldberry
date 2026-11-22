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

## Amendment, 2026-11-22: phase 1

SDL 3.4.18's Direct3D 12 upload is stricter than the plan said. Without the
unrestricted-pitch feature it copies the upload again unless the row pitch
is **exactly** the region's packed row rounded up to 256 bytes (any other
multiple of 256 is realigned as well) and the offset is a multiple of 512.
The staging is laid out to that rule on `direct3d12` and stays packed on every
other driver. It doesn't ask for the feature, which SDL doesn't expose: on a
driver that has it, padded rows cost at most 255 bytes a row of staging
memory and nothing else.

A compressed format's rows of blocks stay packed, because a padded upload is
recorded in texels and those uploads are one-offs at load time, not per frame.
On Direct3D 12 they still take SDL's realignment, as before.

## Amendment, 2026-11-22: phase 0's numbers

Measured on the GTX 1660 Ti and on lavapipe (`docs/zero-copy-tiles.md`,
phase 0). D6 is on for Vulkan on both:

- (1) The 4K whole-frame upload stage in place is 0.17 and 0.07 ms, against
  8.81 and 6.26 ms staged.
- (2) Painting into the mapped buffer is within 1% of heap memory.

Two things the decisions didn't say, now settled by the numbers:

- **The raster stays mapped.** Mapping it around every paint costs about
  0.04 ms, which is negligible in a frame but most of a mebibyte probe. SDL
  doesn't forbid recording from a mapped transfer buffer, so the in-place
  raster keeps its mapping for its whole life, and the run-time probe times
  memory that is already mapped, interleaved with heap.
- **On Direct3D 12, in place copies whole-row bands.** Its exact-pitch rule
  (phase 1) holds for a region of a frame-sized buffer only when the region is
  the frame's width, so each region is widened to whole rows starting at a
  multiple of 512 bytes. That copies more on the GPU and nothing more on the
  CPU.

Direct3D 12 and Metal are measured by the benchmark lane's `upload-paths`
job; D6 for them is recorded when it has run.
