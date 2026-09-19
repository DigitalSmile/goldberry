# 409. Present costs a tenth of a millisecond, and the wait is the rasterizer's

Date: 2026-09-19

## Status

Accepted. Corrects the numbers in
[ADR-0031](0031-blend2d-and-the-borrowed-buffer.md) and the question
[ADR-0045](0045-a-frame-is-not-a-benchmark-iteration.md) left open. Neither record
is edited; this one supersedes their present figure.

## Context

`book/src/TODO.md` carried this, under *Rendering and performance*:

> **Present costs 6.6 ms with no compositor to wait for.** The question ADR-0045
> opened while closing another. ADR-0031 measured present at ~10 ms and concluded
> "most of it is waiting on the compositor rather than copying". Under SDL's
> `dummy` video driver — no compositor, no display, no surface to hand anyone —
> present still measures **6.6 ms**, essentially the same as under Wayland.
> Whatever that time is, the explanation on record is wrong, and present is the
> largest single term in a frame.

The entry is right that the explanation on record was wrong. It is wrong about
everything else, including its own number.

Two hypotheses were worth ruling out before measuring, because both would have
made the 6.6 ms real and misattributed rather than absent.

**That the Blend2D join was being counted as present.** A frame's context is
asynchronous: `draw` queues commands and `end()` joins the workers (ADR-0042), so
a timer boundary in the wrong place would report rasterization as presentation.
It does not. `Window.paint` takes `painted = System.nanoTime()` *after*
`frame.end()`, and present is measured as `done - painted`. The boundary was
already right.

**That the frame pacer's sleep was being counted as present.** 6.6 ms is
suspiciously close to what is left of a 16.67 ms budget after a 10 ms frame, which
is exactly what a pacer would sleep for — and a pacer is ours rather than the
compositor's, which would explain why the number did not change between Wayland
and `dummy`. It is not that either: `FramePacer` caps the *event wait*
(`pacer.capWait` in `Sdl3Backend.pumpEvents`), which is outside the painted frame
entirely.

## Decision

**Measure it, and record what it is.** 300 frames of the showcase under
`-Pgoldberry.backend.videoDriver=dummy` with the per-frame trace on, on this
machine:

| Stage | median | p95 |
|---|---|---|
| buffer | 0.062 ms | 0.131 ms |
| paint — `begin` | 0.060 ms | 0.132 ms |
| paint — `draw` | 5.944 ms | 39.327 ms |
| paint — `end` | 10.158 ms | 20.978 ms |
| **present** | **0.127 ms** | **0.319 ms** |
| whole frame | 16.928 ms | 61.244 ms |

A separate 300-frame run agrees: present's median 0.113 ms, minimum 0.057 ms, p95
0.219 ms, mean 0.138 ms.

**Present costs about a tenth of a millisecond, which is 0.8% of a frame.** The
entry's 6.6 ms is off by a factor of roughly fifty, and its conclusion — "present
is the largest single term in a frame" — has the frame upside down.

**The largest single term is `end`**, at 10.2 ms median: the join that waits for
Blend2D's worker threads. `draw` at 5.9 ms is the second, and the two are one
thing rather than two — `draw` queues the commands and `end` is where they are
actually rasterized, so a frame under `dummy` is essentially all rasterization and
nothing else. That is what ADR-0042 said the asynchronous context would do, stated
in numbers for the first time.

## Consequences

- **The entry is closed as corrected rather than fixed**, because there was
  nothing to fix. What was wrong was a number in the list.
- **ADR-0031's ~10 ms stands unchallenged for Wayland**, and deliberately so: the
  Wayland figure cannot be re-measured here. Opening a real surface on this
  machine takes GNOME Shell down with it — a compositor bug with a core dump
  behind it, recorded in TODO.md — so reproducing it costs the developer their
  session. What this record measures is the driver the entry itself claimed to
  have measured.
- **The qualifier that matters is the branch.** Under `dummy`, `acquireFrame`
  succeeds, so the frame is rasterized straight into SDL's own surface and present
  is `SDL_UpdateWindowSurfaceRects` over the damage with **nothing to copy**. A
  driver where SDL refuses the surface takes `Sdl3Window.present`'s other branch
  and pays a full-buffer copy. So 0.127 ms is the floor, not the universal figure,
  and a like-for-like Wayland measurement is still owed — which is a separate
  entry and stays open.
- **The absolute totals here are inflated by the measurement.** The per-frame
  trace is enabled by `LOG.isTraceEnabled()`, which adds five `nanoTime` calls and
  a formatted log line per frame; the frames are ~17 ms with it on and ~5 ms in a
  run where only present was extracted. The *ratio* is what this record claims,
  and the ratio is not sensitive to it: present is a rounding error against paint
  either way.
- **`--frames=300` under the dummy driver is the reproduction**, and it is cheap
  and safe. Written down because the previous numbers on record cannot be
  reproduced at all, which is how they survived being wrong.
