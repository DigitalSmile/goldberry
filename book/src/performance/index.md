# What a frame costs

<p class="gb-lede">A native window is up in a tenth of a second, a settled frame costs microseconds, and this page says where the rest of the time goes.</p>

Read this page to know which numbers to expect from a Goldberry window and
what each one measures. The three chapters after it say how to start
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

The table below says what each number measures, and how.

| Number | What it measures | Method |
|---|---|---|
| 519 ms, 116 ms | The showcase's native image, from `exec` to the first frame, and the moment the window is open | median of 7 runs, timed from `exec` |
| 1980 ms, 1340 ms | The showcase on the JVM, cold and with an AOT cache | median of 5 runs |
| 3.13 ms, 4.28 ms | A 960×640 frame with text, paced to the display | median and 95th percentile |
| 2.3 ms | A 3840×2160 paint in `PaintBenchmark` | four paint workers, against 6.0 ms on one thread |
| 117 µs | One small box changed at 960×640, repainted inside its damage | against 367 µs for a full repaint |
| 9.1 µs | Layout and the walk on a showcase-shaped tree when nothing changed | the retained tree, against 190 µs rebuilt every frame |

## The frame loop

One UI thread runs the loop. Blend2D's workers rasterize the bands. This is
the pipeline, in the order a frame runs it:

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

Everything before rasterization is a rounding error. Rasterization and
present are the frame.

| Stage | Cost | Note |
|---|---|---|
| Everything but rasterization, nothing changed | 3.5 µs | against 354 µs with nothing retained or cached |
| Rasterization on one thread | about 320 µs | the whole frame, with Blend2D pinned to one thread |
| Paint in a live window, four workers | 2.146 ms | against 2.856 ms synchronous |
| Present, unpaced | about 6.4 ms | 4.8 ms of it blocks on the swapchain and 43 µs is the toolkit's own code |
| Present, paced to the display | 1.20 ms | paint falls to 1.61 ms beside it |

Read the paint row with care. The same scene paints in 0.34 ms in a benchmark
loop and in 2.15 ms in a running window, because `present` leaves the next
paint's caches cold. Only a figure from a live window says what a frame costs.
[Measuring](measuring.md#a-benchmark-is-not-a-frame) explains the gap.

## The three chapters

<div class="gb-cards">
<a class="gb-card" href="startup.html"><strong>Starting fast</strong><span>From exec to the first frame on each launch mode, the TRACE timeline, the AOT cache, the native image, and what to keep out of start.</span></a>
<a class="gb-card" href="frames.html"><strong>Keeping frames cheap</strong><span>What the toolkit retains, invalidates, culls and paces for you, and the ten things an application must do itself.</span></a>
<a class="gb-card" href="measuring.html"><strong>Measuring</strong><span>Per-frame timings, the showcase's resize run, the benchmark inventory, JMH, the flags, and why a count guards a cost.</span></a>
</div>
