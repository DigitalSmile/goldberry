# ADR-0560: No test compares a duration with a bound, and no module has a coverage floor

- **Status:** Accepted
- **Date:** 2026-10-04
- **Supersedes:** [ADR-0559](0559-the-tests-that-read-a-wall-clock-run-in-a-lane-of-their-own.md),
  and [ADR-0552](0552-a-clock-bound-has-room-and-stops-short-of-the-defect.md)
  for the tests; the benchmarks keep their budgets
- **Relates to:** `docs/ci-fixes-2026-10-04.md`, `docs/flaky-tests.md`,
  [ADR-0551](0551-benchmarks-are-a-source-set-run-one-at-a-time-by-their-own-workflow.md),
  [ADR-0555](0555-qodana-inspects-the-toolkit-not-its-measurement-code.md)

## Context

Four days of red Snapshot runs, and three answers in a row that each held for
one push. ADR-0552 gave every clock bound room and a defect to stop short of;
the next run read five `sleep(1)` iterations in a second and a half. ADR-0559
moved the tests that read a clock into a lane of their own, after the build,
with retries; the next run was red again, and not in the lane. `TooltipTest`'s
new `tearDown` called `shutdown()` on a runtime its skipped `setUp` had never
installed, eleven times, on the one job that has no library. The lane itself
was green on all seven jobs that ran it.

So the lane had answered its question, and the question was the wrong one. A
test that compares a measured duration with a bound measures the machine, and
every mechanism that keeps the machine out of the reading -- room, a control
stream, a slot of its own, a retry -- is a second thing that can be wrong, and
on 2026-10-04 one of them was. The bounds themselves guarded little. The
drain rate at 4x, SDL's latency per pull, a count of readings that moved
between pulls: each is a figure, and a figure is what a benchmark measures and
a person reads. The rest -- a read that gives up, a close that returns, a pump
that does not park -- were behaviours with a figure stapled on.

The coverage floors had the same shape. A floor is read from a figure that
moves with which tests a machine ran: which decoders it has, which library it
loaded, what a loaded runner reached in time. The same commit measured 0.87
against `:media`'s floor of 0.88 on one runner and over it on three, the floor
went down four points and onto one leg, and the figure was still the machine's.

Asked, on the fourth day: can we change the approach, and get rid of the
timing and size tests?

## Decision

**No test under `check` compares a measured duration, rate or count of
readings with a bound.** A test asserts what holds however the machine
schedules it. `HttpIOTest.timesOut` asserts that the read throws the timeout's
exception, and that it waited at least the timeout, which a loaded machine can
only lengthen. `SdlAudioSinkTest` asserts that a queue never rises with nothing
written, stands still while paused, and that SDL refuses a rate it does not
take. `NetworkPlaybackTest.closeWhileOpening` asserts that the close returns.
How long any of it took is not compared with anything.

**Where the defect is a wait that would never end, the wait is longer than the
test's own `@Timeout`.** `HeadlessBackendTest.pendingFrameDoesNotPark` pumps
with a 30 s timeout under a 10 s test timeout: a pump that sits its timeout
out, which is the bug, fails on the hang guard every test already has, and a
prompt one returns however loaded the runner is. `closeWhileOpening` sets a
30 s stall under the same guard. A test timeout is a clock, and it is the one
clock a test is allowed: it says "this hung", never "this was slow".

**An assertion that was only a figure is deleted.** The sink's drain ratio at
4x against a stream at 1x, SDL's per-pull latency between 2 and 500 ms, ten
readings in a window and three that moved between pulls: gone, with the
`rawQueuedSamples` reading that existed for one of them. What they guarded is
a benchmark's to measure, in the lane ADR-0551 gave the benchmarks, which
fails nothing a push depends on.

**The wall-clock lane, `@WallClock`, `wallClockTest` and the retry plugin are
removed.** With no bound left there is nothing for a lane to shelter, and a
retry on a deterministic test hides a failure. Every test task has zero
retries and every failure is the error it says it is. The workflows run
`build` as one step again.

**No module has a coverage floor.** Every `jacocoTestCoverageVerification`
rule is removed, in `:core`, `:widgets`, `:html`, `:emoji` and `:media`, and
`check` no longer depends on the task. Coverage is measured, aggregated across
both binding modes, uploaded, and read by a person. A number that goes red for
the machine's reasons is a dashboard, which is where ADR-0338 already put the
other analysers that cry wolf.

**The benchmarks keep `TimeBudget`.** ADR-0552's bound-and-defect shape still
holds for `FrameBudgetBenchmark` and `AxisLabellingBenchmark`, which run one at
a time, by hand or nightly, and whose red is read rather than merged around.
`TimeBudget` stays in `:core`'s test fixtures for them, and its doc says so.

**The two bugs that made the run red are fixed on the way.** `TooltipTest`'s
`tearDown` shuts down a runtime only if `setUp` installed one. `qodana.yaml`
names each benchmark and JMH directory by its path, because an `exclude.paths`
glob is accepted and ignored: 52 of the 59 new findings on the last two runs
were in the directories `**/src/benchmark` was meant to leave out.
`QodanaScopeTest` now holds the file to directory names and refuses a glob.
The seven findings left were `AudioWorker` reading the `Decoder` it owns and
closes in `finally`, which joins the profile's owned resource types.

## Alternatives

- **Keep the lane and fix the bug.** The lane was green; the bug was in a
  test the lane's work had rewritten. Keeping it keeps a second mechanism
  whose failures look like the first's, and keeps a retry, which the
  repository's rule since the first review says teaches people to skip the
  report.
- **Make the lane and the floors advisory** (`continue-on-error`). A red
  step nobody acts on is the same as no step, and costs a minute a job.
- **Move the bound tests to the benchmark source set whole.** The behaviours
  in them -- a read gives up, a close returns -- belong under `check`. Only the
  figures moved out, and those were deleted rather than kept as a benchmark
  nobody asked for.
- **Cap Gradle's workers on the small runners.** Still measures a machine,
  just a less loaded one; ADR-0559 rejected it for the same reason.

## Consequences

- A red `test` task is a regression or a bug in the test, never the runner's
  load, and the ledger in `docs/flaky-tests.md` closes every timing row.
- A new test that wants to assert a duration does not: it asserts the
  behaviour, sets the defect's wait past its `@Timeout`, or writes a benchmark.
  `docs/testing.md` §1.5 says so; the guide's "A clock bound has room" is the
  benchmarks' section now.
- Coverage can fall without a red build. The aggregate report and the Codecov
  upload are what says so, and a review reads them.
- What `SdlAudioSink` reports as SDL's part of its latency, and how fast it
  drains at a rate, are measured by no test. `VideoPlaybackTest` still plays
  through the sink at a rate and reads the audio clock, which is the behaviour
  those figures stood in for.
- The per-OS jobs are one Gradle step shorter, and `build` runs every test
  under one invocation as it did before ADR-0559.
