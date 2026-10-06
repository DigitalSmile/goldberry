# ADR-0574: A pass binds a sampler per slot

- **Status:** Accepted
- **Date:** 2026-10-06
- **Relates to:** the Gwent clone's issue list (GB-018),
  [ADR-0563](0563-a-texture-has-layers-levels-and-samples-and-a-pass-may-have-no-colour-target.md)

## Context

`RenderPass.bindFragmentSamplers(GpuSampler sampler, GpuTexture... textures)`
read every texture of a shader with one sampler. SDL binds an array of
`SDL_GPUTextureSamplerBinding`, one texture–sampler pair per slot, and the
bindings filled every pair with the same sampler. A shader that reads a
texture array trilinearly and a shadow map through a comparison sampler,
which is the downstream's card shader once cards receive shadows, could not
be bound. ADR-0563 had added the comparison sampler and the sampled depth
map; they could be used only by a shader that read nothing else.

## Decision

**`dev.goldberry.gpu.SamplerBinding(GpuTexture texture, GpuSampler
sampler)`**, a record, and **`RenderPass.bindFragmentSamplers(List<SamplerBinding>)`**
and **`bindVertexSamplers(List<SamplerBinding>)`**, slot 0 first. The
checks are the same as before, per binding: the count is the shader's, each
texture can be sampled, and both halves are this device's and open.

**The one-sampler forms stay as the shorthand** and build the list, so there
is one path to SDL. Below, `SdlGpuCommandBuffer.RenderPass` takes a list of
`SdlGpuSamplerBinding` in the same shape.

No native change: the struct and the two SDL functions were already bound.

## Alternatives considered

- **Parallel arrays**, `bind(GpuSampler[] samplers, GpuTexture[] textures)`.
  Two arrays that have to agree in length and order are two ways to be
  wrong; a pair per slot is the shape SDL has and the shape a reader thinks
  in.
- **A sampler on the texture**, read at bind time. A texture is often read
  two ways, a depth map compared in one pass and read as grey in another,
  and SDL keeps the two apart on purpose.

## Consequences

- One shader can mix samplers: `shadowed.frag`, a new test shader, reads a
  colour texture through a nearest sampler and a depth map through a
  comparison sampler, and `GpuApiTest` checks both halves on a real device.
  The test set's outputs were compiled here. The shipped sets were restored
  from git afterwards, so their signed DXIL is untouched.
- `RenderPass` gains two methods; every caller of the one-sampler forms
  is unchanged.
