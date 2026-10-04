# ADR-0552: A clock bound has room, and stops short of the defect

- **Status:** Superseded in part by [ADR-0560](0560-no-test-compares-a-duration-with-a-bound-and-no-module-has-a-coverage-floor.md) — no test under `check` holds a clock bound any more; the benchmarks' budgets still follow this record
- **Date:** 2026-10-03
- **Relates to:** `docs/build-simplification-2026-10-03.md`,
  [ADR-0551](0551-benchmarks-are-a-source-set-run-one-at-a-time-by-their-own-workflow.md)

## Context

A cost is guarded by a count, never by a clock, and most of the suite does that.
A dozen tests under `check` cannot, because what they assert is about time: a
pump returns promptly when a frame is pending, a 404 fails before the backoff, a
close does not wait out a stall, the SDL audio sink drains at the rate it was
given. Each one compared a measured duration with a bare number, and several sat
close to what a loaded runner measures:

- `SdlAudioSinkTest` asked for more than 20 distinct queue readings in a fixed
  150 ms window, sampled with `sleep(1)`. A runner that sleeps 15 ms takes ten
  readings and fails.
- Its rate test slept 250 ms once and then looked.

The benchmark lane's budgets had the same shape. `FrameBudgetBenchmark` held the
style pass to 1 ms when the defect it guards against cost 10 ms.

## Decision

**A bound has two numbers.** The bound is a generous multiple of what the
operation takes when it works. The defect is what it takes when broken: the
timeout it must beat, the backoff it must skip. `TimeBudget`, in `:core`'s test
fixtures, holds both. The allowance is the bound times
`-Dgoldberry.timing.slack` (1 when unset, never less), and it never comes within
the last twentieth of the defect, so no amount of slack lets the defect pass. A
share rather than a fixed margin, because a budget is as often microseconds as
seconds.

**A test that waits for something polls until it happens**, with a `TimeBudget`
as its deadline. It does not sleep a fixed time and look once. A share replaces
a count in a fixed window: the audio sink's smoothness is the fraction of
readings that moved, over at least forty readings.

The bounds that moved:

| Test | Was | Is | Defect |
|---|---|---|---|
| `HeadlessBackendTest` pending frame | 1 s | 2 s | the 5 s timeout |
| `NetworkPlaybackTest` close while opening | 2 s | 5 s | the 30 s stall |
| `HttpIOTest` 404 is final | 3 s | 4 s | 6 s of backoff |
| `HttpIOTest` read timeout | ≥ 2 s, < 10 s | ≥ 2 s − 50 ms, < 10 s | the 30 s stall |
| `SdlAudioSinkTest` rate | 250 ms sleep, then > ½ s drained | rate ≥ 2× within 2 s | a rate of 1 |
| `SdlAudioSinkTest` smooth drain | > 20 values in 150 ms | > half of ≥ 40 readings moved | a step per pull |
| `SdlAudioSinkTest` latency per pull | 5–50 ms | 2–100 ms | none, or a second |
| `ProcessAgeTest` against `ProcessHandle` | 1.1 s | 2 s | — |
| `FrameBudgetBenchmark` build, style, layout | 1 ms | 2.5 ms | 10 ms |
| `FrameBudgetBenchmark` raster | 12 ms floor, 8 ms/Mpx | 16 ms floor, 10 ms/Mpx | 18 ms on the sheet |
| `FrameBudgetBenchmark` settled render | 40× cheaper | 25× cheaper | 11× |
| `AxisLabellingBenchmark` per call | 200 µs | 400 µs | 1 ms |

**The coverage floors get room too.** Each sits at least four points under
what the suite reaches with the library loaded. Measured on 2026-10-03:

| Module | Lines | Branches | Floor |
|---|---:|---:|---|
| `:core` | 85.8% | 76.6% | 80%, 68% (unchanged) |
| `:widgets` | 91.6% | 76.7% | 87%, 71% (unchanged) |
| `:html` | 93.1% | 79.8% | 87%, 71% (unchanged) |
| `:emoji` | 75.0% | 100% | 70%, 50% (unchanged) |
| `:media` | 90.0% | 79.9% | 86%, 74% (was 88%, 77%) |

`:media`'s floors were one and two points under. Which of its tests run depends
on the machine's decoders, so the same commit measures differently on two
runners, and a refactor that only moves code could cross them.

## Consequences

- A loaded machine has a knob, and a defect still fails at any setting of it.
- A bound is written next to the defect it guards, so a red one says whether it
  is a regression or a slow runner.
- The wider floors catch a smaller fall in coverage. They are still ratchets,
  and raising one stays a deliberate edit.
