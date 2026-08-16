# ADR-0047: A frame nobody sees costs full price

- **Status:** Accepted
- **Date:** 2026-08-16
- **Relates to:** `docs/ARCHITECTURE.md` §5; [ADR-0024](0024-a-repaint-must-wake-the-loop.md), [ADR-0031](0031-blend2d-and-the-borrowed-buffer.md), [ADR-0045](0045-a-frame-is-not-a-benchmark-iteration.md), [ADR-0046](0046-what-present-actually-does.md)

## Context

[ADR-0046](0046-what-present-actually-does.md) took `present` apart and found
nothing in it worth tuning: 43 µs of Goldberry's code, ~1 ms of SDL copying the
frame into a texture, and ~4.8 ms blocked on the swapchain. It also found the
thing that was worth fixing, one level up — the loop was producing frames faster
than the display could show them:

> Paint plus present is ~9.5 ms, so the showcase runs at ~105 fps into a
> 59.96 Hz display: about two frames in five are painted, uploaded, and
> discarded.

`BackendWindow.requestFrame` has always documented itself as "vsync-aligned
where the platform offers it". It was not. `Sdl3Window.requestFrame` sets a flag
and wakes the loop ([ADR-0024](0024-a-repaint-must-wake-the-loop.md)); the next
pump emits `FrameDue` as fast as the previous frame finished. Nothing anywhere
consulted the display.

A discarded frame is the most expensive kind of waste available, because it costs
a whole paint *and* a whole present. Every other saving on the table — damage
tracking's ~1 ms, the 43 µs in `present` — is a fraction of one frame.

## Decision

Pace the frame loop to the display, by two mechanisms, because one of them
cannot be relied on.

**1. Ask SDL to hold each present until vertical blank.** Goldberry creates no
renderer, so `SDL_HINT_RENDER_VSYNC` looks like somebody else's setting. It is
not: where the video driver implements no window surface — Wayland is one —
`SDL_GetWindowSurface` falls back to a hidden `SDL_Renderer`, and every present
ends in *that* renderer's `SDL_RenderPresent` ([ADR-0046](0046-what-present-actually-does.md)).
The hint is the only channel that reaches it. Set before `SDL_Init`, on by
default, and `-Dgoldberry.backend.vsync=false` turns it off.

This is the correct fix, it costs one hint, and it needs the display to be real.

**2. A `FramePacer` in the pump, for when it is not.** The hint is accepted and
has no effect on this machine: the GL stack is VMware SVGA3D on LLVM, which does
not honour a swap interval. Virtualized drivers, `llvmpipe`, and compositors with
a deep swapchain all behave this way, and on any of them mechanism 1 is a no-op.

So `Sdl3Backend` holds `FrameDue` back until the frame is due, and — the part
that is easy to leave out — shortens its own `SDL_WaitEventTimeout` to match.
Without that second half the loop defers a frame and then sleeps in the event
wait until something unrelated arrives, which on an idle window is the event
loop's one-second heartbeat: the frame would be held for a second rather than
for the rest of its interval.

**3. The number comes from the display, not from a guess.**
`SDL_GetDisplayForWindow` and `SDL_GetCurrentDisplayMode` are now exported and
bound, and `SdlVideo.refreshRate` reads `refresh_rate` out of the mode. The pacer
starts unpaced and adopts whatever the display reports on the first pump.

Three things about that number are load-bearing:

- **Zero is an answer, not an error.** SDL documents `refresh_rate` as `0.0f`
  when unspecified and some drivers never fill it in. It means "do not pace",
  not "fail to open a window" — so `refreshRate()` returns 0 rather than
  throwing, and the loop free-runs exactly as it did before.
- **It is cached per window, and dropped on a scale change.** Reading it is a
  native call and the loop reads it every pump; the one case where the cached
  value goes stale is the window moving to another monitor, which is what
  `WINDOW_DISPLAY_SCALE_CHANGED` usually is.
- **Two windows take the fastest of their displays.** They share one loop, so
  pacing to the slower one would starve the window on the faster. Overshooting
  costs the slow window a discarded frame; undershooting costs the fast one a
  missed refresh, and the second is the one the user sees.

`-Dgoldberry.frame.rate` still overrides, for measuring an unpaced loop (`0`) or
pinning a rate a driver reports wrongly. It is no longer how pacing is turned on.

## What it bought

The showcase, 960×640, last 150 of 300 frames, back to back in one session, with
the rate read from the display rather than supplied:

| | fps | paint | present | frame path per second |
|---|---|---|---|---|
| `-Dgoldberry.frame.rate=0` | 111.1 | 2.25 ms | 5.51 ms | 862 ms |
| Paced from the display | 58.8 | 1.61 ms | **1.20 ms** | **165 ms** |

SDL reports the panel as 60.0 Hz and the loop settles at 58.8 fps — the interval
is 16.67 ms and a frame costs a shade under 3 ms, so each one lands just past its
deadline and takes the next.

**`present` fell 4.6×**, from 5.51 ms to 1.20 ms, and that is the result worth
reading twice: [ADR-0046](0046-what-present-actually-does.md) measured 1.61 ms of
*CPU* inside a present whose wall time was 6.43 ms. The block did not shrink. It
disappeared, because there was no longer a queue to wait behind.

Paint fell too — 2.25 ms to 1.61 ms — which
[ADR-0045](0045-a-frame-is-not-a-benchmark-iteration.md) predicts and this record
did not: a present that is not thrashing cache leaves less of a mess for the next
paint. The two costs really do compound, in both directions.

The UI thread now spends 165 ms of each second in the frame path instead of 862 —
**a fifth of the work** — and shows the user the same frames, because the ~50 fps
that vanished were never scanned out.

## Alternatives considered

- **Default the pacer to 60 fps.** Rejected: wrong on every display that is not
  60 Hz, and silently so. A toolkit that caps a 144 Hz panel is worse than one
  that paints too many frames. This is why the pacer starts unpaced and waits to
  be told, rather than starting at a plausible number.
- **Read the rate once at window creation.** Rejected: a window that moves to
  another monitor would keep the old pace for its whole life, and moving windows
  between monitors of different refresh rates is the ordinary case on a desk with
  two of them.
- **Use `SDL_GetDesktopDisplayMode`.** Rejected in favour of the *current* mode:
  the desktop mode is the one the display was configured at, not the one it is
  running, and they differ whenever anything has changed it.
- **Pace in `EventLoop` rather than in the backend.** `EventLoop` does not mint
  `FrameDue` and does not own the pump timeout, so it would have to hold an event
  it had already been handed. The backend has both, and `headless` is
  deliberately left unpaced — a test that has to wait for a frame clock is a slow
  test.
- **Let the swapchain throttle us, since present blocks anyway.** Measured: it
  does not. The block is 4.8 ms and the loop still reached 105–145 fps, because
  the swapchain is several buffers deep. Backpressure is not pacing.

## Consequences

- **`requestFrame`'s documented promise is now kept.** The contract has said
  "vsync-aligned where the platform offers it" since
  [ADR-0019](0019-the-backend-spis-first-cut.md) and nothing implemented it.
- **`SDL_DisplayMode` is in the layout probe**, so the offset `refreshRate()`
  reads is checked against the compiled library rather than trusted. That check
  is not decorative here: `refresh_rate` and `pixel_density` are adjacent floats,
  and swapping them deliberately produces
  `SDL_DisplayMode.refresh_rate: Java offset=16, C offset=20` — without the probe
  it would have paced the loop at 1 fps and looked like a hang
  ([ADR-0010](0010-hand-written-ffm-bindings.md)).
- **A layout added to the bindings must be added to `Layouts.registry()`.** It
  was missed on the first pass here: the struct was declared and registered in
  `goldberry_shim.c`, the suite stayed green, and nothing was being verified. The
  registry is the list the probe iterates, and a layout outside it is unchecked
  rather than failing.
- **The two display symbols are bound optionally**, unlike every other call in
  `SdlVideo`. A `libgoldberry` built before they were exported logs one debug
  line and runs unpaced, rather than failing to open a window — which is what
  binding them with the usual `downcall` did, and it is too high a price for an
  optimization whose "unavailable" path is already defined.
- **`goldberry.frame.rate` is a frame-rate cap, not a frame-rate target.** It
  never makes the loop draw faster, and it does not smooth jitter. A window that
  cannot paint in 16.7 ms still misses.
- **Damage tracking got smaller again.** It buys ~1 ms of present's CPU
  ([ADR-0046](0046-what-present-actually-does.md)); paced, present is 1.63 ms
  total. The remaining prize there is under a millisecond a frame.
- **`PaintBenchmark` is unaffected**, and should be: it has no window and paces
  nothing. [ADR-0045](0045-a-frame-is-not-a-benchmark-iteration.md)'s rule still
  holds — that number is for comparing options, and a frame's cost comes from a
  frame.
