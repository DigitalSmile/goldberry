# Zero-copy between Blend2D rasters and GPU textures — implementation plan

Written 2026-11-21, before any code. This is W3.10 of
[`gaps/goldberry-gaps-plan.md`](gaps/goldberry-gaps-plan.md), Tessera's T-30:
"Board tiles and video frames share the GPU canvas path". The owner chose D16
(c) on 2026-11-08: real zero-copy between a raster Blend2D paints and a texture
the GPU samples, where the device allows it, and staging elsewhere. The plan
said this document comes first, because the answer differs per driver. It
follows `gpu-gaming-plan.md`'s shape: what was found, the decisions with a
status, phases with exit criteria, risks, and a log.

Each decision becomes an ADR when it is taken, parked in `docs/snapshot/adr/`.
**No decision is taken yet, and no code is written until the owner has read
this.**

## 1. Where things stand

Read from the tree on 2026-11-21 (master at `5998561b`), and from the SDL
3.4.18 sources the superbuild unpacks into `natives/.deps/linux-x64/sdl3-src`.

| Area | State |
|------|-------|
| Painting | Blend2D paints into any `PixelBuffer` (`size`, `format`, `stride`, a `ByteBuffer`) through `Frame.over(buffer, scale)`. `BlendImage` wraps the buffer's memory (`imageInitFromData`), so Blend2D paints in place, wherever the memory is. With `threadCount` above 1 its workers write the same memory |
| The window's frame, on the CPU | `Sdl3Window.acquireFrame()` lends SDL's window surface; the loop paints into it, and `present` hands it back. No copy of the toolkit's own |
| The window's frame, composited | `acquireFrame()` answers empty, so the frame loop paints into a heap buffer of its own, kept between frames for partial repaint (`retainsFrameContents()`). `SdlCompositedWindow.present` then **copies** each damaged region into a `StagingBuffer` (one SDL upload transfer buffer, mapped with `cycle`, regions packed one after another) and records a copy pass from it into a `B8G8R8A8_UNORM` texture. The GPU copies again, buffer to texture |
| Applications' textures | `CopyPass.upload(texture, source, rowBytes, regions)` and `upload(texture, PixelBuffer, regions)`: the same two copies, through the device's own `StagingBuffer`. A `Canvas3dRenderer` that keeps Board tiles as textures uploads them this way, by damaged region. That is Tessera's "today" |
| Video | The one path with a single copy. `PlaneStaging` hands a decoder `StagedPlanes`: planes laid out in an `UploadBuffer` (an SDL upload transfer buffer), written where they are decoded, and uploaded by the GPU from there. Rows are padded to 256 bytes where the device takes padded rows. The buffer is unmapped for the instant the upload is recorded and mapped again after |
| The toolkit's own Vulkan device | `RayDevice` (`:gpu` `raytrace.vulkan`) allocates buffers and memory itself (`DeviceBuffer`, `memoryType`). It shares no memory with SDL's device: `HardwareTier.traceLighting` uploads its planes into the frame's textures through `CopyPass` |
| SDL's native handles | **None** (`gpu-gaming-plan.md` §1). No `VkDevice`, `VkImage` or `VkDeviceMemory`, no `MTLDevice` or `MTLBuffer`, no `ID3D12Device` or `ID3D12Resource`. Texture creation takes a name and D3D12 clear values (`SDL_PROP_GPU_TEXTURE_CREATE_*`); there is no property to import memory or wrap a native texture. libsdl-org/SDL#10876 (native handles) is open |
| Fences | `SdlGpuCommandBuffer.submitWithFence` and `SdlGpuFence` (query, wait, release) are bound |
| Measuring | `FrameStats` has an `upload` span (`FrameRing.upload()`), shown by the `hud` |

What SDL 3.4.18 does with an **upload** transfer buffer, read from its sources:

| Driver | Memory | Mapping | `cycle` |
|--------|--------|---------|---------|
| Vulkan (`SDL_gpu_vulkan.c`) | Required `HOST_VISIBLE \| HOST_COHERENT`, **preferred `HOST_CACHED`**, `DEVICE_LOCAL` tolerated (UMA) | **Persistently mapped.** `MapTransferBuffer` returns the same pointer each time, and `UnmapTransferBuffer` is a no-op | A fresh buffer only when `cycle` is true **and** the GPU still references the active one. With `cycle` false nothing waits: writing while a copy reads is a race |
| Metal (`SDL_gpu_metal.m`) | Shared storage, **`MTLResourceCPUCacheModeWriteCombined`** for upload buffers (download buffers take the default cache) | `[buffer contents]` | As Vulkan |
| Direct3D 12 (`SDL_gpu_d3d12.c`) | `D3D12_HEAP_TYPE_UPLOAD`: system memory, **write-combined** | Persistently mapped | As Vulkan |

Two more facts from those sources:

- **Direct3D 12 copies again, on the CPU, when rows are not aligned.** Unless the
  driver reports `UnrestrictedBufferTextureCopyPitchSupported` (OPTIONS13), an
  upload whose row pitch is not a multiple of 256 bytes, or whose offset is not
  a multiple of 512, is copied row by row into a temporary upload buffer made
  for that one upload. (Phase 1 found the rule stricter: the pitch must be
  *exactly* the region's packed row rounded up to 256, so a longer multiple of
  256 is realigned too.) `StagingBuffer.stage` packs each region's rows at its own
  width and places regions back to back, so on such a driver nearly every UI
  region takes this path: three copies, one of them into a buffer allocated per
  region per frame. Not measured yet (§4, phase 0).
- **Metal reads every upload's rows as packed** (`uploadsPaddedRows()` is false
  there), so a region cannot be copied from the middle of a wider raster in one
  go; it is copied row by row, as `CopyPass` already does.

**What blending needs from memory.** Blend2D reads the pixels it composites
onto (source-over reads the destination). Reads from write-combined memory are
uncached, and typically one or two orders of magnitude slower than from cached
memory. So painting into an upload buffer pays off only where that buffer is
cached: Vulkan, where SDL asks for `HOST_CACHED`. On Metal and Direct3D 12,
SDL's upload buffers are write-combined, and painting into them would be slower
than today's copy.

## 2. What this plan delivers

1. **Rasters that live in GPU-visible memory.** On Vulkan, Blend2D paints a
   raster straight into an SDL upload transfer buffer, and the GPU copies its
   damaged regions into the texture. No CPU copy.
2. **The composited window paints in place.** While a window is composited on
   Vulkan, `acquireFrame()` lends the frame in that memory instead of answering
   empty, so every application with a `canvas3d`, a video or a GPU layer gains,
   with no code of its own.
3. **An exported raster texture for applications.** A texture kept in step with
   a raster the application paints (Board tiles), uploaded by damaged region,
   in place on Vulkan and staged elsewhere, through one API.
4. **Cheaper staging where in place is not possible.** On Direct3D 12, staged
   rows padded to 256 bytes and regions placed at multiples of 512, so SDL's
   hidden third copy goes.
5. **Numbers.** A benchmark that says, per driver, what each path costs, and a
   gate that decides whether the in-place path is switched on.

**Not in this plan**, so it is not rediscovered:

- **No copy at all**, the GPU sampling the memory Blend2D paints: a linear
  `VkImage` in host-visible memory, `VK_EXT_external_memory_host` importing the
  raster's pages, a Metal texture made from `newBufferWithBytesNoCopy`, a D3D12
  heap from `OpenExistingHeapFromAddress`. Each needs the device's native
  handle, which SDL does not give (§1), and a texture SDL can sample, which SDL
  cannot import. The toolkit's own Vulkan device (D3 of the GPU plan) can do all
  of it, but nothing it makes can be drawn by SDL's device. D1 records this.
- An SDL patch. The project's rule stands.
- Moving the compositor off SDL_GPU.
- Tiles drawn on the GPU (vector rendering in shaders). Blend2D stays the
  painter.

## 3. Decisions

### D1. What "zero-copy" means here (taken: (a))

| Option | What it is | Reachable |
|--------|------------|-----------|
| **(a) No CPU copy** | Blend2D paints in GPU-visible memory; the GPU's copy engine moves the damaged regions into the texture | Through SDL on Vulkan today. On Metal and D3D12 only at a loss (write-combined) |
| (b) No copy at all | The texture *is* the painted memory, or a shader reads it directly | Not through SDL 3.4: no native handles, no import |

**Recommendation: (a)**, and record (b) as declined until SDL exposes native
handles (#10876). Revisit then. On a discrete GPU (b) would not even be the
faster one: a shader sampling host memory reads it over PCIe on every frame,
while (a) moves only the damaged regions, once.

### D2. Where the in-place path is used (taken: (a))

(a) Both: the composited window's frame first, then an exported raster texture
for applications. (b) Only the exported API.

**Recommendation: (a).** The window is the case every GPU-layered application
hits, at the largest size, and it proves the mechanism before an API is
promised.

### D3. Per driver (taken: D6 decides)

No driver is ruled in or out ahead of time. Every driver gets the in-place
path, and D6's gate switches it on per driver: phase 0's benchmark gives
each driver's numbers, and the probe at device creation decides at run time,
so a driver that hands out uncached or write-combined memory stays on the
staged path without a release in between.

| Driver | What is expected | What decides |
|--------|------------------|--------------|
| Vulkan | In place: SDL's upload buffers prefer `HOST_CACHED` and stay mapped | D6 |
| Metal | Staged: upload buffers are write-combined, so painting (which reads what it blends onto) may fail D6's second number | D6 |
| Direct3D 12 | Staged: the upload heap is write-combined, as on Metal | D6 |
| Software (`lavapipe`) | In place: all memory is cached system memory | D6 |

The expectations are what SDL 3.4.18's sources suggest, not a rule: where a
measurement disagrees, the measurement wins. Either way Direct3D 12 needs rows
padded to 256 bytes and regions at multiples of 512 (§2 item 4), for the
staged path and the in-place one alike, so phase 1 stays.

The owner rejected the first proposal, a fixed table with Metal and
Direct3D 12 always staged, on 2026-11-21. Also rejected: painting on Metal
into a *download* transfer buffer, which SDL makes with the default cache. SDL
does not check a copy pass's source usage, so it would work, but it rests on an
internal detail no SDL release promises.

### D4. Who may write while the GPU reads (taken: (a))

The GPU's copy reads the raster some time after `present` records it. The next
frame must not paint the same pixels until the copy has finished. Two ways:

| Option | How | Cost |
|--------|-----|------|
| **(a) One raster, the copy submitted alone** | The copy pass goes into a command buffer of its own, submitted first with a fence; the composite follows in a second one. The next frame waits for that fence before it paints | Nothing in steady state: the copy finishes in well under a frame. A stall only when the GPU's queue is still busy with an earlier frame (a heavy `canvas3d`), because a queue runs in order |
| (b) A ring of rasters with a buffer age | Two or three rasters, used in turn; each remembers which frame it last held, and the next paint repaints the union of the damage since then (EGL's `buffer_age`) | No stall. More painting, three times the memory (100 MB at 4K for three), and `retainsFrameContents()` becomes an age, a change to the backend SPI and the render tree's partial repaint |

**Recommendation: (a) first**, with (b) as the answer if phase 0 or phase 3
shows stalls under a GPU-heavy canvas. (a) keeps the frame loop exactly as it
is: the raster still holds last frame's pixels.

Not an option: mapping with `cycle`. A cycled buffer is fresh memory without
last frame's pixels, so partial repaint would break.

### D5. The application's API (taken: `RasterTexture`)

`dev.goldberry.gpu.raster`, a new exported package:

```java
var tile = RasterTexture.create(device, new PhysicalSize(512, 512));
tile.paint(damage, frame -> drawTile(frame));      // Blend2D, in place where it can be
gpuFrame.copyPass(tile::upload);                     // the damaged regions only
var texture = tile.texture();                        // sampled like any other GpuTexture
```

- `RasterTexture` owns a `GpuTexture` (`B8G8R8A8_UNORM`, `SAMPLER`) and its
  raster: GPU-visible memory where D3 says so, heap memory elsewhere.
- `paint(regions, painter)` clips to the regions, waits on the last upload's
  fence where the raster is the copy's source, and paints. The damage it
  collects is what `upload` copies.
- `upload(CopyPass)` copies the collected damage and clears it.
- `inPlace()` answers which path it took, for a test and the `hud`.

Alternative: a lower-level `MappedRaster` with no texture, which an application
copies itself. **Recommendation: `RasterTexture`**, because the fence and the
damage only make sense next to the texture they feed.

### D6. The gate (taken, with these numbers)

The in-place path is switched on per driver only if phase 0's benchmark shows:

1. the `upload` stage at 4K with the whole frame damaged at least halved, and
2. Blend2D's painting into the mapped memory no more than 5% slower than into
   heap memory, on the same scene.

SDL does not say which memory type it picked, so (2) is also how we learn
whether the buffer is cached. A one-off probe at device creation (paint a
mapped megabyte, compare with heap) makes the choice at run time, which D3
relies on. Taken with these numbers.

## 4. Phases

### Phase 0 — measurements (here 2026-11-22; Windows and macOS wait for a lane run)

- A benchmark in `gpu/src/benchmark`, beside `GpuVideoProbe`, run by the
  benchmark lane: (i) Blend2D paints a fixed UI scene into heap memory and into
  a mapped SDL upload transfer buffer, single- and multi-threaded; (ii)
  `present`'s upload stage at 1080p and 4K with 1%, 10% and 100% of the frame
  damaged, on today's path; (iii) the same through an in-place prototype in the
  benchmark's own code, on every driver (D3).
- On this machine: the GTX 1660 Ti (Vulkan, x11) and lavapipe. On CI: Windows
  (D3D12, so the hidden copy shows up or not) and macOS (Metal), both in place
  and staged.
- An `SdlGpuDeviceTest` case that pins the Vulkan facts §1 relies on: two maps
  without `cycle` return the same address.

Exit: the numbers in this document, and D6 answered per driver.

**Results, 2026-11-22.** `UploadPathProbe` (`:gpu:uploadPathProbe`, in
`gpu/src/benchmark`), run here on the GTX 1660 Ti (driver 610.57, x11) and on
lavapipe (Mesa 26.0.8, LLVM 21), 8 processors, 100 samples after 20. Medians in
ms; "stage" is the UI thread's part, "copied" runs until the GPU's fence
signalled.

Painting (the scene is the paint benchmark's showcase frame, with translucent
panels and six text cards):

| Frame | Workers | 1660 Ti heap | kept mapped | lavapipe heap | kept mapped |
|---|---|---|---|---|---|
| 1920x1080 | 0 | 6.43 | 6.46 (+0.5%) | 6.67 | 6.56 (−1.6%) |
| 1920x1080 | 4 | 2.88 | 2.80 (−2.9%) | 2.65 | 2.72 (+2.6%) |
| 3840x2160 | 0 | 13.45 | 13.37 (−0.6%) | 13.30 | 13.23 (−0.5%) |
| 3840x2160 | 4 | 4.91 | 4.91 (+0.1%) | 4.78 | 4.82 (+0.8%) |
| 512x512, one translucent fill | 0 | 0.055 | 0.058 (+5.0%) | 0.055 | 0.056 (+2.4%) |

The 4K upload of the whole frame:

| Path | 1660 Ti stage | copied | lavapipe stage | copied |
|---|---|---|---|---|
| staged, packed (today) | 8.81 | 15.46 | 6.26 | 10.39 |
| staged, 256/512 (phase 1's layout) | 8.58 | 15.06 | 5.83 | 9.75 |
| in place | 0.17 | 6.80 | 0.07 | 3.54 |
| in place, whole-row bands | 0.18 | 6.80 | 0.06 | 3.53 |

At 10% damage of 4K the staged stage is 1.11 ms (1660 Ti) and 0.79 ms
(lavapipe), against 0.06 and 0.07 in place. At 1% they're all below 0.13 ms.
The full tables are in the log below.

What the numbers say:

- **D6 on Vulkan: on**, on both devices. (1) In place, the 4K whole-frame stage
  is 0.02 (1660 Ti) and 0.01 (lavapipe) of staged, against a bar of 0.50.
  (2) Painting into the mapped buffer is within 1% of heap at 4K, against a bar
  of 5%: SDL's `HOST_CACHED` preference holds on both.
- **The buffer stays mapped.** Mapping and unmapping it around every paint
  costs about 0.04 ms (the shared arena's close is a handshake, plus two SDL
  calls). That's nothing at frame scale, but it is most of the 1 MiB probe. So
  phase 2 keeps the raster mapped for good, and the run-time probe times
  memory that is already mapped. SDL has no rule against recording an upload
  from a mapped transfer buffer: the refusal is `SdlGpuCommandBuffer`'s own,
  and phase 2 needs a way past it for the raster.
- **An early run read +64% for mapped memory**, from timing heap first and
  mapped second. The probe now interleaves the three variants sample by
  sample. A runtime probe has to do the same, or it will learn the JIT's
  warm-up rather than the memory.
- **Phase 1's layout costs nothing on Vulkan** (8.58 against 8.81 ms, 5.83
  against 6.26), so padding there would be harmless. It stays packed anyway,
  because there's nothing to gain.
- **Direct3D 12 can only copy whole-row bands in place.** A region's pitch must
  be exactly its own row rounded up to 256 (phase 1). Inside a frame-sized
  buffer the pitch is the frame's stride, which is that only for a region the
  frame's width. So in place on Direct3D 12 means each region widened to whole
  rows, starting on a row whose offset is a multiple of 512. The bands copy
  more (10% damage becomes 40% of the frame's bytes), but the stage stays
  where in place's is: 0.20 ms at 4K and 10% on the 1660 Ti. Metal records
  padded rows one at a time, so its in-place stage at 4K is 2160 commands. The
  CI runs say what that costs.
- **Windows and macOS: not yet measured.** The benchmark lane runs the probe on
  `windows-2022` (Direct3D 12, on WARP if SDL takes it; that runner has never
  had a GPU lane) and on `macos-15` (Metal), and the tables go into the run's
  summary. The first run that reaches master answers D6 for both.

### Phase 1 — Direct3D 12 staging without the hidden copy (done 2026-11-22)

`StagingBuffer.stage` pads rows to 256 bytes and places regions at multiples of
512 on `direct3d12`, and `CopyPass` passes the padded row length (Direct3D 12
takes padded rows, as `uploadsPaddedRows()` says). Exit: a unit test of the
offsets and pitches; the Windows lane's benchmark shows the upload stage did not
grow; gallery goldens unchanged.

### Phase 2 — the in-place raster

An internal `MappedRaster` in `gpu.render`: an upload transfer buffer sized to
the frame (stride padded to 256), mapped once, wrapped as a `PixelBuffer`; the
copy submitted in a command buffer of its own with a fence (D4 (a)), and the
probe at device creation that picks the path per driver (D3, D6). Exit:
tests on lavapipe that the texture holds what was painted, region by region,
and that a paint waits for an unfinished copy (a fence that has not signalled
holds it); identical pixels to the staged path.

### Phase 3 — the composited window paints in place

`CompositedWindow` gains an optional frame of its own; `Sdl3Window.acquireFrame()`
lends it while composited on a driver D6 switched on. `SdlCompositedWindow.present`
skips the stage for it. A resize makes a new raster and damages everything, as
today. Exit: every gallery and GPU golden unchanged; the `hud`'s upload span
drops by D6's measure on the 1660 Ti; `:example:run` with the dummy driver still
starts.

### Phase 4 — `RasterTexture` for applications

D5's API, its guide section parked in `docs/snapshot/components-gpu.md`, and a
showcase card on the GPU screen: a board of tiles painted by Blend2D and drawn
by a `Canvas3dRenderer`, with the path it took shown. Exit: tests on both paths
(in place on lavapipe, staged with the in-place path off), the card's golden,
and Tessera's G-30 answer in `answers-tessera.md`.

## 5. Risks

- **SDL internals.** "Persistently mapped, same pointer without `cycle`" is how
  3.4.18 is written, not what its header promises. Phase 0's test pins it, so an
  SDL update that changes it fails a test rather than a screen.
- **Memory SDL picked.** If a driver has no cached host-visible type, SDL falls
  back to an uncached one and painting slows down. D6's probe catches that at
  run time.
- **Stalls under GPU load** (D4 (a)). Measured in phase 3 with the ray-traced
  room running; (b) is the answer if they show.
- **Memory.** A frame-sized transfer buffer per composited window, in pinned
  system memory, in place of the heap buffer and the staging buffer it replaces:
  about the same total.
- **Blend2D's workers** write the mapped memory too. Nothing new for Vulkan's
  coherent memory, but phase 2 tests the multi-threaded frame.
- **Platforms not run here.** Metal and Direct3D 12 change only in phase 1's
  staging; the in-place path is off there. What CI runs is what is claimed.

## 6. Documents kept in step

- `gaps/goldberry-gaps-plan.md` §8 and §10, and `gaps/answers-tessera.md` G-30,
  whenever a phase lands.
- `docs/snapshot/adr/` for D1–D6 as they are taken, with the README lines.
- `docs/snapshot/components-gpu.md` for the guide section (phase 4).
- `gpu-gaming-plan.md` §1's native-handles row, if SDL#10876 moves.

## 7. Status and log

| Phase | Status |
|-------|--------|
| 0 — measurements | done here (D6 on for Vulkan, the 1660 Ti and lavapipe); Direct3D 12 and Metal wait for the benchmark lane's first run |
| 1 — D3D12 staging | done 2026-11-22; the Windows lane's upload numbers wait for phase 0's benchmark |
| 2 — the in-place raster | not started |
| 3 — the composited window | not started |
| 4 — `RasterTexture` | not started |

- **2026-11-21.** Plan written from the tree at `5998561b` and SDL 3.4.18's
  sources. The finding that shapes it: through SDL, real zero-copy (the GPU
  sampling Blend2D's memory) is out of reach on every driver, and painting in
  place is worth it only on Vulkan, whose upload buffers are cached and
  persistently mapped. A hidden third copy on Direct3D 12 drivers without
  OPTIONS13 was found on the way. D1–D6 wait for the owner; no code.
- **2026-11-21, later.** The owner answered: D1 (a), no CPU copy, with no
  copy at all declined until SDL exposes native handles; D2 (a), the window's
  frame first and then the exported API; D4 (a), one raster with the copy
  submitted alone behind a fence; D5 `RasterTexture`; D6 the gate as written
  (the 4K upload stage at least halved, painting no more than 5% slower). D3,
  the per-driver table, was rejected; its replacement is asked. Phase 0 can
  start. The ADR is written once D3 is settled, so that it records all six.
- **2026-11-21, last.** D3 taken: D6 decides, per driver and at run time,
  with no driver ruled out ahead of time. Phase 0 therefore measures the
  in-place prototype on Metal and Direct3D 12 too (CI's macOS and Windows
  lanes), and the run-time probe is part of phase 2, not an option. All six
  decisions are recorded in ADR-0712 (parked). Next ADR 0713.
- **2026-11-22, phase 1** (taken before phase 0, at the owner's request). The
  staging moved to a package of its own, `gpu.render.staging`:
  `UploadAlignment` (rows and regions per driver: `DIRECT3D12` 256/512,
  `PACKED` elsewhere), `StagingLayout` and `StagedRegion` (where each region
  goes and its row pitch, arithmetic only), and `StagingBuffer` laying an
  upload out by its device's alignment. The composited window and
  `CopyPass.upload(texture, source, rowBytes, regions)` record each region
  with its pitch as `pixels_per_row`, through a new level-and-layer overload
  of the native copy pass's padded upload; a compressed format's rows stay
  packed. Reading SDL's source again showed the pitch must be *exactly* the
  packed row rounded up to 256 (ADR-0712 amended). A whole 1080p or 4K frame
  has such rows already, so only partial damage changes layout. Tests:
  `UploadAlignmentTest`, `StagingLayoutTest` (fixed cases and 200 random
  damage sets: aligned, fitting, no overlap) and `StagingBufferGpuTest`
  (Direct3D 12's layout on Vulkan, which takes padded rows: regions, a whole
  frame and mip level 1 read back identical to the packed path), green on
  lavapipe and the 1660 Ti; `:gpu`, `:natives` checks and `:example:gpuTest`'s
  goldens unchanged. Not shown yet: that the Windows lane's upload stage did not
  grow, since no benchmark measures it before phase 0. Next ADR 0731.
- **2026-11-22, phase 0** (after phase 1). `UploadPathProbe` measures painting
  in heap memory, in a kept-mapped upload buffer and in one mapped around every
  paint, interleaved. It measures four upload paths (staged packed, staged
  256/512, in place, in place as whole-row bands) at 1%, 10% and 100% of 1080p
  and 4K, and prints D6's verdict for the driver. `SdlGpuDeviceTest` pins the
  fact §5 asked for: an upload buffer mapped without `cycle` has one address,
  and keeps its bytes across a copy from it. That holds on the 1660 Ti and on
  lavapipe. The benchmark lane runs the probe on lavapipe and, in a new
  `upload-paths` job, on Windows and macOS. D6 is on for Vulkan. Direct3D 12
  can copy only whole-row bands in place, and the buffer must stay mapped
  (Results, above). Next ADR 0731. The runs, as printed:

  ```text
  GPU vulkan on NVIDIA GeForce GTX 1660 Ti (NVIDIA 610.57.4.0); padded rows recorded in one go: true; 8 processors

  Painting, heap against mapped upload memory (median / p95 ms)

  | Frame | Workers | Heap | Mapped, kept | Mapped each frame | Kept slower by |
  |---|---|---|---|---|---|
  | 1920x1080 | 0 | 6.432 / 9.002 | 6.463 / 8.451 | 6.582 / 7.729 | +0.5% |
  | 1920x1080 | auto (4) | 2.884 / 4.267 | 2.800 / 3.988 | 2.928 / 4.413 | -2.9% |
  | 3840x2160 | 0 | 13.447 / 14.492 | 13.368 / 14.471 | 13.475 / 14.829 | -0.6% |
  | 3840x2160 | auto (4) | 4.906 / 5.376 | 4.909 / 5.347 | 4.993 / 5.366 | +0.1% |
  | 512x512 | 0, one fill | 0.055 / 0.107 | 0.058 / 0.100 | 0.091 / 0.156 | +5.0% |

  Uploading a frame's damage (median / p95 ms)

  | Frame | Damage | Path | Bytes copied | Stage | Until copied |
  |---|---|---|---|---|---|
  | 1920x1080 | 1% | staged, packed | 0.1 MB | 0.093 / 0.152 | 0.144 / 0.375 |
  | 1920x1080 | 1% | staged, 256/512 | 0.1 MB | 0.073 / 0.119 | 0.125 / 0.378 |
  | 1920x1080 | 1% | in place | 0.1 MB | 0.049 / 0.081 | 0.107 / 0.542 |
  | 1920x1080 | 1% | in place, bands | 0.8 MB | 0.035 / 0.062 | 0.201 / 0.637 |
  | 1920x1080 | 10% | staged, packed | 0.8 MB | 0.260 / 0.364 | 0.483 / 1.327 |
  | 1920x1080 | 10% | staged, 256/512 | 0.8 MB | 0.219 / 0.283 | 0.445 / 1.319 |
  | 1920x1080 | 10% | in place | 0.8 MB | 0.042 / 0.074 | 0.222 / 0.693 |
  | 1920x1080 | 10% | in place, bands | 3.3 MB | 0.079 / 0.164 | 0.701 / 1.609 |
  | 1920x1080 | 100% | staged, packed | 8.3 MB | 2.462 / 3.308 | 4.093 / 5.748 |
  | 1920x1080 | 100% | staged, 256/512 | 8.3 MB | 2.410 / 3.191 | 4.084 / 5.560 |
  | 1920x1080 | 100% | in place | 8.3 MB | 0.159 / 0.349 | 1.669 / 2.814 |
  | 1920x1080 | 100% | in place, bands | 8.3 MB | 0.151 / 0.351 | 1.670 / 2.829 |
  | 3840x2160 | 1% | staged, packed | 0.3 MB | 0.125 / 0.190 | 0.247 / 0.623 |
  | 3840x2160 | 1% | staged, 256/512 | 0.3 MB | 0.075 / 0.104 | 0.164 / 0.307 |
  | 3840x2160 | 1% | in place | 0.3 MB | 0.021 / 0.035 | 0.103 / 0.265 |
  | 3840x2160 | 1% | in place, bands | 3.3 MB | 0.062 / 0.102 | 0.674 / 1.633 |
  | 3840x2160 | 10% | staged, packed | 3.3 MB | 1.113 / 1.357 | 1.817 / 2.538 |
  | 3840x2160 | 10% | staged, 256/512 | 3.3 MB | 1.081 / 1.367 | 1.794 / 2.468 |
  | 3840x2160 | 10% | in place | 3.3 MB | 0.058 / 0.120 | 0.662 / 1.615 |
  | 3840x2160 | 10% | in place, bands | 13.3 MB | 0.204 / 0.537 | 2.622 / 3.841 |
  | 3840x2160 | 100% | staged, packed | 33.2 MB | 8.814 / 11.247 | 15.464 / 18.206 |
  | 3840x2160 | 100% | staged, 256/512 | 33.2 MB | 8.575 / 10.958 | 15.063 / 17.179 |
  | 3840x2160 | 100% | in place | 33.2 MB | 0.171 / 0.806 | 6.797 / 7.864 |
  | 3840x2160 | 100% | in place, bands | 33.2 MB | 0.182 / 0.948 | 6.798 / 8.096 |

  D6 on vulkan: (1) the 4K whole-frame stage in place is 0.02 of staged, packed's (at most 0.50): passes; (2) painting at 4K into mapped memory is at worst +0.1% (at most +5%): passes. In place: on

  GPU vulkan on llvmpipe (LLVM 21.1.8, 256 bits) (llvmpipe 26.0.8); padded rows recorded in one go: true; 8 processors

  Painting, heap against mapped upload memory (median / p95 ms)

  | Frame | Workers | Heap | Mapped, kept | Mapped each frame | Kept slower by |
  |---|---|---|---|---|---|
  | 1920x1080 | 0 | 6.666 / 8.025 | 6.562 / 7.836 | 6.686 / 7.857 | -1.6% |
  | 1920x1080 | auto (4) | 2.647 / 3.430 | 2.717 / 4.041 | 2.673 / 3.853 | +2.6% |
  | 3840x2160 | 0 | 13.304 / 14.512 | 13.233 / 14.141 | 13.425 / 14.096 | -0.5% |
  | 3840x2160 | auto (4) | 4.775 / 5.454 | 4.815 / 5.257 | 4.881 / 5.324 | +0.8% |
  | 512x512 | 0, one fill | 0.055 / 0.093 | 0.056 / 0.087 | 0.087 / 0.126 | +2.4% |

  Uploading a frame's damage (median / p95 ms)

  | Frame | Damage | Path | Bytes copied | Stage | Until copied |
  |---|---|---|---|---|---|
  | 1920x1080 | 1% | staged, packed | 0.1 MB | 0.088 / 0.161 | 0.128 / 0.212 |
  | 1920x1080 | 1% | staged, 256/512 | 0.1 MB | 0.062 / 0.109 | 0.094 / 0.139 |
  | 1920x1080 | 1% | in place | 0.1 MB | 0.047 / 0.054 | 0.069 / 0.093 |
  | 1920x1080 | 1% | in place, bands | 0.8 MB | 0.039 / 0.062 | 0.096 / 0.177 |
  | 1920x1080 | 10% | staged, packed | 0.8 MB | 0.171 / 0.315 | 0.288 / 0.476 |
  | 1920x1080 | 10% | staged, 256/512 | 0.8 MB | 0.137 / 0.373 | 0.230 / 0.582 |
  | 1920x1080 | 10% | in place | 0.8 MB | 0.030 / 0.049 | 0.107 / 0.162 |
  | 1920x1080 | 10% | in place, bands | 3.3 MB | 0.038 / 0.069 | 0.283 / 0.500 |
  | 1920x1080 | 100% | staged, packed | 8.3 MB | 1.600 / 1.901 | 2.617 / 3.047 |
  | 1920x1080 | 100% | staged, 256/512 | 8.3 MB | 1.819 / 2.200 | 2.999 / 3.534 |
  | 1920x1080 | 100% | in place | 8.3 MB | 0.084 / 0.129 | 1.248 / 1.479 |
  | 1920x1080 | 100% | in place, bands | 8.3 MB | 0.071 / 0.109 | 1.059 / 1.242 |
  | 3840x2160 | 1% | staged, packed | 0.3 MB | 0.099 / 0.149 | 0.173 / 0.252 |
  | 3840x2160 | 1% | staged, 256/512 | 0.3 MB | 0.060 / 0.123 | 0.105 / 0.180 |
  | 3840x2160 | 1% | in place | 0.3 MB | 0.016 / 0.031 | 0.060 / 0.083 |
  | 3840x2160 | 1% | in place, bands | 3.3 MB | 0.022 / 0.035 | 0.236 / 0.309 |
  | 3840x2160 | 10% | staged, packed | 3.3 MB | 0.787 / 1.089 | 1.428 / 2.075 |
  | 3840x2160 | 10% | staged, 256/512 | 3.3 MB | 0.768 / 0.969 | 1.436 / 1.788 |
  | 3840x2160 | 10% | in place | 3.3 MB | 0.072 / 0.121 | 0.749 / 0.975 |
  | 3840x2160 | 10% | in place, bands | 13.3 MB | 0.107 / 0.168 | 1.717 / 2.098 |
  | 3840x2160 | 100% | staged, packed | 33.2 MB | 6.263 / 7.936 | 10.394 / 12.788 |
  | 3840x2160 | 100% | staged, 256/512 | 33.2 MB | 5.826 / 6.894 | 9.746 / 11.088 |
  | 3840x2160 | 100% | in place | 33.2 MB | 0.068 / 0.102 | 3.536 / 4.112 |
  | 3840x2160 | 100% | in place, bands | 33.2 MB | 0.060 / 0.103 | 3.527 / 5.122 |

  D6 on vulkan: (1) the 4K whole-frame stage in place is 0.01 of staged, packed's (at most 0.50): passes; (2) painting at 4K into mapped memory is at worst +0.8% (at most +5%): passes. In place: on
  ```
