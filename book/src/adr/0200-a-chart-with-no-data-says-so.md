# 200. A chart with no data says so

Date: 2026-08-24

## Status

Accepted. `charts.md` §3.1's "empty / loading / error states".

## Context

A chart of no series drew its axes anyway: five gridlines, five labels, and
`0, 5, 10, 15, 20` down the side. Every one of those numbers was invented. An
empty grid is not a neutral thing to draw — gridlines and axis labels are an
assertion about a scale, and asserting one over no data is the same class of
untruth as a bar chart with a baseline at 90, which this toolkit refuses at
construction.

§3.1 asks for three states rather than one: empty, loading, and failed. The
second and third cannot be derived — only the application knows whether a query
is in flight or came back angry — so they need a way to be said.

And they raise a question that looks like it has an obvious answer. An
application can write `loading ? spinner : chart` in one line, so why should the
widget grow a state for it?

## Decision

**A chart takes a `ChartStatus`: `READY`, `LOADING` or `FAILED`, with an optional
message.** When it is not ready — or when it is ready and the data is empty — the
chart's parts are one `chart-message` and nothing else: no plot, and no legend
either, because a legend keying series nobody can see is noise.

**Empty is not one of the three.** A chart whose series are empty is `READY`: the
application answered the question and the answer was nothing, and the widget
notices for itself. A fourth state the application had to declare would be one
that can disagree with the list beside it.

**The message is widgets, not paint** — the opposite of the hover readout
(ADR-0198), decided by the same question: *does it participate in layout?* A
readout is placed in plot coordinates and must not affect the box. A message is
centred, wraps when the box is narrow, and *is* the content — so it is a node, a
stylesheet reaches it, and the shaping cache serves it like any other sentence.

**It keeps the chart's box**, and that is the answer to "why not the
application": the chart is the thing with the height. `chart-message` takes the
plot's `flex-grow`, so a chart in a 156px card is 156px tall while it loads, and a
`masonry` of cards whose charts came and went as their queries resolved does not
reflow the wall twice per panel. A spinner in a card has to be told to be
card-sized by somebody, and only the chart already knows.

**Two strings, and no spinner.** `No data` and `Loading…` are the toolkit's own
words — the second and third it writes rather than the application, after
`Field.REQUIRED_MESSAGE`, and for that message's exact reason: an application that
passed an empty list has supplied no words. An application with better ones passes
them. There is no spinner because §1.7 keeps the frame loop idle when nothing is
animating, and a dashboard's charts are all waiting at once (ADR-0081).

## Alternatives considered

- **Leave it to the application.** It costs the box, as above — and it means every
  application writes the centring, the muted colour and the caption size again,
  differently.
- **A fourth `EMPTY` state the application declares.** Two sources of truth for
  one fact, and the interesting failure is the quiet one: a chart told it is
  `READY` with an empty list and a chart told it is `EMPTY` with a full one.
- **Draw the message in the canvas**, like the readout. It would need the text
  shaped in `render` and centred in the painter, and gain nothing: the message
  neither moves with a pointer nor has to avoid the data.
- **Throw, or refuse an empty series list at construction**, which is what
  `donut-chart` does for two slices. Wrong shape here: an empty list is not a
  programming mistake, it is Tuesday. A query returning no rows must not be an
  exception in a paint pass.
- **A red panel for the failed state.** §1.2 draws the aurora hues as a glyph and
  a border on a surface rather than as a filled block, and a chart-sized red
  rectangle reads as an alarm rather than as "this one query did not answer". The
  text takes `--gb-danger` and nothing else does.

## Consequences

- **Four widgets, one answer.** `ChartParts.messageFor` is shared, so `line`,
  `area`, `bar` and `donut` cannot drift — including the one place "empty" means
  something different: a donut of three zeroes has no whole to be part of, so a
  positive total is what counts as data there.
- **The box is asserted, not argued.** `ChartStatusTest` measures the laid-out
  height of a chart with data, loading, and failed, and they are the same number.
  It is the one claim the CSS has to keep.
- **`Series` gained no state.** A chart's status is about the *chart* — one
  query, one panel, one message. Per-series loading would be a chart that is
  half a picture, which is a dashboard-machinery feature §3.4 already refuses.
- **The two strings are a translation obligation.** The toolkit has no i18n
  mechanism and this adds the second and third places that would need one. Both
  are overridable per chart, which is the whole of the answer until there is a
  mechanism.
