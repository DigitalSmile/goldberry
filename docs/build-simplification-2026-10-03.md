# Build simplification, the benchmark lane and clock bounds (2026-10-03)

The decisions are
[ADR-0550](../book/src/adr/0550-a-module-says-what-its-tests-need-and-a-plugin-wires-it.md) (the build),
[ADR-0551](../book/src/adr/0551-benchmarks-are-a-source-set-run-one-at-a-time-by-their-own-workflow.md) (the benchmark lane) and
[ADR-0552](../book/src/adr/0552-a-clock-bound-has-room-and-stops-short-of-the-defect.md) (clock bounds and floors).
This file is the working notes and the status of the work.

## What was asked

1. Ease the build: the convention plugins the review of the build's shape suggested.
2. Benchmarks run nightly only, never on a snapshot or a commit.
3. The nightly does not run when nothing changed.
4. The gates get room: the clock bounds and the floors that fluctuate.
5. The benchmarks are a task CI can run by hand or nightly.

## The shape before

```
:core, :widgets, :html, :emoji, :gpu, :media, :example
    evaluationDependsOn(':natives') + project(':natives').ext.hostNativeLibrary
    17 hand-written loops forwarding -D/-P properties, no two lists alike
    gpuTest x4, resolveTool x3, prepareAssets/vendorLicences x3
:media    -> :gpu.sourceSets.test.output, :natives.sourceSets.test.output
:example  -> configures :natives' tasks from outside
benchmarks: @Tag("benchmark") in the test trees, run in parallel nightly, every night
```

## The shape after

```
build-logic
  goldberry.java-conventions --applies--> goldberry.benchmarks   (src/benchmark, one at a time)
  goldberry.native-tests     --> NativeTests (Java): wire(), gpuTests(), probe()
  goldberry.asset-tool       --> AssetTool (Java): bundle(), licences(), catalogs()
  NativeTarget, ToolLookup, ForwardedProperties (Java, unit-tested)

test fixtures:  :natives (requirements, GpuTestLauncher)  :gpu (CompositeHarness)
                :core (goldens, RendererRequirement, TimeBudget)
:example nativeImage <-- configuration hostNativesJar <-- :natives

CI: PR/push/snapshot -> no benchmark
    nightly.yml: changes job (skip if master unmoved) -> mutation, benchmarks.yml
    benchmarks.yml: workflow_dispatch (tests filter, jmh) | workflow_call
```

## Status

| # | Item | State | Where |
|---|---|---|---|
| 1a | `goldberry.native-tests` + `NativeTests` | done | build-logic `testing/` |
| 1b | `goldberry.asset-tool` + `AssetTool` | done | build-logic `assets/` |
| 1c | `NativeTarget`, `ToolLookup`, `ForwardedProperties` | done | build-logic `natives/`, `toolchain/`, `testing/` |
| 1d | Test fixtures in `:natives` and `:gpu`; no `evaluationDependsOn`, no cross-project `ext` | done | four classes moved |
| 1e | `:example`'s image takes the natives jar through a configuration | done | `hostNativesJar` |
| 1f | CI natives built through `:natives:cmakeBuild` | **macOS, Windows: done, not yet run on a hosted runner.** Linux: stays CMake in the container | ADR-0553; one job per platform. The container has no libdecor, which `checkToolchain` requires and ADR-0422 ships without |
| 2 | Benchmarks in `src/benchmark/java`, tag gone, one at a time | done | 17 benchmarks, 4 probes, `AxisLabellingBenchmark` new |
| 3 | Nightly skips when master has not moved | done | `nightly.yml` `changes` job |
| 4a | `TimeBudget` and `goldberry.timing.slack` | done | `:core` test fixtures |
| 4b | Clock bounds widened (table in ADR-0552) | done | 8 tests, 2 benchmarks |
| 4c | Coverage floors with room | done | `:media` 88/77 → 86/74; the other four already sat 4+ points under (ADR-0552's table) |
| 5 | `benchmarks.yml`, manual with a `--tests` filter, called by nightly | done | `.github/workflows/benchmarks.yml` |

## Guards added

- `BenchmarkLaneTest`: no benchmark in a test tree or under a tag; every probe
  task's class is in its module's `src/benchmark`; every measurement is listed
  in `docs/testing.md` §1.5; only `benchmarks.yml` runs one; nightly calls it.
- `ForwardedPropertiesTest`: every `-D`/`-P` switch a workflow sets for a test
  JVM is on the one list.
- `NativeTargetTest`: the matrix, the host detection, and that `:natives` and
  `:media` install where every module looks.
- `NativeTestsTest`, `AssetToolTest`: the extensions, through `ProjectBuilder`.
- `ToolLookupTest`, `TimeBudgetTest`.

## Found on the way

- **The benchmark lane was red before this started.** The showcase's refactor
  into a screen per chapter renamed `basic`, and `FrameBudgetBenchmark` failed
  five of its six tests on the name. Nothing under `check` could see it. It
  measures `buttons` now, and its screens live in `FrameBudgetScreens` in the
  test tree, where `FrameBudgetScreensTest` resolves them on every build: the
  rule `ShowcaseActionsTest` already applies to `BindingBenchmark`'s names.
- **`TimeBudget` first kept a fixed 1 ms under its defect**, which swallowed
  `AxisLabellingBenchmark`'s 1 ms defect whole and allowed it nothing. The
  margin is a share now, the last twentieth, and `TimeBudgetTest` has a
  microsecond case.
- **A module whose benchmark source set holds only probes** (`:gpu`) has classes
  and no tests, which Gradle 9 fails by default. `failOnNoDiscoveredTests` is off
  for the `benchmark` task.

## Verification (2026-10-03, this machine: linux-x64, NVIDIA under X11)

| Run | Result |
|---|---|
| `./gradlew -p build-logic test` | green, every repository guard included |
| `./gradlew test testWithoutGpu testNativesJar --continue` | green after `DeclaredFontResourceTest` read the new spelling; `:media`'s 65 skips are all another system's decoders |
| The clock tests, five times with every core busy | green five times |
| `./gradlew check --continue -Pgoldberry.gpu.videoDriver=x11` | green; the four GPU lanes ran 108 tests, none skipped |
| `./gradlew benchmark --continue` | green; seven modules, one after another, no two overlapping (from the result files' timestamps) |
| `./gradlew :example:nativeImage --dry-run` | stage, then the host natives jar, then the image |
| CI's Java job, `build checkLicenses checkMarkdown -Pgoldberry.skipNative=true`, then the woven `test` | woven run green. The first run loaded this checkout's library under Wayland and `:natives:gpuTest` aborted, which the old scripts did too; run again as CI sees it, with `-Dgoldberry.native.library=/nonexistent/libgoldberry.so`, see below |
| `./gradlew :example:run -Pgoldberry.example.frames=3 -Dgoldberry.backend.videoDriver=dummy` | three frames on the module path |

## Red without the library, and fixed here

Run as CI's Java job runs, with no `libgoldberry` and no FFmpeg to find, 19
tests failed. They failed the same way at `HEAD` (981796fa) in a clean worktree,
before any of this, and all of them were a skip turned into a failure:

- `ShowcaseDocumentsTest.everyScreenInflates` and `.bindingsReachTheModel`
  (`:example`): every document now includes the Markdown and HTML chapters,
  which parse through md4c inside `libgoldberry`, and neither test asked
  `RendererRequirement` first, so they ended in `NoClassDefFoundError`. They ask
  now, as `markdownIsLive` beside them already did.
- `OverlaysChapterTest`, `MenusChapterTest`, `NavigationChapterTest` and
  `CollectionsChapterTest` (16 tests): `@BeforeEach` aborts on
  `RendererRequirement` before the fonts open, and `@AfterEach` closed them
  anyway, so every skip became a `NullPointerException`. The close is guarded,
  as `LayoutChapterTest`'s already was.
- `GStreamerTest.noDecoder` (`:media`): FFmpeg demuxes the clip, and its
  requirement aborted *inside* `assertThrows`, which reads as the wrong
  exception. It asks `FfmpegRequirement` first now. CI's runner has no
  GStreamer, so this one only showed on a machine that has it.

After: `./gradlew build checkLicenses checkMarkdown -Pgoldberry.skipNative=true
-Dgoldberry.native.library=/nonexistent/libgoldberry.so
-Dgoldberry.media.libdir=/nonexistent` green, and with both libraries every one
of those tests runs and passes.

`VideoPlaybackTest`'s "to the end" case failed once, missing `OPENING`, which is
the flake TODO.md already carries.

## Merging the platform jobs (ADR-0553)

`macos.yml` and `windows.yml` are one job each: `:natives:cmakeBuild` (behind
`ilammy/msvc-dev-cmd` on Windows), the web view check, `./gradlew build` with
`native.required` and `webview.required` minus the coverage floors and (on
Windows) the GPU lanes, then the `native-<target>` upload `publish.yml` reads.
The upstream `*-src` clones are cached per target, keyed on the catalog.

Probed before deciding Linux, in `quay.io/pypa/manylinux_2_28_x86_64`
(2026-10-03): `xkeyboard-config` 2.28 is installed, `mesa-libEGL-devel` is
available, `pkgconfig(libdecor-0)` is provided by nothing, EPEL included. So
`checkToolchain` would stop a Gradle build in the release container, and the
only waiver it has would also waive D-Bus, IBus and udev for the shipped
library. Linux keeps its three jobs until the check learns a release-container
profile.

Run here as the job runs it, on linux-x64 (the closest this machine comes):
`./gradlew :natives:cmakeBuild`, then `./gradlew build --continue -x
jacocoTestCoverageVerification -x gpuTest -Pgoldberry.native.required=true
-Pgoldberry.webview.required=true` -- green, every test task that loads the
library executed, no GPU lane and no floor. `-p build-logic test` green with
`PublishWorkflowsTest.platformsBuildThroughGradle` and `WindowsToolchainTest`'s
windows.yml case.

What this needs next is a hosted run: the first pull request is the test. On a
red run, `-x gpuTest` and `-x jacocoTestCoverageVerification` are the two places
to look first, then the suites these platforms never ran before (`:example`,
`:media`).
