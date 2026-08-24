# 199. A chart answers the keyboard, and a step is relative

Date: 2026-08-24

## Status

Accepted. The second half of `charts.md` §3.1's hover story — the donut — and the
whole of §3.5's first item.

## Context

[ADR-0198](0198-a-charts-readout-is-painted-and-its-legend-is-a-control.md) gave
the three axis charts a crosshair and a readout and left two holes in its own
row of the parity table.

**The donut had no hover at all.** §3.1 asks for "hover on bar/donut" and only the
bar arrived. A ring is not an axis: there is no crosshair to draw, no gutter to
measure, and nowhere along an axis to put a readout — but there is a reserved
empty circle in the middle of it, which is the one region of the chart that
cannot cover the data and cannot be clipped by the box.

**Nothing could be read without a pointer.** §3.5 is unusually pointed about it:

> Keyboard operation of the chart itself. Arrow keys walk the crosshair
> point-by-point, `Home`/`End` jump to the ends, `Tab` moves between series. A
> browser dashboard is a pointer surface; a desktop application is not, and §2.2
> requires everything to be reachable. Grafana is weak here and it is not a model
> to copy.

And writing that surfaced a defect that has nothing to do with charts. The
crosshair index lives on the state and is handed *down* into the widget that
draws it; a key handler on that widget computing "one to the right" reads the
index from **the description the last frame was built from**. Two arrow presses
between two frames — which is what a key repeat produces — both step from the
position before either of them, and the crosshair moves once. It is the same
trap the select family hit and the same sentence applies: after reporting
upward, a widget knows nothing new until it is rebuilt, and reading its own
fields there is reading the past.

## Decision

**A step is relative, and only the state may apply it.** The plot reports
`onWalk(±1)` and the state adds it to the field it owns; absolute positions —
where the pointer is, which end `Home` and `End` mean — stay absolute, because
they do not depend on where the crosshair already was. The callbacks answer
**whether anything changed**, so a widget never has to consult its own stale copy
to decide whether to consume a key.

**A plot is focusable and owns `Left`, `Right`, `Home`, `End` and `Escape`.**

- `Left`/`Right` walk. On an axis they **clamp**: a line has two ends, and a
  crosshair that jumped back to Monday after Sunday would be a chart pretending
  its axis is a circle. On a ring they **wrap**, because a ring has no ends and
  stopping somewhere on it would be an edge the picture does not have.
- `Escape` lets go, and is consumed **only if it cleared something** — so it
  still closes the dialog the chart is sitting in.
- **`Up` and `Down` are left alone.** A chart is very often inside a `scroll`,
  and a focused widget that consumed the vertical arrows would swallow the keys
  that move the page. Two arrows reach every point.
- **`Tab` is not one of them**, which is a refusal of §3.5's own sentence:
  `Tab` is the focus traversal and a composite is one Tab stop with roving
  *arrow* keys inside it (ADR-0073). Recorded in `ARCHITECTURE.md` §17.1.
- A plot with no data is **not** a Tab stop. There is nothing in it to walk, and
  a dashboard of empty placeholders should not cost a tab press each.

**A donut writes in its hole.** The hovered slice's name and share, centred; the
other slices fade to 0.3 so the ring says which one the hole is talking about. It
shows the **share** rather than the value, because a part-to-whole chart is about
the proportion and a reader who wanted the raw number wanted a bar chart. Only
what fits is drawn: a hole is a circle and text is a rectangle, so a slice called
"Uncategorised traffic" gets its share and no name.

**The ring needs no banked geometry.** `DonutGeometry` follows from the box
alone, so — unlike `PlotGeometry`, whose gutter is measured from shaped labels
and has to be left behind for the pointer (ADR-0054, `PaintedGeometry`) — it is
computed fresh on both sides. The gaps between slices belong to a slice: the
painter trims a sliver off each arc so they do not touch, and a hit test that
respected those slivers would put a ring of two-pixel dead wedges through the
chart.

## Alternatives considered

- **Letting the widget compute the step from its own index.** This is what was
  written first, and the test that pressed an arrow twice between two frames
  caught it. It is not a small bug: on a machine dropping frames, a chart would
  ignore most of a held-down arrow key.
- **`Up`/`Down` as a second pair of arrows.** Convenient on a chart in
  isolation, and a theft everywhere else — §2.4 already bans nested same-axis
  scrollers because that class of interception is hard to notice.
- **A donut readout beside the ring**, like an axis chart's. It would need
  placing, flipping and clamping inside the box, to avoid a space the chart has
  already reserved for exactly this.
- **Exploding the hovered slice** — nudging it outward — instead of fading the
  others. It changes the ring's outline as the pointer crosses it, which reads as
  the chart wobbling, and the moved slice no longer lines up with its neighbours'
  edges so its share becomes harder to compare rather than easier.
- **Isolating a slice by clicking the legend**, as the axis charts do. A
  part-to-whole chart showing one part is no longer showing a whole; the donut's
  legend stays a key, which is what `ChartSpec` says by not including it.

## Consequences

- **Every chart with data is a Tab stop.** An application with a wall of charts
  gains a tab stop per chart; that is what §2.2 asks for, and the alternative is
  a chart a keyboard user cannot read at all. `chart-plot:focus-visible` and
  `donut-plot:focus-visible` take the same ring every other control takes.
- **The keyboard and the pointer are held to one answer.** `ChartInputTest`
  asserts that `End` and a pointer at the right-hand edge produce *the same
  pixels* — not two descriptions of the same intent.
- **A donut's slices are reachable in order**, which is also the first thing on
  the way to reading one aloud: whatever a screen reader eventually asks these
  widgets for, the index it would ask about now exists.
- **The relative-step rule generalizes.** Any widget whose keys move a position
  it reports upward has this bug available to it. The shape that avoids it is the
  one here: relative intent up, arithmetic in the state, and a callback that says
  whether anything moved.
- **Still owed from §3.1**: thresholds, log axes, `java.time` axes, null
  handling, interpolation, soft bounds, gradient fills, empty and error states,
  and a shared `CrosshairGroup` across charts. §3.5's other two items — copying
  the hovered value with `Ctrl+C`, and the determinism claim — are a clipboard
  call and already true, respectively.
