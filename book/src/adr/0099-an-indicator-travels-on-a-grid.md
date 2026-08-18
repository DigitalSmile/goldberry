# ADR-0099: An indicator travels on a grid

- **Status:** Accepted
- **Date:** 2026-08-18
- **Relates to:** `docs/design-system.md` §3 and §3.1, `docs/ARCHITECTURE.md` §8,
  supersedes the deferral in
  [ADR-0097](0097-a-selection-that-travels-needs-a-geometry.md) (the rest of that
  record stands), extends [ADR-0068](0068-the-transform-stack-is-java-side.md)

## Context

`segmented` shipped with the switch animated the only way it could be: each
segment's fill cross-fading on `--gb-motion-fast`. §3.1 asks for something else —
"selection indicator `translate` + width between segments, base" — and
[ADR-0097](0097-a-selection-that-travels-needs-a-geometry.md) deferred it with
this argument:

> a `translate` would have to name a **distance** — how far it is from the segment
> the selection is leaving to the one it is arriving at. That distance is a fact
> about two boxes' laid-out geometry. A stylesheet cannot write it, because
> segments are as wide as their labels; and a widget cannot compute it, because
> ADR-0080 is the record of where geometry *is* available — the router, after a
> paint — and `build`/`render` run before Yoga.

Every clause of that is true and the conclusion is wrong, because of the premise
buried in the middle: **"segments are as wide as their labels"**. That was a
choice, not a fact. If every segment is the same width, the distance to segment
*k* is *k* times one segment — and "one segment" is a proportion, not a length,
so nothing has to measure anything.

The second half turned out to be a real gap, but a different one. A widget can
compute the proportion easily; what it could not do is get that value somewhere
the **animation** would see it. A value written in `render` arrives after
`WidgetRenderer` has already observed the node's style and started whatever
transitions it declared, so it snaps. That is why every Java-computed geometry in
the toolkit so far — a knob's arc, a slider's fill ratio — is documented as *not*
animating.

## Decision

### The segments are a grid, and the grid is the widget's

Every segment is exactly `1/n` of the track. `SegmentedTrack` sizes them, because
`n` is the one metric of this control no selector can express — a stylesheet
cannot count the segments.

`flex-grow: 1` alone does not do it: with a content basis each cell is its label
plus an equal *share* of what is left, so three labels of different lengths give
three different widths. `flex-basis: 0` does give equal cells and was tried;
Yoga then computes the track's content size as **zero**, so a bar that nothing
gives a width to collapses to its padding. Explicit percentage widths are the
form that works in both directions.

### The indicator is one box that moves

```
segmented                     radius 8, 1px --gb-border, padding 2
`-- segmented-track           the grid: no padding of its own
    |-- segmented-indicator   absolute, 1/n wide, translate(k × 100%)
    `-- option ...            each exactly 1/n
```

The translation is a **percentage of the indicator's own border box**, which
[ADR-0068](0068-the-transform-stack-is-java-side.md) already established is
resolved by the painter after Yoga has run. One number — `k × 100%` — is right for
every bar at every size and every segment count, and it needs no measurement at
all. The `width` half of §3.1's row therefore never animates and does not need to:
on a grid every cell is the same size, so there is nothing for a width to move.

### The track exists because two percentage bases disagree

Yoga resolves an in-flow child's percentage width against its parent's **content**
box and an absolute child's against its parent's **padding** box — CSS's rule, and
4px of disagreement on a bar whose padding is 2. A pill sized against the padded
bar is wider than the segment it covers, and the error compounds per cell.

A track with no padding of its own makes the two bases the same box. The bar keeps
the padding, the border and the radius; the track keeps the grid. `slider` grew a
track for the same *kind* of reason ([ADR-0080](0080-a-value-is-measured-along-a-part.md)):
two boxes were doing one job under one name.

### `Styled.restyle` — §8's `inline` layer, typed

```java
default ComputedStyle restyle(ComputedStyle resolved) { return resolved; }
```

The widget's last word on its own style, applied by `WidgetRenderer` **after** the
cascade, **after** the style cache, and **before** the animation observes the
result. That ordering is the whole of the mechanism:

- after the cascade, because it is the most specific statement about one element —
  which is exactly what §8's fourth layer is for;
- after the cache, because the cached value is the cascade's answer and a widget's
  own number changes when the widget does, not when a selector stops matching;
- before the observation, because that is what makes it *animatable* rather than
  merely correct.

The style it returns is the node's, so children inherit it — CSS's rule for an
inline style, and the reason it returns a whole style rather than a patch.

**The rule that keeps it honest:** a widget may write here only what a stylesheet
could not have written. The toolkit's own use is two values, both derived from a
count no selector can express.

## Alternatives considered

**One rule per index in `controls.css`** — `segmented-indicator.at-3 { transform:
translate(300%) }`, with the widget contributing a class. Goes through the cascade
untouched, so it animates for free. Rejected because it needs a cap: a bar with
more segments than there are rules would put its pill in the wrong place, silently.
A control that is correct up to seven and quietly wrong at eight is worse than one
that refuses.

**Keep the cross-fade.** It is what shipped, it animates, and it costs nothing.
Rejected because it is not what §3.1 describes, and the difference is exactly the
thing a segmented control is *for*: the eye follows one moving object and reads it
as the same selection changing place. Two fills dimming and brightening in place
read as two things.

**Measure the labels in Java and size the cells to the widest.** Keeps
content-sized segments and still gives a regular grid, which is what
`grid-auto-columns: 1fr` does in CSS. Rejected because it means reimplementing
intrinsic sizing — the widget would have to add up its children's paragraphs,
padding and gaps and get the same answer Yoga would — and the day the arithmetic
diverges the pill is wrong by a few pixels with nothing to point at.

**Animate `left` instead of `transform`.** The obvious spelling, and a layout
property: it would run Yoga every frame of every switch, which is what §1.7's
whitelist exists to prevent.

## Consequences

**A segmented control has no width of its own.** Its cells are proportions, so its
content size is indefinite: it takes the width it is given and fills its parent
when nothing gives it one. In a column — a sidebar, a form — that is what it did
before. In a toolbar beside other widgets it will take the whole row unless it is
given a `width`, and that is a real regression in convenience for a real gain in
what the control can do. §3's row says so now.

**A label longer than its cell overflows it**, because the cells are equal and
nothing in this toolkit clips. The same limit the indeterminate progress sweep
documents, reached here by a different road.

**`position` and `inset` came off §8's unimplemented list**, `flex-basis` did not.
It was implemented in the course of this change, found not to be needed, and taken
back out rather than left in as a property nothing uses — "each arrives with the
thing that paints it".

**A segment's hover and press are a translucent wash** rather than a surface step.
An opaque fill on the selected segment would paint *over* the pill, since the
segments are drawn after it — and worse, clicking a new segment would paint the
destination fill instantly and beat the animation to it. The `--gb-overlay-*`
tokens `button.ghost` uses work on both backgrounds, which also took four tokens
out of each theme. They are translucent, so they leave `ContrastTest`'s sweep on
`button.ghost`'s terms.

**A selected label's `color` moves on `--gb-motion-base`, not `fast`.** §3.1 wrote
that rule for `toggle` — "thumb `translate` base; track colour base (**same
clock — they arrive together**)" — and it matters more here, because the label and
the pill are different boxes: a label that darkened in `fast` would be dark on the
plate for 60 ms while the pill was still on its way.

**`Styled.restyle` is a new escape hatch**, and the honest risk is that it becomes
the place things go when a stylesheet is inconvenient. What is written there is
unthemeable and unoverridable, which is right for a number nobody else can
compute and wrong for anything else. It has one caller in the toolkit and the rule
above; if it acquires a second that is *not* a count, that is the signal to look
again.
