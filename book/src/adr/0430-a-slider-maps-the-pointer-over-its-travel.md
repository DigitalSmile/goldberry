# 430. A slider maps the pointer over its travel

Date: 2026-09-19

## Status

Accepted. Finishes what
[ADR-0079](0079-a-continuous-value-is-placed-by-ratio.md) started and
[ADR-0080](0080-a-value-is-measured-along-a-part.md) narrowed, by spending the
door [ADR-0251](0251-a-widget-may-read-a-token-and-a-nested-scroller-is-named.md)
opened.

## Context

A slider's thumb is a 16px disc centred on the value, so its **centre** travels
from 8px to `width − 8`. Half a thumb at each end is unreachable by
construction: that is what it means for a disc to be centred on a point.

The pointer was mapped over the track's **full width**:

```java
return isVertical() ? 1 - event.local().fractionY() : event.local().fractionX();
```

So a press at `x = 8` — the leftmost position the thumb's centre can occupy —
asked for 4% of the range, and the disc drew itself at 4% of the travel, 7px to
the right of the finger. At `x = width − 8` the same in reverse. The error is
zero at both extremes (0 and `width` clamp to `min` and `max` either way) and
worst at exactly the two points where the thumb is when the value is at an end:
**8px, in a control where 8px is half the thing being dragged.**

It shows up as a readout that disagrees with the grip. A user drags to the right
edge of the track, sees the thumb stop short of the finger, and lets go early.

### The door that was open, and the one that was shut

The entry that asked for this had already found the shape of the fix and where
it jammed:

> "a widget being told a resolved metric" is `Paints.Context.length` and has been
> since ADR-0251 — but it is a **`render`-time** read, and the pointer arrives at
> `onPointer` where there is no context to ask.

That is exactly right, and `scroll` had already hit it and solved it:
`--gb-scroll-line` is resolved in `ScrollViewport.render` and banked into
`ScrollState`, because a wheel arrives where there is no cascade. The bargain is
that the number is one frame late, and it is sound because a paint always
precedes an input — there is no frame in which a finger reaches a tree that has
not been drawn.

A `Slider` could not take that bargain. It was a `record` implementing
`Widget.Leaf`, with nowhere to put a number that outlives a rebuild.

### The tick marks were never wrong

Worth saying plainly, because it is the part that looks like it should have been
broken and was not. `slider-ticks` is `padding: 0 8px` — half a thumb, written in
the stylesheet immediately below the thumb's own width, with a comment saying so
— so a mark has always named a position the thumb's centre can actually reach,
and `SliderGeometryTest.thumbCentresOnTheEndMarks` has always asserted it at both
ends. The marks and the thumb agreed with each other. What disagreed with both
was the finger, and a scale is exactly the arrangement in which that becomes
visible: the user drags until the thumb is on a mark and the readout says
something else.

## Decision

**`slider` is stateful, on `scroll`'s arrangement, and the pointer is mapped over
`width − thumb`.**

Three nodes where there was one:

```
Slider          the value. A record, Stateful, styles nothing
└── SliderState the banked thumb width, and nothing else
    └── SliderControl   `slider` in the cascade; Styled, Paints, Handles, Semantics
        └── SliderTrack …
```

```java
private double travel(double along, double extent) {
    if (extent <= 0) {
        return 0;
    }
    var length = extent - thumb;
    return length <= 0 ? clamp01(along / extent) : clamp01((along - thumb / 2) / length);
}
```

Four things about the shape.

**The split is `scroll`'s and `tabs`', and it is not optional.** A stateful widget
that was also styled would put two `slider` nodes in the cascade, one inside the
other, and every rule would apply twice (ADR-0109, ADR-0116).
`WidgetParityTest` already knows this pattern and checks parity against what a
widget *describes* rather than against the widget, so `slider` passes its
"exactly one node carries this type" assertion unchanged.

**`SliderControl` holds the `Slider` rather than copying it.** `ScrollViewport`
takes thirteen separate components; a second record with eleven of `Slider`'s
would be eleven chances to forget one, and every one of them would be a value
that draws perfectly and is wrong. The arithmetic — `resolved`, `fraction`,
`snap`, `clamp`, `stepFrom`, `ask` — stays on the record where it always was, and
only the two handlers and the `render` move.

**It is state about the stylesheet, not about the value.** ADR-0063 is untouched:
a slider still owns no value and a drag still travels up as a request. What
`SliderState` holds is a *measurement*, which the application has no opinion
about — the same category as `ScrollState.line`, and a different category from
`ScrollState.offsetY`.

**A track with no travel falls back to the position fraction.** When
`extent ≤ thumb` there is nowhere for the centre to go and no mapping is
"correct"; what is available is a mapping that is still monotonic and still
reaches both ends, which are the two properties anything mapping a pointer to a
value has to keep. It also covers `PointerEvent.Local.UNKNOWN` — the zero-sized
local a widget poked with no layout behind it receives — which reads as the start
of the track exactly as it did before.

## Consequences

- The thumb is under the finger everywhere on the track, on both axes. `Scale` is
  untouched: the curve is applied to the fraction, and only the fraction changed.
- **`--gb-slider-thumb-size` is a token now**, and `slider-thumb` sizes itself
  from it, so the disc the user drags and the arithmetic the pointer goes through
  are one declaration rather than two numbers that happen to agree.
- **Two numbers still have to follow it by hand**, and this is the cost: the
  thumb's `border-radius`, which is half of it, and `slider-ticks`' inset, which
  is also half of it. §8's subset has no `calc()` —
  `CssLength.parse` takes a single token and a `calc(…)` is many, so
  `padding: 0 calc(var(--gb-slider-thumb-size) / 2)` parses, resolves to nothing,
  and is dropped with a warning. `SliderTest.ticksAreInsetByHalfAThumb` already
  asserted the relation rather than the number and now carries the weight of it;
  `theTokenAgreesWithTheDefault` holds the CSS declaration against
  `SliderControl.THUMB`. An author who moves the token and not the inset gets a
  failing test rather than a scale that points eight pixels wrong.
- A slider is **an element deeper** than it was. Anything that reached for the
  `slider` node by taking a widget's own element now has to walk to the first
  styled one — `SliderTest.styleOf` and `SliderGoldenTest.PseudoState` both do,
  and the second is the interesting one: a `:hover` forced onto the composition
  is a `:hover` no rule can see. The router has no such problem, because it
  dispatches to the element that handles, which is the styled one. The dark
  interaction golden not moving is what says so.
- **No golden moved for this change.** The pointer mapping is not drawn.
- `Slider` is no longer `Styled`, `Paints`, `Handles` or `Semantics`. It is still
  `Attributed<Slider>` and `Bindable<Slider>`, so every chained call an
  application writes still compiles and `#gain` still reaches the node, which is
  the whole point of a composition handing its attributes down.
- `Knob` has the same class of problem and is **not** fixed here. Its geometry is
  angular rather than linear and its mapping is a drag delta rather than a
  position, so nothing above transfers; it wants its own entry.
