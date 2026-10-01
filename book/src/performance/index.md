# What a frame costs

<p class="gb-lede">A native window is up in a tenth of a second, a settled frame costs microseconds, and this page says where the rest of the time goes.</p>

Read this page to know which numbers to expect from a Goldberry window and
where each one was measured. The three chapters after it say how to start
fast, how to keep a frame cheap, and how to measure your own application
rather than trust these figures.

<div class="gb-stats">
<div><b>519</b><i>ms</i><span>native image, from exec to the first frame; the window is open at 116.2 ms</span></div>
<div><b>1980</b><i>ms</i><span>the same application on a cold JVM; 1340 ms with a JDK 25 AOT cache</span></div>
<div><b>3.13</b><i>ms</i><span>median frame at 960×640 with a wrapped paragraph, paced to the display</span></div>
<div><b>2.3</b><i>ms</i><span>a full 3840×2160 raster across four paint workers</span></div>
<div><b>117</b><i>µs</i><span>repainting one small change, drawing only the damage</span></div>
<div><b>9.1</b><i>µs</i><span>a frame in which nothing changed: layout and the walk on the retained tree</span></div>
</div>

Every number above comes from a record in the decision log, and the table
below says which.

| Number | What it measures | Record |
|---|---|---|
| 519 ms, 116 ms | The showcase's native image, median of 7 runs, timed from `exec`. The window is open at 116.2 ms in the run the record prints | [ADR-0506](../adr/0506-start-up-is-timed-from-the-kernels-clock-and-a-native-window-is-up-in-a-tenth-of-a-second.md) |
| 1980 ms, 1340 ms | The showcase on the JVM, cold and with an AOT cache, median of 5 runs | [ADR-0506](../adr/0506-start-up-is-timed-from-the-kernels-clock-and-a-native-window-is-up-in-a-tenth-of-a-second.md) |
| 3.13 ms, 4.28 ms | Median and 95th percentile of a 960×640 frame with text, paced to the display | [Status, M1](../status.md#m1--vertical-slice), from the pacing in [ADR-0047](../adr/0047-a-frame-nobody-sees-costs-full-price.md) |
| 2.3 ms | A 3840×2160 paint in `PaintBenchmark`, down from 6.0 ms on one thread | [ADR-0042](../adr/0042-blend2ds-workers-and-how-many.md) |
| 117 µs | One small box changed at 960×640, repainted inside its damage, down from 367 µs | [ADR-0072](../adr/0072-a-partial-repaint-needs-a-promise.md) |
| 9.1 µs | Layout and the walk on a showcase-shaped tree when nothing changed, down from 190 µs | [ADR-0069](../adr/0069-the-render-tree-is-retained.md) |

## The frame loop

One UI thread runs the loop. Blend2D's workers rasterize the bands. This is
the pipeline from `docs/ARCHITECTURE.md` §5, in the order a frame runs it:

```text
input events → dispatch (hit-test on render tree)
→ rebuild dirty widgets → diff → update elements/render objects
→ style resolution (invalidated nodes) → Yoga layout (incremental)
→ paint recording (dirty layers only) → Blend2D raster (banded)
→ present(buffer, damage) / GPU composite
```

Three trees take part. Widgets are immutable records that describe. Elements
persist and hold state. Render objects own the Yoga nodes and are kept
between frames, so a frame diffs a new description against a retained tree
rather than building one. The [architecture overview](../overview/architecture.md)
has the full picture.

## Where the time goes at 960×640

Everything before rasterization is now a rounding error. Rasterization and
present are the frame.

| Stage | Cost | Record |
|---|---|---|
| Everything but rasterization, nothing changed | 3.5 µs, down from 354 µs | [ADR-0070](../adr/0070-the-cascade-resolves-invalidated-nodes.md) |
| Rasterization on one thread | about 320 µs | [ADR-0070](../adr/0070-the-cascade-resolves-invalidated-nodes.md) |
| Paint in a live window, four workers | 2.146 ms, from 2.856 ms synchronous | [ADR-0042](../adr/0042-blend2ds-workers-and-how-many.md) |
| Present, unpaced | about 6.4 ms, of which 4.8 ms is blocking on the swapchain and 43 µs is this repository's code | [ADR-0046](../adr/0046-what-present-actually-does.md) |
| Present, paced to the display | 1.20 ms, with paint falling to 1.61 ms beside it | [ADR-0047](../adr/0047-a-frame-nobody-sees-costs-full-price.md) |

Read the paint row with care. The same scene paints in 0.34 ms in a benchmark
loop and in 2.15 ms in a running window, because `present` leaves the next
paint's caches cold. Only a figure from a live window says what a frame costs.
[Measuring](measuring.md#a-benchmark-is-not-a-frame) explains the gap.

> [!NOTE]
> Every number on this page was measured on one Linux machine. The run that
> would repeat the frame benchmarks on Linux, macOS and Windows is open, and
> the status page says what each platform's CI leg has reported so far.
> See [the frame evidence](../status.md#the-frame-evidence--built-run-and-asserting-no-budget).

## The three chapters

<div class="gb-cards">
<a class="gb-card" href="startup.html"><strong>Starting fast</strong><span>From exec to the first frame on each launch mode, the TRACE timeline, the AOT cache, the native image, and what to keep out of start.</span></a>
<a class="gb-card" href="frames.html"><strong>Keeping frames cheap</strong><span>What the toolkit retains, invalidates, culls and paces for you, and the ten things an application must do itself.</span></a>
<a class="gb-card" href="measuring.html"><strong>Measuring</strong><span>Per-frame timings, the showcase's resize run, the benchmark inventory, JMH, the flags, and why a count guards a cost.</span></a>
</div>
