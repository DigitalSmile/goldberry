# 478. The GPU API is confined to one thread, scoped by pass, and checked in Java

Date: 2026-09-24

## Status

Accepted. The public half of `docs/gpu-plan.md`'s phase 2, over the `SDL_GPU`
wrappers of ADR-0475 and ADR-0476.

## Context

Phase 2 asks for "a safe layer an application can use without knowing
`SDL_GPU`'s structs". `canvas3d` renderers are its first users (phase 5), then
the composited window's layers and video (phases 3, 4 and 6). They need
textures, buffers, samplers, shaders, pipelines, a command scope per frame,
uploads, and readback.

The wrappers in `natives.sdl.gpu` already check SDL's rules in Java: one pass
at a time, a draw with everything bound, resources of the same device. But they
are `:natives` types, and ADR-0280 keeps those out of every signature an
application can read. They also cover only what the toolkit's quads needed.
Vertices came from the vertex id, there was no depth, and a pipeline was always
a quad pipeline.

`SDL_GPU` has sharp edges a Java API can blunt:

- A command buffer cannot be cancelled with a pass open.
- A debug group begun in a pass has to end in that pass on Metal.
- A texture cycled on upload loses whatever the upload did not write.
- A transfer buffer mapped for writing makes the CPU wait if the GPU still reads
  it, unless it is cycled.
- Nothing is thread-safe.

## Decision

**A public package, `io.github.digitalsmile.goldberry.gpu`, whose types wrap
the natives wrappers. No SDL type, handle or `MemorySegment` appears in it.**

- **Resources.** `GpuTexture`, `GpuBuffer`, `GpuSampler`, `Shader` and
  `GraphicsPipeline` extend a sealed `GpuResource`. Each is `AutoCloseable` and
  made from a record: `TextureSpec`, `SamplerSpec`, `ShaderCode`, or
  `PipelineSpec` with its builder. The enums those records name map onto SDL's
  by exhaustive `switch`, and a test holds each mapping to be one to one. A use
  after close, or with another device, throws in Java and names the public type.
- **One thread.** A `GpuDevice` belongs to the thread it was handed out on.
  Every call from another throws `WrongThreadException`, as a confined `Arena`
  does, before the driver is reached.
- **The device is not an application's to make or close.** `GpuDevice.wrap` is
  package-private. The backend will own the one device of the process
  (D2) and hand it out through `gpuSurface()` (phase 3).
- **Passes are scoped by lambdas.** `GpuFrame.copyPass(body)` and
  `renderPass(target, load, [depth,] body)` open the pass, run the body, and end
  the pass whether it returns or throws. Passes do not nest, and a pass kept
  past its body throws. A `GpuFrame` is `AutoCloseable`: closing an unsubmitted
  frame discards it, and closing a submitted one does nothing, so
  try-with-resources is always right. `debugGroup(name, body)` is scoped the
  same way, and the wrapper refuses a group left open across a pass's end.
- **Uploads go through one staging buffer per device.** It starts at 64 KiB and
  at least doubles when an upload does not fit. Each map is cycled, so writing
  never waits for the GPU and never overwrites bytes it has yet to copy. An
  upload of regions records the texture upload *uncycled*, so the pixels
  outside the regions stay as they were; that is the composited UI's
  damage-only upload. An upload of the whole texture or buffer is cycled.
- **Readback is recorded in the frame and awaited after it.**
  `GpuFrame.readback(texture, region)` records a download. Submitting a frame
  with readbacks takes one fence, which they share and release.
  `Readback.await()` is **the only blocking call in the API**. `awaitPixels()`
  gives a `PixelBuffer` of premultiplied BGRA, swizzling an RGBA texture.
- **Driver refusals are `GpuException`.** What the API can see for itself is
  `IllegalArgumentException` or `IllegalStateException`.

Under it, the natives wrappers grow what `canvas3d` needs, each piece checked
the same way:

- vertex and index buffers, and their uploads and downloads;
- indexed and instanced draws;
- a `SdlGpuPipelineDescription` with vertex input, primitive type, culling,
  front face and a depth test;
- depth targets for render passes;
- sampler address modes;
- uniforms pushed as bytes;
- balanced debug groups.

That is ten more exports (`SDL_CreateGPUBuffer` … `SDL_InsertGPUDebugLabel`)
and seven more verified structs. The enumerators are verified through nine new
enums, so the names and values are checked against the compiled library.

## Alternatives considered

- **Exporting the natives wrappers.** They are already safe, but it would
  reverse ADR-0280 for one module. It would also make `SdlGpu…` names, and
  every change to them, part of the published surface.
- **Passes as `AutoCloseable` objects returned to the caller.** Safe inside
  try-with-resources, but nothing makes a caller use one, and a pass left open
  locks the command buffer. With a lambda there is nothing to forget. The cost
  is that state a body computes has to leave through a captured variable.
- **One transfer buffer per upload.** Simpler, but a 4K frame would allocate
  33 MB every frame. The staging buffer is sized once, and SDL's cycling keeps
  the few copies frames in flight need.
- **`await` returning the transfer buffer's mapped memory.** It would need no
  copy, but the memory would stop being valid at unmap, the lifetime ADR-0019
  keeps raw memory from having. The copy goes to the heap, and only readback
  pays for it.

## Consequences

- `canvas3d` (phase 5) has what its cube needs: vertex and index buffers,
  depth, culling and a uniform block. The tests draw a mesh with shaders of
  their own, which `:gpu:compileShaders` now compiles from `src/test/shaders`
  into test resources that do not ship. `ShaderManifestTest` holds them to their
  sources too.
- Phase 2's exit is met on Metal: a triangle from a vertex buffer reads back as
  a reference rasterized in Java draws it, every pixel but those on an edge.
  Lavapipe waits for the GPU lane.
- The composited window (phase 3) builds on `GpuFrame`. Its swapchain becomes a
  second `RenderTarget` (the interface is sealed and permits only `GpuTexture`
  today), and `CopyPass.upload(texture, pixelBuffer, damage)` is its UI upload.
  `SDL_CancelGPUCommandBuffer` is refused after a swapchain acquire, so a
  composited frame will have to submit rather than discard.
- Vertex-buffer and sampler bindings are reset whenever a pipeline is bound. SDL
  keeps them on some backends. The stricter rule is the same on all of them.
- Not in this cut, and recorded so it is not rediscovered:
  - `PresentMode`, which has no consumer before phase 3;
  - resource names (`SDL_SetGPUTextureName`);
  - mip levels, multisampling and stencil;
  - compute (phase 7).
- The culling test checks winding in normalised device coordinates, y up, as
  the API documents. If lavapipe disagrees with Metal, that is SDL's backends
  disagreeing, and the test is where it will show.
