# Measuring

<p class="gb-lede">A trace line per frame, a resize run that reports what it cost, a benchmark lane that is never a gate, and the flags that change what you measure.</p>

By the end of this chapter you can read where a slow frame went, run the
showcase under load and get one line that says what it cost, run every
benchmark in the repository, and tell a benchmark's number from a frame's.

## Per-frame timings at TRACE

Every frame logs its stages at trace. The showcase reads the level from one
property, and an application of your own sets the `dev.goldberry` logger to
`trace` in its SLF4J binding. The [logging chapter](../guide/logging.md) has
the configuration.

```sh
./gradlew run -Dgoldberry.log.level=TRACE
```

The window's line splits the frame into the buffer, the paint and the
present, and paint into three parts, because the split says where a frame's
time goes:

```text
frame <size> in <total>us: buffer <n>, paint <n> (begin <n>, draw <n>, end <n>), present <n>
```

`begin` attaches the context, `draw` is the painter's own work, and `end`
waits for Blend2D's workers. During a resize this line is the difference
between "the toolkit is slow" and "the platform is".

The launcher adds a second line per frame that did something, split by the
stages of the widget pipeline. It is a system property rather than a log
level, because a diagnostic that cost an `isTraceEnabled()` per element per
frame would be measuring itself:

```sh
./gradlew run -Dgoldberry.trace.frames=true    # frames that did something
./gradlew run -Dgoldberry.trace.frames=all     # and the quiet ones
```

A frame whose cascade is slow per element reads:

```text
frame 268 | build 0.010 style 8.636 layout 1.115 raster 1.304 ms
  | elements 125, built 0, resolved 3, invalidated 1
  | cascade 5.133 identity 0.956 motion 0.176 boxes 1.467
  | text 17 cached, 6 shaped | damage 3
```

The counts beside the timings are the point. `resolved 3` beside
`style 8.636` is a cascade that is slow per element rather than one running
too often. A frame that is slow for a reason the timings cannot show is
explained by a count and not a duration: a style cache missing on every
element, or a click invalidating the whole tree, shows in the counts first.

> [!TIP]
> The showcase has a HUD on `Ctrl+F` that shows the same stages over the last
> sixty frames. Switch it on during a drag or a resize, when there is
> something to watch.

## The showcase under load

The launcher reads four flags and leaves every other argument to the
application:

| Flag | What it does |
|---|---|
| `--frames=N` | Paints `N` frames and exits |
| `--size=WxH` | Opens the window at that size |
| `--resize=WxH` | Walks the window a pixel a frame on each axis from its opening size to `WxH` and back, for as long as the run lasts |
| `--late-budget=N` | Past `N` missed refreshes the launcher exits non-zero after shutdown, with the summary in the message |

A drag is a resize event per pointer motion, and that is the load a frame loop
has to be measured under. A jump from one size to another is one reallocation
and says nothing. The walk steps between frames, because a resize asked for
from inside a painter changes the size under the frame being painted on every
driver where `SDL_SetWindowSize` is synchronous.

```sh
./example/build/native/goldberry-showcase-linux-x64 --frames=300 --resize=1580x1100
```

After the window closes the launcher logs one line, in `Locale.ROOT` so a
workflow can grep it:

```text
frames: 300 frame(s) painted, 2 late; paint mean 1.31 ms, worst 8.90 ms; display 60.0 Hz
```

The Showcase workflow runs that walk on Linux, macOS and Windows and puts the
line in the step summary. It asserts no budget. On its headless runners two of
the legs report:

| Leg | Frames | Late | Paint mean | Worst | Display |
|---|---|---|---|---|---|
| `linux-x64` | 302 | 75 | 10.14 ms | 1799.59 ms | 0.0 Hz |
| `macos-aarch64` | 300 | 200 | 6.42 ms | 200.26 ms | 60.0 Hz |

`display 0.0 Hz` is Xvfb reporting no refresh rate, so on Linux "late" counts
missed ticks of the pacer's own timer, and on each leg the worst frame is
start-up. `--late-budget` is for a local run against a real display, which is
where it means something.

Under Gradle the same run is `./gradlew run -Pgoldberry.example.frames=300`,
and `-Pgoldberry.backend.videoDriver=dummy` keeps it off the real compositor.
Use the Gradle property rather than `SDL_VIDEODRIVER` in the environment,
because a `JavaExec` fork inherits the daemon's environment and not the
shell's.

## The benchmark lane

A benchmark is a JUnit class under a module's `src/benchmark/java`, and a
probe is a `main` beside it. `./gradlew benchmark` runs every module's
benchmarks, `./gradlew :widgets:benchmark` one module's, and `check` only
compiles them, so a benchmark that stops building fails on the commit that
broke it. They print their measurements and assert almost nothing,
because a timing assertion on shared CI hardware fails for reasons that have
nothing to do with the code.

They run **one module at a time**, whatever `org.gradle.parallel` says: every
`benchmark` task holds a shared build service with one slot. Two benchmarks
measured together measure each other, and a table taken beside a compiler is a
table of the compiler.

The lane has a workflow of its own, **Benchmarks**, which compiles the
benchmarks, runs them, and runs JMH. Start it from the Actions tab, with a
`--tests` filter to run one, or let the nightly workflow call it: nightly runs
only when master has moved since the last nightly that did any work, because
the same commit measured twice is the same numbers. No pull request, push or
snapshot runs a benchmark.

The inventory:

| Benchmark | Module | What it prices |
|---|---|---|
| `PaintBenchmark` | `:core` | Painting a frame, and what Blend2D's workers do to it |
| `TextBenchmark` | `:core` | The text path: shaping, the paragraph cache, wrapping |
| `EditorKeystrokeBenchmark` | `:core` | One keystroke into a 2 kB, a 50 kB and a 500 kB editor document |
| `FrameBenchmark` | `:widgets` | A frame of a real widget tree, split by stage |
| `DeepTreeStyleBenchmark` | `:widgets` | A 50-, 100- and 200-deep tree's first frame and a middle ancestor's hover, against the catalog's sheets |
| `TextAreaFrameBenchmark` | `:widgets` | One keystroke into a 2 kB, a 50 kB and a 500 kB note |
| `AxisLabellingBenchmark` | `:widgets` | One numeric and one time-axis labelling, against a third of a frame |
| `MarkdownFrameBenchmark` | `:html` | The same keystroke through a `markdown-view`, with an md4c control row |
| `PlaneCopyBenchmark` | `:media` | Copying and converting a 4K picture's planes, against a picture's time at 60 fps |
| `ReadPacketBenchmark` | `:media` | One `read_packet` through the upcall stub against a direct call |
| `BindingSchemeBenchmark` | `:weaver` | The two ways of binding a model, against each other |
| `BindingCodegenBenchmark` | `:weaver` | Whether a jar should generate its binding rather than reflect |
| `DowncallBenchmark` | `:natives` | One foreign call, held both ways |
| `ModifierPollBenchmark` | `:natives` | The per-event modifier poll |
| `BindingBenchmark` | `:example` | A `Property` per value against plain bound fields, and the showcase's own names |
| `FrameBudgetBenchmark` | `:example` | A frame of the real application, stage by stage and resolution by resolution |

And the probes, each run by a task of its own and read by a person, because
they open a real window or device and the numbers are the machine's:

| Probe | Task | What it measures |
|---|---|---|
| `GpuPresentProbe` | `:natives:gpuPresentProbe` | Window-surface and composited present, and the swapchain switch |
| `GpuVideoProbe` | `:gpu:gpuVideoProbe` | A composited frame with a 4K video layer under the UI |
| `VideoLayerProbe` | `:gpu:videoLayerProbe` | The video layer's upload of a new 4K picture each frame |
| `VideoPresentProbe` | `:media:videoPresentProbe` | A minute of 4K60 video in a window: dropped pictures and CPU time |

Run one by name:

```sh
./gradlew :widgets:benchmark --tests '*TextAreaFrameBenchmark*'
./gradlew :example:benchmark --tests '*FrameBudgetBenchmark*' -i
./gradlew benchmark --tests '*Text*'
```

The last names benchmarks in two modules, and every other module's task is
given the same filter and finds nothing, which is not a failure.

`MarkdownFrameBenchmark` carries a control row, md4c timed on its own. Two
runs whose parse times disagree are two runs on two differently loaded
machines, and their other rows should not be compared.

### JMH on `:core`

The hot seams that are pure logic have JMH, as a `src/jmh` source set with
forks, warm-up and a blackhole, so a number is not the JIT specialising a loop
away. `CascadeBenchmark` resolves one element against a six-rule sheet at
0.476 ± 0.067 µs/op.

```sh
./gradlew :core:jmh
```

That is what JMH adds over the `benchmark` task, and the only reason to run
both.

## A cost is guarded by a count, never by a clock

A test under `check` that asserts a millisecond fails for what else the
machine was doing. `FrameBudgetBenchmark`'s style row reads 3.6 ms on an idle
machine and 20 ms under a parallel Gradle, on the same commit, which is why it
lives in the benchmark lane and its table is read rather than asserted.

What `check` is owed instead is a count. A keystroke into a 500 kB note shapes
and draws about what a keystroke into a 2 kB note does, 2 021 characters
against 1 941, and a test under `check` asserts that count rather than a
time. A `markdown-view` is held the same way, in blocks built, blocks kept
and paragraphs shaped. A count says the same thing under a parallel build and
a millisecond does not.

> [!IMPORTANT]
> A benchmark's scaffolding is under `check`. `BindingBenchmark` runs nightly
> only, so the two names it resolves are resolved by a test on every build.

## The flags

Each is a system property on the Java command line, so `-D` on a plain
launch, and on `./gradlew run` the showcase forwards the ones below.

| Property | Values | What it changes | Default |
|---|---|---|---|
| `goldberry.paint.threads` | `0` for synchronous painting, `N` to pin the worker count | Blend2D's workers | up to four on any surface over 400×300 |
| `goldberry.frame.rate` | `N` overrides the display's rate, `0` measures the unthrottled loop | The pacer, which `0` switches off | the rate read off the window's display |
| `goldberry.backend.vsync` | `false` | Stops asking SDL to hold each present until vertical blank | `true` |
| `goldberry.gpu` | `off` | Keeps every window on the CPU | on, when `goldberry-gpu` is on the module path |
| `goldberry.gpu.composite` | `always`, `auto`, `never` | Whether a window is composited always, only while it shows GPU content, or not at all | `always` |
| `goldberry.backend.videoDriver` | `dummy` | SDL's video driver, for a headless run that touches no compositor | SDL's own choice, X11 first on Linux |
| `goldberry.trace.frames` | `true`, `all` | The launcher's per-frame stage line, with its counts | off |
| `goldberry.log.native` | `false` | Gives GLib and SDL their stderr back, past the SLF4J bridge | `true`, the bridge installed |
| `goldberry.log.level` | `TRACE` | The showcase's `logback.xml` level for `dev.goldberry`. Your application names its own | `DEBUG` |

A tuning flag that is not understood is taken as the default. A typo should
not stop a window opening.

## A benchmark is not a frame

The same 960×640 scene paints in 0.34 ms in `PaintBenchmark` and in 2.15 ms
in the running showcase, both with four workers. The difference is `present`.
The benchmark's own loop, run inside the live application between two real
frames, is four times faster than the frames on either side, and with
`present` skipped and nothing else changed the paint falls from 2.193 ms to
0.574 ms.

`present` moves megabytes and crosses into the kernel, and by the time the
next frame begins the pipelines, the destination pixels, the glyph caches and
the page tables that reach them have all been displaced. So:

- A benchmark compares options against each other. Worker counts, surface
  sizes and algorithms, with everything else held constant.
- A claim about what a frame costs comes from a frame. Read it off the trace
  line or the launcher's summary, in a window that presents.

The [summary page](index.md#where-the-time-goes-at-960640) quotes both
numbers for that reason, and labels each.
