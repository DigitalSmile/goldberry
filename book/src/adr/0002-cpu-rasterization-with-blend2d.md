# ADR-0002: CPU rasterization with Blend2D

- **Status:** Accepted (recorded retroactively)
- **Date:** 2026-08-15
- **Relates to:** `docs/ARCHITECTURE.md` §1, §3, §5

## Context

A desktop UI toolkit has to choose what draws its pixels, and the choice sets the
floor on startup time, memory, and how many ways the toolkit can fail on a user's
machine.

GPU rasterization is the default assumption in modern toolkits, and for good
reason on animation-heavy or content-heavy UIs. But it buys that throughput with
a context: driver initialization on startup, a device-lost path that has to be
handled correctly, a shader pipeline to compile and cache, and a long tail of
machines where the driver is the reason the app does not start. For the UI
Goldberry targets — application chrome, forms, panels, text — the GPU is mostly
idle between frames, and the work it would accelerate is not the bottleneck.

Goldberry also targets GraalVM native-image as a first-class output and claims
millisecond startup. A GPU context is squarely at odds with that number.

## Decision

Rasterize the UI on the CPU with [Blend2D](https://blend2d.com), and open no GPU
context for plain UI. Blend2D's JIT-compiled pipelines and banded multithreading
make CPU rasterization fast enough that the GPU is not needed for this workload;
its built-in PNG/JPEG/QOI codecs remove an image-decoding dependency; and it
renders glyph runs directly from font outlines, which removes FreeType.

The GPU is not excluded — it is scoped. `BackendWindow.gpuSurface()` is in the
backend SPI from day 1 (§12), and a window containing a `canvas3d` switches to a
composition mode where the CPU-rasterized UI is uploaded as a texture and
composited with GPU content. Apps that want 3D get it; apps that do not, never
touch a driver.

## Alternatives considered

- **Skia.** The obvious comparison and a better rasterizer in raw capability.
  Rejected on footprint and build cost: it is an enormous dependency with a
  bespoke build system, and vendoring it into a single-shared-library
  distribution (ADR-0008) fights the toolkit's whole packaging story. Its
  strengths are mostly on the GPU path Goldberry is choosing not to take.
- **Cairo.** Rejected: slower, no JIT pipelines, and its threading story is poor.
- **A hand-written rasterizer.** Rejected: correct antialiased path filling with
  competitive performance is years of work, and it is not the project's point.
- **GPU-first (Vulkan/WebGPU) for everything.** Rejected: contradicts the startup
  and footprint goals, and turns every driver bug into a Goldberry bug.

## Consequences

- Startup is fast and failure modes are few: no driver, no context loss, no
  shader cache. The `headless` backend is nearly free, which is what makes
  deterministic golden-image tests possible on all three OSes (§14).
- Blend2D has no filter effects. Blur and frost must be implemented in Java — a
  three-pass separable box blur over downscaled layer copies, using the Vector
  API — and drop shadows have to be nine-slice cached (§5). This is real work
  that a GPU pipeline would have given us for free.
- Very large animating surfaces, and heavy compositing, will be slower than a
  GPU toolkit. Layers and damage tracking (§5) are the mitigation, and they are
  mandatory rather than optional as a result.
- Blend2D's JIT means executable-memory allocation at runtime. On Apple Silicon
  this needs W^X handling, and on hardened or JIT-restricted environments it may
  need a fallback. This must be verified in M0, not assumed.
