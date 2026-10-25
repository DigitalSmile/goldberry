# ADR-0673: The showcase has a ray-traced room, and a game's graphics settings

- **Status:** Accepted
- **Date:** 2026-10-25
- **Relates to:** ADR-0672 (the lighting trace), ADR-0671 (render scale and FSR 1), ADR-0670 (present mode and frame cap), ADR-0669 (indirect draws, stencil, copies), ADR-0665 (the showcase's model)

## Context

`docs/gpu-gaming-plan.md`'s phase 7 is the showcase chapter that puts the
plan's pieces in one place:

- a scene with ray-traced shadows, reflections and ambient occlusion, its HLSL
  the showcase's own;
- a settings panel with ray tracing (off, software, hardware, disabled with
  the device's reason), render scale and upscaler, MSAA, anisotropy, and the
  present mode and frame cap, all live;
- goldens on lavapipe, and the unavailable state on every lane.

The GPU screen is pinned by goldens: `gallery-gpu` (no GPU, on every lane),
`gallery-gpu-drawn` (on the GPU), and the sidebar's card counts appear in
`shell-home` and the book's screen pictures. CI's two GPU legs differ: x64
lavapipe traces rays, and arm64's is refused (ADR-0668).

## Decision

**A new card, "Ray tracing and settings", on the GPU screen** (`RayTracedRoom`
in `ui.gpu`). It holds a continuous `canvas3d`, 460px tall, and the panel
under it in two columns: how the picture is lit and made, then how it is
drawn and shown. It is a `WideCard`, so the wall deals it into its wide wall
under the other cards, the whole width beside the sidebar. A stateful card's
`ShowcaseCard.wide()` alone is not seen by the wall, which cannot look inside
it. The
scene is a package of its own, `dev.goldberry.example.gpu.scene`:

- `RoomScene`: a checkered floor, a red block, a mirrored cube that turns, a
  pillar, a ball, a small cube that circles them, a low sun and one camera.
  Each solid's instance id is its material's index, so a reflected id names a
  colour.
- `Mesh`: the room's triangles, non-indexed, as a vertex buffer and as a
  `TriangleMesh`.
- `SceneSettings`: what the renderer reads each frame.
- `RoomRenderer`: the `Canvas3dRenderer`.

Its shaders, `room.vert` and `room.frag`, are the showcase's own.

**The renderer.** Each frame it places the solids for the frame's time. With
ray tracing on, it opens a `RayTracer` on first use (on the UI thread, which
then owns it), builds the scene once and updates it every frame after. It then
traces the lighting (ADR-0672) at the size the canvas draws at. That ties the
render scale (ADR-0671) to the trace's cost: at 0.5 a quarter of the rays.
It uploads the two planes and draws. The fragment shader reads its own texel,
`SV_Position`, because the raster camera (`RoomScene.viewProjection`) and the
traced one (`RoomScene.camera`) see through the same pixel centres; a test
holds them to that. Off, the room is lit by the sun's angle and an ambient
term, from two 1×1 textures that say lit, open, nothing reflected. The
floor's checks are mipmapped and read with the panel's anisotropy. Anti-
aliasing draws into multisampled colour and depth and resolves into the
canvas's texture, at the most samples up to the one asked that the device has
for both, from `GpuDevice.features()`.

**Everything the panel shows by default is the same on every machine.**
Ray tracing starts off, and asking `RayTracer.availability()` waits until
someone chooses Hardware: a probe at build would make the default picture
depend on the machine's Vulkan, and cost a device's creation at startup. Where
it answers unavailable, Hardware is disabled and the reason is shown under the
panel; where the renderer's own open fails later, it says so the same way,
once. Anti-aliasing (off, 2×, 4×, 8×) and anisotropy (off, 4×, 16×) are offered
at fixed counts rather than the device's, for the same reason, and the
renderer takes what the device has.

**Software ray tracing is not offered.** It is phase 3, not built; the
segment comes with it.

**Render scale** is a select of native and FSR 1's four quality modes, with
the upscaler (bilinear or FSR 1) beside it: the canvas's `renderScale`.
**Present mode and frame cap** go to `host.window()`. An offscreen picture's
host has no window and throws; the panel keeps the choice and applies it
where there is a window.

**Tests.**

- `RoomSceneTest`, no device: the settings; every mesh triangle
  counter-clockwise from outside with its normals on its face's side; ids that
  are materials; what moves; and the one-camera check, which projects scene
  points through the raster matrices and asserts the traced ray of the pixel
  they land in passes within a pixel of them.
- `GpuChapterTest`, no device: the card links its section; the upscaler bar
  reaches the canvas's render scale; with `goldberry.raytracing=off`, choosing
  Hardware shows the reason and disables the segment (the unavailable state,
  on every lane); the other bars keep their choices with no window.
- `RoomRendererTest`, on the GPU lane: `gpu-room`, the untraced room, against
  its lavapipe golden on any driver; the traced room lit by the tracer and
  darker than the untraced one, against `gpu-room-traced` where the tracer is
  lavapipe; the refusal under `goldberry.raytracing=off`, said once, drawing
  the untraced golden; and 4× MSAA in use and changing the silhouettes.

**The room's tolerance** is written beside its test: `Tolerance.GPU` with 3
levels a channel, not 2. Its floor is read trilinearly, and drivers filter
between mip levels at different precisions: the GTX 1660 Ti is three levels
from lavapipe on 175 texels of the floor, and nowhere else further than two.
The traced golden is compared on lavapipe alone. On another device an
occlusion ray that grazes an edge may go the other way, which moves a pixel by
a twelfth of its light.

## Consequences

- The plan's settings can be tried together: tracing at a render scale with
  FSR 1 bringing it up, MSAA beside it, mailbox and a cap on the window.
- The GPU screen has 5 cards and the showcase 248. `gallery-gpu`,
  `gallery-gpu-drawn` (lavapipe), `shell-home`, the search index and the
  book's `screen-forms-{light,dark}.webp`, which show the sidebar's counts,
  were taken again. The rest of the gallery is unchanged.
- **CI does not run `:example:gpuTest`**: the GPU lane runs `:natives` and
  `:gpu`'s. The room's GPU goldens, like `gallery-gpu-drawn` before them, are
  checked locally. Running them in CI is one more task on that step, and is
  left for when the lane's time allows.
- Tracing runs on the UI thread and returns when the GPU is done, so a traced
  frame costs its rays and its copy. The lighting trace's cost is not measured
  yet; `:gpu:rayTraceProbe` measures the primary and shadow rays alone.
- `Matrices` in `dev.goldberry.example.gpu` is public now, with `lookAt` and
  `scale`, for the scene's package.
