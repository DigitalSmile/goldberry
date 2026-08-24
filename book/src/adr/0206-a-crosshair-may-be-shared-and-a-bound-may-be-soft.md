# 206. A crosshair may be shared, and a bound may be soft

Date: 2026-08-24

## Status

Accepted. The last three of `charts.md` §3.1's rows that do not need a native
symbol: soft bounds, point markers, and the shared crosshair.

## Context

Three small features with one thing in common — each is about a chart telling the
truth about something it cannot see on its own.

**A chart scales to its data**, which is right until the data does not move. An
uptime reading `99.94, 99.97, 99.91, 99.99` fills the plot with the difference
between 99.91 and 99.99: a mountain range made of eight hundredths of a percent,
shouting loudest exactly when the news is good. The chart cannot know that; only
the application knows what range the number lives in.

**A line does not say which of its bends are readings.** A sparse series drawn as
a polyline looks like a continuous measurement, and the reader cannot tell whether
Tuesday's kink is a sample or the place two segments happen to meet.

**And a dashboard's panels are read together.** "What were the bytes doing when
the requests spiked" is the question a wall of charts exists for, and it is the
one it is worst at: the reader points at Thursday on one chart and then has to
find Thursday on the other by eye.

## Decision

**`Bounds` are soft by default and hard on request.** `softAxis(99, 100)` means
"reach at least here, and further if the data does", so an outage still pushes the
axis down to meet it — a chart that hid the one reading anybody needed would be
worse than the noisy one. `axis(99, 100)` does not move, and data outside it is
drawn outside the plot and clipped, which is the correct rendering of a promise
that was wrong. Soft is what nearly every application wants; hard is for a range
that is a definition rather than an observation.

**Markers are `AUTO` by default**, drawn when a point's neighbours are more than
four marker-widths away — measured **in pixels**, because what makes a dotted mess
is how close the dots are on screen rather than how many there are. The same
chart therefore shows dots at seven readings, none at seven hundred, and shows
them again when the window is widened. This changes what every sparse chart in
the toolkit looks like, and it is the right default: a dot per reading is the
difference between a measurement and a trace.

**A `CrosshairGroup` is a mutable holder an application owns**, like a
`ToastController` (ADR-0177): charts subscribe on mount and unsubscribe on
dispose, and a move notifies only when the index actually changes. What travels
is the **point index**, so the charts in a group are assumed to be sampled
together — which is what a dashboard's panels over one time range are. A group
over unaligned charts wants a shared *time*, which is a different type and is
worth building when something needs it.

**Every chart in the group draws the crosshair; only the one under the pointer
draws the readout.** A dashboard with six floating boxes on it, five of them
about a chart nobody is pointing at, is worse than no linking at all.

## Alternatives considered

- **Inferring soft bounds** from the data's own spread — "if the range is less
  than 1% of the value, pad it". It would fix the uptime and break the chart of a
  quantity that genuinely varies by a hundredth, and neither the chart nor the
  reader could tell which had happened.
- **Markers off by default**, which is what the toolkit looked like before. It
  makes every chart a trace and quietly loses the distinction; and the existing
  goldens are not an argument for anything.
- **A marker threshold in points** ("show them below 30 readings") rather than in
  pixels. The same chart in a wide window and a narrow one would then disagree
  with itself about whether its readings are visible.
- **Sharing through the widget tree** — a `CrosshairScope` ancestor the charts
  find with `findAncestorState`, as `scrollIntoView` does (ADR-0120). It would
  bind the linking to the layout: two charts in different panels of a
  `split-pane` could not share one, and a dashboard is exactly where they would
  be.
- **Sharing the pointer's x** rather than the index. It is the more general
  answer and it needs the charts to agree about what x *means*; with an index the
  group is one integer two widgets read, which is what §3.1 asked for.

## Consequences

- **The crosshair and the readout are separate conditions now.** They were the
  same one — `paintHover` returned early when the readout was null — and a linked
  chart drew nothing at all until they were split. The marker's ring colour moved
  off the `Readout` for the same reason: reading it off the thing that is null
  was the crash.
- **A chart in a group is rebuilt by a pointer that is not in it**, which is one
  `setState` per linked chart per point crossed. Cheap, and guarded by the group
  notifying only on a change; a dashboard of twelve linked charts is twelve
  rebuilds per point rather than per pixel.
- **A leak is possible and is tested for.** A chart that stayed subscribed would
  hold the group's listener list — and through it the last window's charts —
  alive. `CrosshairGroup.listenerCount()` exists for that test and for nothing
  else.
- **Every sparse chart in the toolkit gained dots**, including the showcase's.
  Deliberate; the goldens moved with it.
- **`charts.md` §3.1 is complete except for the gradient fill**, which needs a
  Blend2D gradient on the export list — shared work with `goldberry-html`
  (ADR-0190) and recorded in `TODO.md` rather than faked with a stack of
  translucent strips.
