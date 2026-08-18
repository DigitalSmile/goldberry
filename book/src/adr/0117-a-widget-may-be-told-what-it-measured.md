# 117. A widget may be told what it measured

Date: 2026-08-18

## Status

Accepted. Completes
[ADR-0116](0116-a-scroll-view-is-a-clip-an-offset-and-two-extents.md), and
opens from the other side the door
[ADR-0080](0080-a-value-is-measured-along-a-part.md) and
[ADR-0097](0097-a-selection-that-travels-needs-a-geometry.md) both stopped at.

## Context

ADR-0116 gave a scroll view the two extents it needs to *clamp*, on the event
that asks it to move. That was enough because a clamp is only ever wanted in
response to input: nothing needs to know how far a viewport could scroll until
something tries to scroll it.

A scroll**bar** breaks that. §2.4 asks for a thumb whose length says what
proportion of the document is on screen, and it has to be right **before anyone
touches anything** — a widget whose entire job is to say "there is more below"
cannot wait until you have found out. The extents have to reach `build`, and
`build` runs before layout.

This is the third time the same wall has been hit. ADR-0080 wanted a slider's
track and got the router to answer for it; ADR-0097 wanted the distance between
two segments and redesigned the drawing rather than acquire it; ADR-0116 wanted
two rectangles and put them on the event. The first two avoided needing geometry
outside input. This one cannot.

## Decision

**A widget may implement `Measured` and be told, once per frame, what the last
frame laid it out as.**

```java
public interface Measured extends Widget {
    void measured(Extent bounds, Extent part);
}
```

The same pair a `PointerEvent` carries — the node's own box, and the part it
names through `Handles.localPart()` — so a widget that reads geometry reads one
shape however it arrived.

It is delivered from `PointerRouter.updateRegions`, which is the one place that
holds the painted rectangles and the one call every window already makes once
per frame. That is `localFor`'s argument again: the widget cannot see its own
elements, so the thing that can is the thing that tells it.

### Three rules, and the third is the one that matters

1. **It is last frame's.** A measurement, not a prediction. A widget acting on
   it is one frame behind.
2. **It fires only on a change**, comparing *both* extents. A still window
   notifies nothing, so §1.7's idle frame loop stays idle. Both halves, because a
   viewport whose content grew while it did not is exactly the case a scrollbar
   must redraw for.
3. **What it triggers must not change what it reports.** This is the whole
   safety argument, and it is why the interface is opt-in rather than a hook on
   every widget.

Rule 3 needs care, because the obvious implementation loops: a measurement
causes a rebuild, which causes a frame, which produces a measurement. The scroll
view terminates because **the bars are absolutely positioned** — they take no
space from the content and none from the viewport, so the second frame measures
exactly what the first did, the router sees no change, and it stops. One extra
frame when a window resizes, and none after it. The tests assert the
convergence rather than trusting the argument.

A first attempt did not call `setState` at all, reasoning that a rebuild would
be a loop. That was wrong in the other direction: without one the extents never
reach a `build`, and the thumb never appears. The rebuild is necessary; what
makes it safe is the absolute positioning, not its absence.

### The scrollbar itself

§2.4's numbers, taken as written: a 6px thumb widening to 10 with a visible
track on hover, `full` radius, the accent colour while dragging, and a fade
800ms after the last movement.

- **The length is the widget's; everything else is the stylesheet's.** How long
  a thumb is says what proportion of the document is visible, which is the one
  thing no selector can know — so it is written through `Styled.restyle`, which
  ADR-0099 opened for exactly this. The colour, the radius and the width are
  untouched.
- **It travels by `translate`**, like the content it mirrors, for §1.7's reason:
  a thumb that moved by changing a margin would re-run Yoga on every wheel notch
  to shift a 6px rectangle.
- **The thumb has a floor of 24px.** A hundred-screen document gives a 1.5px
  thumb — proportionally honest and impossible to grab. Every scrollbar ever
  written makes this trade.
- **The bar has no padding**, and that is load-bearing rather than taste. The
  arithmetic runs against the viewport's length because that is the only
  measurement the widget is given; a bar that inset its own track would have a
  track shorter than the number used, and the thumb would overrun the bottom by
  exactly the padding. The thumb is centred across the bar instead.
- **The widening is a jump, not a slide.** §1.7 keeps layout properties off the
  transition whitelist and ADR-0067 refuses `transition: width` outright. Only
  the colour transitions.
- **Hover is read from the viewport, not the thumb.** A 6px target cannot be
  pointed at until it has grown, so `scroll:hover` is what widens it — which is
  also what §2.4's "visible track on hover" describes.

### The fade is a clock, not a transition

"800ms after the last movement" is not a style and no selector can express
*when*, so it cannot be a transition (ADR-0067). It is `spinner`'s shape and
`TabPhase`'s: a function of the frame clock, read in `render`, which is the only
place a widget is handed one (ADR-0081).

The wake and the clock arrive at different moments — a wheel event knows
something happened and has no time; `render` has a time and does not know what
happened — so `ScrollFade` holds a pending flag that the next frame stamps.
Exactly how `TabPhase` records the beginning of an arrival.

**The bars start invisible.** A window that opens on a scrollable document shows
nothing until the user scrolls or points at it. §2.4 calls these *overlay*
scrollbars, and an overlay that greets you is a reserved gutter with extra
steps.

## Consequences

`scroll` is now what §2.4 describes, less the reserved-gutter mode. Dragging the
thumb, clicking the track to page, hover-widening and the idle fade all work.

**Every widget in the catalog can now ask for geometry, and almost none should.**
The temptation is real and the failure mode is specific: a widget that sizes
itself from last frame's measurement lags its own content, and one that does so
in a way that changes the measurement never settles. The rule that keeps this
honest is ADR-0080's, restated: read geometry to interpret an *input* or to draw
something that **cannot affect layout**, never to decide a size.

`scrollIntoView` is still not built, and this does not build it. It needs a
descendant's position *within* the viewport — "where is that box inside me"
rather than "how big are these two boxes" — which is a different question that
nothing asks yet. `affix` needs the same one.

The reserved 12px gutter for "always show scroll bars" is unbuilt, and waits on
a settings mechanism rather than on anything here. It is genuinely different
from the overlay bar — §2.4 says "layout, not overlay" — so it is a second
drawing rather than a flag.
