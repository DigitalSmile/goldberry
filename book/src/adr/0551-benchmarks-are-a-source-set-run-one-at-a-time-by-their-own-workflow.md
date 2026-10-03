# ADR-0551: Benchmarks are a source set, run one at a time by their own workflow

- **Status:** Accepted
- **Date:** 2026-10-03
- **Relates to:** `docs/build-simplification-2026-10-03.md`, `docs/testing.md` §1.5,
  [ADR-0397](0397-a-benchmarks-names-are-resolved-under-check.md)

## Context

A benchmark was a test class tagged `benchmark`, which every test task excluded
and one task, `benchmark`, included. The tag was the whole boundary, and it was
written per class or per method: `TicksTest` and `TimeTicksTest` each held one
tagged method among their tests, and `:weaver` repeated the exclusion because it
does not apply the conventions. The probes, `main` classes that measure a real
window, sat in the test trees beside them.

The nightly lane ran `./gradlew benchmark --continue` with `org.gradle.parallel`
on. With that, every module's benchmarks ran at the same time as every other
module's, and the numbers measured each other: `FrameBudgetTest`'s style row read
3.6 ms alone and 20 ms in that run. The lane also ran every night, whether or not
master had moved, and a person who wanted one benchmark's numbers had no way to
ask CI for them.

## Decision

- **A source set.** Every module has `src/benchmark/java`, which sees the
  module's main and test classes, so a benchmark is built from the same fixtures
  as a test. The `benchmark` task runs that source set and nothing else.
  `check` compiles it and never runs it. The tag is gone, and
  `BenchmarkLaneTest` fails on a `*Benchmark` or `*Probe` in a test tree, on any
  `@Tag("benchmark")`, and on a measurement `docs/testing.md` does not list.
- **One at a time.** Every `benchmark` task holds a shared build service with one
  slot.
- **Its own workflow.** `benchmarks.yml` compiles the benchmarks, runs them and
  runs JMH. It is started by hand, with an optional `--tests` filter, or called by
  `nightly.yml`. No pull request, push or snapshot runs a benchmark.
- **Nightly only when master moved.** The nightly's first job looks up the commit
  of the last scheduled run that did any work. If master is still there, every
  other job is skipped. A manual run always runs.
- `FrameBudgetTest` is `FrameBudgetBenchmark`, so every class in the lane is
  named for it. The tagged methods of `TicksTest` and `TimeTicksTest` are
  `AxisLabellingBenchmark`.

## Consequences

- A benchmark cannot be run by `test` by forgetting a tag, and cannot be
  forgotten by the nightly by being in an unlisted module.
- A nightly with nothing new costs one short job.
- A benchmark's numbers are comparable between runs only on one runner. One at a
  time is necessary for that, not sufficient.
- `./gradlew benchmark --tests '*X*'` gives every module the filter, and a module
  with no match is not a failure.
