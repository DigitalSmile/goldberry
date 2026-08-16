# ADR-0046: What `present` actually does

- **Status:** Accepted
- **Date:** 2026-08-16
- **Relates to:** `docs/ARCHITECTURE.md` §5; [ADR-0003](0003-sdl3-as-the-only-desktop-backend.md), [ADR-0031](0031-blend2d-and-the-borrowed-buffer.md), [ADR-0045](0045-a-frame-is-not-a-benchmark-iteration.md)

## Context

[ADR-0045](0045-a-frame-is-not-a-benchmark-iteration.md) closed one question and
opened a bigger one. It measured `present` at 6.5 ms against a paint of 2.2 ms —
the largest single item in the frame — and then reported that `present` still
cost 6.6 ms under SDL's `dummy` driver, *where nothing composites*. It concluded:
"Whatever that time is, it is not waiting for a compositor. That is a new open
question and a bigger one than the one this record closes."

This record answers it. The answer is that the `dummy` row was wrong, and that
`present` is doing considerably more than the name suggests.

## What was measured

linux-x64, 8 logical cores, a 2560×1315 virtual display at 59.96 Hz, Wayland.
The showcase over 250–300 frames, last 100–150 taken.

**The `dummy` row does not reproduce.** Under `SDL_VIDEODRIVER=dummy`, `present`
is **0.03 ms**, not 6.6 ms — and paint falls to **0.61 ms**, which is
`PaintBenchmark`'s number reproduced inside a live window. ADR-0045's central
claim survives its own broken control: `present` really does make the next paint
about four times more expensive, and with `present` made trivial the two numbers
converge exactly as that record predicted.

**`present` is not Goldberry's code.** Splitting `Sdl3Window.present` the way
ADR-0045 split paint:

| Step | Median |
|---|---|
| `physicalSize()` | 0.022 ms |
| Damage validation and marshalling | 0.021 ms |
| `SDL_UpdateWindowSurfaceRects` | **6.495 ms** |

Goldberry's own overhead in `present` is 43 µs. There is nothing here to tune.

**Three quarters of it is a block, not work.** Wall clock against
`ThreadMXBean.getCurrentThreadCpuTime()` on the UI thread:

| | Median |
|---|---|
| `present` wall | 6.43 ms |
| `present` CPU | 1.61 ms |
| **Blocked** | **4.82 ms (75%)** |

**The CPU quarter is a copy.** Sweeping the window size:

| Size | Mpixels | `present` CPU | Blocked |
|---|---|---|---|
| 480×320 | 0.154 | 1.00 ms | 4.30 ms |
| 960×640 | 0.614 | 1.70 ms | 4.36 ms |
| 1440×960 | 1.382 | 3.43 ms | 4.68 ms |
| 1920×1280 | 2.458 | 4.74 ms | 2.45 ms |

CPU scales linearly at about **1.65 ms/Mpixel** — 2.4 GB/s, the throughput of a
memory copy. The block does not scale with size at all, because it is not
data-dependent.

## What SDL is doing

`src/video/wayland/` has no `CreateWindowFramebuffer` or
`UpdateWindowFramebuffer` hook. The Wayland backend does not implement the
window-surface API at all, so `SDL_GetWindowSurface` falls through to SDL's
generic fallback in `SDL_video.c` — `SDL_CreateWindowTexture`. That fallback:

1. creates a **full hardware `SDL_Renderer`** behind the window,
2. `SDL_malloc`s a plain heap buffer and hands that back as the "window surface".

`SDL_UpdateWindowSurfaceRects` then reaches `SDL_UpdateWindowTexture`, which
does `SDL_UpdateTexture` (**the copy**, into a streaming GPU texture),
`SDL_RenderTexture`, and `SDL_RenderPresent` (**the block**).

Two consequences follow, and both contradict things the codebase currently says.

**The borrowed buffer is not the compositor's.** [ADR-0031](0031-blend2d-and-the-borrowed-buffer.md)
and `BackendWindow.acquireFrame`'s contract both state that the pixels Blend2D
draws into are the platform's own memory, and that this removes a full-frame
copy. On Wayland that is false: the buffer is `SDL_malloc`'d by SDL, and SDL
copies it into a texture on every present. The copy ADR-0031 set out to remove is
still paid — just on the other side of the SPI, where nothing in this repo could
see it. What `acquireFrame` genuinely buys is one copy instead of two; it does
not buy zero.

**Damage already matters, and is already wired up.** `SDL_UpdateWindowTexture`
uploads only `SDL_GetSpanEnclosingRect` of the damage list, so the damage
Goldberry passes is honoured today — and `Window` always passes
`DamageRect.all()`. Forcing partial damage:

| Damage | `present` CPU | Blocked |
|---|---|---|
| whole frame | 1.76 ms | 5.75 ms |
| half | 1.36 ms | 5.73 ms |
| quarter | 0.98 ms | 5.38 ms |
| a tenth | 0.87 ms | 5.74 ms |

So damage tracking is worth about **1 ms a frame at this size**, and no more: it
buys the copy, not the block. That is less than [ADR-0045](0045-a-frame-is-not-a-benchmark-iteration.md)
hoped when it wrote that the two costs "compound instead of adding" — they do
compound, but the block is not one of the terms it can reach.

## Decision

Record the mechanism, and correct the two records that state otherwise.

1. **`present` on the Wayland/SDL surface path is a texture upload plus a render
   pass plus a swapchain wait.** It is not a blit to the compositor. Roughly, at
   960×640: 1.05 ms of copy that scales with damage, 0.7 ms of fixed render-and-
   present, and 4.8 ms of blocking that scales with nothing.
2. **ADR-0045's `dummy` row is struck**, and its conclusion that the cost "is not
   waiting for a compositor" with it. It is.
3. **ADR-0031's zero-copy claim is narrowed** to what it actually delivers: one
   copy instead of two, and a guarantee that Blend2D never allocates.
4. **`PaintBenchmark`'s number is not an artefact.** Under `dummy`, in-app paint
   is 0.61 ms against the benchmark's 0.57 ms. The benchmark measures
   rasterization correctly; ADR-0045 was right to keep it and right to label it.

## Alternatives considered

- **Tune `Sdl3Window.present`.** There is 43 µs in it. Rejected as not worth
  finding.
- **Blame the compositor and stop.** This is what ADR-0031 did — it measured
  present at ~10 ms and concluded "most of it is waiting on the compositor rather
  than copying". Half right, and the half it got wrong is the half that can be
  fixed: a quarter of present is a copy nobody knew was happening.

## Consequences

- **The frame loop produces frames nobody sees.** Paint plus present is ~9.5 ms,
  so the showcase runs at ~105 fps into a 59.96 Hz display: about two frames in
  five are painted, uploaded, and discarded. Nothing in `requestFrame` is paced
  to the display, though `BackendWindow.requestFrame`'s own contract promises
  "vsync-aligned where the platform offers it". That promise is currently not
  kept, and it is the largest remaining win — it costs a whole frame's paint and
  present, not a millisecond of one.
- **Owning the renderer would remove the copy and the indirection.** SDL is
  already creating an `SDL_Renderer`; Goldberry could create it instead, and
  `SDL_LockTexture` would give Blend2D genuinely mapped staging memory to paint
  into. That is the only route to the zero-copy path ADR-0031 believed it had.
  It is an architectural change to the sdl3 backend and is not decided here.
- **Damage tracking is worth about 1 ms a frame**, not the compounding win
  ADR-0045 projected. Still worth having; no longer the first thing to build.
- **These numbers are from a virtual display.** The *mechanism* is read out of
  SDL's source and holds anywhere the Wayland backend is used; the absolute costs
  — especially the block, which depends on a virtualized GPU — should be
  re-measured on real hardware before anything is sized against them.
- **The probes are not kept.** A CPU-versus-wall split of `present`, a damage
  fraction override, and a three-way split inside `Sdl3Window.present` are how
  the tables above were produced. Each is a few lines, and the rule from
  ADR-0045 applies: a flag that silently changes what reaches the screen is worse
  than re-adding it when somebody next needs it.
