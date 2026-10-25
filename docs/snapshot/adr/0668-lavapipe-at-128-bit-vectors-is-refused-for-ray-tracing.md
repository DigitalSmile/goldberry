# ADR-0668: Lavapipe at 128-bit vectors is refused for ray tracing

- **Status:** Accepted
- **Date:** 2026-10-25
- **Relates to:** ADR-0666 (rays on a Vulkan device of the toolkit's own), ADR-0667 (scene updates, compaction, the packed shadow mask)

## Context

Snapshot 76 was the first CI run of the ray tracer on lavapipe, and it
answered `docs/gpu-gaming-plan.md`'s phase 0 question. Both runner legs'
lavapipe (Mesa 25.2.8 from noble's updates) lists `VK_KHR_acceleration_structure`,
`VK_KHR_ray_tracing_pipeline`, `VK_KHR_ray_query`,
`VK_KHR_deferred_host_operations` and `VK_KHR_buffer_device_address`. The x64
leg passed every ray-tracing test, with "required" on.

The arm64 leg's test JVM died with SIGSEGV in its first test. The faulting PC
was in code LLVM compiles at run time, on one of lavapipe's worker threads.
Another project reported the same crash on the same platform
([aether-lang-dev/aether#2562](https://github.com/aether-lang-dev/aether/issues/2562)),
at the same page offset (`…ea3c`), and traced it to lavapipe's
acceleration-structure build at 128-bit vectors. Every arm64 machine runs
llvmpipe at 128 bits, and an x86 one does under `LP_NATIVE_VECTOR_WIDTH=128`.

Reproduced here on x86 with Mesa 26.0.8, so a newer Mesa does not fix it:

- with `LP_NATIVE_VECTOR_WIDTH=128`, `RayTracerTest` crashes the JVM;
- opening a tracer (device and pipeline) does not crash; a build alone does;
- no build flag avoids it: `PREFER_FAST_TRACE`, `PREFER_FAST_BUILD`, with and
  without `ALLOW_COMPACTION`, and none at all all crash;
- 64 times the scratch the driver asks for, plus 16 MB, still crashes, at a
  wild address. The fault is not a scratch size we could pad.

A crash in the driver takes the whole application down. The device probe
answered "Hardware" for such a device, so an application that asked first
and then built a scene would still die.

## Decision

**`RayDevice` refuses lavapipe at 128-bit vectors.** Lavapipe states its width
in its device name (`llvmpipe (LLVM 20.1.2, 128 bits)`), and
`RayDevice.buildsCrash(name)` matches it. Such a device is not usable, and its
refusal says why, so `RayTracer.availability()` answers `Unavailable` with
that sentence rather than letting a build crash. Lavapipe at 256 bits is
taken as before.

`linux.yml`'s "Ray tracing on lavapipe" step reads the device name from
`vulkaninfo` too. Where it says 128 bits, it leaves a notice and does not
require the tests, which then skip with the probe's reason. The x64 leg still
requires them.

## Consequences

- An arm64 Linux machine traces rays only on a GPU whose driver has the
  extensions; on the CPU it is refused. That machine had no working ray
  tracing before either; now it is told so instead of crashing.
- The arm64 CI leg runs no ray-tracing test. Its lavapipe is reported, so a
  Mesa that fixes the build is noticed in the log, and the refusal is lifted
  then, by Mesa version.
- Nothing has been filed with Mesa from here.
- Beside it, Snapshot 76's other failures. Two were the ray tracer's own:
  `RayTracerTest` closed a tracer it never opened when it skipped, which
  failed every skip on the Java, macOS and Windows jobs, and Qodana 80 found
  six owned resources, a list lookup and two log lines. One was older:
  Windows refused `AssetCache`'s move of a fresh download over an archive
  another build had open, and the cache now keeps that archive when it is the
  right bytes. All fixed without a decision.

## Alternatives

- **Skip the step on arm64 in the workflow alone.** CI would be green, but an
  application on arm64 would still crash at its first build.
- **A Mesa version check.** The device name already carries what matters, and
  no fixed version exists yet to compare against.
- **Building the structures with host commands** (`accelerationStructureHostCommands`).
  It would be a second build path for one driver, and whether lavapipe's host
  build has the same fault was not tried.
