# ADR-0045: A frame is not a benchmark iteration

- **Status:** Accepted, with one row corrected by [ADR-0046](0046-what-present-actually-does.md)
- **Date:** 2026-08-16
- **Relates to:** `docs/ARCHITECTURE.md` §5; [ADR-0031](0031-blend2d-and-the-borrowed-buffer.md), [ADR-0037](0037-what-the-text-path-costs.md), [ADR-0042](0042-blend2ds-workers-and-how-many.md), [ADR-0046](0046-what-present-actually-does.md)

> **Correction.** The `dummy`/`offscreen` row below, and the consequence drawn
> from it, do not reproduce: under `dummy`, `present` is 0.03 ms and paint is
> 0.61 ms. See [ADR-0046](0046-what-present-actually-does.md). Everything else in
> this record stands — and the corrected control confirms its central claim
> rather than weakening it.

## Context

[ADR-0042](0042-blend2ds-workers-and-how-many.md) shipped with a hole in it. Its
benchmark measured a 960×640 frame at **0.473 ms** painted synchronously;
[ADR-0037](0037-what-the-text-path-costs.md) had measured the same size and the
same scene inside the showcase at **5.10 ms**. An order of magnitude, unexplained,
with the borrowed compositor surface named as the likely cause and explicitly
labelled a hypothesis.

A benchmark that is 10× optimistic is worse than no benchmark. Every conclusion
drawn from it — which knob to turn, what fits in 16.67 ms, whether damage
tracking matters — is drawn against the wrong denominator.

## What was measured

Every step reproduced on linux-x64, 8 logical cores, over 300 frames of the
showcase, taking the last 100 to exclude warm-up.

**The gap is real and it reproduces.** In-app paint is 2.15 ms median at four
workers; the benchmark says 0.34 ms. Warm-up accounts for part of ADR-0037's
5.10 ms — the first twenty frames median 5.94 ms against the last hundred's
2.66 ms — but steady state is still 6–8× the benchmark.

Then, one hypothesis at a time:

| Suspect | Test | Result |
|---|---|---|
| The borrowed compositor buffer | Force the fallback path, paint into a heap buffer | **Refuted.** 2.28 ms heap vs 2.22 ms borrowed |
| The three new icons | Benchmark the scene with and without them | **Refuted.** +0.010 ms |
| The display server | Same build under Wayland and under X11 | **Refuted.** 2.22 ms vs 2.07 ms |
| Compositor contention | SDL's `dummy` and `offscreen` drivers — nothing composites | ~~**Refuted.** 2.00 ms and 2.03 ms~~ — **struck; does not reproduce, see [ADR-0046](0046-what-present-actually-does.md)** |
| Per-frame logging | Move the showcase's `LOG.info` out of the timed region | Real but small: ~0.4 ms |
| A cold cache | Rotate 24 buffers (59 MB, past the 32 MB L3) | Real but small: 1.3–1.4× |
| The environment as a whole | Run the benchmark's exact loop **inside the live application**, on the UI thread, between two real frames | **Refuted.** 0.49 ms, while the real frames on either side were 2.06 and 2.25 ms |

That last row is the one that turned the investigation around. Same JVM, same
JIT state, same thread, same scene, same machine load, same buffer type — and
the loop was still 4× faster than the frames surrounding it. Nothing about the
*environment* was responsible. The difference had to be the shape of the loop.

**The last variable was `present`.** With it skipped and everything else
unchanged, paint fell from **2.193 ms to 0.574 ms** — the benchmark's number,
recovered inside the running application.

## Decision

Record that **`present` makes the next paint about four times more expensive**,
and treat the benchmark accordingly.

The mechanism is cache and TLB pollution rather than anything present does to the
buffer it is handed. Present moves megabytes and crosses into the kernel; by the
time the next frame begins, Blend2D's pipelines, the destination pixels, the
glyph caches and the page tables that reach them have all been displaced. A
synthetic data-cache eviction — walking 96 MB between iterations — reproduces
about 1.6× of the 3.8×, so data caches are part of it and not all of it.

Two things follow, and they are the decision:

1. **`PaintBenchmark` measures rasterization in isolation, and says so.** It is
   the right tool for comparing worker counts, surface sizes and algorithms
   against each other, because it holds everything else constant. It is the
   wrong number to quote as "what a frame costs". Both numbers now appear in
   ADR-0042, labelled.
2. **A claim about frame cost has to come from a frame.** The in-app worker sweep
   is now in ADR-0042 alongside the benchmark sweep, and it agrees on the shape:
   one worker loses to none, four is the floor, eight is worse.

`Window`'s trace line now splits paint into begin, draw and end, because that
split is what localised the cost — and it is what the next person will need.

## Alternatives considered

- **Make the benchmark present.** It has no window; `:core`'s tests deliberately
  run without one so they need no display. Adding a windowed benchmark means the
  benchmark cannot run in CI's headless containers, which is most of where it
  would be useful.
- **Insert a synthetic eviction pass into every benchmark iteration**, to
  approximate a real frame. Tried, and rejected as a default: it reproduces
  1.6× of 3.8×, so it would trade a number that is honestly wrong for one that
  is dishonestly close. It is kept as an explicit comparison
  (`paintCostWhenTheBufferIsNotAlreadyInCache`) rather than folded into the
  headline figures.
- **Quote only the in-app number and delete the benchmark.** Rejected: the
  in-app number cannot isolate anything. It could not have told us that four
  workers beat two, because a 0.2 ms difference is inside the frame-to-frame
  spread of a live window.

## Consequences

- **ADR-0031's conclusion needs revisiting, and this record does not do it.**
  That ADR measured present at ~10 ms against paint at ~1.3 ms and concluded
  present dominates "and most of it is waiting on the compositor rather than
  copying". Present is 6.5 ms here, and where it goes was the open question this
  record left. [ADR-0046](0046-what-present-actually-does.md) answers it: a
  quarter of it is a copy SDL makes on our behalf, and three quarters is a
  swapchain wait. ADR-0031 was half right.
- **Damage tracking is worth more than it looked.** If a present poisons the
  next paint, then not presenting the whole window is worth the paint time as
  well as the present time. The two costs compound instead of adding. —
  *Measured since, in [ADR-0046](0046-what-present-actually-does.md): about
  1 ms a frame at 960×640. Damage buys the copy, not the wait, so the two
  compound less than this projected.*
- **Every performance number in the book now needs its context stated** — in a
  loop, or in a frame. The two differ by 4× on this workload and there is no
  reason to think that factor is stable across others.
- **The showcase logs three frames and then every fiftieth.** A console write per
  frame sat inside the paint callback, and therefore inside what `Window` reports
  as paint, at about 0.4 ms a frame. The instrument was changing the reading.
- **`goldberry.paint.noBorrow` and `goldberry.paint.noPresent` are not kept.**
  They were how two of the rows above were measured and they are a few lines
  each to reinstate; leaving a flag in the frame loop that silently stops the
  window updating is worse than re-adding it the next time somebody needs it.
