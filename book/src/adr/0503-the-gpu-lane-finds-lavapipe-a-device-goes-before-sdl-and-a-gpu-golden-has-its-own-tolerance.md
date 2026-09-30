# 503. The GPU lane finds lavapipe, a device goes before SDL does, and a GPU golden has its own tolerance

Date: 2026-09-30

## Status

Accepted. Repairs the GPU lane `docs/gpu-plan.md` phase 2 wrote before it could
be run. Adds a second tolerance beside the one
[ADR-0050](0050-golden-images-have-a-tolerance.md) set, for pictures a GPU
draws, and leaves every existing golden on the first. Corrects the record in
[ADR-0495](0495-media-is-published-and-snapshots-publish-again.md) and in
`book/src/TODO.md` that the lane "has never run": it had run twice, and failed
both times before a single GPU test.

## Context

The lane in `linux.yml` installs Mesa's lavapipe, points the Vulkan loader at
it alone, and runs `:natives:gpuTest` and `:gpu:gpuTest` with a device
required. It was written without a host to run it on, and its comment said its
first run would be its first test.

It had two first runs. In Snapshot runs 32 and 33 both Linux legs stopped at
the lane's own first check, `no lavapipe ICD in /usr/share/vulkan/icd.d`
(the runs' public annotations). The runners' red was put down to ADR-0495's
teardown failures in the Java jobs, which were real and were in a different
job; nothing had looked at this one.

Running the lane here, on lavapipe, answered the rest in three steps.

1. **The glob.** Mesa's packaging names the file `lvp_icd.json` now; older
   releases wrote `lvp_icd.x86_64.json`, and the lane globbed only for
   `lvp_icd.*.json`. This machine's Mesa 26 has the new name.
2. **A crash behind it.** With the ICD found, `:gpu:gpuTest` took the JVM down
   in `VULKAN_DestroyDevice`, from `GpuLayerBackendTest`'s `@AfterEach`. That is
   the crash `docs/refactor-2026-09-30.md` §4 and ADR-0491's commit recorded as
   this machine's NVIDIA driver's. It is not the driver's. The test made a
   device in `@BeforeEach` to prove one could be made, kept it, and closed it
   after the test body — where an `Sdl3Backend` had already been closed, and
   `Sdl3Backend.close()` ends in `SDL_Quit`. Its own comment says a device has
   to be destroyed ahead of that. Every driver would have crashed; the one here
   was the only one anybody had run.
3. **A golden that cannot hold across drivers.** With the crash gone, 66 of 67
   passed on lavapipe. `canvas3d-cube`, blessed on Metal, drew two of its
   32,000 pixels 157 levels apart: silhouette pixels, covered on one driver and
   not on the other. On NVIDIA's Vulkan driver the same golden differed by one
   level on 52% of its pixels — the lit faces, rounded differently — and by
   nothing more. The six z-order goldens, which are screen-aligned quads, matched
   on both.

## Decision

**The lane accepts both spellings of the ICD**, `lvp_icd.json` first.

**A GPU test proves a device can be made and lets it go at once.**
`GpuLayerBackendTest` closes the device `GpuDeviceRequirement.enforce()` gives
it inside `@BeforeEach`, keeps SDL's video initialised for the headless
backend, and quits SDL in `@AfterEach` only if it reached SDL. The backends
make their own devices and destroy them before their own `SDL_Quit`.

**A picture a GPU rasterized and shaded is compared under
`Tolerance.GPU`, and nothing else is.** A golden's tolerance is now a value,
`Tolerance(channel, differing, stray)`:

| | channel | differing | stray |
|---|---|---|---|
| `RASTER` — every Blend2D golden, and every golden before this | 2 | 2% | 0 |
| `GPU` — `canvas3d-cube` | 2 | 100% | 0.1% |

`stray` is the share of pixels allowed beyond `channel`. `RASTER` is exactly the
rule ADR-0050 wrote, restated: nothing beyond two levels and at most 2% within
them, because Blend2D's coverage is analytic and only antialiased edges round
differently. Both halves of that reasoning fail on a GPU, in opposite
directions. A fragment shader's arithmetic is not specified to the last bit, so
a lit fill may round a level apart on every pixel. And Vulkan, Metal and D3D12
each leave the tie-break for a sample exactly on an edge, and the sub-pixel
precision a vertex snaps to, to the implementation — so an aliased edge pixel
can flip, and a flip moves it by the edge's whole contrast. One pixel in a
thousand may. A cube's silhouette is several hundred pixels, so a vertex in the
wrong place or a reversed winding still fails, and so does a colour that moved
a fill by tens of levels.

`GoldenImage.assertMatchesAtOneScale` gains an overload that takes a
`Tolerance`. The existing entry points use `RASTER`.

## Consequences

- The lane passes here, on lavapipe, as CI will run it: `:natives:gpuTest` 27
  of 28 with one skipped by its own condition, and `:gpu:gpuTest` 67 of 67, with
  `-Dgoldberry.gpu.required=true` and the `offscreen` driver.
- `:gpu:gpuTest` also passes on this machine's NVIDIA driver under
  `-Dgoldberry.gpu.videoDriver=x11`, 67 of 67. It was recorded as a known-red
  segfault. Under SDL's default Wayland driver here, `CompositorTest`'s claimed
  window and `GpuLayerBackendTest.autoCompositesForLayers` still fail with
  `This surface does not support presenting` and `VK_ERROR_SURFACE_LOST_KHR`:
  a Wayland surface the NVIDIA driver will not present to, which is a property
  of the session and not of the code.
- **`:example:gpuTest`'s `gallery-gpu-drawn` was stale, and not by a driver.** It
  failed identically on lavapipe and on NVIDIA — 344 pixels, all in one 20×20
  box — which two drivers do not do by rounding. The box is the status bar's
  presentation badge, which 5878e577 added and re-blessed into every gallery
  golden except this one, because only the GPU lane draws it and nobody ran the
  lane. It is re-blessed, with only that box changed, and stays under `RASTER`:
  its picture is Blend2D's except for the cubes, and the cubes match. The
  memory of it as "the same class as the cube" was wrong.
- Nothing about the lane on CI is proven until it runs there. It is answered by
  the next push, like the rest of ADR-0495.
- A golden that opts into `GPU` is weaker than one that does not, and the
  overload's documentation says a Blend2D golden has no business with it: a
  Blend2D edge does not flip, so admitting one that did would hide the
  regression the golden exists for.

## Alternatives considered

- **A golden per driver.** Exact on each, and three references that rot
  separately — ADR-0050's own reason for a tolerance, and worse here, because
  the drivers a user has are not the three a runner has.
- **Multisampling the test's render target** so edges stop being aliased. It
  would hide the ownership flips, and it would also stop the golden describing
  what `canvas3d` draws, which is single-sampled: `:gpu` has no multisampled
  target to ask for.
- **Widening `RASTER`** until the cube passed. Every Blend2D golden would then
  admit a pixel off by 157 levels, which is a regression, not a rounding.
