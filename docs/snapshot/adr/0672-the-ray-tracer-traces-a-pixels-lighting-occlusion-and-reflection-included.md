# ADR-0672: The ray tracer traces a pixel's lighting, occlusion and reflection included

- **Status:** Accepted
- **Date:** 2026-10-25
- **Relates to:** ADR-0666 (rays are traced on a Vulkan device of the toolkit's own), ADR-0667 (shadows come back a byte a pixel), ADR-0673 (the showcase's ray-traced room)

## Context

`docs/gpu-gaming-plan.md`'s phase 7 asks for a scene with ray-traced shadows,
reflections and ambient occlusion. The hardware tier (ADR-0666) traced a
primary ray a pixel and a shadow ray from each hit, and answered with hit
records or a packed shadow mask. Occlusion and reflection need what it did not
have: the surface's normal at a hit, to aim rays around it and off it.

The normal is not to be had from the acceleration structure on every device.
`VK_KHR_ray_tracing_position_fetch` hands a hit shader its triangle's vertices,
and lavapipe has it; the GTX 1660 Ti does not. The meshes' vertex buffers were
freed once their structures were built (ADR-0666).

## Decision

**A scene keeps its triangles' normals.** On build, `AccelerationScene` uploads
every distinct mesh's face normals in object space, one `float4` a triangle,
the meshes one after another (`FaceNormals`), and for each instance where its
mesh's normals start, rewritten with the instance records on every update. The
two are storage buffers at bindings 2 and 3 of the pipeline's one descriptor
set, read by the closest-hit shader. It turns the normal into world space with
the inverse transpose of the instance's transform, `WorldToObject3x4()`, so a
scaled instance's normals stay perpendicular. 16 bytes a triangle and 4 an
instance; no extension.

**A third output, the lighting.** `RayTracer.traceLighting(scene,
LightingRequest)` traces, from each primary hit:

- the shadow ray toward the light, as the other outputs do;
- `occlusionRays` rays, cosine-weighted over the hemisphere the hit faces,
  `occlusionRadius` long, and counts how many escape;
- one reflection ray off the hit. Where it hits something, a shadow ray from
  there gives the cosine to the light at the surface it sees, or 0 in shadow.

The normal is turned to face the ray, so a triangle seen from behind is lit
from the side the ray sees. Every ray is traced from the ray-generation shader,
so the recursion depth stays 1.

**What comes back is two planes, laid out for two textures** (`Lighting`), 8
bytes a pixel:

- `R8G8B8A8_UNORM`: lit, openness, the reflected surface's light, and whether
  the primary ray hit.
- `R32_FLOAT`: the reflected instance's id, or -1. `TextureFormat` has no
  integer formats, and a 24-bit id is exact in a float.

The renderer's shader looks up what the reflected surface looks like from the
id. The tracer knows no materials, and does not learn any.

**The occlusion rays are drawn from a fixed sequence**: Chris Wellons'
lowbias32 hash of `pixel * 64 + ray`, in the top 24 bits of two hashes. A still
scene lights the same on every trace, and a reference in Java draws the same
rays. Up to 64 rays a hit.

**The push constants grow to 112 bytes**: the ray count, the radius and the
reflections switch, after the output. The other outputs write zeros there.

**Tested against the double-precision reference**, as the primary and shadow
rays are. `LightingReference` traces each pixel's rays brute-force with
`ReferenceTracer`, which now answers a triangle's normal, and with the
shader's sequence ported to Java. For every pixel whose rays all keep clear of
an edge it asserts equality: hit, lit, the openness byte, the reflected id and
the reflected light within a level and a half. It passes on the GTX 1660 Ti
and on lavapipe. A one-bit change to the hash's constant fails it. A second
test holds the lighting's shadow to the shadow mask's, pixel for pixel, and
checks that occlusion and reflections are absent when not asked for.

## Consequences

- A renderer gets shadows, ambient occlusion and one bounce of reflection in
  one call and two uploads, at 8 bytes a pixel across the bus.
- A lighting trace is up to `4 + occlusionRays` rays a pixel. At 12
  occlusion rays it is noisy, as one sample a frame of any ray-traced
  occlusion is. Nothing denoises it; a renderer that wants it smooth blurs it,
  or accumulates still frames.
- The reflection is the surface it sees and that surface's direct light, not
  its occlusion or a second bounce. A mirror shows a mirror as its colour.
- Bindings 2 and 3 are bound for every output, because the closest-hit
  shader reads them whatever the dispatch writes.
- Not done: a reflection off a smooth mesh's interpolated normals. The tracer
  has face normals only, so a sphere's reflection is faceted where its raster
  picture is smooth.
