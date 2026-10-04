# ADR-0559: The tests that read a wall clock run in a lane of their own, and are the only ones run again

- **Status:** Superseded by [ADR-0560](0560-no-test-compares-a-duration-with-a-bound-and-no-module-has-a-coverage-floor.md) — the lane, the tag and the retries were taken out the same day, with the last clock bound
- **Date:** 2026-10-04

> **Superseded the same day.** The first push with the lane went red in a test
> the lane's work had rewritten, while the lane itself was green on every job.
> The bounds it sheltered are gone, so there is nothing for it to shelter. The
> `TestClock`-driven tests it introduced stay; that part of this record holds.
- **Relates to:** `docs/ci-stability-2026-10-04.md`, `docs/flaky-tests.md`,
  [ADR-0551](0551-benchmarks-are-a-source-set-run-one-at-a-time-by-their-own-workflow.md),
  [ADR-0552](0552-a-clock-bound-has-room-and-stops-short-of-the-defect.md),
  [ADR-0553](0553-macos-and-windows-build-and-test-their-library-in-one-gradle-job.md)

## Context

Of the last forty Snapshot runs on master, twenty went red. Read one by one,
about half were cross-platform bugs the matrix exists to catch: a class that
would not initialise on macOS, build-logic path tests that assumed a Linux
temporary directory, a golden that photographed the build machine. The other
half came from three test classes, and from one cause.

`SdlAudioSinkTest`, `TooltipTest` and `HttpIOTest` compare something with a wall
clock: the dummy device drains at a real rate, a tooltip's dwell is real time, a
read times out after a real 300 ms. Since ADR-0553 the macOS and Windows jobs run
`./gradlew build` for every module in one invocation, with `org.gradle.parallel`
on and no worker cap, on a four-core runner. The `:core` suite, the `:media`
suite, the `:widgets` suite, SpotBugs, PMD and javadoc run at once, and the test
JVM reads the scheduler. On 2026-10-04 a `sleep(1)` loop managed five iterations
in a second and a half; ADR-0552 had widened the bounds the day before, and the
next run showed that widening does not converge. The first release attempt of
v2026.2 died on the same cause, in `HttpIOTest`, on Windows.

Three things were true at once. A test that measures time needs the machine to
itself, or it measures the machine. Most tests that waited a delay out did not
need to: every delay a widget asks for is an `EventLoop.after`, and the loop has
read an injectable clock since the `TestClock` fixture was written. And one
flaky test turns a fourteen-job run red, with nothing to tell it from a
regression.

## Decision

**A test that reads a wall clock is tagged, and the tag runs in a lane.**
`@WallClock`, in `:core`'s test fixtures, is a JUnit tag, `wallclock`, on a class
or a method. Every `Test` task but the lane and the benchmarks excludes it;
`wallClockTest`, registered by the conventions in every module, runs it over the
test source set and holds the benchmark lane's single slot, so nothing measured
runs beside anything measured. `check` depends on it, after the module's own
suite. The workflows run it as a step of its own after `build -x wallClockTest`,
so the lane's JVM has the runner to itself. The coverage reports read its exec
file, so the floors count the tests that moved out as they did before.

Tagged on 2026-10-04: `SdlAudioSinkTest`, `VideoPlaybackTest` and `HttpIOTest`
whole; `NetworkPlaybackTest.closeWhileOpening`; `HeadlessBackendTest`'s pending
frame and pump timeout tests.

**The lane is the only place a failed test is run again.** The test-retry plugin
is applied by the conventions and configured on `wallClockTest` alone: two
retries, no retry at all past three failures in one run, and a pass after a
retry reported as flaky rather than hidden. Every other test task keeps zero
retries. A failure in the lane is a warning annotation on the run, since the
lane will run it again; a failure that persists fails the build, which is the
error.

**A delay that goes through the loop is driven, not slept past.**
`GoldberryRuntime.install(backend, loop)` starts the launcher over a loop the
test made, and `TestClock.loopOver(backend)` is such a loop. `TooltipTest` moves
the clock 499 ms and reads "not yet", two more and reads "up"; a token of 60 ms
is read at 61, a 60px token at 250 and at 501. Two facts make the readings exact
rather than probable: an event posted to the backend is delivered by the next
pump, and `TimerQueue.fireDue` collects what is due before running any of it, so
a zero-delay timer scheduled during a drain fires after the next pump and never
before. The test's one remaining wall-clock wait is the launcher's own 250 ms
spurious-exit window, which a sleep can only overshoot. The assumption that
aborted a run read too late is gone, because there is no longer a reading that
can be too late.

**The `:media` coverage floor is read on one leg.** It ran in all four Media
jobs, and which of `:media`'s tests run moves with the machine's decoders, so
the same commit measured 0.87 against a floor of 0.88 on one runner and over it
on three. It is gated on linux-x64, as `:core`'s and `:widgets`' floors are.

## Alternatives

- **Retry everything.** The whole suite retried hides a deterministic failure
  that happens to pass the second time, and the repository's rule since the
  first review is that a gate that is sometimes wrong teaches people to skip the
  report. Retries stay where a wrong answer has a known cause.
- **Cap Gradle's workers on the small runners.** `--max-workers=2` would slow
  the whole build to help a dozen tests, and a test that reads the machine still
  reads a machine with a compile beside it. Kept as the fallback if the lane
  turns out not to be enough.
- **Widen the bounds again.** ADR-0552 did, and the next run read five
  iterations in 1.5 s. A bound wide enough for that is wide enough to pass the
  defect.
- **A source set, as the benchmarks are.** The tagged tests are tests: they
  assert, they count toward coverage, and `check` must run them. A tag keeps
  them beside the tests they belong with.

## Consequences

- `build` on a four-core runner no longer runs a test that reads the machine
  beside the build; a red lane run is retried before it is believed and a red
  `test` run is a regression.
- A new test that compares a duration with a bound wears `@WallClock`, or drives
  a `TestClock` and wears nothing. `docs/flaky-tests.md` is the ledger a repeat
  offender is looked up in.
- The root aggregate report (`testCodeCoverageReport`) reads the `test` suite's
  data and not the lane's, so the Codecov figure is a point low; the per-module
  floors read both.
- The woven run on the Linux Java job is `./gradlew test` and does not run the
  lane; every tagged test needs a library that job does not have.
