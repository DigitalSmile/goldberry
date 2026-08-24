# 202. A limit is not a series

Date: 2026-08-24

## Status

Accepted. `charts.md` §3.1's "thresholds: lines and shaded regions — drawn in
the *semantic* hues, never a series slot".

## Context

A dashboard chart is usually read against something: an SLO, an error budget, a
disk that is nearly full. Grafana models that as a list of value/colour steps and
lets the author pick the colour, which is how a threshold ends up the same red as
the series beside it.

The colour is the whole question here, because this toolkit has two palettes and
they mean different things. The **series palette** exists to keep the things being
compared apart, and was searched over all 40 320 orderings to do it
(ADR-0194). The **semantic hues** mean "this is fine" and "this is not". A
threshold is not one of the things being compared — it is a statement *about*
them — so a threshold drawn from the series palette would both read as one more
series and steal the hue of a real one.

There is a second question underneath: whether a limit you have not reached
belongs on the chart at all.

## Decision

**A threshold takes one of four semantic levels — `INFO`, `SUCCESS`, `WARNING`,
`DANGER` — and there is no way to give it a colour.** It reads
`--gb-<level>-line`, which is the rank the design system added for *a stroke
drawn on the page* rather than for a label or a fill, measured against §1.2's
3:1 floor for non-text (ADR-0088). An application that wants a different colour
overrides the token on the chart, which is an ordinary rule and changes the
meaning rather than working around it (ADR-0195).

**A line or a band.** `Threshold.at(v)` is a line — *this* is the limit;
`above`, `below` and `band` are regions — *this range* is the bad one. A band is
drawn under the data at low alpha so the series and the gridlines read through
it: a warning that hid what it was warning about would cost you the reading you
came for.

**A band is a wash and its edges**, which was not the first design. The wash
alone failed a measurement: this theme's warning hue at 16% over the dark surface
computes to `(76, 76, 76)` — *exactly* neutral grey. A band whose semantic colour
a reader cannot perceive says "something" rather than "warning". So each finite
edge is drawn at full strength, which is where the hue lives, and the wash marks
the extent.

**A threshold is part of the domain.** The axis stretches to reach it, so a limit
you are a long way from is on screen. A threshold that only appeared once it had
been breached would be a warning light that comes on after the fire. An unbounded
side contributes nothing: `above(90)` says the axis must reach 90 and says
nothing about infinity.

**`NaN` is refused at construction**, with a message naming `NullPolicy`. It is
the one place in this area where a `NaN` means something specific — a missing
*reading* — and a limit that is missing is not a limit.

Not on `donut-chart`: a part-to-whole chart has no axis to draw a limit across,
and a threshold on a share would be a statement about a number the chart is
deliberately not showing.

## Alternatives considered

- **Letting the application pass a colour.** Every request for this will be for
  exactly that, and it is how a threshold ends up indistinguishable from series 5.
  The token override gives the same power and keeps the meaning attached.
- **Grafana's value/colour steps**, where the region above each step takes that
  colour and the *series* is recoloured by which step it is in. It reads well on a
  stat panel and badly on a multi-series line chart, where recolouring the line
  destroys the identity the legend just established.
- **Drawing thresholds over the data.** Easier to see, and it hides the point.
- **Leaving a threshold out of the domain**, so it only appears when the data
  reaches it. That is the reading nobody needs: by then you can see the problem in
  the series.
- **A dashed line**, which is the convention. `bl_context_set_stroke_dash_array`
  is not on the export list, and widening the native surface for a line style is
  not worth it while a 1px solid line in a semantic hue already reads as "not the
  data" — the series are 2px.

## Consequences

- **Four levels and no fifth.** A chart that needs six thresholds has six of the
  four levels, which is right: a reader distinguishes "fine / close / over", not
  six degrees of over.
- **The three axis charts grew a sixth component.** `series`, `categories`,
  `status`, `nulls`, `thresholds`, `attributes` — and the next §3.1 item makes it
  seven. **The next one should bundle them**: a `ChartOptions` record holding
  everything that is not the data, with the withers kept as the public surface.
  Recorded here rather than done now, because doing it in the same change as the
  feature would have hidden the feature.
- **The measurement is kept as a test.** `ThresholdTest` asserts both halves of
  the finding: that the band's edges are in the warning hue, and that the wash
  really is the grey that made the edges necessary.
- **The showcase carries one.** The `p99 latency` card has an SLO band, which is
  also the only colour on that screen that is not from the series palette — the
  point of the decision, visible on the wall.
