# ADR-0042: Blend2D's workers, and how many

- **Status:** Accepted
- **Date:** 2026-08-16
- **Relates to:** `docs/ARCHITECTURE.md` §5; [ADR-0002](0002-cpu-rasterization-with-blend2d.md), [ADR-0031](0031-blend2d-and-the-borrowed-buffer.md), [ADR-0037](0037-what-the-text-path-costs.md)

## Context

[ADR-0002](0002-cpu-rasterization-with-blend2d.md) chose Blend2D partly *because*
it can rasterize across threads. Nothing has ever used that.
[ADR-0031](0031-blend2d-and-the-borrowed-buffer.md) measured a frame at
960×640 — paint ~1.3 ms, present ~10 ms — concluded present dominated by an order
of magnitude, and parked `thread_count` as "only matters if paint ever becomes
the bottleneck". [ADR-0037](0037-what-the-text-path-costs.md) then measured a
frame with text in it: buffer 0.18 ms, **paint 5.10 ms**, present 1.92 ms, total
7.86 ms median and 14.18 ms at p95 against a 16.67 ms budget. On those numbers it
had.

The knob itself is one field. `BLContextCreateInfo::thread_count` is already in
the layout table and already checked against the compiled library; `bl_context_init_as`
already takes the struct. What was missing was not a binding. It was a number,
and a reason for it.

## Decision

`BlendContext` takes a worker count, and `:core` decides what it is.

`:natives` gains `BlendContext.on(image, scale, threadCount)`. Zero renders
synchronously on the calling thread, which is what every existing caller keeps
getting. Anything higher renders asynchronously: draw calls are recorded, workers
execute them over horizontal bands, and `bl_context_end` is where the calling
thread waits. `Frame.end()` already ran before `present` for exactly this reason
([ADR-0031](0031-blend2d-and-the-borrowed-buffer.md)) — a rule that was a caution
and is now the mechanism.

**Threads are requested, not demanded.** If Blend2D refuses asynchronous mode —
a thread pool at its limit, a process out of threads — the context is begun
synchronously instead and `threadCount()` reports zero. The fallback is in Java
rather than through `BL_CONTEXT_CREATE_FLAG_FALLBACK_TO_SYNC`, so it can be
logged and tested, and so the create-info stays a struct with one field set
rather than a struct with a magic constant nothing in the layout table checks.

`PaintThreads` in `:core` is the policy, and `-Dgoldberry.paint.threads=N`
overrides it. The rules are three, and each is a measurement:

1. **Zero or at least two, never one.** A single worker pays for the command
   queue and the hand-off and gets no parallelism back. At 640×480 it measured
   *slower* than synchronous — 0.499 ms against 0.478.
2. **Cap at four.** Four was best or tied-best at every size measured, and eight
   was worse at every size but the smallest.
3. **Nothing under 400×300.** Below that the gain is under 50 µs, inside the
   run-to-run spread, and not worth waking four threads sixty times a second.

The automatic count is therefore `clamp(cores - 1, 0, 4)`, rounded *down to zero*
when that leaves one. A one- or two-core machine paints synchronously.

## The numbers

`./gradlew :core:benchmark`, `PaintBenchmark`. The showcase's own scene — a bar,
a sidebar, a wrapped paragraph — painted whole per sample, `Frame` construction
and `end()` included, because that is the unit `Window.paint` pays. 200 samples
after 60 warm-up frames, on linux-x64 with 8 logical processors.

Medians, in milliseconds:

| Surface | 0 | 1 | 2 | 3 | 4 | 6 | 8 |
|---|---|---|---|---|---|---|---|
| 240×120 | 0.240 | 0.223 | 0.238 | 0.196 | 0.194 | 0.191 | 0.212 |
| 400×300 | 0.412 | **0.438** | 0.314 | 0.301 | 0.270 | 0.269 | 0.279 |
| 640×480 | 0.478 | **0.499** | 0.404 | 0.314 | 0.302 | 0.316 | 0.323 |
| 960×640 | 0.473 | 0.481 | 0.355 | 0.337 | 0.337 | 0.328 | 0.357 |
| 1920×1080 | 0.594 | 0.586 | 0.480 | 0.395 | **0.380** | 0.391 | 0.415 |
| 3840×2160 | 6.034 | 4.245 | 3.151 | 3.482 | **2.337** | 2.500 | 2.763 |

Bold marks the two facts the policy is built on: one worker losing to none, and
four being the floor of the curve.

The shape is the same everywhere and the size is not. At 960×640 four workers
save 136 µs — real, and 0.8% of a frame budget. At 3840×2160 they save 3.7 ms,
which is 22% of one. **This decision is worth little today and a great deal at
4K**, which is the honest way to hold it: it is bought now because the frame that
needs it is a window resize away, not because 136 µs was the problem.

## The number that did not match

ADR-0037 measured paint at **5.10 ms** for a 960×640 frame with text. The same
size and scene here is **0.473 ms**. That gap was left open when this record was
first written, with the borrowed compositor buffer as the suspect.

**It was not the buffer.** [ADR-0045](0045-a-frame-is-not-a-benchmark-iteration.md)
chased it down: the cause is `present`, which leaves the *next* paint about four
times more expensive, and the benchmark never presents. The policy above is
unaffected — the in-app sweep reproduces the same shape, one worker losing to
none and four winning — but the absolute numbers here are rasterization in
isolation and a real frame costs more.

The in-app figures, measured the same day on the same machine over 300 frames:

| Workers | in-app paint (median) |
|---|---|
| 0 | 2.856 ms |
| 1 | 3.005 ms |
| 2 | 2.363 ms |
| 4 | **2.146 ms** |
| 8 | 2.240 ms |

So four workers are worth **1.33× on a real 960×640 frame**, against 1.4× in the
benchmark. The decision stands and the claim is now measured rather than argued.

## Alternatives considered

- **Leave it synchronous.** The runner-up, and defensible on the 960×640 numbers
  alone. Rejected on the 4K row: a 22% saving on a surface a user can produce by
  maximizing a window is not something to leave on the table, and the cost of
  taking it is one field and a fallback.
- **`BL_CONTEXT_CREATE_FLAG_FALLBACK_TO_SYNC`.** The C way to say the same
  thing. Rejected: it is a constant this project has not verified against the
  compiled library, in a struct field the layout table does not check the
  *values* of, and it would make the fallback invisible from Java. Catching the
  refusal costs four lines and can be logged.
- **One worker per core, uncapped.** Rejected by the table: eight workers on an
  eight-processor machine were worse than four at every size but 240×120, and
  markedly worse at 4K (2.763 against 2.337).
- **Thread every surface, however small.** Rejected, but narrowly — 240×120 did
  not measure *slower* threaded. It buys 46 µs and wakes four threads to do it,
  and a menu or a tooltip is exactly the surface a battery notices.
- **Decide the count in `:natives`.** Rejected on the module boundary that has
  held so far: `:natives` is mechanism. How many threads a *frame* deserves is a
  question about surfaces and machines, which is `:core`'s to answer.

## Consequences

- **Painting is now concurrent, and the rule that made it safe was already
  there.** `Frame.end()` before `present` was documented as a caution against a
  context with work in flight; it is now the synchronization point. Anything that
  reads pixels before `end()` returns is a bug that did not exist yesterday —
  `ThreadedPaintTest` asserts a threaded frame is pixel-identical to a
  synchronous one, at 1, 2, 3, 4 and 8 workers, every pixel compared rather than
  sampled, because a band seam is one wrong row in three hundred.
- **Up to four threads now wake per frame on a machine with three or more
  cores.** On a laptop drawing an idle window at 60 fps that is a real power
  cost, and the size floor is the only thing limiting it. Damage tracking would
  limit it far better, by not painting the frame at all — this makes that work
  more valuable, not less.
- **The p95 is not improved as much as the median.** At 3840×2160 the median
  falls 2.6× and the p95 only 2.6× as well (10.987 → 4.163), which is better than
  feared; at 960×640 the p95 barely moves (0.550 → 0.457). The tail is where M1's
  60 fps claim lives, so this helps that claim at 4K and hardly at all at the
  size it was measured.
- **A new way for a frame to be slow.** With workers, a frame's cost includes
  waiting for the slowest band. A machine whose cores are busy with something
  else will now see paint times that depend on what else is running, where
  synchronous painting only competed for one core.
- ~~**The in-app measurement is owed.**~~ Taken, and it moved the whole frame of
  reference rather than just this number —
  [ADR-0045](0045-a-frame-is-not-a-benchmark-iteration.md).
