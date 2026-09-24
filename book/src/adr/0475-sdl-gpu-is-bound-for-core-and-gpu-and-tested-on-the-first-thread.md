# 475. SDL_GPU is bound for :core and :gpu, and tested on the first thread

Date: 2026-09-24

## Status

Accepted. The first decisions of M4 (`docs/gpu-plan.md`): D6 as built, and
what phase 1 found about where a GPU device can exist. Amends
[ADR-0280](0280-natives-exports-to-core-and-to-nobody-else.md) as
[ADR-0461](0461-a-media-engine-binds-its-own-libraries.md) did.

## Context

M4 needs `SDL_GPU` from Java. `SDL_GPU` is already inside `libgoldberry`,
because SDL 3.4.16 is built with its defaults (ADR-0003). But none of its 97
functions was exported, and `:natives` had no binding for any of them.

Phase 1 of the plan binds the first surface: a device, textures, transfer
buffers, command buffers, a render pass that clears, a copy pass that uploads
and downloads, and fences. Its exit criterion is a texture cleared to a known
colour that downloads byte for byte.

Three facts were found in SDL's source on the way, and each changes something:

1. **A device needs the video subsystem, and a video driver that can make a
   Metal view or a Vulkan surface.** `SDL_CreateGPUDeviceWithProperties` asks
   the current video device (`SDL_gpu.c`). The Metal driver's `PrepareDriver`
   needs `Metal_CreateView` and the Vulkan driver's needs
   `Vulkan_CreateSurface`. SDL's `dummy` video driver, which every headless
   test in the project runs under, has neither, so under it there is no device
   at all. The `offscreen` driver has a headless Vulkan surface, so lavapipe
   works under it with no display. On macOS, only `cocoa` has a Metal view.
2. **`cocoa` starts only on the process's first thread** (`Cocoa_CreateDevice`
   returns NULL otherwise). That is ADR-0039's failure, "No available video
   device", met again in a place where the flag cannot be passed: Gradle's
   `Test` task runs tests on a worker thread however its JVM is started.
3. **The exports cost almost nothing.** `libgoldberry` for macos-aarch64 was
   6,100,832 bytes without the 29 new symbols and 6,102,848 with them: 2 KB.
   The GPU drivers were already linked, reached through SDL's renderer, which
   backs the window surface where a platform has no framebuffer (ADR-0046).
   The size risk in the plan's phase 0 is answered for this target.

## Decision

**Bind `SDL_GPU` in `:natives` by the rules every other library follows, and
seal it to the two modules M4 builds on it.**

- The functions are holders in `natives.sdl.calls` (ADR-0173):
  `SdlGpuDeviceCalls`, `SdlGpuResourceCalls` and `SdlGpuCommandCalls`, plus
  `SdlPropertiesCalls` for the property groups a device is configured with.
  They are exported in `goldberry.symbols` only as they gain a caller, so
  `ExportListTest` holds.
- Their six structs and 23 enumerators are on the shim's layout table and in
  `Layouts`/`NativeConstants`, so the C compiler checks every offset and value.
  The ABI version is 16.
- The wrappers are in `natives.sdl.gpu`: `SdlGpuDevice`, `SdlGpuTexture`,
  `SdlGpuTransferBuffer`, `SdlGpuCommandBuffer` with its `CopyPass`, and
  `SdlGpuFence`.
  - No public signature carries a `MemorySegment`.
  - SDL's rules are checked in Java before SDL is called: one pass at a time,
    a command buffer used once, unmapped buffers in a copy pass, regions inside
    their texture, and resources of the same device.
  - A closed resource fails in Java, naming itself. A device releases whatever
    is still open when it closes.
  - Mapped memory is a `ByteBuffer` scoped to an arena that `unmap` closes, so
    holding it too long throws rather than reading freed memory.
- **`natives.sdl.gpu` is exported to `:core` and `:gpu`, and to nobody else.**
  `:core` will claim windows and present; `:gpu` is the public API. It is the
  first package sealed to two readers, and `ExportedSurfaceTest` now holds a
  sealed package to a set of readers.

**Run the GPU tests on the first thread.** They are tagged `gpu`, and the
ordinary `test` task leaves them out. `:natives:gpuTest` runs them instead: a
`JavaExec` with `-XstartOnFirstThread` on macOS, whose main
(`GpuTestLauncher`) asks JUnit to run them on the calling thread. It is part
of `check`.

- Without a device they skip. `-Pgoldberry.gpu.required=true` makes that a
  failure, which is ADR-0016's rule applied to a device.
- `-Pgoldberry.gpu.videoDriver` names the video driver: `offscreen` for
  lavapipe on a runner with no display.
- A launch that finds no tests fails.

## Alternatives considered

- **Vulkan through MoltenVK under `offscreen` on macOS.** It needs no first
  thread, but it tests a driver no Mac user runs, and needs MoltenVK installed.
  Rejected: the macOS path is Metal.
- **Dispatching SDL's video initialisation to the main queue from the test
  thread.** The `java` launcher parks the first thread in a run loop, so a
  block sent there would run. But SDL's video would then belong to a thread the
  tests never run on, and every later call would cross threads. Rejected for
  what it would teach the code under test.
- **One export per SDL_GPU function up front (the plan's ~60).** Rejected by
  `ExportListTest`'s rule: a symbol nothing binds is dead weight, and the rest
  arrive with the phases that call them.

## Consequences

- Phase 1's exit is met on Metal: 16 device tests pass under Metal's API
  validation. They cover the clear and download, a region uploaded and read
  back, 16-bit planes, and every rule above. Layouts and constants are
  verified by `LayoutVerificationTest`. Lavapipe waits for the GPU lane.
- **A readback in a headless test needs `offscreen` or `cocoa`, not `dummy`.**
  The plan's "headless `GpuSurface` in readback mode" (D5) is corrected: the
  golden tests that render GPU content run in a `gpuTest`-style task under a
  GPU-capable driver. The CPU goldens keep `dummy` and `Offscreen`, unchanged.
- The size gate needs no allowance for GPU exports on macOS. Linux and Windows
  are measured when their builds run.
