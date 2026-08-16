# ADR-0056: The wheel is lines, and the sign is ours

- **Status:** Accepted
- **Date:** 2026-08-16
- **Relates to:** `docs/ARCHITECTURE.md` §7.1; [ADR-0010](0010-hand-written-ffm-bindings.md), [ADR-0019](0019-the-backend-spis-first-cut.md), [ADR-0054](0054-hit-testing-runs-against-the-painted-frame.md)

## Context

§7.1 asks for "wheel/scroll: pixel-precise deltas with line-based fallback".
[ADR-0054](0054-hit-testing-runs-against-the-painted-frame.md) closed with wheel
listed among the things still missing, and it is the last pointer event a scroll
view needs.

Three things about `SDL_MouseWheelEvent` decide the shape of this:

1. **There is no pixel-precise delta.** SDL reports `x` and `y` as floats in
   scroll "detents" — what CSS calls lines. Wayland and macOS both have a
   pixel-precise axis underneath, and SDL does not surface it. §7.1's "pixel
   precise with a line-based fallback" describes an API SDL does not offer.
2. **The sign is a platform preference.** When the user has "natural scrolling"
   turned on, SDL sets `direction` to `SDL_MOUSEWHEEL_FLIPPED` and leaves `x` and
   `y` *inverted*, documenting that the caller should multiply by -1. A reader
   that ignores the field is correct on its own machine and backwards on the
   machines of everyone who changed the setting.
3. **Vertical positive means away from the user**, which is the opposite of the
   direction a document scrolls in and of what every scroll view is written
   against.

There is also a fourth, quieter one: a wheel event carries its own `mouse_x` and
`mouse_y`, at different offsets from the motion arm's `x` and `y`. On a wheel
event, the offset the *motion* arm keeps its position at holds the vertical
**delta** — so a reader that reused the motion accessor would get a plausible
float that is not a coordinate.

## Decision

**The unit is lines, and the API says so.** `PointerEvent.deltaY()` is documented
as lines, not pixels, because inventing a pixel number would mean multiplying by
a line height this layer does not know. A scroll view multiplies by whatever a
line is worth in the thing it is scrolling. If SDL ever exposes the precise axis,
this becomes a second pair of accessors rather than a redefinition of these.

**Fractions survive.** The delta is a `float` and is routinely fractional: a
touchpad reports a fraction of a detent per frame, and a toolkit that rounded
would scroll in visible jerks. `integer_x`/`integer_y` — SDL's accumulation into
whole clicks — are deliberately not read.

**The flip is undone in `:natives`, and the sign convention is applied in the
backend.** Two steps, in two places, on purpose:

- `SdlEventBuffer.wheelX()`/`wheelY()` negate when `direction` is `FLIPPED`, and
  otherwise report SDL's own numbers in SDL's own convention. A binding that
  silently redefined a field would be a binding nobody could check against the
  header.
- `Sdl3Backend.translate` negates `y` once more, turning "away from the user"
  into "down the document". That is the CSS convention and the one every scroll
  view is written against, and doing it at the boundary means no two backends
  have to agree on anything harder.

**The event carries the position SDL gave it**, read from `mouse_x`/`mouse_y`
rather than from the last motion. Scrolling with the pointer parked over a window
that has not seen a move since it was focused is ordinary, and the last motion
would be stale or absent.

**A wheel is a `PointerEvent` with a `WHEEL` kind, not a type of its own.** §7.1
lists it under pointer events; it travels the same capture → target → bubble path
and `consume()` means the same thing on it. That is what makes nested scrolling
work: an inner list consumes while it still has somewhere to go, and the page
behind it does not lurch.

**`SDL_MouseWheelEvent` and `SDL_MOUSEWHEEL_*` join the layout registry**
(ADR-0010). The struct's offsets and the two direction values are checked against
the compiled SDL, because the failure mode here is silent: a wrong offset scrolls
by the pointer's coordinates and looks like a working scroll view until the
pointer moves.

## Alternatives considered

- **Report pixels, multiplying by an assumed line height.** Rejected: the
  assumption would be wrong for every widget with a different line height, and it
  would bake a guess into the SPI where a caller could make an informed one.
- **Pass SDL's sign through and let widgets negate.** Rejected: every widget would
  have to know, and the ones that forgot would scroll backwards. One negation, at
  the boundary, once.
- **Normalize the flip in the backend rather than the binding.** Rejected: the
  flip is a fact about the struct — the field is right there in the same event —
  and leaving it to a caller means every caller of the binding has to remember.
- **A separate `WheelEvent` type and an `onWheel` method.** Rejected: it would
  need its own `consume()`, its own capture and bubble path, and its own
  interaction with pointer capture — three copies of machinery that already
  exists, to gain a delta field.
- **Read `integer_x`/`integer_y` for a detent count.** Rejected: it throws away
  the touchpad's resolution, which is the case that most needs it.

## Consequences

- **A scroll view is now buildable**, which is what M3's `scroll` was waiting on.
- **The wheel follows pointer capture.** A wheel event during a drag goes to the
  captor — see [ADR-0058](0058-a-press-captures-the-pointer.md).
- **A wheel does not move `:hover`.** The pointer did not move, so nothing about
  what is hovered has changed. This is a deliberate difference from moving.
- **Horizontal scrolling is delivered and nothing consumes it yet.** `deltaX` is
  populated from a shift-wheel or a horizontal touchpad gesture; there is no
  widget to receive it until `scroll` exists.
- **§7.1's "pixel-precise" is not met and cannot be met through SDL.** It is
  written down here rather than left as an unread promise: the honest statement is
  lines with fractional precision. Reaching real pixel deltas means going around
  SDL to the platform, which is a much larger decision than this one.
