# 203. A time axis is time, not a relabelled index

Date: 2026-08-24

## Status

Accepted. `content-widgets.md` §3.1's "`java.time`-driven time axes (tick
stepping across sec/min/hour/day/month/year boundaries)", and the last of
`charts.md` §3.1's big rows.

## Context

Every chart in the toolkit has had one x: the **point index**. Points are evenly
spaced and `categories` labels them, which is right for `Mon…Sun` and wrong for
anything that arrived on a clock. A metric scraped every 15 seconds that missed
four minutes has, on an index axis, exactly one step of gap — the same step as
every reading that was on time. That is a picture of a schedule nobody kept.

So the question is not "how do I write the labels", which is how a time axis is
usually implemented. It is where the points go.

The second question is the calendar. `Ticks` is Wilkinson's algorithm for
numbers, and a nice number is a round multiple; time has no round multiples. Sixty
seconds are a minute, sixty minutes an hour, twenty-four hours a day — and then a
month is 28, 29, 30 or 31 days and a year is 365 or 366. A step of
`2 592 000 000 ms` is a month only in a year with no February in it and has
drifted five days by December. A step of `86 400 000 ms` is a day except on the
two days a year a zone changes offset, when it is 23 or 25 hours — so an axis
stepped that way reads `00:00, 00:00, 01:00, 01:00…` from the last Sunday in
March.

## Decision

**The x is time.** `TimeAxis` carries one `Instant` per point index, the plot's x
scale runs over epoch milliseconds, and the drawn position of every point — line,
band, crosshair, marker — comes from one method so the four cannot disagree.

**The ticks are stepped in `java.time`.** `TimeTicks` picks a rung from a ladder
of the steps a clock is actually read in — 1, 2, 5, 10, 15, 30 seconds; the same
minutes; 1, 2, 3, 6, 12 hours; 1, 2, 7, 14 days; 1, 3, 6 months; 1, 2, 5, 10 years
— snaps to a boundary of that rung's own unit, and advances with
`ZonedDateTime.plus`. So a month is as long as that month is, and a day across a
zone change is 23 or 25 hours, because `java.time` knows and this file does not
have to. The only arithmetic on numbers is choosing the rung, where a month is
30.44 days and being approximate is the point.

**The zone is the application's; the format is the root locale.** These land on
opposite sides of the same-looking question and the reason is that they are
different questions: a locale changes *how* a number is written and a zone
changes *which* number it is. An axis in the machine's language is an unfamiliar
picture; an axis in the machine's zone is the correct one, because a desktop
application showing "Tuesday" means the user's Tuesday. So `times(list)` reads
`ZoneId.systemDefault()` and `times(list, zone)` is what a test — or a chart of a
server's clock — passes.

**An axis that does not cover the data is not used.** Fewer instants than points
falls back to the index rather than drawing half a time axis: an axis that ran
out would place the remaining readings at a time nobody measured.

**A bar chart ignores it.** A bar has a width and sits *in* a band; bands of
unequal width are a different chart, and half-applying the axis — bars on bands,
labels on instants — would draw labels that do not line up with the bars under
them.

**A sampling gap is not a hole.** The axis makes an unscraped stretch *wide*; the
line still crosses it, because both ends are readings that happened. An
application that means "and nothing was measured in between" says so in the data
with a `NaN`, and `NullPolicy` draws the break it already knows how to draw
(ADR-0201). Two mechanisms, two meanings, and they compose.

## Alternatives considered

- **Relabelling the index**, which is what "time axis" often means: keep the even
  spacing and write times under it. It is a third of the work and it destroys the
  one thing the reader came for — a chart of a system that stopped reporting looks
  exactly like a chart of one that did not.
- **Times on the `Series`**, so each series carries its own. A chart has one x
  (`charts.md` §3.4), and two series timed differently is the same mistake as two
  y-scales in the other direction.
- **Stepping in milliseconds** with a table of "nice" durations. Simpler, and
  wrong twice a year and every February — see the context. The DST case is in
  `TimeTicksTest` because it is the one nobody writes a test for.
- **`Duration`-based steps for months and years.** `Duration` is a fixed number of
  seconds by construction, so it cannot express "a month"; that is what `Period`
  and `ChronoUnit.MONTHS` are for, and it is why the ladder holds a unit and an
  amount rather than a length.
- **Defaulting the zone to UTC** for reproducibility. It would make every
  application's default wrong to gain what only a test needs, and the test can
  say `UTC` in one call.

## Consequences

- **`ChartOptions` earned itself.** The axis is a field on the bundle rather than
  a seventh component on three records, which is what ADR-0202 said the next
  feature should find.
- **A crowded time axis strides.** Every *n*th tick for the smallest *n* that
  fits, like the categorical labels — the first version dropped colliding labels
  individually and produced `09:00, 09:30, 09:45`, which keeps two neighbours and
  loses the one between them so the reader cannot tell what the spacing is. The
  stride allows for the end labels being clamped inward, which is the thing that
  makes the first two touch.
- **The readout says *when*, to the second.** An axis label is a position and a
  readout is *the* reading, so `14:32:07` is right even on an axis stepping in
  hours; the date joins it only when the chart spans more than a day.
- **A 100 000-point timed series costs an array of doubles per frame.** The point
  times are unpacked once in `render` and read by the painter; LTTB still
  downsamples what is drawn. If that ever matters, the fix is to keep the array
  across frames on the state, which is where `PaintedGeometry` already lives.
- **Still not built**: log axes, interpolation, soft bounds, gradient fills, point
  markers, and a crosshair shared between charts. §3.1's remaining rows are all
  smaller than this one.
