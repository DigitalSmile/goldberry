# Tests and gates

<p class="gb-lede">Pixels are the assertion for painters, every suite runs in both binding modes, and a cost is guarded by a count rather than a clock.</p>

`docs/testing.md` is the full account, with the status of every piece. This chapter is the part a contributor needs before the first pull request.

## Principles

1. **Determinism is a feature under test.** Embedded fonts, CPU rasterization, seeded randomness and a virtual clock make output byte-stable. The suite asserts exact results, and nondeterminism is itself a bug. `DeterminismTest` bans clocks, randomness, the default locale and the default time zone in the layer whose output is asserted exactly.
2. **Pixels are the assertion for painters.** Line coverage of paint code proves reachability. Golden images prove correctness. Logic modules are asserted the classic way.
3. **Both execution modes are first-class.** Every suite runs bound reflectively, which is what a jar does, and again woven, which is what a native image does. The two must agree, and CI runs both.
4. **A widget is not done until it is in the gallery.** The showcase is the demo, the visual-regression corpus and the accessibility sweep at once.
5. **Test the seams that break silently.** Native struct layouts, cascade order, cache invalidation, focus order. Not getters.

## The test kinds

| Kind | What it is | Where |
|---|---|---|
| Unit | JUnit 5 over pure logic with exact assertions: the CSS compiler, KDL and the inflater's errors, Yoga property mapping, text segmentation, tick labelling, aggregation, easing, binding paths. jqwik property tests over the chart stack | Every module |
| Headless widget | The `headless` backend renders to memory and pumps synthetic events through the normal dispatch path. A virtual clock steps animations. Every test task presses `Ctrl` as the primary modifier on every desktop | `:core`, `:widgets`, `:html`, `:example` |
| Golden | A rendered frame against a committed PNG, with a tolerance | `:widgets`, `:core`, `:html`, `:example` |
| Native layer | The layout probe, the export list, FFM lifecycle, and the GPU tests on the JVM's first thread | `:natives`, `:gpu`, `:media` |
| Benchmark | JUnit classes and probes under `src/benchmark/java`. They print numbers and assert almost nothing | `./gradlew benchmark` and the Benchmarks workflow; `check` only compiles them |
| Accessibility | Contrast over the token tables, and sweeps that read the widget registry | `:widgets` |
| Drift guard | A test that reads the repository rather than the JVM | `build-logic`, `:natives` |

[Repository layout](repository.md#tests-as-drift-guards) lists the drift guards. The primary-modifier pin is [ADR-0396](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0396-a-test-presses-the-same-modifier-on-every-desktop.md).

## What `check` runs

```sh
./gradlew check
```

In every module under the conventions, `check` runs:

- the compile, with Error Prone and NullAway as errors in `src/main`;
- `spotlessCheck`, `pmdMain`, and SpotBugs as a report;
- `test`, and in `:natives`, `:gpu`, `:media` and `:example` also `gpuTest`;
- `benchmarkClasses`: the benchmarks compile, and none of them runs;
- `jacocoTestCoverageVerification`, which passes trivially where a module declares no floor;
- `javadoc`, in every published module, with doclint on;
- in `:natives`, the tests of the included `build-logic` build.

CI's fast lane adds `checkLicenses` and `checkMarkdown`, and then runs the suite a second time woven:

```sh
./gradlew build checkLicenses checkMarkdown -Pgoldberry.skipNative=true
./gradlew test -Pgoldberry.skipNative=true -Pgoldberry.nativeImage=true
```

Two invocations rather than two tasks, because weaving rewrites the compiled classes in place and one build cannot hold both forms. Coverage survives the split: the exec file is named for the mode and the report reads every file it finds. The record is [ADR-0155](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0155-a-jar-binds-at-run-time-an-image-is-woven.md).

> [!IMPORTANT]
> `-Pgoldberry.lenient` turns Error Prone off and is for triage only. Nothing in CI sets it, so `check` never passes with it.

### With and without the library

Every module whose tests load `libgoldberry` applies one plugin, `goldberry.native-tests`, and every test task in it gets the same wiring: JEP 472's grant, the library named by `-Dgoldberry.native.library` or else the one this machine's `:natives:cmakeBuild` makes, that library as an input so a rebuilt one re-runs what paints through it, and `cmakeBuild` first unless `-Pgoldberry.skipNative=true` asks for a Java-only build or a library was handed over. The switches a test reads from the command line — `goldberry.native.required`, `goldberry.golden.update`, `goldberry.gpu.videoDriver`, `goldberry.timing.slack` and the rest — reach every test JVM from one list in build-logic, `ForwardedProperties`, so a flag cannot work in one module and stop at the Gradle daemon in the next. The record is [ADR-0550](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0550-a-module-says-what-its-tests-need-and-a-plugin-wires-it.md).

The Java-only jobs build with `-Pgoldberry.skipNative=true`, so a test that rasterizes calls `RendererRequirement.enforce()` first and skips without a library. To check a change the way those jobs will, point the library at nothing and run everything:

```sh
./gradlew test --continue -Dgoldberry.native.library=/nonexistent/libgoldberry.so
```

Failures there are the defect. Skips are correct. That is [ADR-0357](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0357-a-test-that-paints-asks-for-the-library-and-a-download-asks-twice.md).

The per-platform jobs do the opposite. They download the library their native job built and pass `-Dgoldberry.native.required=true`, so a skip is a failure. CI verifies the binary that ships and cannot pass without loading it, which is [ADR-0016](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0016-verify-the-artifact-and-never-skip-the-check.md).

The web view library, `libgoldberry-webview`, may be missing from a local build where WebKitGTK's headers are absent, so its tests skip without it. A published one may not: CI builds it for every target (on Linux in a job of its own, on Ubuntu 22.04, since the manylinux image has no WebKitGTK 4.1). Every verify job puts it beside `libgoldberry` and passes `-Dgoldberry.webview.required=true`, so `WebviewBindingTest` fails rather than skips if the library does not load, report the right ABI and bind. That is [ADR-0521](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0521-every-natives-jar-carries-the-web-view.md).

### The GPU tests

GPU tests are tagged `gpu`, left out of `test`, and run by `:natives:gpuTest`, `:gpu:gpuTest`, `:media:gpuTest` and `:example:gpuTest` on the JVM's first thread, which macOS's Cocoa needs for a GPU device. They need a video driver with a Metal view or a Vulkan surface: the desktop's default, or `-Pgoldberry.gpu.videoDriver=offscreen` for lavapipe on a runner with no display, and never `dummy`. They skip without a device, and `-Pgoldberry.gpu.required=true` makes that a failure. The record is [ADR-0475](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0475-sdl-gpu-is-bound-for-core-and-gpu-and-tested-on-the-first-thread.md).

## Goldens

A golden compares a rendered frame against a committed PNG. **One** reference set serves every platform, compared with a per-channel tolerance of 2 of 256 and a cap of 2% of pixels allowed to differ at all. Both gates must pass. Blend2D compiles its rasterizer pipelines at run time for the CPU it finds, AVX2 on one runner and NEON on another, and the pipelines are not required to agree on the last bit of a blended subpixel. The tolerance absorbs antialiased edges and nothing else: a colour that changed or a box in the wrong place moves thousands of pixels by tens of levels. That is [ADR-0050](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0050-golden-images-have-a-tolerance.md).

**The scale sweep.** Every golden that matches is then redrawn at 2x and 1.5x and checked for describing the same picture, with nothing further committed. It exists because a layer blitted at the wrong size was invisible at 1x on every machine the goldens ran on, which is [ADR-0157](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0157-a-layer-is-blitted-into-its-own-size.md). The sweep itself is [ADR-0162](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0162-a-golden-is-checked-at-every-scale.md), and `-Dgoldberry.golden.scales=false` turns it off.

Goldens render through the shipped `Offscreen` entry point, so every golden also tests the API an application would use to take the same picture. That is [ADR-0284](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0284-a-picture-with-no-window-under-it.md).

**A golden does not photograph the machine.** Some screens show what the build machine has, such as the capabilities `libgoldberry` was compiled with, or whether the web view and FFmpeg are there. Each of these is pinned for the picture. The test task points the web view and FFmpeg at nothing. `ShowcaseScene` hands every screen a fixed set of capabilities instead of `Goldberry.capabilities()`. A screen that reads anything else from the machine takes it from its `GalleryContext` too. That is [ADR-0554](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0554-a-picture-of-a-screen-pins-what-the-build-can-do.md).

**Blessing a change.** After a deliberate visual change:

```sh
./gradlew blessGoldens
git diff --stat
```

The task runs the whole suite with `goldberry.golden.update=true` and rewrites every reference from what the code draws. It is not part of `check`, because a build that re-blessed on every run would assert nothing. Commit the PNGs and explain the visual change in the pull request. The image diff is the review. A golden failure in CI uploads the actual, expected and diff images as a run artifact.

## Static analysis

| Tier | Tool | Scope | What it does |
|---|---|---|---|
| Blocking at compile | Spotless with palantir-java-format | Every Java file | `spotlessCheck` fails, `spotlessApply` fixes. The step is wrapped so an import that only a `///` doc link uses is kept |
| Blocking at compile | Error Prone | `src/main` | A finding is a compile error. Off for tests and JMH sources |
| Blocking at compile | NullAway | `src/main`, every `@NullMarked` package | `OnlyNullMarked` mode, and since [ADR-0497](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0497-every-package-says-what-it-is-and-is-null-marked.md) that is every package |
| Blocking after triage | PMD | `src/main` | The hand-picked rules in `config/pmd/ruleset.xml`. `module-info.java` is excluded on purpose, because PMD cannot read two modifiers on a `requires` |
| Reports | SpotBugs | `src/main`, max effort | `build/reports/spotbugs/main.html` per module. The triage has not happened, so it does not gate |
| Blocking | ArchUnit | The whole graph, from `:widgets` | `BoundaryTest` asserts the module arrows and the FFM boundary. `DeterminismTest` bans clocks, randomness and the default locale |
| Blocking | Javadoc | Every published module | `check` generates it with doclint on, so a rotted `[link]` fails four minutes in rather than partway through an upload |
| Blocking | `checkMarkdown` | `docs/`, `book/`, the top level | Trailing whitespace and a missing final newline. `formatMarkdown` fixes them |

The javadoc gate is [ADR-0343](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0343-the-published-javadoc-is-linted.md) and [ADR-0405](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0405-check-generates-the-published-javadoc.md). The PMD exclusion and the Markdown tasks are [ADR-0398](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0398-the-build-declares-what-it-actually-writes.md).

> [!NOTE]
> `:natives` runs no Error Prone. It is hand-written FFM bindings, and its contract is the layout probe. Its `@Nullable` annotations are still there, because `:core`'s NullAway reads them.

### Advisory dashboards

Three more analysers run and none of them blocks a merge:

- **CodeQL**, in `codeql.yml`, on pull requests and weekly. The first triage is [ADR-0341](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0341-codeql-findings-are-fixed-where-real-and-answered-where-not.md): every finding that stays is in its table with a reason. The alerts need a token to read, and `docs/testing.md` §2 has the recipe for running the same scan locally in about six minutes.
- **Qodana**, in `qodana.yml`, against a committed baseline in `config/qodana/`. The gate fails on new high-severity findings only. The profile is reviewed as code, which is [ADR-0498](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0498-qodana-reads-a-reviewed-profile-and-a-bound-value-may-be-null.md). It inspects `main` only. The tests are left out, and so is every other source set, `src/benchmark` and `src/jmh`, by name in `qodana.yaml`; `QodanaScopeTest` fails on one that is not ([ADR-0555](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0555-qodana-inspects-the-toolkit-not-its-measurement-code.md)).
- **Codecov**, a step in `linux.yml`, guarded on its secret and silent until connected. `docs/testing.md` §7 is the checklist for connecting it.

Coverage is one of them. JaCoCo measures every module, the Linux Java job aggregates both binding modes into one report and uploads it, and nothing reads the figure as a gate: there is no coverage floor in any module. There were, set from measured values and four points under what the suite reached, and the figure still moved with which tests a machine ran -- which decoders it had, which library it loaded -- so the same commit measured a point under `:media`'s floor on one runner and over it on three. A number that goes red for the machine's reasons is read as a dashboard, not a gate. That is [ADR-0560](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0560-no-test-compares-a-duration-with-a-bound-and-no-module-has-a-coverage-floor.md).

## A cost is guarded by a count

A timing assertion on shared CI hardware fails for reasons that have nothing to do with the code. So the benchmarks live in a source set of their own, print measurements and assert almost nothing, and `check` only compiles them:

```sh
./gradlew benchmark                                        # every module, one at a time
./gradlew :widgets:benchmark --tests '*TextAreaFrameBenchmark*'
```

No pull request, push or snapshot runs them. The Benchmarks workflow does, by hand or when the nightly workflow calls it, and the nightly does nothing on a night master has not moved. That is [ADR-0551](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0551-benchmarks-are-a-source-set-run-one-at-a-time-by-their-own-workflow.md).

What `check` is owed instead is the counting pair. `TextAreaKeystrokeCostTest` asserts that typing into a 500 kB note shapes and draws about what typing into a 2 kB note does, in characters, which is the same number on every machine. `BlockReuseTest` counts blocks built and kept for the Markdown preview. `FrameBudgetBenchmark`'s style row reads 3.6 ms alone and 20 ms under a parallel Gradle, which is why it is a benchmark and not a gate. The inventory of benchmarks is in `docs/testing.md` §1.5, and [Measuring](../performance/measuring.md) says how to read one.

### A clock bound has room

A bound on a measured duration lives in a benchmark and nowhere else. A benchmark that holds a figure compares it with a `TimeBudget` from `:core`'s test fixtures, which has two numbers: **the bound**, a generous multiple of what the operation takes when it works, and **the defect**, what it takes when it is broken — the timeout it was meant to beat. The allowance may grow towards the defect and never reach it, and `-Dgoldberry.timing.slack=3` multiplies every bound by three for a machine known to be loaded; it is `1` when unset and cannot be less. That is [ADR-0552](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0552-a-clock-bound-has-room-and-stops-short-of-the-defect.md), which still holds for the benchmarks.

```java
TimeBudget.of(Duration.ofSeconds(2)).shortOf(Duration.ofSeconds(5))
        .assertWithin(elapsed, "the style pass on the Diagnostics screen");
```

No test under `check` reads one. For a day the tests that could not avoid a clock -- a pump that must return *promptly*, a 404 that must fail *at once* rather than after a backoff, the SDL sink draining at the dummy device's rate -- compared a measured duration with a `TimeBudget`, then ran in a lane of their own with retries, and the bounds did not converge: a `sleep(1)` loop under a parallel build on a four-core runner read five times in a second and a half, and a 4x stream drained at 1.34x the wall clock. So the bounds are gone. A test asserts what holds however the machine schedules it: that a read *gives up* with the exception the timeout names, that a queue *never rises* with nothing written, that a close *returns*. How long each took is not compared with anything. Where the defect is a wait that would never end -- a pump that sits its timeout out, a close that waits a 30 s stall out -- the wait is made longer than the test's own `@Timeout`, so the defect fails on the hang guard every test already has and a loaded machine fails nothing. Where the assertion was *only* a figure -- the sink's drain rate at 4x, SDL's per-pull latency, a count of readings that moved -- it is gone, and what that figure guarded is a benchmark's to measure. No test task runs a failed test again. That is [ADR-0560](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0560-no-test-compares-a-duration-with-a-bound-and-no-module-has-a-coverage-floor.md).

A delay that goes through the event loop is not a clock bound at all. `EventLoop.after` reads a clock the loop was given, and `TestClock` in `:core`'s test fixtures is one a test moves. `DrivenRuntime.install(backend)` starts the launcher over it and gives a test three steps: `afterTheNextPump`, for an event it posted; `elapsed(by, then)`, for a delay; and `afterTheFirstFrame`, the one wall-clock wait, for the frame hit testing runs against. `TooltipTest` reads "not yet" at 499 ms and "up" at 501 ms, `PopupLifecycleTest` reads a focus-lost a tick past the settle delay, and no machine can make either reading wrong. A test that waits for something to happen polls until it does, with a deadline that only ends the loop, rather than sleeping a fixed time and looking once.

## Accessibility sweeps

The catalogue sweeps read the registry, not a list. `WidgetParityTest`, `ImmutabilityTest` and `ChainingTest` walk every widget `Widgets.inflater()` registers, and each has a `theExemptionsAreLive` test so an exemption cannot go stale. `SemanticsSweepTest` reads the source tree and fails on any focusable widget that does not implement `Semantics`, a role and an accessible name. Contrast is a unit test over the token tables: every text and surface pair at 4.5:1 or better in both themes.

The AccessKit bridge that would export the semantics tree is on hold, and the sweep is unaffected. That is [ADR-0440](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0440-the-accessibility-bridge-is-on-hold-and-the-semantics-tree-stays.md).

## The CI matrix

| Workflow | Trigger | What it does |
|---|---|---|
| `linux.yml` | Pull request, and every push through `snapshot.yml` | The Java job: `build checkLicenses checkMarkdown` without the library, then the suite woven, then the coverage report. The natives job builds `libgoldberry` inside a `manylinux_2_28` container, and the web view job its library on Ubuntu 22.04. The verify job runs `:natives:test`, the `:core`, `:widgets`, `:html` and `:emoji` suites with every golden, then the GPU tests on lavapipe, and `:example`'s tests, against the libraries it downloaded |
| `macos.yml`, `windows.yml` | Pull request, and every push through `snapshot.yml` | One job: `:natives:cmakeBuild`, then `./gradlew build` against the library it made with `native.required` and `webview.required`, then the upload `publish.yml` packages. The GPU lanes run on macOS, not required ([ADR-0553](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0553-macos-and-windows-build-and-test-their-library-in-one-gradle-job.md)) |
| `snapshot.yml` | Push to `master` | Calls `publish.yml`, which calls all three per-OS workflows and then uploads one `-SNAPSHOT` of every module to Central |
| `release.yml` | A `v*` tag. A manual run rehearses into `mavenLocal` | The same chain as a signed release to a Central Portal deployment, after the licence check, then a pull request that bumps the version |
| `showcase.yml` | A `v*` tag, or by hand | The GraalVM native image on three platforms, each run for 300 frames with its window resized a pixel a frame. On a tag, attached to a draft GitHub Release |
| `media.yml` | A pull request touching `media/`, and the publish chain | The FFmpeg and dav1d superbuild on macOS and Linux, and `:media`'s tests against what it built |
| `nightly.yml` | 03:40 UTC, when master has moved since the last nightly | PIT mutation testing and SpotBugs, and the Benchmarks workflow. Numbers to read, not gates to pass |
| `benchmarks.yml` | Called by `nightly.yml`, or by hand with an optional `--tests` filter | Every module's benchmarks, one module at a time, and JMH on `:core` |
| `codeql.yml` | Pull request, and weekly | The security and quality query suite |
| `qodana.yml` | Pull request and push | IntelliJ's inspections, new findings against the baseline |
| `pages.yml` | Push touching `site/` or `book/` | The landing page and this book |

The per-OS workflows have no `push` trigger of their own. A commit builds each library once, through `publish.yml`, and the library that passed verification is the one published. That is [ADR-0334](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0334-central-is-fed-once-per-run.md), and `PublishWorkflowsTest` holds every workflow to it.

**A failure names itself.** On a runner, every failed test and the build's own failure become check-run annotations, which the public API serves where a job log needs a login. That is [ADR-0338](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0338-a-red-run-says-why-in-public.md).

> [!NOTE]
> Goldens run on every pull request, not behind a label. They are the assertion for painters, so gating them behind a label would mean the check that matters most runs least.
