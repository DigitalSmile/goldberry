# 205. A log axis has no room for zero

Date: 2026-08-24

## Status

Accepted. `charts.md` §3.1's "log axis, with correct log tick labelling", and the
last structural row of that table.

## Context

A series that spends its life at 3 and spikes to 30 000 is, on a linear axis, a
flat line along the bottom with one spike: every reading anybody came to read is
in the bottom pixel. Four decades is not an unusual range for a latency, a rate
or a queue depth, and a log axis is the standard answer — each decade gets the
same room.

Three things make it more than a different multiplication.

**Every scale in the toolkit is affine.** `Scale` maps a domain onto a range
linearly, and every chart, the sparkline included, is written against it. A log
scale is the first thing that is not.

**Wilkinson is the wrong labelling algorithm.** `Ticks.extended` scores a
candidate labelling on how round its numbers are and how evenly they cover the
range, and on a log axis those pull apart completely: `1, 10, 100, 1000` is the
only labelling anybody wants, and in the value space Wilkinson works in it is
wildly uneven — three quarters of the axis carries one label.

**And a logarithm has no room for zero.** `log10(0)` is negative infinity and
`log10(-1)` is not a number. This is not a rendering question with a nice answer
hiding behind it; there is genuinely nowhere on the axis to put the reading.

## Decision

**`Scale` gains a flag rather than a subtype.** `Scale.log(min, max, …)` maps
`log10(value)` linearly, and `at`/`from` branch on it. Every caller wants *a
scale* and none of them wants to know which kind — a painter asks where a value
goes and a pointer asks what is at a pixel, and both have one answer either way.
A `Scale` subtype would have made `PlotGeometry` generic in the axis for the
benefit of one `if`.

**`LogTicks` labels decades**, striding them when there are too many — `1, 100,
10000` rather than thirteen powers of ten — and subdividing by the **1-2-5**
mantissas when there are too few, which is what every sheet of log-ruled paper
has ever used. Not every integer: `1, 2, 3 … 10` crowds the bottom of each decade,
which is where a log axis has least room. The subdivision is chosen as the one
*nearest* the label target rather than the first to reach it, which is a rule
worth stating because the other one silently turns a four-decade axis into a
seven-label half-decade one to gain a label it did not need.

**A non-positive reading becomes a hole.** `Gaps.positiveOnly` turns it into a
`NaN` and `NullPolicy` draws it as one, so the line **breaks** where the data went
to zero rather than sliding off the bottom of the picture. That is the honest
rendering of "there is nowhere to put this", and it costs the reading visibly
rather than quietly — which is also why a log axis is opt-in and not something a
chart could choose for itself when its numbers span enough decades. Choosing it
costs data, and only the application knows whether the zeroes matter.

**Only `line-chart`.** A bar and a band encode their value as a **length from
zero**, and zero is infinitely far down a log axis. A chart drawing one anyway
would have to pick a bottom, and every choice is a number nobody gave it. `logY`
on a bar or an area chart is ignored, exactly as a time axis is on a bar chart.

**A series with nothing positive in it falls back to a linear axis and keeps its
data.** `Scale.log` refuses a non-positive domain, and a chart must not turn that
into an exception in a paint pass — a query can return zeroes. The order matters:
the first version filtered and *then* discovered it had nothing left, and drew an
empty grid, which is worse than either honest answer.

## Alternatives considered

- **Symlog** — linear near zero, logarithmic outside it — which is matplotlib's
  answer to the zero problem. It needs a threshold nobody can choose correctly
  without knowing the data, and it draws an axis whose *scale changes* partway
  along: two equal distances on it are not equal ratios or equal differences, and
  a reader cannot know where the join is.
- **Clamping non-positive readings to the axis minimum.** It draws them, at a
  value they never had, on the one part of the axis a reader is most likely to
  believe. The current answer loses the same reading and says so.
- **A tiny epsilon floor** (`max(value, 1e-9)`), which is the same lie with a
  smaller number in it and a spike to the bottom of the chart wherever a zero was.
- **A `LogScale` subtype**, or making `Scale` an interface. Every call site would
  gain a type parameter to save one branch, and `PlotGeometry` — which is a record
  read by both a painter and a pointer — would gain a generic.
- **Log on the x axis too.** Nothing in §11 has a numeric x: it is an index, a
  category or a time. When `goldberry-plot`'s scatter arrives it will want one,
  and `Scale` now has the flag it needs.

## Consequences

- **The gridlines are unevenly spaced, which is the point.** `paintGrid` takes an
  explicit list of tick values now rather than deriving them from a linear
  labelling's `min + i·step`, because on a log axis there is no step.
- **It composes with everything before it.** `SMOOTH` stays monotone because the
  tangents are computed on the pixels the painter is about to draw, whatever the
  scale did to get there; a time axis is the x and is untouched; a threshold is
  placed by the same scale as the data.
- **`Scale.at` can now return `NaN`.** Deliberately: a caller that forgot to
  filter draws nothing rather than drawing a reading at the bottom of the axis
  that never happened.
- **`charts.md` §3.1's rows are done** bar the small ones — soft bounds, gradient
  fills, point markers and a crosshair shared between charts.
