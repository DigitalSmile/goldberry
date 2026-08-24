# 201. A hole is not a zero

Date: 2026-08-24

## Status

Accepted. `charts.md` §3.1's "null handling: gap / connect / zero — three-way,
explicit. A gap drawn as zero is a lie about the data and the default is the
gap."

## Context

A sensor that was offline for an hour and a sensor that read zero for an hour are
different facts. A chart that draws them the same way has destroyed one of them,
and it is the *cheap* rendering — substituting zero needs no code at all, which is
why so many charts do it.

Before this, the toolkit had no answer at all and the failure was worse than a
lie. `List.copyOf` refuses nulls, so a missing reading had to arrive as
`Double.NaN` — and `Math.min` propagates `NaN`, so one hole made `Series.min()`
answer `NaN`, the axis found its domain was not finite, fell back to `0…0` and
**collapsed the entire chart onto one line**. A single missing sample destroyed
the picture, silently, and no test noticed because no test had a hole in it.

Three questions had to be answered rather than one.

**How is a hole spelled?** `List<Double>` can hold nulls if the copy allows it,
and `NaN` is what a `double[]` and a `DoubleStream` already use.

**Who chooses the policy?** Grafana puts it on the series, as a field override.

**What does a *stack* do with one?** A band's y is a running total, so an index
where one component is missing is an index where the total is unknown — and
there is no way to draw "unknown" in the middle of a stack without moving the
bands above it.

## Decision

**A hole is `Double.NaN`, and a `null` is read as one.** `Series` normalises at
construction rather than refusing, because the alternative made every caller
convert a nullable column themselves and the obvious conversion is `orElse(0)` —
the one answer that destroys the distinction this record exists to preserve.
`Series.min`/`max` skip holes, and `valueCount()` says how many readings there
really are, so a series of nothing but holes is as empty as a series of no points
(ADR-0200).

**The policy belongs to the chart, not to the series.** One picture, one
convention: two series in one chart treating their holes differently is a chart a
reader cannot interpret without being told which line is which kind — the same
argument that gives a chart one x axis and refuses it a second y (§3.4).

**The default is `GAP`**, which is the only one of the three that invents nothing.
Most chart libraries connect by default, because a broken line looks like a
rendering bug; that is a reason to make the honest rendering *legible*, not a
reason to draw the other one.

**One place applies it.** `Gaps.resolve` turns a series and a policy into
substituted values plus the runs of consecutive drawable indices, and every mode
reads that:

- `GAP` substitutes nothing and produces a run per stretch, so a line is a
  polyline per run and a hole is a hole.
- `CONNECT` **interpolates** the interior holes linearly. For a line that is
  exactly the straight segment across the gap; for a filled band it is the same
  shape filled — one substitution, both modes, no second definition of
  "connected". A hole at either **end** stays a hole: connecting needs two ends,
  and a series that started late did not have a value before it started.
- `ZERO` substitutes zero, which is right where a missing value genuinely means
  zero — a counter that reports nothing when nothing happened — and a lie
  everywhere else.

**A hole in one series is a hole in the whole stack.** For `area-chart` the runs
are the indices where *every* component has a value, so the bands break together.
Drawing the ones above a hole as though the missing one were zero would put them
at a height nobody reported.

**Nothing is dropped silently.** A run of one has no segment to draw, so a line
draws a **dot** and a stacked band draws its **cross-section** a pixel wide. That
is the same objection LTTB exists to answer — a picture that omits a reading it
was given — and it applies to one point as much as to a spike in a hundred
thousand.

A bar chart needs no policy of its own: a bar is a length from zero, so a missing
reading has no length and no bar, and `ZERO` draws the zero-height bar it asked
for. The readout leaves a missing series' row out entirely, which is also how it
says *which* series was missing — the one that is not in it.

## Alternatives considered

- **Nulls in the list rather than `NaN`.** It reads better at the call site and
  breaks every `mapToDouble` downstream of it, including the ones in this file's
  own arithmetic. Accepting a null and storing a `NaN` gets both.
- **Per-series policies**, as Grafana has. Grafana needs them because its charts
  are configured through a form by someone who cannot write code; here the
  application is written in Java and can split a chart in two. What it buys is a
  picture with two conventions in it.
- **Substituting zero by default**, or connecting by default. Both are the chart
  choosing what the data means, and the whole of §3.1's sentence is that it must
  not.
- **A gap in a stack drawn as zero for the missing component only.** It keeps the
  band continuous and moves every band above it to a wrong height — a chart that
  looks complete and is not, which is worse than a visible break.
- **Interpolating a leading or trailing hole** by extending the nearest reading.
  That is adding data rather than joining it.

## Consequences

- **One missing sample no longer destroys a chart.** That is the largest of these
  consequences and it was a live defect, not a feature gap.
- **Downsampling happens per run.** LTTB runs over each stretch with a budget
  shared out by length, because the alternative is downsampling *across* a hole
  and inventing a segment through it.
- **`ZERO` changes the axis, and should.** A substituted zero is a value the chart
  is drawing, so it is in the domain; an axis scaled to the raw series would leave
  the substituted point off the bottom of a chart that is drawing it.
- **Three goldens, and the assertion that matters is that they differ.** A chart
  whose null handling was wired up but never applied would pass every unit test
  about `Gaps` and draw one picture for all three policies.
- **`smooth` interpolation is still not built.** §3.1's monotone-cubic
  interpolation is a different axis of the same area — how the line gets from one
  point to the next, rather than what happens where a point is absent — and
  `CONNECT` is deliberately linear, so a chart cannot end up smoothing through
  data that was never there.
