# ADR-0079: A continuous value is placed by ratio, and the router says where you are

- **Status:** Accepted
- **Date:** 2026-08-17
- **Relates to:** `docs/core-widgets.md` §3; `docs/design-system.md` §1.3, §3, §3.1;
  extends [ADR-0075](0075-a-gestures-origin-is-the-routers.md); relies on
  [ADR-0073](0073-a-composite-is-one-tab-stop.md) and
  [ADR-0078](0078-a-focus-scope-has-an-axis.md); fifth instance of
  [ADR-0065](0065-a-part-is-styleable-and-not-constructible.md)

## Context

`slider` is the sixth control and the first whose value is **a number rather than
a state**.

Every control before it has a value a stylesheet can name. A checkbox is on or
off; a switch is one of two positions, and
`toggle-track:checked toggle-thumb { transform: translate(16px) }` is literally
how its thumb moves — the stylesheet owns where, and `ToggleThumb` does not know
that it moves at all. That is a design worth keeping, and it stops working the
moment the value is 37.4.

Two things follow, and neither has an answer in the toolkit as it stands:

1. **Where does the thumb go?** No rule can name a position that came out of a
   model.
2. **Where is the pointer along the track?** §3.1 asks for "drag: 1:1, no
   animation", which needs the pointer's position *relative to the control* — and
   a widget is a value with no idea where it was laid out.

## Decision

### The thumb is placed by flex ratio, because a transform cannot express it

The track's children are a fill, the thumb and a spacer:

```
[ slider-fill grow=f ][ slider-thumb 16 ][ slider-rest grow=1-f ]
```

Yoga hands free space out in proportion to the grow factors, so the thumb lands
exactly `f` of the way along whatever width the track turned out to be — and
**nothing in Java ever learns that width**.

`transform: translate` was the obvious first answer and it is not merely awkward,
it is *unable*: CSS percentages inside `translate` are a proportion of **the
moving box**, so `translate(50%)` moves the thumb by half a thumb rather than to
the middle of the track. Expressing it as pixels would need the track's width,
which is Yoga's answer and does not exist until after layout —
[ADR-0068](0068-the-transform-stack-is-java-side.md) says exactly this about
`translate(50%)`, arriving at the same wall from the other side.

The ratio also produces the **filled portion for free**, as a box the cascade can
reach. A slider with no fill reads as a groove with a dot on it; the fill is what
says "this much", and it costs nothing because it is the flex child doing the
positioning anyway.

`slider-rest` paints nothing and exists so that Yoga has something to give the
remaining space to. It is a node rather than a number because a theme that wants
to style the unfilled groove separately should be able to.

### The router reports where inside a widget an event landed

`PointerEvent.local()` — the position relative to **the widget currently
handling** the event, and that widget's size, with `fractionX()` and
`fractionY()` on top.

This is the direct sibling of ADR-0075's `dragX()`, and the argument is the one
already written three times: the router owns what the widget cannot see. A widget
does not know where it was laid out; the router is holding the hit-test snapshot
that says.

**Relative to the handler and not to `target()`**, which is the part that took
thought. Dispatch bubbles, so one event reaches a chain of widgets: a press on a
slider's thumb *targets the thumb*, and the slider handling that press wants the
position along **itself**. So the router re-points `local()` before each handler
runs, rather than computing it once. The alternative — making the parts
un-hit-testable so the slider is always the target — would have changed how every
existing part behaves, mid-stream, to avoid a three-line loop.

`Local.UNKNOWN` is zero-sized rather than null, so a widget poked directly by a
test reads `fractionX() == 0` instead of dividing by zero. Same shape of decision
as `dragX()` returning `NaN`: the degenerate value has to behave sensibly under
the arithmetic a caller will actually write.

### The control snaps and clamps; the application does neither

What travels up through `change` is already snapped to `step` and clamped to the
range. A widget that reported a raw fraction would make **every** application
repeat the same arithmetic and get it slightly differently wrong.

Three rules, and each is a choice rather than an obvious consequence:

- **Steps are counted from `min`, not from zero.** A slider from 1 to 10 stepping
  by 2 offers 1, 3, 5, 7, 9 — the values reachable from where the track starts.
  From zero it would offer 2, 4, 6, 8, 10 and make `min` unreachable, which is
  the more surprising of the two and hides at the end of the track.
- **An arrow offers the next *reachable* value, not the current plus a step.**
  Nothing snaps a value on the way *in* — snapping what the application set would
  be the control overruling the model — so a slider stepping by 25 can be showing
  40, and `Right` should offer 50 rather than `40 + 25` rounded to 75. The two
  readings agree whenever the value is on the grid, which is every other time.
- **The ends are always reachable.** 0 to 10 stepping by 3 has a grid of 0, 3, 6,
  9, and a user who presses `End` and lands on 9 has been told the end of the
  track is not the end. `max` is a value the slider promises; the grid is a
  convenience over the values between.

A value from the model is **clamped but never snapped**: out of range is an
application bug, and a thumb drawn off the end of its track is a worse way to
report it than one pinned at the end.

### It is the first control that relies on arrows reaching it first

ADR-0073 put focus-scope traversal *after* the focused chain declines a key, and
wrote down that it was for "a slider stepping its value". This is that slider.

The arrows are consumed **even when the value did not move** — a slider at its
maximum still owns `Right`. Letting it through would hand the key to an enclosing
scope and move focus off the control the user is adjusting, which is a strictly
worse outcome than nothing happening.

Repeats are honoured, and this is the first control where they are: holding an
arrow to run a value up is how a slider is used, while holding `Space` on a
checkbox to flutter it is not.

### `fader` is a class, not a widget

`docs/core-widgets.md` calls `fader` a vertical `slider`. It ships as
`slider.vertical`, for the reason `radio-group.inline` is a class: **the widget
names the semantics and the stylesheet names the axis.**

`flex-direction: column-reverse` on the track is the whole of it — that puts the
minimum at the bottom, where a fader's minimum belongs — and the widget inverts
the pointer fraction to match. The two have to agree, and a golden image of a
fader at 25% is what says they do.

`fractionY()` is deliberately **not** inverted at the router. Zero is the top,
because that is where zero is on a screen; a control whose maximum is at the top
is a fact about the control.

## Consequences

- `slider` ships: a record, a node, a CSS type, `bind` + a valued `change`, drag,
  arrows, PageUp/PageDown, Home/End, `:disabled`, the shared focus ring, and four
  golden images. **Six of thirteen controls**, and `fader` with it.
- `PointerEvent.local()` is the primitive `knob`, `split-pane` and a scrollbar
  each need next, and none of them will look like a slider.
- **Four new parts** — `slider-track`, `slider-fill`, `slider-rest`,
  `slider-thumb` — bringing the total to ten. ADR-0065's argument holds a fifth
  time and was not restated.
- `slider` is deliberately **absent from the shared `transition` rule**, asserted
  by a test. §3.1's "drag: 1:1, no animation" is a requirement a stylesheet can
  break silently, and a thumb that eased toward the finger would lag it.
- **Open: no tick marks and no value label.** §3 asks for both as optional. The
  label is the awkward one: it would sit inside the control's own box, so the
  pointer-to-value mapping would stop being "along the control" and would need
  the *track's* rectangle rather than the slider's. Worth doing when something
  needs it, and it is a reason to be glad `local()` is per-handler.
- **Open: `fader`'s dB scale is not implemented.** §3 asks for "optional dB scale
  mapping", which is a non-linear value curve — the same shape of thing `knob`
  will want for its taper. It belongs on the widget as a mapping function and is
  not invented here for one caller.
- **Open: the pointer maps over the control's full width**, so at the extremes
  the thumb's centre is up to 8px from the finger. Mapping over the *travel*
  needs the thumb's width, which is the stylesheet's (`slider-thumb { width }`)
  and not the widget's. The mapping is monotonic and reaches both ends exactly,
  which is what matters; closing the gap means a widget being told a resolved
  metric, and that is a bigger door to open than this is worth.
