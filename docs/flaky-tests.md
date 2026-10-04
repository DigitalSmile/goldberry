# Flaky tests: the ledger

A test that failed on CI without a change that explains it is written down here,
with the failure's signature and the runs it was seen in, so the next person who
meets it looks it up rather than diagnosing it again. The rule that keeps the
list short is in
[ADR-0560](../book/src/adr/0560-no-test-compares-a-duration-with-a-bound-and-no-module-has-a-coverage-floor.md):
no test compares a measured duration with a bound, a wait that would never end
is set past the test's own `@Timeout`, a figure is a benchmark's, and a test
whose delay goes through the event loop drives a `TestClock` and reads an exact
time.

What goes in a row: the test, the message the failure carried, where it was seen
(workflow job and run id), what was decided, and the status. A row is **closed**
when the cause is fixed or the test is deterministic, and **open** when it has
been seen and not yet explained. Rows that were *in the lane* for the day of
ADR-0559 are closed now, each with what became of its bound. A closed row stays, because the same signature can come back.

## Timing

| Test | Signature | Seen | Decision | Status |
|---|---|---|---|---|
| `SdlAudioSinkTest.drainsSmoothly` | `only 5 readings before the deadline`; earlier `only 1 readings before the queue ran dry` | Windows Build and test, Snapshot 37193330434 (2026-10-04) and 37124296703 (2026-10-03) | The device's thread and a `sleep(1)` loop under a parallel build on four cores. The count of readings is gone; the test asserts that the queue never rises and stands still paused, however many readings land (ADR-0560) | closed |
| `SdlAudioSinkTest.latency` | `no pull was seen` | Windows Build and test, Snapshot 37124296703 | The per-pull figure is gone; the test asserts the system is asked once on open and not per write (ADR-0560) | closed |
| `SdlAudioSinkTest.rate` | `the queue drained at 1.34x the wall clock, asked for 4x` | Windows Build and test, Snapshot 37124296703 | The ratio is gone; the test asserts that SDL takes a rate and refuses one it does not (ADR-0560) | closed |
| `TooltipTest.aLengthIsNotADelay` | `a length is not a delay, so the 500ms default should still be waiting`, read after the delay had come due | Windows Build and test, Snapshot 37124296703 | The tooltip's delay is an `EventLoop.after`; the test now drives a `TestClock` through `GoldberryRuntime.install(backend, loop)` and reads at exact times. No clock bound remains in the class | closed |
| `HttpIOTest$Ranged.timesOut` | `HttpTimeoutException: request timed out` from `HttpIO.open` rather than from the read | Windows Java, release run 36985188261 (2026-10-02, the first v2026.2 attempt) | The open's 300 ms timeout fired first on a slow runner; the test gives the open its own time since 36dbf9bf. The upper bound on the wait is gone; the read must throw the timeout's exception and wait at least the timeout, under a 20 s `@Timeout` (ADR-0560) | closed |
| `VideoPlaybackTest.playsToTheEnd` | `[BUFFERING, PLAYING, ENDED]` where `OPENING` is expected first; once `[BUFFERING, PLAYING, BUFFERING, ENDED]` | Locally, full `check`, 2026-10-01 and 2026-10-03; never on CI | Two things. `OPENING` is published before the engine's threads start since 45145b77 (2026-10-02), so it is heard. A `BUFFERING` then `PLAYING` between the first `PLAYING` and `ENDED` is a demux thread starved by the machine and the state diagram's own stall and recovery, so the assertion allows exactly that and nothing else. The class wore `@WallClock` for a day | closed |
| `VideoPlaybackTest.statistics` | `expected 4 shown, got 5` | Locally, one run in three on 2026-10-01 | The test asked for a picture before the last one was prepared; it waits on `untilNextPicture()` first (45145b77, 2026-10-02). Five lane runs of the class on 2026-10-04 were green | closed |
| `VideoPlaybackTest.accurateSeek` | `expected: <PT0.5S> but was: <PT0.0005S>` | Media macOS, Snapshot 36971313547 and 37104177948 | A real defect: a paused seek's target did not hold until the sink was current. Fixed in 11746a23. Not a flake; here because it looked like one for two days | closed |

## Coverage

| Gate | Signature | Seen | Decision | Status |
|---|---|---|---|---|
| `:media:jacocoTestCoverageVerification` | `lines covered ratio is 0.87, but expected minimum is 0.88` | Media linux-x64, Snapshot 36971313547 (2026-10-02) | Which of `:media`'s tests run moves with the machine; the floor went to 0.86 and 0.74 (ADR-0552), then onto the linux-x64 leg alone (ADR-0559), then away with every other floor (ADR-0560) | closed |

## Not flakes, for the record

Red runs that looked like flakes and were cross-platform bugs the matrix is for.
Listed so the ratio is on record: of the eleven red Snapshot runs between
2026-10-01 and 2026-10-04, six were these.

| Signature | Seen | What it was |
|---|---|---|
| `NoClassDefFoundError: Could not initialize class ...SdlVideo$Holder`, 54 to 56 failures | Media and Java jobs, 2026-10-01, three runs | A class initialiser that needed the library on a job without it; fixed the same day |
| `AssetToolTest`, `NativeTestsTest` on macOS and Windows | Build and test, Snapshot 37124296703 | `@TempDir` against the canonical project directory; `java.io.tmpdir` is a symlink on macOS. Fixed in f21f01e8 |
| `GalleryGoldenTest > Diagnostics`, 1566 pixels, worst channel 189 | All three OSes, Snapshot 37124296703 | The golden photographed the build machine's capabilities. ADR-0554 |
| `MediaPlayer showing subtitles` NPE in `close()` | All three Java jobs, Snapshot 36968220745 | A test fixture that opened nothing on a doc-comment commit; fixed in the next push |
| `TooltipTest` × 11, `NullPointerException: ... DrivenRuntime.shutdown() ... "this.runtime" is null` in `tearDown` | Linux Java, Snapshot 37196908643 (2026-10-04) | The job has no library, `setUp` aborts as a skip before installing a runtime, and the `tearDown` ADR-0559 rewrote shut down what was never there. Fixed the same day, with ADR-0560 |

## How to add a row

1. Take the signature from the check-run annotation or the job log tail
   (`docs/testing.md` §4 says how a failure names itself).
2. Decide which of three it is: a clock bound, which is deleted or turned into
   a behaviour under the test's `@Timeout`; a delay through the loop, which
   becomes a `TestClock` test; or a defect, which is fixed and recorded as
   closed.
3. Write the row before the fix lands, and move its status when it does.
