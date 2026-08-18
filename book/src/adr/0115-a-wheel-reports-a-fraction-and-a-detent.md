# 115. A wheel reports a fraction and a detent

Date: 2026-08-18

## Status

Accepted. Closes the second of the five disagreements in `docs/ARCHITECTURE.md`
§17.1. Follows [ADR-0056](0056-sdl-is-the-windowing-layer.md) (SDL is the
windowing layer) and [ADR-0089](0089-a-knobs-gesture-is-a-rate.md) (a knob's
wheel is a rate).

## Context

`docs/design-system.md` §2.4 asks for "pixel-precise wheel/trackpad deltas with
line fallback". What ships is lines. That has been recorded as a disagreement
since the input layer landed and left open ever since, on the grounds that SDL
exposes no pixel axis: Wayland's `wl_pointer.axis` and macOS's
`scrollingDeltaY` both carry one, and `SDL_MouseWheelEvent` does not surface
it. Reaching the design system's wording means going around SDL to the
platform.

`scroll` is the first widget that has to care, so the question is due. And
reading the header again makes the shape of the answer clearer than "SDL has
no pixel axis" suggested:

```c
typedef struct SDL_MouseWheelEvent {
    ...
    float x, y;                 /* fractional */
    SDL_MouseWheelDirection direction;
    float mouse_x, mouse_y;
    Sint32 integer_x, integer_y; /* accumulated whole clicks (3.2.12+) */
} SDL_MouseWheelEvent;
```

There are **two** numbers per axis, and the toolkit has been reading one of
them. `x` and `y` are fractional — a precision touchpad reports parts of a
detent — and `integer_x`/`integer_y` are SDL keeping the running fraction
itself and emitting a whole click when it crosses one.

Which of the two a consumer wants is not a matter of taste, and neither is
derivable from the other:

- A **scroll view** wants the fraction. Round it and a trackpad scrolls in
  jerks; that is the entire content of "pixel-precise" as a user experiences
  it.
- A **`select` stepping options**, or anything else that moves in discrete
  steps, wants the click. And it cannot get one by truncating the fraction: a
  trackpad dragged slowly reports a long run of values that each truncate to
  zero, so a control that truncates per event **never moves at all**. The
  accumulation has to be kept across events, and SDL is already keeping it.

## Decision

**The delta stays in lines, and the SPI carries the detents beside it.**

`PointerWheel` and `PointerEvent` gain `ticksX`/`ticksY` alongside
`deltaX`/`deltaY`. The float is the fraction, normalized as before — positive
down and right, un-flipped where the platform inverted it. The int is SDL's
accumulator, negated on the same axis and for the same reason, and **passed
through rather than derived**. A consumer picks the one that matches what it
means: a distance reads the float, a step reads the int.

Every path that cannot know better truncates — a synthesized event, a headless
`scrollPointer`, a test poking the router — so the pair is always populated and
the honest value is available wherever a real accumulator exists.

**Lines are not becoming pixels.** What a line is worth in pixels is the
scrolling widget's to decide, and it is decided where that widget is rather than
in the event. It is *not* a token today, and that is a gap rather than a choice:
nothing lets a widget read a resolved custom property, so a `--gb-scroll-line`
would be a number an author could set and no widget could see. ADR-0116 carries
the constant and `book/src/TODO.md` carries the gap.

### Why not go to the platform for a real pixel axis

Because ADR-0056 already decided this, and the reasons have not weakened.
Goldberry ships one native library and no hand-written platform backends; a
pixel axis means Wayland, X11, macOS and Windows code paths, four places for
"natural scrolling" to be got wrong again, and a second event route that the
SDL one would have to be reconciled with. That is a large permanent cost for a
property the fraction already delivers.

So §2.4 is satisfied **in effect and not in mechanism**, and this ADR is where
the difference is written down rather than left as an open row in a table. What
the design system wanted from "pixel-precise" is scrolling that does not
quantize; that is what a fractional line delivers, and the only thing lost is
that a line's worth in pixels is the toolkit's decision rather than the
compositor's. If a platform backend ever exists for another reason, the float
becomes a pixel count, the token becomes 1, and no consumer changes.

## Consequences

The wheel path carries two numbers per axis, and each one has exactly one right
consumer. Reading the wrong one is a bug the documentation now names in both
directions: truncating `deltaY` gives a control that never steps, and rounding
it gives a scroll view that jerks.

`Knob` keeps reading the fraction. §3 calls its wheel a rate and ADR-0089 built
it as one — a fast scroll moves it further — so it is not a detent consumer,
and the `Math.max(1, …)` that makes a stepped knob move at least one step for
any scroll at all stays what it was. The detent reader is `select`'s, when it
lands.

`SdlEventBuffer.writeWheel` gains an overload that states the detents rather
than truncating them, because the case worth testing — fractions that truncate
to nothing and a click arriving part-way through — cannot be produced by any
function of one event's floats.

The `integer_*` pair is SDL 3.2.12 and later. The build pins 3.4.14 and the
layout probe already declares both fields, so nothing new is being assumed
about the struct; what changes is that the offsets are now read.
