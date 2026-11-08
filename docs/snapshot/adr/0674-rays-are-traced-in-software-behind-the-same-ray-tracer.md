# ADR-0674: Rays are traced in software behind the same ray tracer

- **Status:** Accepted
- **Date:** 2026-10-25
- **Relates to:** ADR-0666 (rays are traced on a Vulkan device of the toolkit's own), ADR-0667 (scene updates, compaction and the packed shadow mask), ADR-0672 (the lighting trace), ADR-0673 (the showcase's ray-traced room), ADR-0477 (the GPU's arithmetic is held to a Java reference), ADR-0560 (no clock bounds in tests)

## Context

`docs/gpu-gaming-plan.md`'s D2 planned a software tier before there was a
hardware one: a bounding volume hierarchy (BVH) built in Java, traversed by
an HLSL include in an application's own compute shaders, with
`GB_RAYTRACE_HARDWARE` switching the include's body to `RayQuery`. That
switch assumed D1, ray queries on SDL's own device.

ADR-0666 went the other way. The hardware tier runs on a Vulkan device of the
toolkit's own, with ray-tracing pipelines, behind a Java API: `RayTracer`
builds a `RayScene` and traces it into `Hits`, a `ShadowMask` or `Lighting`,
which come back through host memory. An include on SDL's device and a Java API
on another device would be two ways to trace rays with nothing in common, and
the switch between them could not be written.

Meanwhile only Linux has the hardware tier proven, and macOS has none at all:
SDL hands out no native handles, there is no Vulkan on macOS, and MoltenVK's
ray tracing is an open pull request (the plan's §1 and phase 8). The
showcase's panel has no Software segment because there was no software
tracer.

## Decision

**The software tier is a second backend behind `RayTracer`.** An application
asks one API whichever tier traces: `build(SceneGeometry)`, `RayScene.update`,
`trace`, `traceShadows` and `traceLighting`, with the same `Hits`,
`ShadowMask` and `Lighting`, byte for byte in the same layouts. A test of one
tier is a test of the other.

**It runs on the window's device.** The software tier is compute shaders on
SDL's GPU, so it needs that device, which `RayTracer.open(mode)` never had:

- `RayTracer.open(GpuDevice, RayTracingMode)` opens either tier. `HARDWARE`
  is the hardware tier or a refusal that says why; `SOFTWARE` is the software
  tier on the device; `AUTO` is the hardware tier where its device is a GPU,
  and the software tier otherwise. Lavapipe has the ray-tracing extensions, so
  a machine with lavapipe installed and a GPU without them would otherwise
  trace on its CPU, 600 ms a 1080p frame, beside a GPU that traces in compute.
- `RayTracer.open(RayTracingMode)` stays, for the hardware tier alone.
  `SOFTWARE` there is refused: it names the device it needs.
- `RayTracer.availability(GpuDevice, RayTracingMode)` answers what `open`
  would pick for that mode, without opening anything; `availability()` stays
  the hardware tier's answer, for a screen with no device to hand.
- `RayTracingMode` gains `SOFTWARE`, and `goldberry.raytracing=software`
  names it.
- `RayTracing` gains `Software(device)`. `Hardware` and `Software` are
  `RayTracing.Available`, which `RayTracer.device()` now returns.

**Results can stay on the GPU.** A software trace's results are in the window
device's memory already. Copying them to the host to upload them again, as
the hardware tier must, would cost both copies for nothing.
`RayTracer.traceLighting(GpuFrame, RayScene, LightingRequest, LightingTargets)`
records the lighting into two textures inside a frame. The software tier
dispatches straight into them as storage textures, and the hardware tier
traces, then records the planes' upload into the same textures. The
showcase's room uses it, whichever tier is chosen. `LightingTargets` holds an
`R8G8B8A8_UNORM` and an `R32_FLOAT` texture, each made to be sampled and
written by compute, and `LightingTargets.create` makes them.

**The BVH is built in Java, two levels, as the hardware tier's structures
are** (`dev.goldberry.gpu.raytrace.bvh`, unexported):

- **Bottom level: one a distinct mesh**, built once by a binned surface-area
  heuristic: 16 bins along the widest axis of the centroids, leaves of up to
  four triangles, and a leaf wherever splitting costs more than not. A branch
  that reaches depth 48 becomes a leaf, whatever its size, so the shaders'
  traversal stacks cannot overflow. Boxes are widened by a few units in the
  last place, so a ray grazing a face is never refused by its box.
- **Top level: over the instances**, built by the same builder on their
  world-space boxes and rebuilt by every `RayScene.update`, as the hardware
  tier rebuilds its top level. A mesh whose triangles change is a new scene in
  both tiers, so a bottom level is never refitted.
- **Nodes are 32 bytes**, flattened depth first: a box, and either the right
  child's index (the left follows its parent) or a leaf's first item and
  count.
- **On the GPU, five storage buffers**: the top level's nodes, the instances
  (each one's world-to-object transform, mask, id, bottom-level root, first
  triangle and first normal), the bottom levels' nodes, the triangles (their
  three corners in the order the leaves need them, and each one's index in its
  mesh), and the face normals the hardware tier's lighting uses.
- **A Java traversal of the same buffers** (`BvhTraversal`) finds what the
  shaders find, closest hits and any hits. It is the unit tests' check that a
  BVH holds every triangle, beside a brute-force pass over every triangle.

**Three compute shaders, one include** (`raytrace.hlsli`, shipped like every
built-in shader, in SPIR-V, DXIL and MSL, and named with what each declares in
a new `BuiltInComputeShader` beside `BuiltInShader`, which
`ShaderManifestTest` holds to the sources): `trace.comp` writes the records or
the lighting into a byte buffer laid out as the hardware tier's,
`shadows.comp` writes the shadow mask a word at a time, four pixels a thread,
so it needs neither a zeroed buffer nor atomics, and `lighting.comp` writes
the lighting into the two textures. The include is a port of
`trace.lib.hlsl`: the same rays from the same 112-byte constants, the same
bias, the same occlusion directions from the same hash, the same facing
normals. `TraceRay` becomes a stack-based traversal of the two levels and a
Möller–Trumbore test of both faces, whose barycentrics weigh the second and
third corners, as DXR's do.

**The shared pieces move to an unexported `dev.goldberry.gpu.raytrace.tier`**:
the `Tier` and `TracedScene` interfaces each backend implements, the
constants' writer, the face normals and the output layouts.

**`GpuFrame.readback(GpuBuffer, offset, size)`** is new in `:gpu`'s public
API, returning a `BufferReadback`. The software tier's synchronous traces are
a frame of one compute pass and one buffer readback, awaited. Both kinds of
readback share one lifecycle (`TransferState`): recorded, submitted, copied
out, discarded or closed.

**The showcase's room traces through `LightingTargets` in either tier.** Its
panel offers Off, Software and Hardware. Choosing Software sets it at once;
choosing Hardware asks `availability()` first, as before. A tier that refuses
is disabled with its reason, and the other stays offered: a refusal is kept per
tier, in the panel and in the renderer, and a change of tier closes the tracer
and opens the other.

## Consequences

- Every SDL_GPU driver traces rays: Metal, Direct3D 12 and Vulkan without the
  ray-tracing extensions. macOS and Windows have the showcase's room traced
  before phase 8 starts.
- The showcase's panel offers Off, Software and Hardware. Software is never
  disabled where the canvas has a GPU; Hardware is disabled with the device's
  reason, as before.
- The software tier is held to the same double-precision references as the
  hardware tier (`ReferenceTracer`, `LightingReference`), by one contract of
  tests that runs against each tier. The software tier's runs in `gpuTest`,
  because it needs SDL's device.
- A software trace costs what the shaders cost on the window's GPU, measured
  by `:gpu:rayTraceProbe` beside the hardware tier, with no bound in a test
  (ADR-0560). Medians, the probe's 132,450-triangle scene, 2026-10-25:

  | | GTX 1660 Ti, hardware | GTX 1660 Ti, software | lavapipe, hardware | lavapipe, software |
  |---|---|---|---|---|
  | build | 67 ms | 175 ms | 301 ms | 148 ms |
  | update, 201 instances | 0.8 ms | 0.5 ms | 7 ms | 0.5 ms |
  | 1080p shadow mask | 7 ms | 14 ms | 605 ms | 660 ms |
  | 4K shadow mask | 26 ms | 41 ms | 2.16 s | 2.11 s |
  | 1080p lighting into textures, 12 occlusion rays and reflections | 58 ms | 53 ms | 4.66 s | 3.96 s |

  The 1660 Ti has no ray-tracing cores, so its hardware tier runs on the shader
  cores too, and is twice as fast on a plain shadow pass and slower on the
  lighting, which it copies back and the frame uploads. On lavapipe the
  "hardware" tier is the CPU emulating pipelines, and the software tier is as
  fast or faster: what `AUTO`'s choice rests on. Both tiers hit the same
  pixels but for 8 of two million at 1080p and up to 29 of eight million at
  4K, rays along edges.
- A trace that returns records at 4K makes a 199 MB transfer buffer each time,
  which on the 1660 Ti's software tier ran from 173 ms to 1.6 s. A renderer
  asks for the mask or traces into textures instead.
- `RayTracer.device()` returns `RayTracing.Available` instead of
  `RayTracing.Hardware`, and `RayTracing.isAvailable()` is true for both
  tiers. Both are unreleased, and are recorded among the breaking changes.
- The include is not offered to applications' own shaders, which was D2's
  second half. It could be later, on top of the same BVH, with nothing taken
  back.
- The three shaders' DXIL was compiled here with DXC 1.9.2609, which signs it;
  the unchanged shipped DXIL was restored from the macOS build, and the four
  new hashes (three shaders and the include) were added to
  `shaders.properties` by hand.
