# ADR-0666: Rays are traced on a Vulkan device of the toolkit's own

- **Status:** Accepted
- **Date:** 2026-10-25
- **Relates to:** ADR-0477 (SDL_GPU directly), ADR-0478 (the GPU's public API), ADR-0663 (Vulkan Video decodes on the GPU, with the headers read in Java), ADR-0560 (no clock bounds in tests)

## Context

A game asks the GPU for ray tracing, and `:gpu` is built on SDL_GPU, which has
none. SDL 3.4.18 has no acceleration structures, ray queries or ray-tracing
pipelines; its FAQ calls them "not in the 'never' category" and "definitely not
'near future' items". It also hands out no native handle: no `VkDevice`, no
`VkImage` behind a texture, no command buffer to record into. What it does have
since 3.4 is `SDL_GPUVulkanOptions`, which can turn the ray-tracing extensions on
in its own device — and nothing outside SDL can then reach that device.

`docs/gpu-gaming-plan.md` (2026-10-25) weighed three ways to the hardware:

- **(a)** an SDL patch exposing the Vulkan handles, with the extensions turned on
  through `SDL_GPUVulkanOptions`;
- **(b)** a second Vulkan device of the toolkit's own, with `:media`'s generated
  Vulkan bindings moved to `:natives`, and what it traces copied through the
  host into SDL's textures;
- **(c)** waiting for SDL.

The plan recommended (a). The user chose **(b)**: the FFmpeg work had already
chosen "no SDL patch", and (b) keeps that rule.

Two devices here, probed the same day:

| | GTX 1660 Ti (NVIDIA 610.57) | lavapipe (Mesa 26.0.8) |
|---|---|---|
| `VK_KHR_acceleration_structure` | yes | yes |
| `VK_KHR_ray_tracing_pipeline` | yes | yes |
| `VK_KHR_ray_query` | **no** | yes |

The plan's D1 had chosen inline ray queries, because they run in the compute
passes SDL_GPU already has. On a device of the toolkit's own that argument is
gone: nothing has to fit SDL's passes.

## Decision

**`:gpu` traces rays on a Vulkan device of its own, through ray-tracing
pipelines, and hands back what each pixel's ray hit for a renderer to upload
into SDL's textures.**

- **The Vulkan bindings are `:natives`'.** `tools/natives/generate_vulkan.py`
  (moved from `tools/media/`) writes `dev.goldberry.natives.vulkan`, with the
  holders in `…vulkan.calls`, both exported to `:media` and `:gpu` alone.
  `VkCalls` links through `Downcalls`, so `:natives`' foreign metadata records
  every Vulkan shape and `:media` no longer lists them; the holders' package is
  initialised at build time like every other `…calls` package.
- **Device functions are grouped by what enables them**: `VkDeviceFunctions`
  (core), `VkVideoFunctions` (`VK_KHR_video_*`) and `VkRayTracingFunctions`
  (`VK_KHR_acceleration_structure`, `VK_KHR_ray_tracing_pipeline`). A group is
  resolved whole or refused, so a device resolves only the groups it enabled.
- **Ray-tracing pipelines, not ray queries.** They run on both devices here,
  and on every RTX, RDNA 2 and Arc GPU; ray queries do not run on the GTX 16
  series. One SPIR-V library, `trace.lib.hlsl` (DXC, `lib_6_3`), with ray
  generation, a primary and a shadow miss, and one closest-hit group;
  recursion depth 1, since the shadow ray is traced from ray generation.
  `:gpu:compileRayShaders` is its own task, so it never rewrites the shipped
  DXIL of `compileShaders`' sets.
- **One device a process** (`RayDevice`), made at the first ask: the device
  with the extensions, the features (buffer device address,
  synchronization2, acceleration structures, pipelines) and a compute queue,
  a discrete GPU first and the CPU last. `goldberry.raytracing.device` names
  one by part of its name; `goldberry.raytracing=off|auto|hardware` overrides
  what an application asks.
- **The API** (`dev.goldberry.gpu.raytrace`, exported): `TriangleMesh`,
  `Transform`, `Instance` (a 24-bit id and an 8-bit mask), `SceneGeometry`,
  `Camera`, `TraceRequest`, `RayTracer` (`availability()`, `open(mode)`,
  `build`, `trace`), `RayScene`, `Hits`/`Hit`, and the sealed `RayTracing`
  (`Hardware(device)` or `Unavailable(reason)`). A tracer is confined to its
  thread, as a `GpuDevice` is.
- **The output is a hit record a pixel**, 24 bytes: instance id, triangle,
  distance, barycentrics, and whether the light reaches it. `Hits` makes an
  `R8_UNORM` shadow mask and an `R32_FLOAT` distance picture of it for
  `CopyPass.upload`, and lends the records for a storage buffer. The shaders
  write them into device-local memory, one `vkCmdCopyBuffer` moves them to a
  host buffer (cached where the device has it), and the trace returns once
  they are copied out. Written straight into host memory instead, they cost a
  1080p trace 133 ms on the 1660 Ti; this way it is 26 ms.
- **No restricted call in `:gpu`.** An application grants native access to
  `dev.goldberry.natives` alone, so the one restricted step — giving
  `vkMapMemory`'s address a size — is `natives.vulkan.VulkanMemory.map`, and a
  test keeps `:gpu`'s sources free of them. The tracer runs on the module path
  under `--illegal-native-access=deny`.
- **Tested against a reference** that intersects every triangle in double
  precision: every pixel whose answer does not hang on an edge must agree on
  instance, triangle, distance, barycentrics and shadow. Green on the GTX 1660
  Ti and on lavapipe; skipped without a device unless
  `goldberry.raytracing.required` is set.

## Consequences

- Every trace costs its copy back: the hits travel GPU → host → SDL. At
  1920×1080 that is 50 MB of records, or 2 MB as a shadow mask. Measured on a
  132,450-triangle scene with shadows (`:gpu:rayTraceProbe`): 26 ms at 1080p
  and 96 ms at 4K on the GTX 1660 Ti, about half of it the copy into the Java
  heap; 600 ms at 1080p on lavapipe. Packing the output and tracing at a
  render scale are the plan's next steps.
- D3D12 and Metal have no hardware ray tracing through this path; macOS has no
  Vulkan with these extensions at all. `RayTracer.availability()` says so, in
  the probe's words.
- The CI GPU lane runs lavapipe, which has the extensions in Mesa 26; whether
  `ubuntu-24.04`'s Mesa does is phase 0's open question.
- `ADR-0663`'s package names (`media.platform.vulkan.ffm`,
  `tools/media/generate_vulkan.py`) are history; its decision stands.
- The generator dropped every enum value next to an `#ifdef` block (eleven,
  `VK_PIPELINE_BIND_POINT_RAY_TRACING_KHR` among them). It strips preprocessor
  lines from enum bodies now; no value `:media` used changed.

## Alternatives

- **(a) The SDL patch.** Rays on SDL's own device, no copy. Rejected for now by
  the user: the first patch to SDL, rebased on every bump.
- **(c) Waiting.** No hardware ray tracing for an unknown time.
- **Inline ray queries.** Would let a future SDL path share the shaders, but do
  not run on this machine's GPU, and gain nothing on a device of our own.
