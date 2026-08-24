# 204. A smooth line cannot overshoot

Date: 2026-08-24

## Status

Accepted. `charts.md` §3.1's "interpolation: linear, smooth, step — step matters
for state-ish series; smooth is monotone-cubic, which cannot overshoot into
impossible values".

## Context

Between two readings a chart has to draw *something*, and whatever it draws is a
claim about what happened there. A straight segment claims the value moved
steadily. A curve claims it moved smoothly. A step claims it did not move at all
until the next reading.

The interesting one is the curve, because the obvious implementations are wrong
in a specific and expensive way. Fit a Catmull-Rom or a natural cubic spline
through `0, 0, 100, 100` and the curve **dips below zero** before it climbs and
**overshoots above 100** after. That is not a bug in those splines — it is what
they are for; they minimise curvature, and swinging past the endpoints is how.
It is simply wrong for data: on a percentage, a queue depth or a byte count the
overshoot is not inaccurate but *impossible*, and it lands exactly where a reader
is looking, because it lands where the interesting thing happened.

The step has a smaller question in it: whether a value holds forward from its
reading or backward into it.

## Decision

**`Curve.LINEAR` is the default**, because it makes the weakest claim and a chart
should not make a stronger one without being asked.

**`Curve.SMOOTH` is Fritsch–Carlson monotone cubic** (SIAM J. Numer. Anal. 17(2),
1980), which takes the obvious tangents and then limits them so that a curve
through monotone data stays monotone — and therefore never leaves the interval
its own endpoints define. Two limits, not one, and the second is the one that is
easy to miss:

- the `α² + β² > 9` circle, which scales a pair of tangents back;
- and **a local extremum has a flat tangent**. Averaging the secants at the top
  of `1, 9, 2` gives `+0.5`, and the curve reaches 9.0013 on a series whose
  maximum is 9. The circle does not catch it, because scaling a tangent back is
  not the same as zeroing it.

**`Curve.STEP` holds forward.** A value read at 09:00 is what was true from 09:00
until somebody looked again, so the horizontal comes first and the jump lands on
the next reading's x. Holding *backward* would say the new value was already true
before it was observed, which is the one direction the data cannot support.

**One emitter, used by lines and by both edges of a band.** A smooth band whose
underside was straight would be thicker than its own numbers wherever the top
bulged — a band that overstates itself. The underside is the same curve reversed,
which for a cubic is its control points in reverse order, so the two edges agree
exactly.

**A bar chart ignores it**, because a bar is a length from zero rather than a
path between readings.

## Alternatives considered

- **Catmull-Rom**, which is what most charting libraries reach for and what
  "smooth" usually means. It is one line shorter and it draws negative
  percentages.
- **Clamping the drawn curve to the data's range** instead of choosing a
  monotone one. It hides the overshoot by flattening the curve against an
  invisible wall, which draws a plateau the data does not have — a different
  invented reading, and a harder one to notice.
- **Bézier smoothing with a tension parameter.** A knob that turns a correct
  chart into an incorrect one somewhere in its range, and no value of it is
  right for all data.
- **Step-before**, or offering both. Both is a choice nobody can make correctly
  without knowing how the series was sampled, and the toolkit knows: a `Series`
  is readings, and a reading is what was true from when it was taken.
- **Sampling the curve into a polyline** rather than emitting cubics. Blend2D
  flattens a cubic better than a fixed sample count would, and the control points
  are three multiplications each.

## Consequences

- **The property is asserted by sampling, not by inspection.** `CurvesTest`
  evaluates the Hermite form densely and checks the bounds, which is the only
  kind of proof worth having about something one missing `if` away from being
  false — and it is what found the missing `if`: the local-extremum case failed
  the interval test before the flat-tangent rule was added.
- **`Curves` is public and pure.** No renderer, no natives, no `BlendPath`: the
  painter asks for tangents and control points and does the drawing. A future
  `goldberry-plot` gets the same arithmetic without the widget.
- **Smooth composes with everything already there.** The tangents are computed on
  the pixels the painter is about to draw, so an unevenly sampled series on a
  time axis curves correctly for free, a `GAP` run curves per run, and an
  isolated series curves alone.
- **The showcase's stacked area is smooth**, which is also the case that would
  expose a mismatched pair of band edges.
- **Not built**: log axes, soft bounds, gradient fills, point markers, and a
  crosshair shared between charts. §3.1's remaining rows.
