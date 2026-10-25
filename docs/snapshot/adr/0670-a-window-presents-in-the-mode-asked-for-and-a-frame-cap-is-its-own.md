# ADR-0670: A window presents in the mode asked for, and a frame cap is its own

- **Status:** Accepted
- **Date:** 2026-10-25
- **Relates to:** ADR-0669 (indirect draws, the stencil test and GPU copies), ADR-0480 (windows present through the GPU by default), ADR-0560 (no clock bounds in tests)

## Context

`docs/gpu-gaming-plan.md`'s phase 2 asks for the settings a game's options
screen changes at run time: vertical sync and a frame-rate limit. It also asks
for a record of what the device can do, so that a choice the device lacks is
never offered.

What was there:

- `goldberry.backend.vsync=false`, read **once at launch**. The compositor set
  MAILBOX or IMMEDIATE when it claimed a window, and the sdl3 backend left the
  window surface's renderer unpaced. Nothing changed it afterwards.
- One `FramePacer` for the whole loop, paced to the fastest display any window
  is on. It held every window to one refresh, whatever the window presented
  in, so a window in MAILBOX was still painted at most once a refresh.
- No frame cap of any kind except `goldberry.frame.rate`, which is per process
  and set at launch.
- `SdlGpuWindow.setPresentMode` existed, and nothing outside the compositor's
  claim called it.

## Decision

**`PresentMode` (`VSYNC`, `MAILBOX`, `IMMEDIATE`) belongs to a window, as a
request and as the mode in effect.** `Window.presentMode(mode)` asks;
`Window.presentMode()` answers with what is in effect. A composited window
sets its swapchain to the mode asked for, or to the nearest of
`PresentMode.fallbacks()` the driver has: MAILBOX falls back to IMMEDIATE and
then VSYNC, and IMMEDIATE to MAILBOX and then VSYNC. A window on the CPU keeps
the request and presents as its window surface was set up to at launch, which
is VSYNC, or IMMEDIATE under `goldberry.backend.vsync=false`. It applies the
request when a claim moves it to the GPU. The claim is made in VSYNC, and the
window then sets its mode. The compositor no longer reads the property, and
`goldberry.backend.vsync=false` now means "a window opens asking for MAILBOX".

**The frame loop follows the mode in effect.** A window in VSYNC waits for the
display's pacer, as every window did. A window in MAILBOX or IMMEDIATE does not
wait for it: it is painted as fast as it asks, and only its cap holds it.
The pacer is stamped only when a window that waits for it got a frame.

**A frame cap is per window.** `Window.frameCap(hertz)` (zero lifts it) is
held by a `FrameCap` in the sdl3 backend, which is pure and clock-injected like
`FramePacer`. A window is due when the display wants a frame, if it waits for
the display, and when its cap allows, whichever is later
(`FramePacer.isDue(now, cap, waits)`). The loop's wait is shortened to the
first held frame (`capWait`), and lateness is counted from when the cap lets a
frame go, so a capped frame is held rather than late. A cap above the display's
rate changes nothing for a window in VSYNC. The headless backend keeps both
settings and paces nothing.

**The `hud` shows and judges by them.** `FrameStats` gains `presentMode()`,
`frameCap()` and `budgetHertz()`: the display's rate, or the cap's where that
is lower or where the window does not wait for the display. Every reading's
budget is a share of `budgetHertz()`, so a window capped at 30 judges its paint
against 16.7 ms rather than 8.3. A new reading, `pacing`, shows `pacing vsync`
or `pacing mailbox, cap 144`, uncoloured.

**`GpuDevice.features()`** answers with a `GpuFeatures` record, asked once and
kept. It holds the driver, the adapter, the shader formats, the colour and
depth sample counts, the depth formats with a stencil, the compressed formats
sampled, and whether half-float targets render. Ray tracing is not in it, which
changes D4 of the plan: `RayTracer.availability()` opens a Vulkan device of its
own on first ask, which nobody wants to pay for in order to read sample counts.
`dev.goldberry.gpu.raytrace` also depends on `dev.goldberry.gpu`, and the
record would have closed a cycle between the two packages.

Found by the first lavapipe run: **sample counts are not contiguous.**
Lavapipe gives a colour target 1, 4 and 8 samples and no 2. `GpuFeatures`
takes an ascending list from 1, with gaps, and a settings screen offers exactly
those counts.

## Consequences

- An options screen can switch vsync and the cap while the game runs, offer
  only the anti-aliasing the device has, and offer stencil effects only where
  there is a stencil format.
- `BackendWindow`, `CompositedWindow` and `FrameStats` gain default methods,
  so their other implementations compile unchanged. `Reading` gains a
  constant, so an exhaustive `switch` over it gains a case, as the breaking
  changes say.
- A window presenting in MAILBOX with no cap paints as fast as the GPU takes
  frames. That is the point of the mode, and a battery drain if it is the
  default. It is not the default, and `goldberry.backend.vsync=false` keeps
  meaning "unpaced, for measuring".
- Where the window surface presents on the CPU nothing changes at run time.
  `SDL_SetWindowSurfaceVSync` could switch it, and it is not exported, because
  a game that cares presents through the GPU.
- Tests: `FrameCapTest` (6), the pacer's ported `capWait` tests, `PresentModeTest`,
  `FrameRingTest`'s pacing, `WindowPacingTest` (headless), the `pacing` reading
  and capped budgets in `HudTest`, `GpuSpecsTest`'s features, and on the GPU lane
  `GpuGameFeaturesTest$Features`, `CompositorTest`'s present modes and
  `CompositedBackendTest`'s end-to-end switch and CPU fallback. All green on
  lavapipe and the GTX 1660 Ti. No test measures a rate against the clock
  (ADR-0560); every pacing decision is checked on stamps.

## Alternatives

- **One loop-wide present mode and cap.** The pacer is loop-wide, and two
  windows of one game rarely want different modes. But a tool window beside a
  game would be dragged to the game's rate, and the swapchain is per window
  anyway.
- **Refuse a mode the driver lacks.** An options screen would have to ask
  first and handle a refusal for a setting the player chose. The fallback
  order is the one every engine uses, and the window says what it got.
- **Put the cap in `FramePacer`.** A per-window interval inside a loop-wide
  pacer would have meant a map in the pacer; a field on the window keeps the
  pacer what it was.
