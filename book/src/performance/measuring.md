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
present, and paint into three parts, because the split is what said where a
frame's time goes
([ADR-0045](../adr/0045-a-frame-is-not-a-benchmark-iteration.md)):

```text
frame <size> in <total>us: buffer <n>, paint <n> (begin <n>, draw <n>, end <n>), present <n>
```

`begin` attaches the context, `draw` is the painter's own work, and `end`
waits for Blend2D's workers. During a resize this line is the difference
between "the toolkit is slow" and "the platform is".

The launcher adds a second line per frame that did something, split by the
stages of the widget pipeline. It is a system property rather than a log
level, because a diagnostic that cost an `isTraceEnabled()` per element per
frame would be measuring itself
([ADR-0101](../adr/0101-a-diagnostic-must-not-be-the-thing-it-measures.md)):

```sh
./gradlew run -Dgoldberry.trace.frames=true    # frames that did something
./gradlew run -Dgoldberry.trace.frames=all     # and the quiet ones
```

The line that found a cascade slow per element, from
[ADR-0151](../adr/0151-a-frame-can-say-what-it-did.md):

```text
frame 268 | build 0.010 style 8.636 layout 1.115 raster 1.304 ms
  | elements 125, built 0, resolved 3, invalidated 1
  | cascade 5.133 identity 0.956 motion 0.176 boxes 1.467
  | text 17 cached, 6 shaped | damage 3
```

The counts beside the timings are the point. `resolved 3` beside
`style 8.636` is a cascade that is slow per element rather than one running
too often. The two times a frame was slow
for a reason nobody could see, the answer was a count and not a duration: a
style cache missing on every element
([ADR-0142](../adr/0142-a-style-handed-down-keeps-its-identity.md)) and a
click invalidating the whole tree
([ADR-0149](../adr/0149-a-state-invalidates-what-it-can-reach.md)).

> [!TIP]
> The showcase has a HUD on `Ctrl+F` that shows the same stages over the last
> sixty frames
> ([ADR-0146](../adr/0146-a-hud-shows-where-the-frame-went.md)). Switch it
> on during a drag or a resize, when there is something to watch.

## The showcase under load

The launcher reads four flags and leaves every other argument to the
application
([ADR-0342](../adr/0342-a-window-is-resized-from-outside-and-the-run-says-what-it-cost.md)):

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

`showcase.yml` runs that walk on all three platforms and puts the line in the
step summary. It asserts no budget. The first number chosen, 30 late frames,
had been reasoned about and never measured, and the first legs to reach it
reported 75 and 200
([ADR-0452](../adr/0452-a-refresh-budget-needs-a-display-somebody-chose.md)):

| Leg | Frames | Late | Paint mean | Worst | Display |
|---|---|---|---|---|---|
| `linux-x64` | 302 | 75 | 10.14 ms | 1799.59 ms | 0.0 Hz |
| `macos-aarch64` | 300 | 200 | 6.42 ms | 200.26 ms | 60.0 Hz |

`display 0.0 Hz` is Xvfb reporting no refresh rate, so on Linux "late" counts
missed ticks of the pacer's own timer, and on each leg the worst frame is
start-up. A budget waits on enough green runs to set one per platform from
the top of a distribution. `--late-budget` is still what to run locally
against a real display, which is where it means something.

Under Gradle the same run is `./gradlew run -Pgoldberry.example.frames=300`,
and `-Pgoldberry.backend.videoDriver=dummy` keeps it off the real compositor.
Use the Gradle property rather than `SDL_VIDEODRIVER` in the environment,
because a `JavaExec` fork inherits the daemon's environment and not the
shell's.

## The benchmark lane

Benchmarks are JUnit classes tagged `benchmark`. `./gradlew benchmark` runs
them all, `./gradlew :widgets:benchmark` one module's, and `check` never does.
They print their measurements and assert almost nothing, because a timing
assertion on shared CI hardware fails for reasons that have nothing to do
with the code. The inventory, from `docs/testing.md` §1.5:

| Benchmark | Module | What it prices |
|---|---|---|
| `PaintBenchmark` | `:core` | Painting a frame, and what Blend2D's workers do to it |
| `TextBenchmark` | `:core` | The text path: shaping, the paragraph cache, wrapping |
| `FrameBenchmark` | `:widgets` | A frame of a real widget tree, split by stage |
| `DeepTreeStyleBenchmark` | `:widgets` | A 50-, 100- and 200-deep tree's first frame and a middle ancestor's hover, against the catalog's sheets |
| `TextAreaFrameBenchmark` | `:widgets` | One keystroke into a 2 kB, a 50 kB and a 500 kB note |
| `MarkdownFrameBenchmark` | `:html` | The same keystroke through a `markdown-view`, with an md4c control row |
| `BindingSchemeBenchmark` | `:weaver` | The two ways of binding a model, against each other |
| `BindingCodegenBenchmark` | `:weaver` | Whether a jar should generate its binding rather than reflect |
| `DowncallBenchmark` | `:natives` | One foreign call, held both ways |
| `ModifierPollBenchmark` | `:natives` | The per-event modifier poll |
| `BindingBenchmark` | `:example` | The binding schema before and after ADR-0125, and the showcase's own names |
| `FrameBudgetTest` | `:example` | A frame of the real application, stage by stage and resolution by resolution |

Run one by name:

```sh
./gradlew :widgets:benchmark --tests '*TextAreaFrameBenchmark*'
./gradlew :example:benchmark --tests '*FrameBudgetTest*' -i
```

`MarkdownFrameBenchmark` carries a control row, md4c timed on its own. Two
runs whose parse times disagree are two runs on two differently loaded
machines, and their other rows should not be compared
([ADR-0389](../adr/0389-a-block-nobody-typed-in-keeps-its-widget.md)).

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
machine was doing. `FrameBudgetTest` is the record of it: it asserted
per-stage wall-clock budgets, and its style row reads 3.6 ms on an idle
machine and 20 ms under a parallel Gradle, on the same commit. It moved to
the benchmark lane on 2026-09-18, where its table is read rather than its
assertions.

What `check` is owed instead is a count. `TextAreaKeystrokeCostTest` is the
pair to `TextAreaFrameBenchmark` and runs under `check`: it asserts that a
keystroke into a 500 kB note shapes and draws about what a keystroke into a
2 kB note does, in characters. Run against the commit before
[ADR-0388](../adr/0388-a-note-is-shaped-a-line-at-a-time.md) it fails with
500 005 characters shaped. After it, 2 021 against 1 941. `BlockReuseTest`
does the same for a `markdown-view`, in blocks built, blocks kept and
paragraphs shaped. A count says the same thing under a parallel build and a
millisecond does not.

> [!IMPORTANT]
> A benchmark's scaffolding is still under `check`. `BindingBenchmark` runs
> nightly only, so the two names it resolves are resolved by
> `ShowcaseActionsTest` on every build
> ([ADR-0397](../adr/0397-a-benchmarks-names-are-resolved-under-check.md)).

## The flags

Each is a system property on the Java command line, so `-D` on a plain
launch, and on `./gradlew run` the showcase forwards the ones below.

| Property | Values | What it changes | Record |
|---|---|---|---|
| `goldberry.paint.threads` | `0` for synchronous painting, `N` to pin the worker count | Blend2D's workers. The default is up to four on any surface over 400×300 | [ADR-0042](../adr/0042-blend2ds-workers-and-how-many.md) |
| `goldberry.frame.rate` | `N` overrides the display's rate, `0` measures the unthrottled loop | The pacer. `0` reproduces the numbers from before pacing | [ADR-0047](../adr/0047-a-frame-nobody-sees-costs-full-price.md) |
| `goldberry.backend.vsync` | `false` | Stops asking SDL to hold each present until vertical blank | [ADR-0047](../adr/0047-a-frame-nobody-sees-costs-full-price.md) |
| `goldberry.gpu` | `off` | Keeps every window on the CPU when `goldberry-gpu` is on the module path | [ADR-0480](../adr/0480-windows-present-through-the-gpu-by-default-and-on-the-cpu-where-it-cannot.md) |
| `goldberry.gpu.composite` | `always` by default, `auto`, `never` | Whether a window is composited always, only while it shows GPU content, or not at all | [ADR-0480](../adr/0480-windows-present-through-the-gpu-by-default-and-on-the-cpu-where-it-cannot.md) |
| `goldberry.backend.videoDriver` | `dummy` | SDL's video driver, for a headless run that touches no compositor | [ADR-0026](../adr/0026-sdl-picks-the-video-driver.md) |
| `goldberry.trace.frames` | `true`, `all` | The launcher's per-frame stage line, with its counts | [ADR-0101](../adr/0101-a-diagnostic-must-not-be-the-thing-it-measures.md) |
| `goldberry.log.native` | `false` | Gives GLib and SDL their stderr back, past the SLF4J bridge | [ADR-0443](../adr/0443-somebody-elses-log-line-is-still-a-log-line.md) |
| `goldberry.log.level` | `TRACE` | The showcase's `logback.xml` level for `dev.goldberry`. Your application names its own | [ADR-0028](../adr/0028-the-start-up-timeline.md) |

A tuning flag that is not understood is taken as the default. A typo should
not stop a window opening.

## A benchmark is not a frame

The same 960×640 scene paints in 0.34 ms in `PaintBenchmark` and in 2.15 ms
in the running showcase, both with four workers
([ADR-0045](../adr/0045-a-frame-is-not-a-benchmark-iteration.md)). The gap
was chased one hypothesis at a time: the borrowed buffer, the icons, the
display server, logging, a cold cache. The benchmark's own loop run inside the
live application between two real frames was still four times faster than the
frames on either side. The last variable was `present`. With it skipped and
nothing else changed, paint fell from 2.193 ms to 0.574 ms.

`present` moves megabytes and crosses into the kernel, and by the time the
next frame begins the pipelines, the destination pixels, the glyph caches and
the page tables that reach them have all been displaced. So:

- A benchmark compares options against each other. Worker counts, surface
  sizes and algorithms, with everything else held constant.
- A claim about what a frame costs comes from a frame. Read it off the trace
  line or the launcher's summary, in a window that presents.

The [summary page](index.md#where-the-time-goes-at-960640) quotes both
numbers for that reason, and labels each.
