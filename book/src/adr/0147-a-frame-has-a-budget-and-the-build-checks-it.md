# 147. A frame has a budget, and the build checks it

Date: 2026-08-19

## Status

Accepted. The regression test [ADR-0142](0142-a-style-handed-down-keeps-its-identity.md)
should have had before it needed it.

## Context

A 34× performance regression lived in this repository for a month with **every
test passing**. `FrameBenchmark` in `:widgets` measured the engine on a synthetic
15-node tree, reported 0.6 ms for a whole frame, and was right: the defect only
appears in a tree with a `scroll` in it, which is to say in a real application
and not in a benchmark's fixture.

A benchmark that prints is a benchmark nobody reads on a green build.

## Decision

**`FrameBudgetTest` measures the showcase's own tree at five resolutions, prints
the table, and fails the build when a stage is over its ceiling.**

```
  resolution          build    style   layout   raster
  800x600            0.000    0.076    0.010    1.113  (ms, median)
  1920x1080          0.000    0.060    0.005    1.579
  3840x2160 @2x      0.000    0.035    0.005    6.671
```

**Ceilings, not stored comparisons.** A test that compared against a recorded
number would fail on a slower machine and pass on a faster one that had
regressed. Every budget is written beside the measurement it is a multiple of —
`style` measures 0.03–0.08 ms and is allowed 1 ms — which is useless against a
20% drift and exactly right against what actually happens. The bug this exists
for was 34×, and it trips this test at 10.1 ms against a 1 ms budget, naming the
stage and the resolution.

**Two structural claims that hold on any machine**, and they are the sharper
half:

- **Style and build do not grow with the pixel count.** The cascade runs per
  element and a 4K window has the same elements as an 800×600 one. A style cost
  that followed the pixels would be a cache keyed on something it has no business
  being keyed on — one letter from the defect that prompted this.
- **A settled render is two orders of magnitude cheaper than a cold one.**
  Measured at 450–520× with the cache working and **11×** with ADR-0142's defect
  reintroduced; the threshold is 40, which is a chosen number rather than a
  picked one.

**Zero Blend2D workers**, and the first run of this test is why: a threaded
context queues its work and blocks at `frame.end()`, so a timing loop around
`paint` measures *submitting* a frame — which reported a 4K raster as cheaper
than an 800×600 one. `FrameBenchmark` pins it for the same reason and the note
was already there to be read.

**A warm-up sweep before the measurements**, because without one the first
resolution measured costs three times the last whatever order they are in, and
every ratio in the class becomes a measurement of C2.

## Consequences

**It runs in `test` and not in `benchmark`.** That is the point: a regression has
to fail a normal build. The cost is about four seconds and the risk is a slow or
loaded runner tripping a ceiling — which is why every failure message says
"either something regressed or this machine is slower than the one the budget was
written on" and prints the table that answers it.

**The budgets are this machine's**, and the first CI run on other hardware is
what will say whether the multiples are right. They are deliberately loose enough
that the answer should be yes.

**`FrameBenchmark` stays.** It measures the engine's parts against each other —
retained against throwaway, damage against whole-frame — which is a different
question from "is a real frame still fast", and neither replaces the other.

**`raster` is the only stage that scales with the frame**, and the table now
says so on every run: 1.3 ms per megapixel at 800×600 and 0.83 at 4K, because a
frame has a fixed cost a small one cannot spread.
