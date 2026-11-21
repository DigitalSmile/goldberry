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
  for that one upload. `StagingBuffer.stage` packs each region's rows at its own
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

### D3. Per driver (rejected; the replacement is open)

| Driver | In place | Staged |
|--------|----------|--------|
| Vulkan | **Yes**, behind D6's gate | The fallback where the gate says no, or the memory turns out uncached (D6) |
| Metal | No: upload buffers are write-combined | Today's path; rows packed, as now |
| Direct3D 12 | No: the upload heap is write-combined | Rows padded to 256 bytes, regions at multiples of 512 (§2 item 4) |
| Software (`lavapipe`) | Yes: all memory is cached system memory | — |

**The owner rejected this table on 2026-11-21.** What takes its place is not
decided yet, and nothing that depends on it (phases 1 to 3, and where
`RasterTexture` paints in place) is built until it is. Phase 0's measurements
do not depend on it and are what it will be decided from.

Rejected in the proposal: painting on Metal into a *download* transfer buffer, which SDL makes
with the default cache. SDL does not check a copy pass's source usage, so it
would work, but it rests on an internal detail no SDL release promises.

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
whether the buffer is cached. A one-off probe at device creation (read a mapped
megabyte, compare with heap) can make the choice at run time if a driver turns
out to give uncached memory. **Recommendation: take this gate.** The owner may
set other numbers.

## 4. Phases

### Phase 0 — measurements

- A benchmark in `gpu/src/benchmark`, beside `GpuVideoProbe`, run by the
  benchmark lane: (i) Blend2D paints a fixed UI scene into heap memory and into
  a mapped SDL upload transfer buffer, single- and multi-threaded; (ii)
  `present`'s upload stage at 1080p and 4K with 1%, 10% and 100% of the frame
  damaged, on today's path; (iii) the same through an in-place prototype in the
  benchmark's own code.
- On this machine: the GTX 1660 Ti (Vulkan, x11) and lavapipe. On CI: Windows
  (D3D12, so the hidden copy shows up or not) and macOS (Metal).
- An `SdlGpuDeviceTest` case that pins the Vulkan facts §1 relies on: two maps
  without `cycle` return the same address.

Exit: the numbers in this document, and D6 answered per driver.

### Phase 1 — Direct3D 12 staging without the hidden copy

`StagingBuffer.stage` pads rows to 256 bytes and places regions at multiples of
512 on `direct3d12`, and `CopyPass` passes the padded row length (Direct3D 12
takes padded rows, as `uploadsPaddedRows()` says). Exit: a unit test of the
offsets and pitches; the Windows lane's benchmark shows the upload stage did not
grow; gallery goldens unchanged.

### Phase 2 — the in-place raster

An internal `MappedRaster` in `gpu.render`: an upload transfer buffer sized to
the frame (stride padded to 256), mapped once, wrapped as a `PixelBuffer`; the
copy submitted in a command buffer of its own with a fence (D4 (a)). Exit:
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
| 0 — measurements | not started; unblocked |
| 1 — D3D12 staging | waits for D3's replacement |
| 2 — the in-place raster | waits for D3's replacement |
| 3 — the composited window | waits for D3's replacement |
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
