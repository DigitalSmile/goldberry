# M4 — GPU: implementation plan

Written 2026-09-24, before any code. The milestone is two lines in
`docs/ARCHITECTURE.md` §16 — "`canvas3d`, GPU composition path" — and §12
sketches it. This document is how it gets built: what is decided, what still has
to be measured before it can be, the phases with their exit criteria, and what
unblocks `docs/media-plan.md` phase 4 (GPU present) and the half of phase 5's
exit criterion that waits on it ("4K60 VP9 without dropped frames on GPU
present").

It follows `media-plan.md`'s shape: tables with a status column, corrections to
the design recorded as they are found, and a log at the end.

## 1. Where things stand

Everything below was read from the tree on 2026-09-24.

| Area | State |
|------|-------|
| `:gpu` | One file, `module-info.java`. It is published as `goldberry-gpu` so the coordinate is reserved (ADR-0334), and it requires `:core` and nothing else |
| SPI | `BackendWindow` has `acquireFrame`, `retainsFrameContents`, `present(PixelBuffer, damage)`, `requestFrame`, `nativeHandle`, `refreshRate` and `lateFrames`. There is **no `gpuSurface()`**: `Backend`'s doc says it "needs a consumer before its shape can be decided" (ADR-0019). `ARCHITECTURE.md` §12 still says it is in the SPI "from day 1", and ADR-0002 says the same |
| Present | Blend2D paints into SDL's window-surface memory (`acquireFrame`) or into an owned `PixelBuffer`. `SdlVideo.present` then calls `SDL_UpdateWindowSurfaceRects` with the damage. Goldberry owns no GPU context. On Wayland, SDL itself hides an `SDL_Renderer` and a streaming texture under the window surface (ADR-0046) |
| Pacing | `SDL_HINT_RENDER_VSYNC=1` reaches SDL's hidden renderer, and `FramePacer` holds `FrameDue` to the display's rate (ADR-0047). `FrameStats` and the late-frame budget (ADR-0271, ADR-0342, ADR-0452) measure the CPU side |
| SDL | 3.4.16, static in `libgoldberry`. It is built with SDL's defaults, so the GPU subsystem (Vulkan, Metal, D3D12 drivers) and the renderer are compiled. **None of the 97 `SDL_GPU*` functions is in `exports/goldberry.symbols`**, and neither is any `SDL_*Renderer*` or `SDL_*Texture*` function |
| Bindings | Hand-written holders in `…calls` packages (ADR-0010, ADR-0173). Structs are checked against the shim's layout table (`LayoutVerifier`). Native-image registrations are generated from the holders (ADR-0339), and upcall owners are listed in `UPCALL_OWNERS` |
| Module seal | `:natives` exports to `:core` alone (ADR-0280). The one exception is `natives.sdl.audio`, exported to `:media` (ADR-0461) |
| Video | The frame queue holds premultiplied BGRA made by swscale on the decode thread (ADR-0463). Hardware frames are always copied back to NV12/P010 (ADR-0470). The frame contract has NV12, I420, P010 and I010, each with `ColorMatrix` (601/709/2020) and `fullRange` |
| Tests | Goldens render through `Offscreen` with no backend, at one tolerance on every platform (ADR-0050). **No CI runner has a GPU** (ADR-0342): they are GitHub's virtual machines, and the Linux showcase leg runs under Xvfb |

What SDL 3.4.16 offers, read from its headers in `natives/.deps/…/sdl3-src`:

- **`SDL_GPU`** covers devices, textures, samplers, buffers, cycling transfer buffers, shaders (SPIR-V, MSL/metallib, DXIL), graphics and compute pipelines, copy, render and compute passes, fences, swapchains (VSYNC, MAILBOX and IMMEDIATE; SDR, SDR-linear, HDR-extended-linear and HDR10), `SDL_SetGPUAllowedFramesInFlight`, `SDL_DownloadFromGPUTexture` for readback, and debug labels.
- **`SDL_ClaimWindowForGPUDevice` needs no window flag.** The Vulkan driver calls the video device's `Vulkan_CreateSurface` directly, and the Metal driver makes its own `SDL_Metal_CreateView`, so a window created today can be claimed later. The call must be made on the window's thread.
- **The GPU API has no import of external textures** (IOSurface, D3D11 shared handles, dma-buf), and it hands out no native handle. Zero-copy from a hardware decoder is therefore not possible through an unmodified `SDL_GPU` 3.4. SDL's older renderers already take a `CVPixelBuffer` or a D3D11/D3D12/Vulkan texture; the GPU API does not. Upstream tracks this as [SDL #14077](https://github.com/libsdl-org/SDL/issues/14077), with [PR #14157](https://github.com/libsdl-org/SDL/pull/14157) open for Metal and D3D12, and no date. What that costs and how it gets fixed is D9.
- **`SDL_CreateGPURenderer(device, window)` and `SDL_GPURenderState` (3.4)** are an `SDL_Renderer` on an `SDL_GPU` device that takes custom fragment shaders. Its textures take NV12 and P010 with a colorspace. It is a real alternative for the composition pass (§3, D1).

## 2. What M4 delivers

1. **A GPU composition path for a window.** The UI is still rasterized by Blend2D on the CPU (ADR-0002 stands). In this mode it is uploaded, damage only, as a texture and composited in one `SDL_GPU` render pass with the GPU layers, then presented on the window's swapchain.
2. **`GpuSurface` in the SPI**, designed against its two consumers: `canvas3d` and `video-view`.
3. **`:gpu`'s public API**: a small, safe Java layer over `SDL_GPU` (device, textures, buffers, shaders, pipelines, one command scope per frame, readback), and the **`canvas3d`** widget built on it.
4. **Readback**: a GPU layer rendered to a texture and read back into a `BLImage`. This is how headless, `Offscreen`, the goldens and any window that cannot be claimed show GPU content.
5. **Media phase 4 (GPU present)**: planes uploaded as textures, YUV→RGB in a shader, with the matrix and range from the frame and 10-bit handled. The CPU path stays as the fallback ladder's last rung.
6. **A CI lane that runs the GPU tests on a real driver**: Mesa's lavapipe (software Vulkan) on the Linux runners.

**Not in M4**, and recorded so it is not rediscovered:

- zero-copy decoder interop on Windows and Linux (D9; macOS is phase 6b);
- HDR swapchains and tone mapping;
- compute passes in the public API (the bindings will have them);
- translucent GPU layers over UI (§3, D4);
- frost that blurs GPU content (§3, D4);
- a GPU rasterizer for the UI;
- runtime shader cross-compilation for applications' shaders;
- multi-window sharing of GPU resources beyond the one device.

## 3. Decisions

The ones marked **to measure** are made in phase 0, from the spikes listed
there. Each one becomes an ADR when it is taken.

### D1. `SDL_GPU` directly, not SDL's GPU-backed renderer: decided (ADR-0477)

The composition pass is trivial: one textured quad per layer, scissored, and the
UI quad blended over them. It is small enough to own. Owning it gives:

- frames in flight chosen by us;
- cycling transfer buffers we map once and fill from Java;
- readback through the same device;
- one abstraction that `canvas3d`, video and the goldens share;
- shaders whose output we test.

`SDL_CreateGPURenderer` would give NV12/P010 conversion for free, but not I010,
and its colour pipeline is SDL's to change between releases. It would also add a
second abstraction beside the device `canvas3d` needs anyway.

**Phase 0 spike:** composite a 4K UI texture plus one 4K NV12 layer both ways on
macOS (Metal) and under lavapipe. Compare the cost per frame, the code needed,
and whether the renderer's colour output matches swscale. Choose the renderer
only if it is clearly smaller at equal fidelity.

**Decided (ADR-0477):** the direct path converts all four layouts exactly
against its reference and composites a 4K picture a frame at under 1.4 ms of CPU
at 120 Hz (§4.1). The renderer has no I010, so it is not of equal fidelity and
its cost was not measured.

### D2. One device per process, created on first need (amended by ADR-0480)

`GpuDevice` is owned by the backend and created by the first GPU layer to attach,
never at startup. ADR-0002's promise stands: an application that never shows a
GPU layer never loads a driver.

- Creation goes through `SDL_CreateGPUDeviceWithProperties`, with the shader formats we ship (SPIR-V, MSL, DXIL), `PREFERLOWPOWER` by default, and `-Dgoldberry.gpu.driver=vulkan|metal|direct3d12` to override the driver.
- A device that fails to create is remembered for the process, as `Hardware` remembers a failed codec (ADR-0470), and every consumer drops to its fallback rung.
- `-Dgoldberry.gpu=off|auto` is the switch; `auto` is the default.

**Amended (ADR-0480):** windows are composited by default, so "first need" is
the first frame of the first window. With `goldberry-gpu` on the module path an
application loads a driver then, in about 20 ms, measured here. Without it,
nothing changes.

### D3. Composition is per window, entered when a GPU layer attaches (decided on macOS, ADR-0479)

A window is in one of three modes:

| Mode | When | Present |
|------|------|---------|
| CPU | no GPU layer attached (every window today) | unchanged: window surface and `SDL_UpdateWindowSurfaceRects` |
| Composited | a GPU layer is attached and the window was claimed | the owned `PixelBuffer` is uploaded as the UI texture (damage only), and GPU layers plus the UI quad are drawn on the swapchain |
| Readback | a GPU layer is attached, but the window cannot be claimed or is headless/offscreen | each GPU layer renders to a texture, is downloaded, and is drawn by Blend2D as an image |

Entering the composited mode means `SDL_DestroyWindowSurface` and then
`SDL_ClaimWindowForGPUDevice`. Leaving it means the release and a new window
surface. The window stays composited for a hold time after its last GPU layer
detaches (hysteresis), so a list scrolling a video in and out of view does not
flip modes on every frame.

**Phase 0 measures:**

- what the switch costs on each OS;
- whether a claimed window can go back to a window surface at all;
- what the switch does to a window with a `web-view` (a Metal view inside the content view, ADR-0458) or in fullscreen (ADR-0473).

If a switch cannot be made reliable, the fallback decision is that a window
chooses its mode when it is created (`Window.Builder.gpu(true)`) and GPU layers
in a CPU window use readback.

**Decided on macOS (ADR-0479):** the switch is made at a window's first frame
after it is wanted, in `acquireFrame`. `goldberry.gpu.composite=always` wants it
from the first frame, and **is the default** (ADR-0480). Popups, which are
transparent windows SDL will not claim, stay on the CPU. A window with an embedded page stays on the CPU. A claim
or a present that fails sends the window back to the CPU for good. Leaving on
a timer after the last layer detaches waits for phase 4, which is when layers
attach and detach.

### D4. Z-order by hole-punching

A GPU layer's element paints, into the CPU frame, a transparent rectangle in its
clipped box (`SRC_COPY` with transparent black) and records the layer and its
rect in paint order. The composite pass then:

1. clears the swapchain;
2. draws each GPU layer in paint order, scissored to its clip;
3. draws the UI texture over everything with premultiplied blending.

What is painted *after* the GPU element (controls over a video, a popup over a
3D view) is above it. What was painted *before* it and under its box has been
punched out, which is what the opaque layer would hide anyway. Z-order comes
from the paint order that already exists, and no second UI texture is needed.

The rules that follow, each to be written in the ADR:

- **GPU layers are opaque** in v1. A translucent layer would show the transparent hole, not the UI under it.
- **Clips are rectangles.** A layer inside a rounded clip gets the clip's bounding rect as its scissor. Rounded corners are drawn by UI painted over it, or wait for a mask pass.
- **Frost does not blur GPU content.** The Java blur (`ARCHITECTURE.md` §5) reads the CPU frame, where the layer is a hole. §12's "3D under frost — all works" is corrected: it works in readback mode, and in composited mode the frost blurs the UI only. A GPU blur pass is post-M4.
- A layer's rect is in physical pixels. It is rounded with `DisplayScale`'s one rule, so the hole and the quad cover the same pixels.

### D5. The SPI: `BackendWindow.gpuSurface()`

The shape follows its consumers, and all of it is confined to the UI thread
(ADR-0019):

```java
// :core, …render.window — no SDL type in it
Optional<GpuSurface> gpuSurface();          // empty: no device, or headless without one

public sealed interface GpuSurface permits … {
    GpuDevice device();                      // :core's opaque handle, unwrapped by :gpu
    CompositionMode mode();                  // CPU, COMPOSITED, READBACK
    void attach(GpuLayer layer);             // enters COMPOSITED (D3) if it can
    void detach(GpuLayer layer);
}

public interface GpuLayer {                  // implemented by canvas3d and video-view
    void render(GpuFrame frame, PhysicalRect target, PhysicalRect scissor);
}
```

`HeadlessWindow` returns a `GpuSurface` in readback mode when a device can be
created, and empty otherwise. That lets a test of `canvas3d` run on lavapipe with
no window.

**Corrected in phase 3 (ADR-0479):** `:gpu` requires `:core`, so a
`GpuLayer.render(GpuFrame …)` cannot be declared in `:core`. `GpuFrame` is a
`:gpu` type (ADR-0478). `:core` declares a seam of its own instead,
`render.composite` (`Compositor`, `CompositedWindow`), exported to `:gpu` alone
and found by `ServiceLoader`. `GpuSurface`, `GpuLayer` and `CompositionMode`
arrive with phase 4, their consumer: the layer interface will be `:gpu`'s, and
`:core` will hold opaque slots in paint order.

**Corrected in phase 1 (ADR-0475):** no device can be created under SDL's
`dummy` video driver, which the headless tests use. A headless `GpuSurface`
therefore needs `offscreen` (Vulkan, lavapipe) or, on macOS, `cocoa` on the
first thread. GPU goldens run in a `gpuTest`-style task; the CPU goldens are
unchanged.

### D6. Where the bindings live, and who may call them

- **Bindings.** `SDL_GPU` goes in a new `natives.sdl.gpu` package with its `…calls` holders. The layout table gains the create-info structs. It is exported, qualified, to `:core` (window claim, present) and `:gpu` (everything else). This is an amendment to ADR-0280 of the kind ADR-0461 made for `:media`.
- **`:media`.** It does not bind `SDL_GPU`. It uses `:gpu`'s public API through `requires static io.github.digitalsmile.goldberry.gpu`, and without `:gpu` on the module path, video presents on the CPU exactly as today.
- **Pointers.** No raw `MemorySegment` leaves `:natives` (ADR-0019, `ExportedSurfaceTest`). Handles cross as small final classes wrapping the address, created and read only inside `:natives`. Mapped transfer memory crosses as a `ByteBuffer`, the pattern `acquireFrame` already uses.

### D7. Shaders are HLSL, compiled offline, and the output is committed

- **Build.** Sources in `gpu/src/main/shaders/*.hlsl`. `:gpu:compileShaders` runs DXC (to SPIR-V and DXIL) and SPIRV-Cross (SPIR-V to MSL), the two engines SDL_shadercross wraps, called directly; both come with the Vulkan SDK, and the manifest records their versions. Like `:media:ffmpegBuild`, it is a task run on purpose, not part of `build`. *As built (ADR-0476): shadercross itself was not needed, and DXC 1.9 signs DXIL off Windows.*
- **Committed output.** The blobs are committed under `gpu/src/main/resources/…/shaders/`, with a manifest of each source's SHA-256. A unit test fails when a source changes and its blobs do not, so a stale shader cannot ship. A resource glob declares them to native-image (`DeclaredResourcesTest` enforces it).
- **Why offline.** Shadercross needs DXC and SPIRV-Cross, which is too much to require of every build and every CI leg. The same rule as the layout fixtures: generated by a tool, committed, and checked.
- **Applications' shaders.** `canvas3d`'s users bring their own, as a `ShaderCode` record with bytes per format. `:gpu` picks the device's format and fails with a message naming the missing one.

### D8. Video's queue holds YUV when a GPU layer shows it (to measure)

In the composited or readback mode, `VideoWorker` copies each kept picture's
planes into the slot instead of converting them. A copy is cheaper than swscale,
so the decode thread gets lighter. At present, the UI thread writes the planes
into a cycling transfer buffer and uploads them in the frame's copy pass.

The queue's format is chosen when the view attaches or detaches. The change is a
flush plus an accurate reseek, the mechanism a video track switch uses
(ADR-0469), so no picture in the queue is ever the wrong format. In CPU mode
nothing changes.

**Phase 6 measures** 4K60 P010: about 24 MB per picture, about 1.4 GB/s through
the memcpy and the upload. If the UI thread cannot carry it, slots become mapped
transfer buffers written by the decode thread, which `SDL_GPU` permits for a
mapped buffer as long as the upload is recorded on the UI thread.

### D9. Copy-back first, and zero-copy on macOS through a patch of our own

**What having no import costs.** Every hardware-decoded picture goes GPU → CPU
(copy-back, ADR-0470) → GPU (the upload in D8). For 4K60 P010 that is about
24 MB a picture and 1.4 GB/s each way. The cost depends on the machine:

- **Apple Silicon, unified memory:** copy-back is a memory copy. Phase 5
  measured VideoToolbox at 4K60, copy-back included, at about 1.5 ms of CPU a
  picture (0.15 cores). This is expected to meet phase 6's exit, but phase 6
  measures it.
- **Discrete GPUs:** the picture crosses the bus twice. The bandwidth exists,
  but CPU time, latency and power are spent on it.
- **VAAPI:** some drivers read decoded surfaces back slowly. This is a risk to
  4K60 on Linux (§5), and it can only be measured on a Linux host with a GPU.
- **Where it fails first:** 8K, several streams at once, and battery life.
- **Beyond video:** textures made outside our device (CEF, CUDA, another
  engine) cannot enter the composite either. `canvas3d` content from elsewhere
  goes through readback, and `web-view` stays a native view (ADR-0458).
- **What it does not change:** correctness, 10-bit precision, and every phase of
  this plan.

**Decision.**

1. M4 is built with copy-back, and phase 6 measures it.
2. Phase 6 gives the video layer an input that is either CPU planes or a native
   surface (`PlaneSource`), so zero-copy fits in later without a redesign.
3. **Phase 6b** carries a Metal-only patch to the pinned SDL. It adds a texture
   property taking an `IOSurfaceRef` and a plane, and creates the `MTLTexture` on
   SDL's own device with `newTextureWithDescriptor:iosurface:plane:`, as a
   container that cannot be cycled.
   - It takes an `IOSurface` rather than PR #14157's `CVPixelBuffer`, whose
     Metal path a reviewer found may read GPU surfaces back through the CPU.
   - Its property name follows the PR's, so moving to upstream is a rename, and
     the patch is offered back.
   - The superbuild applies it with `PATCH_COMMAND` and checks that it applied.
4. Windows (D3D11/D3D12 shared handles) and Linux (dma-buf into Vulkan) stay on
   copy-back until upstream lands or a host exists to build and measure them on.

**Why not bypass SDL on macOS.** `AVSampleBufferDisplayLayer` would be
zero-copy with no patch, but it is a native layer, as `web-view` is (ADR-0458).
The UI cannot be composited over it, so it breaks D4.

## 4. Phases

Every phase ends with `check` green on this Mac, and with the lavapipe lane
green once it exists (phase 2 onward). Exit criteria are pass/fail. Numbers are
measured, not estimated.

### Phase 0 — spikes and decisions

Throwaway code on a branch, and the ADRs that come out of it.

| Item | Question | Status |
|------|----------|--------|
| Device on each OS | Does `SDL_CreateGPUDevice` succeed on macOS (Metal), under lavapipe on `ubuntu-24.04` and `-arm`, on `macos-14` runners (Metal in a VM?) and on `windows-2022` (D3D12, WARP, or nothing)? Which runners can host the GPU lane? | **macOS (Metal): yes**, on this M1 Pro, under `cocoa` on the first thread (ADR-0475). **Found:** a device needs a video driver with a Metal view or a Vulkan surface, so there is none under `dummy`; `offscreen` has headless Vulkan, which is what lavapipe runs under. The runners are open |
| Claim and release | Window surface → claim → release → window surface, on macOS, Wayland, X11 (Xvfb) and Windows. What it costs, and whether anything leaks or flickers. Behaviour with `web-view` and in fullscreen (D3) | **macOS: reliable and cheap.** 10 of 10 cycles on a 2560×1600 window: switch in (destroy the surface, claim) 1.75 ms median, 2.05 ms p95; switch out (release, surface, first present) 6.0 ms median, 6.6 ms p95; the surface came back at full size every time (`:natives:gpuPresentProbe`, §4.1). SDL counts a second claim by the same device, so `claimWindow` refuses one. Wayland, X11, Windows, `web-view` and fullscreen open |
| Composite cost | 4K UI texture, damage-only upload, plus one 4K NV12 layer: CPU time per frame, and the wait in `WaitAndAcquireGPUSwapchainTexture` against today's present (0.127 ms median under `dummy`, ADR-0409) | **Measured on Metal** (§4.1). Video: a 4K NV12 layer under the UI costs 0.94 ms of CPU a frame, 4K P010 1.37 ms, at the display's 120 Hz. UI: a whole 2560×1600 frame costs 1.1 ms of CPU (copy 0.84, record 0.13, blit and submit 0.14), against 2.65 ms for today's window-surface present of the same frame; a caret-sized damage upload 0.35 ms; a 4K BGRA frame 1.56 ms to copy |
| D1 | `SDL_GPU` pass against `SDL_CreateGPURenderer` with `SDL_GPURenderState`: code, cost, colour fidelity | **decided: `SDL_GPU` directly** (ADR-0477). The renderer has no I010; the direct path is exact on all four layouts and cheap (§4.1) |
| Size | `libgoldberry` growth when ~60 `SDL_GPU` symbols are exported and the dead-stripping no longer removes the drivers, per target | **macos-aarch64: 2 KB** for the first 29 symbols (6,100,832 → 6,102,848 bytes). The drivers were already linked, through SDL's renderer (ADR-0475). Linux and Windows open |
| Pacing | Swapchain VSYNC against `FramePacer`: does the backstop step aside in composited mode, and does `refreshRate`'s answer still hold? | **macOS, observed:** the acquire blocks to the display: composited frames came 8.39 ms apart (median) on a 120 Hz display, with p95 at 16.8 ms, where the window surface is not paced at all (2.8 ms apart without `FramePacer`). So in composited mode the swapchain is the pacer. What `FramePacer` does then is phase 3's |
| Shadercross | Builds and runs on macOS; output for the four shaders of phase 3 and phase 6 | open |
| ADRs | D1–D8 recorded, and ADR-0002's and ADR-0019's day-1 claims corrected with a link | open |

**Exit:** each "to measure" decision is taken on numbers from this table, and
the GPU CI lane has a runner that creates a device.

### 4.1 Phase 0 measurements, macOS

Taken on 2026-09-24 by `:natives:gpuPresentProbe`: an M1 Pro, macOS, a
1280×800 window at 2× (2560×1600 pixels), a 120 Hz ProMotion display, `cocoa`
and Metal, 240 frames per row. Blend2D's painting is not timed; moving the frame
is.

| Measurement | median | p95 |
|---|---|---|
| A. window surface: `SDL_UpdateWindowSurfaceRects`, whole window | 2.649 ms | 4.931 ms |
| A. window surface: frame interval, unpaced | 2.795 ms | 5.385 ms |
| B. switch in: destroy the window surface, claim | 1.751 ms | 2.045 ms |
| B. switch out: release, window surface, first present | 6.015 ms | 6.641 ms |
| C. composited, whole window: copy into the transfer buffer | 0.844 ms | 1.566 ms |
| C. composited, whole window: record the upload | 0.127 ms | 0.173 ms |
| C. composited, whole window: wait for the swapchain texture | 7.307 ms | 15.449 ms |
| C. composited, whole window: blit and submit | 0.139 ms | 0.243 ms |
| C. composited, whole window: frame interval | 8.392 ms | 16.804 ms |
| D. composited, 200×40 damage: copy | 0.107 ms | 0.182 ms |
| D. composited, 200×40 damage: record | 0.098 ms | 0.176 ms |
| D. composited, 200×40 damage: blit and submit | 0.137 ms | 0.252 ms |
| E. 3840×2160 BGRA: copy into the transfer buffer (33 MB) | 1.562 ms | 2.238 ms |
| E. 3840×2160 BGRA: copy, upload, scale, present | 8.340 ms | 16.741 ms |

What they say:

- **D3 holds on macOS.** A switch costs a couple of milliseconds in and six out,
  and never failed, so entering the composited mode automatically is viable. The
  hold time before leaving it is still wanted: six milliseconds is a visible
  hitch at 120 Hz if a list scrolls a video in and out of view.
- **Compositing is not a CPU cost on this machine; it is a saving.** Moving a
  whole frame to the GPU and presenting it costs less than half of what
  `SDL_UpdateWindowSurfaceRects` costs for the same frame, and damage-only
  uploads cost a tenth of it. The difference from ADR-0409's 0.127 ms is the
  driver: that was `dummy`, which presents nothing.
- **The wait is the display, not the work.** The acquire's median is the rest of
  a 120 Hz frame. Its p95 at one 60 Hz frame is ProMotion dropping the rate
  while the probe drew little, which phase 3 checks with the `hud` before any
  budget is set on it.
- **A 4K video layer is cheap** (`:gpu:gpuVideoProbe`, ADR-0477): a new
  picture every frame, converted and composited under the UI:

  | Layer | copy | record | convert, composite, submit | interval median / p95 |
  |---|---|---|---|---|
  | 4K NV12, 12 MB | 0.599 ms | 0.148 ms | 0.191 ms | 8.317 / 8.933 ms |
  | 4K P010, 24 MB | 1.075 ms | 0.142 ms | 0.154 ms | 8.305 / 8.951 ms |

- **D8's arithmetic is comfortable here.** 33 MB copies in 1.6 ms, so a 4K
  P010 picture's 24 MB is about 1.2 ms on the UI thread at 60 pictures a
  second. The decode-thread transfer buffers are not needed on this Mac.

### Phase 1 — natives and bindings

| Item | Status |
|------|--------|
| Exports: the v1 subset of `SDL_GPU` (below) in `goldberry.symbols`, the ABI version bumped | in part (ADR-0475): 25 `SDL_GPU` functions and 4 property setters (device, textures, transfer buffers, command buffers, clear, copy pass, fences), ABI 16; then the window's claim, release, swapchain parameters, present modes, frames in flight, swapchain format and acquire, and `SDL_BlitGPUTexture`, with `SDL_GPUBlitRegion`/`SDL_GPUBlitInfo` verified; then shaders, samplers, pipelines and render-pass state (ADR-0476); then buffers, their uploads and downloads, vertex and index binding, indexed draws and debug groups (ADR-0478): 56 in all. Compute, resource names and `WaitForGPUIdle` arrive with the phases that call them, as `ExportListTest` requires |
| Holders, one per function, `invokeExact` from a `static final` handle (ADR-0161) | in part: `SdlGpuDeviceCalls`, `SdlGpuResourceCalls`, `SdlGpuCommandCalls` and `SdlPropertiesCalls` in `natives.sdl.calls`, the package already initialised at image build time, rather than a new `…gpu.calls` |
| Layouts in the shim's table and in Java, checked by `LayoutVerifier`: `SDL_GPUTextureCreateInfo`, `…SamplerCreateInfo`, `…ShaderCreateInfo`, `…BufferCreateInfo`, `…TransferBufferCreateInfo`, `…GraphicsPipelineCreateInfo` with its nested vertex-input, rasterizer, multisample, depth-stencil and target-info structs and `…ColorTargetDescription`/`…ColorTargetBlendState`, `…VertexBufferDescription`, `…VertexAttribute`, `…TextureTransferInfo`, `…TextureRegion`, `…BufferRegion`, `…TransferBufferLocation`, `…ColorTargetInfo`, `…DepthStencilTargetInfo`, `…Viewport`, `…BufferBinding`, `…TextureSamplerBinding`, `…BlitInfo`, `SDL_FColor` | done on macos-aarch64: every struct listed, and each enumerator the bindings hard-code, verified against the compiled library (ADR-0475, ADR-0476, ADR-0478). The other three targets wait for CI |
| `natives.sdl.gpu` exported to `:core` and `:gpu` only. `ExportedSurfaceTest` extended so no segment escapes | done (ADR-0475): wrappers `SdlGpuDevice`, `SdlGpuTexture`, `SdlGpuTransferBuffer`, `SdlGpuCommandBuffer`/`CopyPass`, `SdlGpuFence`, with SDL's rules checked in Java and mapped memory scoped to an arena. `ExportedSurfaceTest` holds a package to a set of readers |
| Native-image registrations: generated by `:natives:foreignMetadata` with no change (the holders are in a `…calls` package). No upcalls | open |
| Tests: device create/destroy and a clear-and-download round trip, skipped without a device and required on the GPU lane (`-Pgoldberry.gpu.required=true`, the `media.required` rule, ADR-0016) | done on Metal: 16 tests tagged `gpu`, run by `:natives:gpuTest` on the JVM's first thread (cocoa needs it, and a `Test` task cannot give it), part of `check`; 8 value tests in the ordinary task |

The v1 subset, about 60 functions:

- **Device:** `CreateGPUDeviceWithProperties`, `DestroyGPUDevice`, `GetNumGPUDrivers`, `GetGPUDriver`, `GetGPUDeviceDriver`, `GetGPUShaderFormats`, `GPUSupportsProperties`.
- **Window:** `ClaimWindowForGPUDevice`, `ReleaseWindowFromGPUDevice`, `SetGPUSwapchainParameters`, `WindowSupportsGPUPresentMode`, `WindowSupportsGPUSwapchainComposition`, `SetGPUAllowedFramesInFlight`, `GetGPUSwapchainTextureFormat`, `WaitAndAcquireGPUSwapchainTexture`, `AcquireGPUSwapchainTexture`, `WaitForGPUSwapchain`.
- **Resources:** create and release for textures, samplers, shaders, graphics pipelines, buffers and transfer buffers; `SetGPUTextureName`.
- **Transfer:** `MapGPUTransferBuffer`, `UnmapGPUTransferBuffer`, `BeginGPUCopyPass`, `UploadToGPUTexture`, `UploadToGPUBuffer`, `DownloadFromGPUTexture`, `EndGPUCopyPass`.
- **Commands:** `AcquireGPUCommandBuffer`, `SubmitGPUCommandBuffer`, `SubmitGPUCommandBufferAndAcquireFence`, `CancelGPUCommandBuffer`, `WaitForGPUFences`, `QueryGPUFence`, `ReleaseGPUFence`, `WaitForGPUIdle`, `PushGPUVertexUniformData`, `PushGPUFragmentUniformData`.
- **Render pass:** `BeginGPURenderPass`, `EndGPURenderPass`, `BindGPUGraphicsPipeline`, `SetGPUViewport`, `SetGPUScissor`, `BindGPUVertexBuffers`, `BindGPUIndexBuffer`, `BindGPUFragmentSamplers`, `DrawGPUPrimitives`, `DrawGPUIndexedPrimitives`, `BlitGPUTexture`.
- **Formats:** `GPUTextureSupportsFormat`, `GPUTextureFormatTexelBlockSize`, `CalculateGPUTextureFormatSize`.
- **Debug:** `PushGPUDebugGroup`, `PopGPUDebugGroup`, `InsertGPUDebugLabel`.

Compute is bound in phase 7 if `canvas3d`'s API grows it.

**Exit:** a clear to a known colour, downloaded, reads back byte for byte on
Metal here and on lavapipe in CI. Layouts are verified on all four targets.

**Status:** met on Metal (macos-aarch64), with Metal's API validation on.
Lavapipe and the other three targets' layouts wait for the GPU lane and CI.

### Phase 2 — `:gpu`'s API and the GPU CI lane

A safe layer an application can use without knowing `SDL_GPU`'s structs.

| Item | Status |
|------|--------|
| `GpuDevice` (from the backend, D2), `GpuTexture`, `GpuBuffer`, `GpuSampler`, `Shader`, `GraphicsPipeline`: `AutoCloseable`, release deferred by SDL until the GPU is done with them, and a use after close fails in Java, not in the driver | done (ADR-0478): in the exported `io.github.digitalsmile.goldberry.gpu`, over the `:natives` wrappers, which gained buffers, vertex input, depth targets, culling, indexed and instanced draws, address modes, byte uniforms and balanced debug groups. Confined to the device's thread (`WrongThreadException`); another device's or a closed resource refused in Java. `GpuDevice.wrap` is package-private until phase 3 hands the device out |
| Create-infos as records with builders for the long ones (`PipelineSpec`, `TextureSpec`), and enums over SDL's (`TextureFormat`, `PresentMode`, `BlendMode`, …) with exhaustive `switch` mapping | done (ADR-0478): `TextureSpec`, `SamplerSpec`, `ShaderCode`, `PipelineSpec` with its builder, `VertexBufferLayout`, `VertexAttribute`, `DepthTest`, `DepthTarget`, `Load`; fifteen enums, each held one to one with SDL's by `GpuEnumsTest`. `PresentMode` waits for phase 3, its consumer |
| `GpuFrame`: the per-frame command scope. It holds one command buffer, `copyPass(…)` and `renderPass(…)` as scoped lambdas so a pass cannot be left open, and a debug group per layer | done (ADR-0478): passes do not nest and throw when kept past their body; `debugGroup(name, body)` and `debugLabel`; closing an unsubmitted frame discards it |
| `Upload`: cycling transfer buffers, sized to the largest upload seen and grown by doubling, with damage rects packed row by row | done (ADR-0478): one per device, from 64 KiB, each map cycled; `CopyPass.upload` of regions from a strided image or a `PixelBuffer` keeps the rest of the texture (uncycled), and a whole upload is cycled |
| `Readback`: download into a transfer buffer, a fence, then a `PixelBuffer`. The only blocking call in the API | done (ADR-0478): `GpuFrame.readback(texture, region)`; one fence per frame shared by its readbacks; `await()` gives heap bytes, `awaitPixels()` premultiplied BGRA |
| `ShaderCode`: bytes per format and the format chosen for the device (D7); `:gpu:compileShaders` and the freshness test | done (ADR-0476, ADR-0478): `SdlGpuShaderCode`; `BuiltInShader` (`quad.vert`, `texture.frag`, `solid.frag`), `ShaderLibrary` and `Quad` in `…gpu.render`; the bytecode committed and declared to native-image; `ShaderManifestTest`. The public `ShaderCode` carries a format per family and `load`s `name.spv`/`.dxil`/`.msl` through the caller's own resource lookup; `compileShaders` also compiles the tests' own shaders from `src/test/shaders` into test resources |
| CI: `gpu.yml` (or a leg of `linux.yml`) with lavapipe (`mesa-vulkan-drivers`), running `:natives`, `:core`, `:gpu` and `:media` GPU tests with `goldberry.gpu.required=true`, plus whatever phase 0 found usable on macOS and Windows | **written, not yet run**: `linux.yml`'s `verify` job installs lavapipe on both Linux targets and runs `:natives:gpuTest` and `:gpu:gpuTest` under `offscreen` with a device required, the loader pointed at lavapipe alone; `macos.yml` runs the same without requiring a device, to find out whether `macos-14` has Metal. Both upload the summaries. No container runtime was at hand to try either before a push |
| Coverage floor for `:gpu`, measured on the GPU lane | open |

**Exit:** a triangle is rendered to an offscreen texture and read back matching
a golden, on Metal and lavapipe, within ADR-0050's tolerance. The API has no
`MemorySegment` in a public signature.

**Status:** met on Metal (`:gpu:gpuTest`, Metal's API validation on). Through
the public API, a triangle from a vertex buffer reads back exactly as a
reference rasterized in Java draws it, every pixel but those whose centre lies
on an edge (`GpuApiTest`). Indexed quads (16- and 32-bit), uniforms, a depth
test, culling, partial and staged uploads, and readbacks are held to the same.
Through the wrappers, the built-in shaders: a solid quad fills exactly its
pixels, top left at top left; a texture drawn 1:1 with a nearest sampler reads
back byte for byte, which is what the composited UI rests on; premultiplied
"over" lands within 2 in 256; a scissor clips. No `SdlGpu…` type or
`MemorySegment` is in an exported signature. Lavapipe and the coverage floor
are open.

### Phase 3 — the composited window

| Item | Status |
|------|--------|
| `GpuSurface`, `GpuLayer`, `CompositionMode` in `:core`'s SPI (D5); `BackendWindow.gpuSurface()` on `Sdl3Window` and `HeadlessWindow` | moved to phase 4, their consumer (D5 corrected, ADR-0479). What phase 3 needed instead: `render.composite` in `:core`, exported to `:gpu` alone and provided by `:gpu`'s `SdlCompositor` through `ServiceLoader`; `Composition` (`goldberry.gpu`, `goldberry.gpu.composite`) |
| `Window.paint` in composited mode: the owned `PixelBuffer` (so `retainsFrameContents` is true and partial repaint keeps working), the damage uploaded to the UI texture, then the composite pass: clear, layers, UI quad (premultiplied `ONE, ONE_MINUS_SRC_ALPHA`) | done (ADR-0479). `Window.paint` is unchanged: a composited `Sdl3Window` lends no surface, so the frame loop paints into its own buffer, which it keeps; `present` goes to `SdlCompositedWindow`. The composite pass is `UiComposite`: clear to opaque black, then the UI quad. Layers go between the two in phase 4 |
| The UI quad's shader pair, `ui.vert`/`ui.frag` (D7). The UI texture is `B8G8R8A8_UNORM` so the Blend2D bytes upload unconverted | done without a pair of its own: `quad.vert` and `texture.frag`, sampled nearest, which phase 2 proved byte for byte. The UI texture is `B8G8R8A8_UNORM` |
| Mode entry and exit with hysteresis (D3); resize (UI texture recreated, full upload, ADR-0158); HiDPI (swapchain and UI texture in physical pixels, `DisplayScale`) | in part: entry at the first frame wanted; exit on failure, for good; a page embedded pulls a window back to the CPU; resize recreates the UI texture and uploads the frame whole; physical pixels throughout. The hysteresis waits for phase 4's attach and detach |
| No swapchain texture (minimized, occluded): the upload is still submitted and nothing is presented, and the frame counts as presented for the budget | done: the upload is submitted, `PresentTimings.shown` is false, and `Window.paint` records the frame as before |
| Pacing: swapchain VSYNC; `FramePacer` steps aside where phase 0 says it should; `SetGPUAllowedFramesInFlight(2)` by default | done (ADR-0479): VSYNC (MAILBOX or IMMEDIATE when `goldberry.backend.vsync=false`), two frames in flight. **`FramePacer` does not step aside.** Stepping aside let frames with no damage, which wait for no swapchain texture, spin about 1 ms apart. With it on, the showcase's 240 frames took 2.9 s composited and 2.8 s on the CPU, 14 and 13 late |
| `FrameStats` gains upload, acquire-wait and submit times; the `hud` shows them | done: `BackendWindow.lastPresent()` reports `PresentTimings`, `Window.paint` banks it beside its frame, and `FrameStats` gives `upload()`, `acquire()`, `submit()`, `uploadBytes()` and `compositedFrames()`, over the composited frames alone. The `hud` has `upload`, `acquire` (never coloured: it is the display's wait) and `submit` readings, and `readings="present"`; a window on its surface reads dashes. The launcher logs a `presents:` line at exit beside `frames:` |
| **Parity test:** the gallery screens composited with no GPU layer, read back from an offscreen target, match their CPU goldens within ADR-0050's tolerance | done in a stronger form (`CompositorTest`): the composite pass keeps every colour byte of random premultiplied frames, and makes them opaque, which is what the window surface shows. That holds at 64×32 and at 1920×1080, 3024×1842 and 2561×1599, to the far corner; a damage-only upload keeps every pixel outside the damage. The pass is a per-pixel 1:1 copy, so random bytes at window sizes cover whatever a gallery screen holds, exactly rather than within a tolerance. The gallery screens themselves were not put through it: that would take a GPU test harness in `:example` to show the same thing again |

**Exit:** the showcase runs composited (forced on with
`-Dgoldberry.gpu.composite=always`) with every tab indistinguishable from CPU
mode, parity tests green on the GPU lane, and damage-only uploads measured (the
bytes uploaded per frame while the caret blinks).

**Status:** the showcase runs composited on Metal
(`./gradlew :example:run -Pgoldberry.gpu.composite=always`). Its 240-frame run
paints and paces like the CPU one. `CompositedBackendTest` drives the whole
path through `Sdl3Backend` in `:gpu:gpuTest`. The run's `presents:` line: 91 of
240 frames composited (the rest had no damage and presented nothing), a mean
upload of 0.79 ms and 9.3 MB (the showcase's animations damage most of a
3024×1842 frame), and a mean acquire of 0.18 ms, since the pacer waits in the
pump before a frame is emitted.

Open: the tab-by-tab comparison by eye; the caret's upload bytes, which no unattended run can take,
since no screen focuses a field without a click (`hud readings="present"`
shows them live); and every platform but macOS.

### Phase 4 — GPU layers in the tree

| Item | Status |
|------|--------|
| The hole-punch painter (D4): the element's clipped box cleared in the CPU frame, the layer recorded in paint order with its target and scissor | open |
| Clips from `scroll` viewports and ancestors as scissors; a layer scrolled fully out of view is not rendered | open |
| The readback mode (D3): a layer renders to its own texture, which is downloaded and drawn as an `Image`. Used by headless, `Offscreen` and an unclaimable window | open |
| A test layer (a solid colour with a moving square) and the z-order cases: a popup over a layer, a layer in a scrolled list, two overlapping layers with UI between them, a layer under a rounded clip | open |

**Exit:** each z-order case is a golden that is the same in composited mode (read
back) and in readback mode, on the GPU lane.

### Phase 5 — `canvas3d`

| Item | Status |
|------|--------|
| `gpu.view.Canvas3d`, `@Markup("canvas3d")`, weaved into `:gpu`'s catalog (ADR-0131). A leaf sized like an image, `renderer=` naming a `Canvas3dRenderer` | open |
| `Canvas3dRenderer`: `init(GpuDevice)`, `render(GpuFrame, Canvas3dTarget)` with colour (and optional depth) textures at the box's physical size, `resize`, `dispose`. `continuous` or on-demand redraw through `requestFrame` | open |
| Fallback when no device: the widget paints `--gb-canvas3d-unavailable` and the reason, as `web-view` does without its engine (ADR-0441) | open |
| Showcase: a **GPU** tab with a lit spinning cube (vertex and index buffers, depth, one uniform block), its frame times, and a switch between composited and readback | open |
| Goldens: the cube at a fixed angle, through readback, on the GPU lane; the unavailable state on every leg | open |
| `docs/content-widgets.md` / `core-widgets.md` row, the widget catalog, `ARCHITECTURE.md` §12 as built | open |

**Exit:** the cube renders in the showcase on this Mac at display rate with the
UI composited over it. The goldens are green on the GPU lane, and the fallback
golden is green everywhere.

### Phase 6 — media phase 4: GPU present

| Item | Status |
|------|--------|
| `video-view` as a `GpuLayer` when a `GpuSurface` is available and `:gpu` is on the module path (`requires static`). Otherwise the CPU path is unchanged | open |
| The queue in YUV (D8): plane copies into slots, the format switched by flush and reseek, picture lifetimes unchanged (valid for two more pictures, ADR-0463) | open |
| Plane textures: NV12 → `R8` plus `R8G8`; I420 → three `R8`; P010 → `R16` plus `R16G16`; I010 → three `R16`, scaled by 64 in the shader (10 bits in the low bits) | done: `YuvLayout.planeFormats()`, and the scale as `sampleToCode` |
| `yuv.frag`: BT.601, BT.709 and BT.2020 non-constant-luminance matrices, limited and full range, chroma sited left (MPEG-2) as swscale assumes. The coefficients sit in a uniform block, so one pipeline serves every frame | done (ADR-0477): `yuv2.frag` and `yuv3.frag` over `yuv.hlsli`; `YuvLayout`, `YuvMatrix`, `YuvConversion` (uniforms and the Java reference); every layout × matrix × range exact against the reference on Metal |
| The fallback ladder: GPU present → CPU present when the device fails or the layer cannot attach, with a reseek to the shown picture (the mechanism of ADR-0470's rungs) | open |
| **Parity:** each fixture's picture at a fixed time, GPU-presented and read back, against swscale's `SWS_BITEXACT` BGRA within tolerance. The 601 and 709 fixtures are the exit criterion from `goldberry-media.md` §8; 2020 and 10-bit are held to the same | open |
| Measured: 4K60 VP9 on VideoToolbox, GPU present, no dropped pictures over 60 s, CPU time per picture. **This closes phase 5's exit criterion** | open |
| `media-plan.md` phase 4 row and phase 5's exit status updated; `goldberry-media.md` §3 "Presentation" as built | open |

**Exit:** parity green on the GPU lane; 4K60 with no drops measured on this Mac;
`video-view` falls back to CPU present with `-Dgoldberry.gpu=off` and with no
`:gpu` on the path, with every media test green both ways.

### Phase 6b — zero-copy on macOS (D9)

Taken up when phase 6's measurements or battery use justify it.

| Item | Status |
|------|--------|
| The SDL patch: `SDL_PROP_GPU_TEXTURE_CREATE_METAL_IOSURFACE_POINTER` and `…_METAL_IOSURFACE_PLANE_NUMBER`, a container that cannot be cycled, and the external `MTLTexture` released with it. Applied by the superbuild and checked. Offered upstream | open |
| `PlaneSource.Surface`: FFmpeg's `AV_PIX_FMT_VIDEOTOOLBOX` frames (`data[3]` is the `CVPixelBufferRef`) and `:media-platform`'s VideoToolbox decoder, both asked for IOSurface-backed, Metal-compatible buffers | open |
| Lifetime: the `CVPixelBuffer` is retained by the queue slot until the fence of the last frame that sampled it signals, so the decoder's pool cannot reuse a surface the GPU is still reading | open |
| Copy-back skipped when the view is on a patched Metal device, and kept for CPU present, readback and every other device | open |
| Parity: the zero-copy picture against the copy-back picture, byte for byte after the same shader | open |
| Measured: 4K60 CPU time per picture and package power, copy-back against zero-copy | open |

**Exit:** parity green on this Mac; the measurement recorded; the patch applies
cleanly to the pinned SDL and a test fails if it does not.

### Phase 7 — hardening

| Item | Status |
|------|--------|
| Device loss and GPU switches (eGPU unplugged, a laptop's GPU changing): a failed submit or acquire drops the window to CPU and the layers to readback or their fallback, recorded like a failed hardware codec. A VideoToolbox session invalidated by the switch is replaced (ADR-0472) | open |
| Display moves between monitors with different scales and rates while composited | open |
| A native image of the showcase with the GPU tab: registrations, shader resources, startup unchanged for a window with no GPU layer | open |
| The frame-budget run (ADR-0342) in composited mode on the lanes that have a GPU | open |
| Windows (D3D12) and Linux on hardware, when a host exists: written and unit-tested before, measured here | open |
| `book/src/status.md` M4, `ARCHITECTURE.md` §12 and §16, `README.md` | open |

**Exit:** M4 is done when phases 1–6 have met their exits, and this table's
items are either done or recorded as waiting on a host.

## 5. Risks

| Risk | Mitigation |
|------|------------|
| No GPU on any CI runner, so the GPU path is tested only here | Lavapipe is a conformant Vulkan 1.3 driver, and phase 0 proves it hosts `SDL_GPU` before anything is built on it. Goldens are taken through readback, so a GPU leg and a CPU leg compare the same pixels |
| Lavapipe's output differs from Metal's by more than ADR-0050's tolerance (filtering, rounding) | Nearest filtering in the parity tests, and the UI quad sampled 1:1. If a bound is still needed, it is set per test, measured, and written beside the test |
| Claim/release is unreliable on some platform | D3's fallback: the mode is fixed at window creation, and layers in CPU windows use readback |
| `SDL_GPU` changes shape between SDL releases | Pinned SDL, layouts verified at startup and in CI (the same net that caught ADR-0047's offset), and the bindings behind `:gpu`'s own API |
| Startup and footprint regress (ADR-0002) | No device until a layer attaches (D2); a startup test asserts no GPU symbol is called for a CPU-only window; `libgoldberry` size delta measured in phase 0 and gated |
| The UI thread cannot carry 4K60 plane copies | D8's second step: transfer-buffer slots written by the decode thread |
| Hole-punching surprises (translucency, rounded clips, frost) | The limits are written into the ADR and the widget docs before phase 4, and each has a golden that shows it |
| Shader toolchain drift | Committed blobs with a source hash test (D7); shadercross pinned in the catalog |
| VAAPI copy-back too slow for 4K60 on some Linux drivers (D9) | Measured on a Linux host when one exists; until then Linux defaults to software decode for 4K, as it already defaults hardware decode off (ADR-0470) |
| The macOS SDL patch (phase 6b) has to be rebased on every SDL bump | It is small and touches only the Metal texture path; a test checks it applied; the upstream property name is followed so it can be dropped when upstream lands |
| GPU-specific crashes are native and kill the JVM | SDL's debug mode (`SDL_PROP_GPU_DEVICE_CREATE_DEBUGMODE_BOOLEAN`) on in tests; validation layers on the lavapipe lane |

## 6. Documents kept in step

| Document | What changes | Status |
|----------|--------------|--------|
| `docs/ARCHITECTURE.md` §12 | "day 1 in the SPI" corrected; the three modes as built; frost over GPU content (D4) | open |
| `docs/ARCHITECTURE.md` §3.1, §15 | `natives.sdl.gpu`'s qualified export (D6); `:core`'s `render.composite` exported to `:gpu` (ADR-0479) | open |
| ADR-0002, ADR-0019 | a note linking the ADRs that replace their day-1 claims | open |
| ADR-0280 | amended for `:gpu`'s export, as ADR-0461 was for `:media` | open |
| `docs/goldberry-media.md` §3, §8 | GPU present as built; phase 4 exit | open |
| `docs/media-plan.md` | phase 4 unblocked, then done; phase 5's exit criterion closed | open |
| `docs/testing.md` | the GPU lane, `goldberry.gpu.required`, parity tests | open |
| `THIRD-PARTY-NOTICES.md` | SDL_shadercross (build-time only, "Not distributed") | open |
| `gpu/src/main/java/module-info.java` | the "empty and published" note replaced by the module's doc | done: it exports the GPU API, and says what is still to come |

## 7. Log

| Date | Entry |
|------|-------|
| 2026-09-24 | Plan written from the tree: `:gpu` empty, no `gpuSurface()`, no `SDL_GPU` export, no GPU runner. Read from SDL 3.4.16's headers: a window can be claimed without a flag, there is no external-texture import (so zero-copy stays post-v1), and `SDL_CreateGPURenderer` is an alternative to weigh (D1) |
| 2026-09-24 | D9: zero-copy. Upstream has it as SDL #14077 with PR #14157 open (Metal, D3D12; no Vulkan). M4 ships copy-back; phase 6b carries a Metal-only IOSurface patch; Windows and Linux wait. Implementation begins with phase 1's bindings, which the phase 0 spikes run on |
| 2026-09-24 | Phase 1 begun and its exit met on Metal (ADR-0475): 29 exports (ABI 16), four holder records, six structs and 23 enumerators verified, the `natives.sdl.gpu` wrappers sealed to `:core` and `:gpu`, and `:natives:gpuTest` on the first thread. Found: no device under `dummy`; cocoa needs the first thread; the exports cost 2 KB. `:natives:check` green: 562 tests, and 16 GPU tests on Metal |
| 2026-09-24 | Phase 0 on macOS: the window's claim and swapchain bound (`SdlGpuWindow`, `SdlGpuSwapchainTexture`, blits, a sealed `SdlGpuTarget`), 23 GPU tests on Metal, and `:natives:gpuPresentProbe` measured (§4.1). Switching is reliable and cheap; compositing a frame costs less CPU than the window surface; the swapchain paces to the display. SDL counts repeat claims by one device, so they are refused |
| 2026-09-24 | Phase 2 in part (ADR-0476): shaders, samplers, pipelines and render passes bound (13 more exports, 13 structs and 13 enumerators verified); three HLSL shaders compiled by DXC and SPIRV-Cross into SPIR-V, DXIL and MSL, committed with their sources' hashes; `:gpu:gpuTest`, whose five draws read back exact on Metal |
| 2026-09-24 | D1 decided (ADR-0477): `SDL_GPU` directly. Y'CbCr shaders for NV12, I420, P010 and I010, exact against a Java reference for every matrix and range on Metal; a 4K layer composited under the UI at under 1.4 ms of CPU a frame at 120 Hz |
| 2026-09-24 | The GPU lane written: lavapipe under `offscreen` on both Linux targets, device required; macOS runners asked, not required. Not yet run |
| 2026-09-24 | ADR-0480: windows present through the GPU by default, and on the CPU wherever it cannot be used. Each fallback is logged where it is known; a claim answers with a sealed `Claim` that carries the refusal's reason; popups are not composited. Measured: device 19.5–21.3 ms at the first frame, first frame on screen 1016.9 ms median against 1013.4 ms with `goldberry.gpu=off` |
| 2026-09-24 | Phase 3's statistics: `PresentTimings` moved to `render` and reported by `BackendWindow.lastPresent()`; `FrameRing` banks it per frame and sums it for a `presents:` exit line; the `hud` gained `upload`, `acquire`, `submit` and `readings="present"`. The showcase composited: 91 of 240 frames through the GPU, 0.79 ms and 9.3 MB a frame uploaded |
| 2026-09-24 | Phase 3 begun (ADR-0479): `:core` declares `render.composite`, exported to `:gpu` alone, and `:gpu`'s `SdlCompositor` provides it through `ServiceLoader`. `goldberry.gpu.composite=always` composites every window: it lends no surface, and its frames' damage goes up to a UI texture drawn over black onto the swapchain. D5 corrected: layers and `GpuSurface` move to phase 4. Found: stepping `FramePacer` aside let undamaged frames spin 1 ms apart, so it stays on. The showcase runs composited on Metal and paces like the CPU. `:gpu:gpuTest` 37 green, the backend end to end among them |
| 2026-09-24 | Phase 2's public API (ADR-0478): `io.github.digitalsmile.goldberry.gpu` exported, with resources made from records, frames of scoped passes, staged uploads and readback, one thread, misuse refused in Java. Under it, ten more exports (buffers, indexed draws, debug groups: 56 `SDL_GPU` functions), seven structs and the pipeline enumerators verified. Phase 2's exit met on Metal: a vertex-buffer triangle against a Java reference. `:natives:check` and `:gpu:check` green, with 27 and 28 GPU tests on Metal |
