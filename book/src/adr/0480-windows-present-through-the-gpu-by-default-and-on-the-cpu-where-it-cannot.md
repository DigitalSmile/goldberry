# 480. Windows present through the GPU by default, and on the CPU where it cannot

Date: 2026-09-24

## Status

Accepted. It supersedes ADR-0479's default policy, and ADR-0002's promise that
an application without GPU content never touches a driver. Rasterization is
unchanged: the UI is still painted by Blend2D on the CPU (ADR-0002). Only the
last step changes, how the painted frame reaches the screen.

## Context

ADR-0479 built the composited window and left it off by default:
`goldberry.gpu.composite=auto` composited a window only when a GPU layer needed
one, and there are none before phase 4. That followed `docs/gpu-plan.md`'s D2
and ADR-0002: no driver is loaded until something needs one.

The measurements since say the GPU path is the better default where it works.
On this Mac (M1 Pro, 120 Hz, the showcase's 240-frame run):

| | first frame on screen | device | paint mean | late |
|---|---|---|---|---|
| through the GPU | 1016.9 ms (median of 3) | 19.5–21.3 ms at the first frame | 3.67 ms | 10 |
| through the window surface | 1013.4 ms (median of 3) | none | 5.34 ms | 13 |

- The first frame reaches the screen about 3.5 ms later, which is about 0.3%.
- Frames paint faster, because the frame loop's own buffer replaces SDL's
  window surface.
- A present costs less CPU than `SDL_UpdateWindowSurfaceRects` (phase 0,
  §4.1).

And the path fails safe. Every way it can fail already ends with the window
presenting on the CPU, as it did before there was a GPU path.

## Decision

**Composite every window by default, and present on the CPU wherever the GPU
cannot be used.** Log both: the outcome for every window, and why when it is
the CPU.

- `goldberry.gpu.composite` defaults to `always`. `auto` keeps its meaning,
  composite only for GPU layers, and `never` and `goldberry.gpu=off` still keep
  every window on the CPU. A value that is not understood is the default.
- **Fallbacks.** Each is logged once, where it is known:

  | What happens | Where it is logged | Level |
  |---|---|---|
  | the backend starts | the policy, and how to change it | INFO |
  | no `:gpu` on the module path | the backend: "add goldberry-gpu" | INFO |
  | the device is made | the compositor: driver, formats, time | INFO |
  | no device can be made | the compositor: why, and the two properties | WARN |
  | a window is claimed | the window: "presents through the GPU" | INFO |
  | a window is refused | the window, with the compositor's reason | INFO |
  | a page is embedded in a composited window | the window, from now on | INFO |
  | a composited present fails | the window, with the exception | WARN |

  A claim now answers with a sealed `Claim`, either `Claimed(window)` or
  `Refused(reason)`, so the window's line carries the driver's own reason rather
  than a generic one.
- **Popups are not composited.** They are transparent windows, which SDL will
  not claim, and asking would tear a menu's surface down and rebuild it, only to
  be refused.
- **The device** is made at the first frame of the first window, not before, and
  its creation is on the start-up timeline ("GPU device created").

## Alternatives considered

- **Keep `auto` as the default until GPU layers exist.** What ADR-0479 did. It
  keeps ADR-0002's promise, but then the composited path runs only when someone
  asks for it, which is the reason it has run so little. It would also first
  meet real users only once layers exist, with two new things arriving at once.
- **Decide per platform.** Composite by default only where it has been
  measured, which is macOS. That would be more conservative, but every other
  platform already falls back per window with a logged reason, and a default
  that differs by platform is one more thing to explain. Where the GPU lane or a
  user finds a platform that composites badly rather than failing,
  `goldberry.gpu=off` is the switch, and the default can be narrowed then.

## Consequences

- Every application with `goldberry-gpu` on its module path loads a GPU driver
  at its first frame. One without it is unchanged, and says so once at INFO.
- The composited path now runs by default on this Mac for the showcase, the GPU
  tests and anything else with `:gpu` on its path. The CPU path stays covered by
  every backend test in `:core`, which has no `:gpu`, and by
  `goldberry.gpu=off`, which `CompositedBackendTest` drives.
- **Untried platforms.** Wayland, X11 and Windows now take the GPU path by
  default without having been tried. A refused claim or a failed device falls
  back cleanly. What would not fall back by itself is a platform that claims,
  presents without error, and shows the wrong thing. The GPU lane and the first
  runs there are what find that.
- **What a first frame costs:** about 20 ms of device creation, measured here.
  ADR-0002's millisecond start-up claim is about painting, which is unchanged.
