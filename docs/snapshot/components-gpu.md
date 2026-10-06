# Guide additions held back until the release

Written 2026-10-06. These sections were drafted into `book/src/components/gpu.md`
and taken out again: the guide and the site are not adjusted before a release.
Each goes back where the heading says, in this order, before
`## What is measured, and what is not yet`.

## The texture model

A `TextureSpec` is a 2D texture of a format and a size, with three counts that
default to one: `layers`, `mipLevels` and `samples`. `TextureSpec.sampled`,
`renderTarget`, `depth` and `sampledDepth` make the common shapes;
`TextureSpec.array(format, width, height, layers, usages)` makes an array, and
`withMipLevels`, `withMipChain`, `withLayers`, `withSamples` and `withUsages`
vary any spec.

```java
var faceUsages = EnumSet.of(TextureUsage.COLOR_TARGET, TextureUsage.SAMPLER);
var faces = device.createTexture(TextureSpec.array(TextureFormat.B8G8R8A8_UNORM, 256, 358, 64, faceUsages));
var board = device.createTexture(TextureSpec.renderTarget(TextureFormat.R8G8B8A8_UNORM, 2048, 2048).withMipChain());
var shadow = device.createTexture(TextureSpec.sampledDepth(TextureFormat.D32_FLOAT, 2048, 2048));
var scene = device.createTexture(TextureSpec.renderTarget(TextureFormat.R16G16B16A16_FLOAT, w, h));
```

**Layers and levels are addressed, not made.** A `GpuTexture` stands for its
level 0 of layer 0 wherever a `RenderTarget` is taken. `texture.layer(i)`,
`texture.level(n)` and `texture.view(level, layer)` name one level of one
layer as a `TextureView`, which `frame.renderPass` draws into, `copy.upload`
fills, and `frame.readback` reads. A shader samples the array as one
(`Texture2DArray` in HLSL) and picks the layer by index, so a card-face cache
is one texture and every card one instance of one draw.

**Mip levels** are uploaded one at a time through a view, or generated:
`frame.generateMipmaps(texture)` fills every level below the first from the
one above it, outside any pass, for a texture made with
`TextureSpec.renderTarget` (the drivers blit through a colour target). A
`SamplerSpec` reads level 0 alone unless it has a mip filter:
`SamplerSpec.trilinear()`, or `withMipFilter(Filter.LINEAR)`, reads the level
the footprint calls for.

**Float colour formats** -- `R16G16B16A16_FLOAT`, `R32_FLOAT` and
`R11G11B10_UFLOAT` -- are colour targets a `Load.clear` takes past 1 and below
0, and read back as their bytes: halves, which `Float.float16ToFloat` decodes,
or floats. The canvas's own target stays `B8G8R8A8_UNORM`; a renderer draws
its scene in HDR and tonemaps into the canvas in its last pass. Whether a
device renders into `R11G11B10_UFLOAT` is a question for
`device.supports(format, usages)`.

**A depth texture can be sampled.** `TextureSpec.sampledDepth` makes a depth
target a later pass reads: a shadow map. It is drawn in a render pass with no
colour target, `frame.renderPass(DepthTarget.clear(shadow), body)`, with a
pipeline made by `PipelineSpec.depthOnly(vertex, fragment, DepthTest.less(format))`
whose fragment shader outputs nothing. The two kinds of pipeline and pass do
not cross: a colour pipeline in a depth-only pass, or the reverse, is refused
in Java. The scene then binds the shadow map like any texture, through a
sampler with a comparison, `SamplerSpec.linear().withCompare(CompareOp.LESS_OR_EQUAL)`,
for HLSL's `SampleCmp`. A sampled depth texture reads back as floats or shorts;
one made with `TextureSpec.depth` alone is never read back.

**Samples.** A spec with `samples` of 2, 4 or 8 is a multisampled render
target: one layer, one level, never sampled itself. A pipeline made with
`.samples(4)` draws into it, in a pass that names where the samples go:
`frame.renderPass(msaa, load, resolved.level(0), body)` resolves them into a
single-sampled texture of the same format and size when the pass ends, and
the multisampled texture's own contents are then undefined. A depth target in
such a pass has the same sample count. The sampled picture is the resolved
one.


## Compute and storage

A compute shader is `<name>.comp.hlsl`, compiled by the same task, and loaded
by `ComputeCode.load` with a builder that says its workgroup and what it
binds. `device.createComputePipeline(code)` makes the pipeline, and a frame
runs it in a compute pass that names what it writes:

```java
var declared = ComputeCode.builder(64, 1, 1).readOnlyStorageBuffers(1).readWriteStorageBuffers(1).uniformBuffers(1);
var code = ComputeCode.load("particles.comp", declared, Vfx.class::getResourceAsStream);
var simulate = device.createComputePipeline(code);
var usages = EnumSet.of(BufferUsage.COMPUTE_STORAGE_WRITE, BufferUsage.GRAPHICS_STORAGE_READ);
var particles = device.createBuffer(usages, COUNT * STRIDE);

frame.computePass(particles, pass -> {
    pass.bindPipeline(simulate);
    pass.bindStorageBuffers(emitters);
    pass.pushUniforms(0, dt, 0, 0, 0);
    pass.dispatch(COUNT / 64);
});
frame.renderPass(target.colour(), Load.keep(), pass -> {
    pass.bindPipeline(sprites);
    pass.bindVertexStorageBuffers(particles);
    pass.draw(6, COUNT, 0, 0);
});
```

What a pass writes is said when it begins, `frame.computePass(written, body)`
or the form with lists of buffers and texture views, so the driver orders the
writes against the draws around them; what it reads is bound after the
pipeline with `bindStorageBuffers` and `bindStorageTextures`. A buffer or
texture carries the usages of every stage that touches it: `COMPUTE_STORAGE_WRITE`
for the pass, `GRAPHICS_STORAGE_READ` for the draw that reads it through
`bindVertexStorageBuffers`, `bindFragmentStorageBuffers` or
`bindFragmentStorageTextures`, `COMPUTE_STORAGE_READ` for a later pass. A
graphics shader that reads storage says so when loaded,
`ShaderCode.builder(stage).storageBuffers(n)`, and a vertex shader that
samples a texture, a height map, binds it with `bindVertexSamplers`.

SDL's HLSL bindings for compute: samplers, then read-only storage textures and
buffers, as `t[n]` in space 0; read-write storage textures and buffers as
`u[n]` in space 1; uniform blocks as `b[n]` in space 2. For a vertex shader,
storage comes after its samplers in space 0; for a fragment shader, in space
2 after its samplers.

`BlendMode.ADDITIVE` sums the output onto the target, colour and alpha alike:
particles, glows and light. The counts and the usages are checked in Java
before SDL sees a dispatch or a draw, as everything in this API is.


## Rendering offscreen

A test of a scene with a `canvas3d` in it opens an `OffscreenGpu` and hands
its surface to `Offscreen`:

```java
try (var gpu = OffscreenGpu.open()) {
    var picture = Offscreen.of(1600, 900).gpu(gpu.surface()).render(new Board(state));
    GoldenImage.assertMatches("board", picture);
}
```

The surface is the read-back kind a window that presents on the CPU has: each
layer the frame places is rendered on the device, downloaded, and drawn into
the picture, so the picture holds the canvas's pixels as a window would show
them, and a golden compares them. The device is made on the calling thread,
and the renderer's `init`, `render` and `dispose` run on it as they would in a
window. Closing the `OffscreenGpu` closes the device and everything left on
it. Where no window has started SDL's video, the offscreen GPU does, under the
driver `goldberry.gpu.videoDriver` names, SDL's default, or `offscreen` when
the default cannot start on a machine with no display; it quits what it
started when closed. With no device at all, `open` throws with the driver's
reason, rather than rendering the notice.

A running window's picture is `window.capture()`: the last frame as the
screen shows it, GPU layers and all, composited again into a texture and read
back where the window is composited, and the window surface's pixels where it
presents on the CPU. The launcher writes it for a run that paints a set number
of frames: `--frames=N --capture=PATH` writes the last frame as a PNG
([Measuring](../performance/measuring.md#the-flags)).
