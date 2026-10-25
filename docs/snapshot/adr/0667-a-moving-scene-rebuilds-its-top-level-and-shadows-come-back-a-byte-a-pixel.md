# ADR-0667: A moving scene rebuilds its top level, and shadows come back a byte a pixel

- **Status:** Accepted
- **Date:** 2026-10-25
- **Relates to:** ADR-0666 (rays on a Vulkan device of the toolkit's own), ADR-0560 (no clock bounds in tests)

## Context

ADR-0666 built the hardware ray tracer and left three costs open in
`docs/gpu-gaming-plan.md`'s phase 4:

- **Every trace copied 24 bytes a pixel back.** A renderer that wants only the
  light still got the full hit records, 50 MB at 1080p, and reduced them to a
  mask in Java. Measured with `:gpu:rayTraceProbe` (132,450 triangles, primary
  and shadow rays), copying the records into the Java heap was half of a
  1080p trace on the GTX 1660 Ti.
- **A moved instance meant a new scene.** `RayScene` could not change, so a
  frame with one moving object built every mesh's structure again: 28 ms on
  the 1660 Ti and 300 ms on lavapipe, mostly for the 131,000-triangle
  height field, which had not moved.
- **Structures were kept at their build size.** Drivers build a bottom level
  with room to spare and can copy it into a tighter one.

## Decision

**A scene's instances move by rebuilding only the structure over them; the
meshes' structures are compacted once built; and `RayTracer.traceShadows`
packs the shadow mask on the GPU.**

- **`RayScene.update(geometry)`** places other instances of the meshes the
  scene was built with, by identity. They can be moved, renumbered, or more
  or fewer than before. The top level is **rebuilt**, with
  `VK_BUILD_ACCELERATION_STRUCTURE_MODE_BUILD_KHR`, not updated in place
  (`MODE_UPDATE`). The rebuild reuses the top level's instance buffer, scratch
  and storage, and grows them when the count needs more. A mesh the scene was
  not built with is refused with `IllegalArgumentException`, and the scene is
  left as it was. `Instance.withTransform` and `withId` build the moved
  instances.
- **Bottom levels are compacted**, always. They are built with
  `ALLOW_COMPACTION`, and their compacted sizes are read through a
  `VK_QUERY_TYPE_ACCELERATION_STRUCTURE_COMPACTED_SIZE_KHR` query pool
  (`CompactedSizes`). The second submission copies each one into a structure
  of that size with `MODE_COMPACT`, then builds the top level over the copies.
  The structures as first built are freed. `RayScene.structureBytes()` says
  what the scene holds.
- **`RayTracer.traceShadows(scene, request)`** traces the same rays as
  `trace` and returns a `ShadowMask`: a byte a pixel, rows with no padding,
  `0xFF` where the light reaches or the ray hit nothing, 0 in shadow. One
  ray-generation shader serves both outputs, picked by an `output` push
  constant (`RayConstants` is 100 bytes now). For the mask,
  `vkCmdFillBuffer` zeroes the buffer, newly bound in `natives.vulkan`, and
  each lit pixel `InterlockedOr`s its byte into the word it shares with three
  others. A request without a light is refused. `Hits.shadowMask()` returns
  the same type, made from the records.
- **The output goes through a `RWByteAddressBuffer`**, so records and masks
  use one binding, and `Store<HitRecord>` writes a record whole.

## Consequences

Measured with `:gpu:rayTraceProbe` on 2026-10-25, medians. The machine was
loaded and the same row varied by 50% between runs, so these are orders of
magnitude, not budgets:

| | GTX 1660 Ti | lavapipe |
|---|---|---|
| build, compacted | 32–50 ms (28 ms uncompacted, ADR-0666's run) | 280 ms |
| meshes' structures, built → compacted | 11.9 → 9.6 MB | 13.5 → 13.5 MB |
| update, 201 instances moved | 0.7–1.1 ms | 7 ms |
| 1080p, records copied out | 30–33 ms | 616 ms |
| 1080p, mask from the records in Java | 4–6 ms more | 3.4 ms more |
| 1080p, mask packed on the GPU | 8–9 ms | 600 ms |
| 4K, records copied out | 129 ms | 2.22 s |
| 4K, mask packed on the GPU | 26–28 ms | 2.08 s |

- A frame of a moving scene costs about a millisecond on the GPU here, not a
  rebuild of every mesh. A deforming mesh (skinning, cloth) still means a new
  scene. Refitting a bottom level is not built.
- At 1080p the light alone costs a quarter of what the records cost on the
  1660 Ti. On lavapipe the rays dominate, so the copy saving is small there.
- Compaction saved 19% on NVIDIA and nothing on lavapipe. It adds a
  submission and a host wait to every build, which is a few milliseconds of
  the build on the 1660 Ti.
- `Hits.shadowMask()` returns a `ShadowMask` rather than a `ByteBuffer`. It was
  never released.
- The Khronos validation layer is not installed on this machine. The barriers
  and the query usage were checked against the specification, and the results
  against the double-precision reference on both devices, not by the layer.

## Alternatives

- **Updating the top level in place** (`ALLOW_UPDATE`, `MODE_UPDATE`). It is
  cheaper in theory, but the count cannot change, the structure gets worse as
  instances move away from where it was built, and the flag can slow tracing.
  A rebuild over a few hundred instances is already about a millisecond.
- **Four pixels per ray generation, one word each, with no atomics or
  fill.** This was built first. It tied on the 1660 Ti (7.7 ms at 1080p) and
  was 18% slower on lavapipe (709 ms), slower than copying the records,
  because lavapipe runs each thread's four rays in lockstep. It also had to
  pad each row to a word.
- **Compaction as an option.** No game asks for bigger structures, and the
  cost is a few milliseconds once a scene, so it is always on.
