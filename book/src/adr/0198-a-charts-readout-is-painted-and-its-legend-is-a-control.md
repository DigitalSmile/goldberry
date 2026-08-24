# 198. A chart's readout is painted, and its legend is a control

Date: 2026-08-24

## Status

Accepted. The first half of `charts.md` §3.1's interaction layer: the crosshair,
the readout, and clicking a legend entry to isolate a series.

## Context

The five charts of §11 draw correctly and do nothing when a pointer arrives.
`charts.md` §3.1 lists what they owe — a tooltip, a crosshair, thresholds, log
scales, time axes and legend isolation — and calls the last of those "the one
interaction Grafana users reach for first". Two decisions had to be made before
any of it could be written, and both of them are about *where* things live rather
than about what they look like.

### A chart is a value, and a hovered point is not

A widget is an immutable description rebuilt every frame ([ADR-0004]) and the
painting is a pure function of it. Which point the pointer is over cannot live in
that: it survives rebuilds, so it belongs to an element, which means a
[`Widget.Stateful`] somewhere above the drawing. Which series is *isolated* is
worse — the click lands on the **legend** and changes what the **plot** draws, and
those two are siblings, so nothing below the chart can hold it.

### The plot's geometry is only known in the painter

Where a point is drawn depends on the size and on the **shaped y labels**: the
gutter is measured from them, so an axis reading `1,000,000` starts further right
than one reading `5`. Neither is available when a pointer event is handled — an
event carries a rectangle and no text stack — and the arithmetic must not be
written twice, or a crosshair lands a few pixels off the line it belongs to at one
window size and nowhere else.

### And a tooltip is text, which the legend argued should be widgets

`ChartLegend` is real nodes rather than something the painter draws, and the
reasons were good: a stylesheet reaches them, the shaping cache serves them, and
the entries wrap when the chart is narrow. The same argument points at making the
hover readout a node too — and it stops working when you follow it through.

## Decision

**Four things, and each one is the answer to one of those.**

1. **`ChartPlot` becomes stateful and builds `ChartSurface`.** The state holds the
   hovered index; the surface is the `chart-plot` part — the canvas, the painter,
   and the node that implements `Handles`. The shape is `SplitPane`'s: a stateful
   widget above, a leaf below that hears the pointer and reports upward
   ([ADR-0063]).
2. **The three axis charts become stateful too, through one `ChartSpec`,
   `ChartState` and `ChartView`.** The state holds the isolated series and reaches
   both halves; the view is the chart's own box, with the chart's CSS type, `id`
   and classes — so **the box tree, every stylesheet rule and every golden image
   are unchanged**, and all the widgets bought was somewhere to keep an integer.
   `DonutChart` is not one of them: isolating one slice of a part-to-whole chart
   leaves a chart that no longer shows a whole.
3. **The geometry is lifted into `PlotGeometry`, and the painter leaves it in
   `PaintedGeometry` for the pointer to read.** One arithmetic, used forwards to
   draw a crosshair and backwards to resolve a pointer, with a round-trip test
   over every point count from 1 to 40. Resolving against the *painted* frame is
   [ADR-0054]'s rule one level down: the toolkit already routes a pointer against
   the frame the user was looking at, and a chart deciding *which point* they
   pointed at owes the same answer.
4. **The readout is painted, not built.** Its text is shaped in `render` — where
   the hovered index is known, so it is one point's worth of strings and the cache
   serves the repeats — and placed by the painter, which is the only thing that
   knows where the crosshair is.

The readout takes `hud`'s tokens — `--gb-hud-bg`, `--gb-hud-border`,
`--gb-hud-text` — read through `Paints.Context#color`, which is ADR-0195's
mechanism doing exactly what it was built for. A floating overlay over content
the reader is looking through it at *is* a HUD, so a chart inventing a fourth
surface token would be a chart the theme cannot restyle with the rest.

## Alternatives considered

- **The readout as widgets, like the legend.** It would need absolute
  positioning inside the plot, and the position depends on the gutter — which
  `children()` cannot know, because it has no context and therefore no shaped
  labels. So it would read a banked geometry one frame late, needing `Measured`
  and a rule about what happens on a resize, to produce a node that must not
  affect layout and cannot be laid out. The legend's argument is about a *static*
  part: it wraps, it is selected by a stylesheet, and it is in the same place
  every frame. A readout is none of those. **Painted is not the cheap option
  here, it is the correct one** — and the cost is real and stated below.
- **The toolkit's `tooltip` widget.** It is a hover-delay popup for a control
  (ADR-0181): one string, a timer, a window. A chart readout follows the pointer
  continuously, shows a row per series, and must never leave the plot. Opening a
  popup window per pointer move is not a smaller version of that.
- **Isolation as a set of hidden series** rather than one isolated index.
  Unhiding then requires remembering what you hid, and a chart with three of
  eight series showing has a legend that no longer says what the picture is. One
  click shows one series; the same click again puts them all back.
- **Filtering the series list when one is isolated.** The index *is* the colour
  (ADR-0194), so an isolated fourth series would be redrawn in the first slot's
  hue and its own swatch would then disagree with it. The list stays whole and
  the surface skips what it is not showing.
- **A `chart-body` node wrapping the plot and the legend**, to hold the state
  without making the charts stateful. One node, one new CSS type, and every rule
  and golden that names `line-chart > …` rewritten — for the same integer.

## Consequences

- **A crosshair, markers and a readout on `line-chart` and `area-chart`; a band
  highlight on `bar-chart`.** A hairline down the middle of a *group* of bars
  points at the gap between two of them, so a bar chart highlights the band it
  owns instead. A stacked band gets no marker: a disc on the top of a stack marks
  the running total rather than the series.
- **The readout cannot be styled by CSS.** Its colours are the theme's and its
  padding, radius and layout are the painter's. That is the cost of the decision
  above, and it is written on the class. If an application needs to restyle it,
  the answer is a token — or `canvas`, which exists for exactly this.
- **Isolating rescales the axis**, because a series that was a flat line at the
  bottom of a chart scaled to a bigger one has nothing to read, and re-labelling
  is what turns it back into a chart.
- **The interaction is asserted through the real router.** `ChartHoverTest` and
  `LegendIsolationTest` render, lay out, **paint** — the step a hover cannot work
  without — and then dispatch. They compare pictures to pictures rather than
  coordinates, because the hovered index is state and deliberately unreadable
  from outside; three goldens say what it looks like.
- **`RoundRect` is public.** A canvas painter needs a rounded rectangle, and the
  alternative was a second derivation of ADR-0064's four cubics in `:widgets`.
  Nothing new crosses the native boundary.
- **What is still owed**, and it is most of §3.1's list: thresholds, log axes,
  `java.time` axes, null handling, interpolation, soft bounds, gradient fills,
  empty and error states, a shared `CrosshairGroup` across charts, hover on
  `donut-chart`, and §3.5's keyboard operation — arrow keys walking the crosshair,
  which this makes possible by giving the plot somewhere to keep the index but
  does not implement.
