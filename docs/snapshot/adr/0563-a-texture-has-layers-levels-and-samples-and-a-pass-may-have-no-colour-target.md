# ADR-0563: A texture has layers, levels and samples, and a pass may have no colour target

- **Status:** Accepted
- **Date:** 2026-10-05
- **Relates to:** the Gwent clone's issue list (GB-005, GB-006, GB-009, GB-010),
  [ADR-0478](0478-the-gpu-api-is-confined-scoped-and-checked-in-java.md),
  [ADR-0562](0562-the-native-abi-grows-once-for-the-whole-texture-model-and-compute.md)

## Context

The GPU API's texture was "a 2D texture with one mip level, one layer and one
sample", in one `UNORM` or depth format, and its depth texture could be tested
and nothing else. That was every texture the toolkit draws: the UI, video
planes, a canvas's result. A renderer written against the API wanted four
things it could not say: a texture array for a card-face cache drawn as one
instanced draw, mip levels for textures seen at a slant, float colour targets
for a scene drawn in HDR, and a depth map drawn in a pass of its own and
sampled by the scene. Each is a count or a usage SDL already takes, and each
asks the same question: does the API grow new types for them, or do the
existing records grow?

## Decision

**The existing records grow, with defaults of one.** `TextureSpec` has
`layers`, `mipLevels` and `samples`, and its four-argument constructor and
every factory still make a texture of one each, so no caller changes.
`TextureSpec.array` and the `with…` methods make the others. The native
`SdlGpuTexture` carries the three counts, and the device refuses, in Java, a
level count the size cannot halve into and a multisampled texture that is
sampled, layered or mipmapped: SDL's rules, stated before SDL is asked.

**A sub-resource is addressed, not made.** `TextureView(texture, level, layer)`
is a `RenderTarget`, and a `GpuTexture` stands for its level 0 of layer 0
wherever one is taken. Render passes draw into a view, copy passes upload to
one, and readbacks read one; the same three verbs the texture already had, on
the same frame. There is no layer object, no level object, and nothing to
close. A copy pass cycles a texture's memory only when every texel of a
one-level, one-layer texture is about to be written, because cycling discards
the rest.

**Mip levels are generated, or uploaded one at a time.**
`GpuFrame.generateMipmaps` wraps SDL's, outside any pass, for a texture that
is both sampled and a colour target: Vulkan blits, and Direct3D 12 and Metal
render, from each level into the next, and the colour-target usage is what
those need on every driver. A `SamplerSpec` reads level 0 alone unless given a
mip filter, which is what the toolkit's one-level textures want and what every
existing sampler keeps doing.

**Float colour formats join the enum.** `R16G16B16A16_FLOAT`, `R32_FLOAT` and
`R11G11B10_UFLOAT` are colour targets and sampled textures; a clear in them is
taken as it is, and a readback hands back their bytes. `TextureFormat.isFloat`
says which. The canvas's own target stays `B8G8R8A8_UNORM`; the renderer
tonemaps into it.

**A depth texture may be sampled, and a pass may have no colour target.** A
depth format's usages are `DEPTH_TARGET` alone, or with `SAMPLER`. A render
pass with a `DepthTarget` and no colour target writes a shadow map, with a
pipeline made by `PipelineSpec.depthOnly`, whose target format is empty and
whose fragment shader outputs nothing. The native pass sets no colour target,
and the pipeline no colour description; the two kinds do not cross, which is
checked where the pipeline is bound. A sampled depth texture reads back, as
floats or shorts; a depth texture made only to be tested still does not. A
`SamplerSpec` may carry a `CompareOp`, for the comparison samplers a shadow
map is read with.

## Alternatives considered

- **A `TextureArraySpec`, a `MipmappedTexture`, a `DepthMap`.** Separate types
  for each shape, as some engines have. Rejected because every verb on them is
  the texture's verb, and because a texture is often two of the shapes at once:
  a mipmapped array, a sampled multisampled depth target. Three counts on one
  record compose; three types do not.
- **A layer or level as a `GpuTexture` of its own.** It would have let a layer
  be passed anywhere a texture is, including `bindFragmentSamplers`, where SDL
  binds the whole array and the shader picks the layer. A view that is only a
  `RenderTarget` cannot be bound where it would be wrong.
- **Requiring `SAMPLER` alone for `generateMipmaps`.** SDL's documentation asks
  for nothing but more than one level, and its Vulkan driver would take it.
  Its Direct3D 12 and Metal drivers render into each level, and a texture made
  without `COLOR_TARGET` fails there with a validation message a Linux build
  never sees. The rule is the strictest driver's, stated in Java.
- **Cycling per level as before.** It was the bug the first run found: an
  upload into level 2 that cycled the texture lost level 0, with nothing to say
  so. The rule is now what cycling means.

## Consequences

- `PipelineSpec.targetFormat`, `RenderPass.target` and the native
  `SdlGpuGraphicsPipeline.targetFormat` are `Optional` now. The one caller
  outside the module, the toolkit's own, was the test that compared the pass's
  target.
- `SamplerSpec` has four components; its two-argument constructor and factories
  stay, and `GpuSampler.toString` names the extras.
- Mip generation needs a `renderTarget` texture, which costs nothing on a
  texture that is only sampled but has to be said when it is made.
- A `TextureView` is a record, equal by value: two views of one level are
  equal and not the same object.
- Resolving a multisampled target is specified by the sample count and not yet
  wired to a pass (phase 5 of the plan). Compute and storage usages are phase
  4. Both have their native constants already (ADR-0562).
- The test shaders gained four sources, compiled here. Their DXIL is unsigned,
  as Linux DXC cannot sign it; the shipped shaders' bytecode was left as the
  macOS build made it, and the test set never runs on a Windows GPU in CI.
