# CI stability: the wall-clock lane (2026-10-04)

> **Superseded the same afternoon.** The first push with the lane went red in
> `TooltipTest`'s rewritten `tearDown`, not in the lane, and the ask became
> "get rid of timing/size related tests". ADR-0560 removed every clock bound,
> the lane, the retries and the coverage floors; the `TestClock`-driven tests
> below stay. Notes: [`ci-fixes-2026-10-04.md`](ci-fixes-2026-10-04.md).

The decision is
[ADR-0559](../book/src/adr/0559-the-tests-that-read-a-wall-clock-run-in-a-lane-of-their-own.md).
This file is the working notes and the status of the work; the ledger of
individual failures is [`flaky-tests.md`](flaky-tests.md).

## What was asked

"Tests are very unstable from run to run in CI; even a small change could fail
it on some platform. What can we do?"

## What the runs said

The last forty Snapshot runs on master: 17 green, 20 red, 3 cancelled. The
eleven red runs since 2026-10-01, read job by job:

| Date | Platform | Failure | Verdict |
|---|---|---|---|
| 10-04 | Windows | `SdlAudioSinkTest.drainsSmoothly`, 5 readings in 1.5 s | timing, after the 10-03 rework |
| 10-03 | Windows | three `SdlAudioSinkTest` tests, `TooltipTest.aLengthIsNotADelay` | timing |
| 10-03 | all three | build-logic path tests, `GalleryGoldenTest` Diagnostics | real, fixed in f21f01e8 |
| 10-02, 10-03 | macOS | `VideoPlaybackTest.accurateSeek` | real, fixed in 11746a23 |
| 10-02 | Linux | `:media` floor 0.87 against 0.88 | ratchet read on four legs |
| 10-02 | all three | `MediaPlayer showing subtitles` NPE | real |
| 10-02 | Windows | `HttpIOTest.timesOut`, the first v2026.2 release | timing |
| 10-01 | all three | `SdlVideo$Holder` NoClassDefFoundError | real |

About half real, about half three classes reading a clock while
`./gradlew build` runs every module's suite and three analysers beside them on
a four-core runner.

## What was done

| Piece | Where | Status |
|---|---|---|
| `@WallClock` tag, `wallclock` | `core/src/testFixtures/.../junit/WallClock.java` | done |
| `WallClockLane`: the tag out of every other `Test` task, `wallClockTest` over the test source set in the benchmark lane's slot, `check` and the coverage reports depend on it | `build-logic/.../testing/WallClockLane.java`, applied by `goldberry.java-conventions` | done |
| Retries on the lane alone: two, none past three failures, a pass after a retry reported as flaky | `org.gradle.test-retry` 1.6.6, from the conventions | done |
| A lane failure is a warning annotation, a persisting one the build's error | `TestFailureAnnotations` takes a level | done |
| Tagged: `SdlAudioSinkTest`, `VideoPlaybackTest`, `HttpIOTest`, `NetworkPlaybackTest.closeWhileOpening`, `HeadlessBackendTest.pendingFrameDoesNotPark` and `.pumpTimesOut` | the tests | done |
| `TooltipTest` on the `TestClock`, through `GoldberryRuntime.install(backend, loop)`; no sleep decides an assertion | `core` | done |
| `GoldberryTestAccess.install(backend, loop)` for a test outside `dev.goldberry` | `core` test fixtures | done |
| `:media` floor gated on linux-x64 | `media/build.gradle` | done |
| Workflows: `build -x wallClockTest`, then the lane as its own step; the Linux verify leg runs the suites, the lane, then the floors; Media runs `check` without the lane and the floor, then both | `linux.yml`, `macos.yml`, `windows.yml`, `media.yml` | done |
| Guards: `WallClockLaneTest` (the task's shape, the annotation's tag, every workflow that pulls the lane in excludes it and runs it alone) | build-logic tests | done |
| Guide: "The wall-clock lane" under "A cost is guarded by a count"; `docs/testing.md` §1.5, §4, §6 | book, docs | done |
| Ledger | `docs/flaky-tests.md` | done |
| `DrivenRuntime` fixture: the clock-driven runtime and its three steps, shared by `TooltipTest`, `PopupLifecycleTest` and `ContextMenuSelectsTest` | `core` test fixtures | done |

## What was measured here

- `TooltipTest` on the `TestClock`: 11 tests, 2.4 s, no skip and no assumption,
  where the sleeping version took the delays out in real time and aborted a run
  it read too late.
- `:core:test` still runs 24 of `HeadlessBackendTest`'s tests; the two pump
  bounds moved to `:core:wallClockTest`.
- `:media:wallClockTest` on this machine: `SdlAudioSinkTest` 8, `VideoPlaybackTest`
  35, `HttpIOTest` 26, `NetworkPlaybackTest` 1, all green, in about 12 s of test
  time. `:media:test --tests '*SdlAudioSinkTest*'` now finds no test, which is
  the tag doing its job.
- The retry, on Gradle 9.7.1 with test-retry 1.6.6: a throwaway tagged test that
  failed on its first run and passed on its second left a green
  `:core:wallClockTest` and a report holding one failed and one passed case of
  the same name. Each attempt runs in a fresh test worker JVM, so the first
  probe, which counted its runs in a static, failed three times.

## Not done, and why

- ~~`PopupLifecycleTest` and `ContextMenuSelectsTest` still wait real time~~
  Done the same day: both drive `DrivenRuntime`, the fixture `TooltipTest`'s
  helpers became. The popup's focus-settle is read a tick past
  `Launcher.focusSettle()` on the loop's clock; the context menu's events are
  posted after the first frame and read after the next pump. No `later` is left
  in either.
- ~~`VideoPlaybackTest`'s two races are in the lane, not fixed~~ Both had been
  fixed on 2026-10-02 (45145b77) and the ledger was wrong to hold them open.
  What was left was the end-of-playback assertion, which now allows the one
  stall and recovery a starved demux thread produces and nothing else. Five
  lane runs of the class: 175 tests, all green.
- **`--max-workers` on the small runners** was not tried. The lane removes the
  tests that read the machine from the parallel build; capping the build would
  slow everything to help what has already left.
- **`widgets`' `TestLoop.later`**, in 67 files, waits for a frame and never
  asserts on time. Left alone.

## Verification

- `./gradlew :build-logic:test`: 384 tests green, the new `WallClockLaneTest`
  among them, with `SourceDocsTest` on the new guide links and `DecisionLogTest`
  on ADR-0559.
- `./gradlew :core:test --tests '*TooltipTest*' --tests '*HeadlessBackendTest*' :core:wallClockTest :media:wallClockTest`: green.
- `./gradlew :core:spotlessApply :media:spotlessApply`, then the test sources
  compile; `checkMarkdown` green; `tools/book/guide_links.py` reports no problem
  in the fifteen changed paths.
- After the second pass: `TooltipTest`, `PopupLifecycleTest` and
  `ContextMenuSelectsTest` on `DrivenRuntime`, green with no `later` left but
  the spurious-exit wait; `VideoPlaybackTest` in the lane five times, 175 green;
  build-logic, `checkMarkdown` and the guide links green again.
- Not run here: the workflows themselves. The first push shows whether the
  Windows lane step is quiet; the ledger is where its result goes.
