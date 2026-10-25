# ADR-0669: Indirect draws, the stencil test and GPU copies join the GPU API

- **Status:** Accepted
- **Date:** 2026-10-25
- **Relates to:** ADR-0562 (the native ABI grows once for the whole texture model and compute), ADR-0563 (a texture has layers, levels and samples), ADR-0565 (a compute pass names what it writes), ADR-0666 (rays on a Vulkan device of the toolkit's own)

## Context

`docs/gpu-gaming-plan.md`'s phase 1 lists the SDL_GPU functions that a game
needs once it has a lit cube, and that `libgoldberry` did not export: draws
and dispatches whose counts come from a buffer, the stencil reference, blend
constants, copies from texture to texture and from buffer to buffer, samplers
in compute, storage textures read by a vertex shader, the adapter's name, an
idle wait, resource names, and whether a window can present in HDR.

Binding the functions alone would not have been enough. Three things turned
up along the way:

- **The stencil reference and the blend constants did nothing.** No pipeline
  could test stencil (`SdlGpuPipelineDescription` left
  `enable_stencil_test` off, and no texture format had a stencil), and no
  blend mode read the constants. A setter with nothing to read it is not an
  API.
- **`BufferUsage.INDIRECT` was not there.** The plan said it was "already
  mapped". Neither `BufferUsage` nor `SdlGpuBufferUsage` had the bit, so no
  buffer could be made for an indirect draw.
- **A texture both sampled and read as storage by a draw aborted the
  process.** The first run of the new tests on lavapipe ended in SDL's
  `SDL_assert_release`: "usage cannot contain both GRAPHICS_STORAGE_READ and
  SAMPLER". `TextureSpec` let the pair through, so this was already possible
  before the change. Nothing in the tree had asked for it until now.

## Decision

**Native ABI 26** exports the fifteen functions (`SDL_GetStringProperty`
among them, which the adapter's name is read through). It adds five structs to
the layout probe (`SDL_GPUIndirectDrawCommand`, `…IndexedIndirectDrawCommand`,
`…IndirectDispatchCommand`, `SDL_GPUTextureLocation`, `SDL_GPUBufferLocation`)
and the constants for the stencil ops, the compositions, the indirect usage,
the two depth-stencil formats and the two constant blend factors. The holders
join the existing `…calls` records by what they act on. `SDL_FColor`, passed
by value to `SDL_SetGPUBlendConstants`, is written out in its holder rather
than taken from `Layouts`, because that package is initialised when a native
image is built.

**The public API**, in `dev.goldberry.gpu`:

- `RenderPass.drawIndirect` and `drawIndexedIndirect`, and
  `ComputePass.dispatchIndirect`, read from a buffer made with
  `BufferUsage.INDIRECT`. The offset must be a whole word, at least one
  command is drawn, and every command must lie inside the buffer. These are
  checked in Java. The command layouts are records in a new exported package,
  `dev.goldberry.gpu.indirect` (`DrawCommand`, `IndexedDrawCommand`,
  `DispatchCommand`, and `IndirectCommand.pack`). A shader that writes a
  command writes the same words.
- **A stencil test.** `StencilTest` (one test for both faces, with presets
  `replace`, `equal` and `notEqual`) and `StencilOp` go into
  `PipelineSpec.stencilTest`. A stencil test needs a depth test of
  `D24_UNORM_S8_UINT` or `D32_FLOAT_S8_UINT`, the two new formats.
  `DepthTest.off(format)` is that depth test for a pipeline that leaves depth
  alone. `DepthTarget` gains `clearStencil`. A pass clears or keeps the
  stencil along with the depth, and always stores it at the end.
  `RenderPass.setStencilReference` is refused in a pass whose depth target
  has no stencil.
- **`BlendMode.CONSTANT`** computes `src × c + dst × (1 − c)`, with `c` set
  by `RenderPass.setBlendConstants`.
- `CopyPass.copy` from texture to texture (one level of one layer to another,
  one format, single-sampled, never onto itself) and from buffer to buffer
  (whole words, since Metal copies nothing else, and no overlap within one
  buffer).
- `ComputePass.bindSamplers` binds what `ComputeCode.samplers` declares. A
  dispatch now refuses an unbound sampler as it already refused unbound
  storage.
- `RenderPass.bindVertexStorageTextures` binds what a vertex shader's
  `storageTextures` declares. A draw refuses them unbound.
- `GpuDevice.adapter()` (a `GpuAdapter` of free text, never parsed),
  `GpuDevice.waitForIdle()`, and `setDebugName` on `GpuTexture` and
  `GpuBuffer`.
- `SdlGpuWindow.supports(SdlGpuSwapchainComposition)` stays in `:natives`
  until phase 6 has something to present in HDR.

**`TextureSpec` and `SdlGpuDevice.createTexture` refuse a texture that is both
sampled and read as storage by a draw**, and `createTexture` also refuses a
depth texture with any usage but target and sampler. SDL asserts on both, and
an assertion takes the application down rather than failing the call.

The tests: `GpuGameFeaturesTest` (15 on the device) checks each draw against
what it means, pixel by pixel away from the edges. It covers a command the
GPU wrote (`command.comp`), an indexed command with a first index and a
vertex offset, an indirect dispatch whose output is drawn, a stencil mask
with every pixel checked, the constant mix within one level, copies read back
byte for byte, a sampled compute pass (`sampled.comp`) and a mesh moved by a
vertex storage texture (`displaced.vert`). `SdlGpuDeviceTest` covers the same
copies, the adapter, names and compositions at the native level. Value tests
cover the layouts, the enums and the records. All are green on lavapipe and on
the GTX 1660 Ti. The three new test shaders were compiled here. `compileShaders`
rewrites every set's DXIL on Linux, so the shipped sets were restored and only
the test set's new files kept.

## Consequences

- An application can cull on the GPU and draw what survives without reading
  anything back, mask a portal or an outline with the stencil, cross-fade with
  one pipeline, and keep last frame's picture with a copy.
- `PipelineSpec` and `DepthTarget` each have one more component, and keep
  their previous constructors. A record pattern that deconstructs either must
  name the new component. This is in the breaking changes for the release.
- A texture that was made both sampled and storage-read by a draw is now
  refused in Java. It used to abort, so nothing that ran could depend on it.
- `D24_UNORM_S8_UINT` is missing on Apple's GPUs. The `StencilTest` doc says
  to ask `GpuDevice.supports` first, and the test takes the first of the two
  the device has.
- An indirect command with a first instance other than 0 needs Vulkan's
  `drawIndirectFirstInstance`. That is documented, and not checked, since
  SDL does not report the feature.
- Phase 2's `GpuFeatures` builds on `adapter()` and on the composition query.

## Alternatives

- **Bind the functions and nothing else**, as the plan's phase 1 row reads.
  That would leave two setters with no pipeline to read them, and it would
  have hidden the abort.
- **One `IndirectBuffer` resource type** instead of a usage on `GpuBuffer`.
  A compute pass has to write the commands, so the buffer needs a compute
  usage as well. A separate type would have to carry both and duplicate
  `GpuBuffer`.
- **Separate front and back stencil states.** Shadow volumes want them, but
  nothing here does yet. One state for both keeps the record small, and a
  second component can be added later without breaking the first.
