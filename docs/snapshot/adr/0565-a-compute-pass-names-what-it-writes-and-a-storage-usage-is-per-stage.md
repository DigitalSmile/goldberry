# ADR-0565: A compute pass names what it writes, and a storage usage is per stage

- **Status:** Accepted
- **Date:** 2026-10-06
- **Relates to:** the Gwent clone's issue list (GB-008),
  [ADR-0476](0476-shaders-are-hlsl-compiled-by-dxc-and-spirv-cross-and-committed.md),
  [ADR-0478](0478-the-gpu-api-is-confined-scoped-and-checked-in-java.md),
  [ADR-0562](0562-the-native-abi-grows-once-for-the-whole-texture-model-and-compute.md)

## Context

The GPU API had graphics pipelines, vertex and index buffers, and two blend
modes: what the toolkit draws with. A renderer's particles simulate in a
compute shader and are drawn additively from the buffer it wrote, and its
distortion reads the scene's colour texel by texel. SDL has all of it: compute
pipelines and passes, storage buffers and textures with a usage bit per stage
that touches them, and a general blend state. ADR-0562 bound the calls and
the constants. What was left to decide is the shape of a compute pass in an
API whose passes are scoped bodies checked in Java, and how a buffer says
which stages may read and write it.

## Decision

**A compute pass names what it writes when it begins.** `frame.computePass(written, body)`
takes the buffers and texture views the pass writes, which is what SDL's
`SDL_BeginGPUComputePass` takes and what the driver orders the surrounding
draws and copies against. The body binds a `ComputePipeline`, whose
`ComputeCode` declares how many of each it writes, and the bind refuses a
pipeline whose counts are not the pass's. What the pass reads is bound after
the pipeline, `bindStorageBuffers` and `bindStorageTextures`, and a dispatch
with them unbound is refused, as a draw with its samplers unbound is.

**A storage usage is per stage, as SDL's bits are.** `BufferUsage` and
`TextureUsage` carry `GRAPHICS_STORAGE_READ`, `COMPUTE_STORAGE_READ` and
`COMPUTE_STORAGE_WRITE`, and a resource is made with every usage of every
stage that touches it: a particle buffer is written by compute and read by a
draw, so it is made with both. The API does not fold the three into a
`STORAGE` that means all of them, because the driver allocates and
synchronises by the bits, and a bit the resource does not need is a cost on
every frame.

**A graphics shader declares its storage when loaded.** `ShaderCode` carries
`storageTextures` and `storageBuffers` beside its samplers and uniform
blocks, with a builder for the shaders that read any, and `SdlGpuShaderCode`
hands them to `SDL_GPUShaderCreateInfo`, which left them at zero before. A
`RenderPass` binds them with `bindVertexStorageBuffers`,
`bindFragmentStorageBuffers` and `bindFragmentStorageTextures`, and a vertex
shader's textures with `bindVertexSamplers`; the draw checks every count the
two shaders declared is bound.

**`ComputeCode` is its own record, not a `ShaderCode` stage.** SDL has no
compute shader stage: a compute pipeline is made from code directly, with
its workgroup size and six resource counts, none of which a graphics shader
has. `ShaderStage` stays the two stages a graphics pipeline joins, and the
enum bijection test stays true.

**`BlendMode.ADDITIVE`** is `(ONE, ONE)` on colour and alpha. The named modes
stay an enum; a general blend state is not added until something asks for a
fourth.

## Alternatives considered

- **Writes bound inside the pass, like reads.** The natural shape of a bind
  API, and the one Vulkan has. SDL does not: its pass begins with its
  read-write bindings so the backends can place their barriers, and the API
  follows SDL here as it does in scoping a pass to a body.
- **One `STORAGE` usage.** Simpler to say, and wrong by a bit on every
  resource that is only read, or only written, or only touched by one stage.
- **Compute as a `ShaderStage`.** It would have let `ShaderCode.load` load a
  compute shader with the graphics signature, and left eight of its ten
  counts with no home.

## Consequences

- `ShaderCode` has six components; its four-argument constructor and the
  `load` with four counts stay, so every existing shader loads unchanged.
- The shader task compiles `<name>.comp.hlsl` with the `cs_6_0` profile; the
  test set has `scale.comp` and `storage.vert`.
- A compute pass has no debug-group test of its own; it shares the command
  buffer's, which closes a leaked group with the pass as the other two do.
- Compute samplers (`SDL_BindGPUComputeSamplers`) are not bound: no export, no
  holder, no caller. They join when something samples in a compute shader.
- The toolkit's own shaders declare no storage, and nothing it draws blends
  additively; the usages and the mode exist for a renderer written against
  the API.
