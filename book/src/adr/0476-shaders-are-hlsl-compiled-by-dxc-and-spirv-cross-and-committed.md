# 476. Shaders are HLSL, compiled by DXC and SPIRV-Cross, and committed

Date: 2026-09-24

## Status

Accepted. D7 of `docs/gpu-plan.md`, as built, with the pipeline surface of
`SDL_GPU` that phase 2 needed to draw with the shaders.

## Context

`SDL_GPU` takes a different bytecode on each backend: SPIR-V for Vulkan, DXIL
for Direct3D 12, and MSL (or a metallib) for Metal. SDL cannot read what a
shader declares from any of them, so the samplers and uniform blocks are stated
when a shader is created. The plan said to author each shader once and
cross-compile it with SDL_shadercross, offline, and commit the output.

Three things were found:

1. **Shadercross is two engines.** It runs DXC (HLSL to SPIR-V and DXIL) and
   SPIRV-Cross (SPIR-V to MSL). Both ship with the Vulkan SDK, and this Mac had
   them (1.4.328), while shadercross itself would have to be built.
2. **DXC signs DXIL off Windows.** Since 1.8 its validator is built in: the
   container's digest is non-zero without `-Vd`, and zero with it. Direct3D 12
   refuses unsigned DXIL, so this was the one reason to fear compiling off
   Windows, and it does not apply.
3. **SDL's binding conventions fix the registers.** A vertex shader's uniform
   blocks are `(b[n], space1)`. A fragment shader's textures and samplers are
   `(t[n], space2)` and `(s[n], space2)`, and its uniforms `(b[n], space3)`.
   DXC maps spaces to SPIR-V sets. Vulkan wants combined image samplers, which
   DXC writes only when told (`[[vk::combinedImageSampler]]`, guarded by
   `__spirv__` so the DXIL compile does not warn). SPIRV-Cross then gives MSL
   `[[texture(0)]]`, `[[sampler(0)]]` and `[[buffer(0)]]`, as SDL's Metal
   backend expects, and renames the entry point `main0`.

## Decision

**Author in HLSL, compile with DXC and SPIRV-Cross directly, commit the
output, and check it.**

- Sources are `gpu/src/main/shaders/<name>.vert.hlsl` and `.frag.hlsl`.
  `:gpu:compileShaders` is run on purpose, not in `build`. It writes `.spv`,
  `.dxil` and `.msl` for each source under the module's resources, and
  `shaders.properties` with each source's SHA-256 and the tools' versions. The
  output is deterministic: a second run gives the same bytes.
- `ShaderManifestTest` fails when a source's hash differs from the manifest, when
  a source has no bytecode or bytecode no source, and when `BuiltInShader` and
  the sources disagree. It needs no DXC and no device, so every leg runs it.
- `BuiltInShader` states each shader's stage, samplers and uniform blocks.
  `ShaderLibrary` loads the format the device takes (MSL, SPIR-V, DXIL, in that
  order) with the right entry point. The directory is declared to native-image
  by glob, which `DeclaredResourcesTest` holds to.
- The first three shaders are `quad.vert`, `texture.frag` and `solid.frag`.
  `quad.vert` makes a quad from the vertex id and one uniform block (destination
  in normalised device coordinates, source in texture coordinates). `Quad` turns
  pixels from the top left into that block, in one place.

**The pipeline surface, bound as phase 2 needs it.** 13 more exports: shaders,
samplers, graphics pipelines, and what a render pass records (pipeline,
viewport, scissor, fragment samplers, vertex and fragment uniforms, draws).
Their 13 structs are on the layout table, `SDL_GPUGraphicsPipelineCreateInfo`
among them at 168 bytes with five structs by value, and so are 13 enumerators.
In `natives.sdl.gpu`:

- `SdlGpuShader`, `SdlGpuSampler` and `SdlGpuGraphicsPipeline`. A pipeline
  draws triangles made from the vertex id into one colour target, with no
  culling and no depth, and one of two blends: `REPLACE`, or
  `PREMULTIPLIED_OVER` for the UI.
- `beginRenderPass` takes a sealed `SdlGpuLoad` (keep, clear, don't care), and
  `clear` is one such pass.
- The `RenderPass` refuses, in Java: a draw with no pipeline, or with fewer
  textures bound than the pipeline samples; a pipeline for another format than
  the target's; and a scissor outside the target.

## Alternatives considered

- **Build and pin SDL_shadercross.** It adds reflection (the counts
  `BuiltInShader` states), but that is three numbers a shader, checked by the
  device's validation. It would also add a C build of shadercross to a machine
  that already has both engines. Rejected for now, and nothing here prevents it
  later.
- **Write MSL by hand for Metal.** Rejected: three sources per shader, which
  drift apart.
- **Compile at build time on every leg.** Rejected, as the plan said: it would
  need the Vulkan SDK on every runner and every contributor's machine.

## Consequences

- Phase 2's exit is met on Metal for these shaders. A solid quad fills exactly
  its pixels, the right way up. A texture drawn 1:1 with a nearest sampler reads
  back byte for byte, which is what a composited UI rests on. Premultiplied
  "over" lands within 2 in 256, and a scissor clips.
- DXC and SPIRV-Cross are in `THIRD-PARTY-NOTICES.md` under "Not distributed":
  build-time tools whose output is ours.
- The DXIL has not run on a Direct3D 12 device yet; that waits for a Windows
  host. The SPIR-V waits for the lavapipe lane.
