# 178. A stack closes its own hole

Date: 2026-08-23

## Status

Accepted. Builds §3's **sibling reflow**, the one thing
[ADR-0177](0177-a-toast-is-a-queue-and-the-stack-is-the-widget.md) left §7
owing, and the last of `docs/core-widgets.md` §7.

## Context

`docs/design-system.md` §3, the `toast` row: "in: slide 16px from edge +
`opacity`, overlay · out: `opacity` base · **siblings reflow via `translate`**
base (explicit controller — the one sanctioned movement effect, transforms not
layout)".

Everything else in that sentence shipped with the widget. The reflow did not,
because it is the one part that is not about a toast at all: it is about what
happens to the toasts that *stay* when one of them goes. Until ADR-0177 there
was nothing in the catalog that knew what a notification's siblings were — a
`message` is written where it goes and has no owner
([ADR-0175](0175-a-banner-says-its-kind-twice.md)) — so the effect had no
subject. A stack that holds a queue is a subject.

Without it, a toast dismissed from the middle of three is a jump: the column
reflows on the frame the element leaves the tree, and everything on one side of
it is suddenly somewhere else.

## Decision

### Only the older toasts move, and the corner decides which way

This is the part that is not obvious, and it falls out of where the column is
**pinned** rather than out of anything the widget does.

A `toaster` is a corner overlay: content-sized, with two of its four insets
undefined, so it is anchored along the edge it is against and grows away from
it. `controls.css` then arranges the children so that the newest toast is the
one nearest that corner — `column` at the bottom corners, `column-reverse` at
the top. Put those together and the column is **anchored by its newest member**.

So taking one out of the middle leaves everything between it and the corner
exactly where it was, and moves everything on the far side — the older half —
toward the corner by the height of the hole plus the gap it was keeping. Which
direction "toward the corner" is, is the corner's own, and `ToastBox` reads it
there rather than being handed a signed number; a signed distance would be the
one value in the widget that had to be recomputed when a stack changed corner.

A corollary worth writing down: **the ordinary case moves nothing.** A stack
whose toasts all have the same timeout loses its oldest first, and the oldest
has nothing older to move. The reflow is what a dismissal *from the middle*
looks like — an action button pressed, a `clear()`, a burst with uneven
timeouts.

### The translate runs backwards

Nothing here moves a toast to a new place. The sibling is gone, so Yoga has
already put the survivor where it belongs; what the translate does is put it
**back where it was** for one frame and then let go. The offset shrinks to zero
rather than growing from it.

This is the same shape as the arrival — `(1 - visible) * TRAVEL` — and it is
worth naming because the two are otherwise easy to confuse: the arrival is
about the toast, the reflow is about a hole, and they compose on two axes
rather than taking turns. A toast can still be sliding in when the one beside
it is dismissed, and either effect being dropped for the length of the other is
a gap that closes late.

### The height comes from `Measured`, and the gap from the stylesheet

The distance is two numbers, and neither is one a widget may invent.

- **The height of the hole** is the departing toast's, which nothing can ask for
  afterwards — by the time it is wanted the toast is gone. So each `ToastBox`
  implements [`Measured`](0117-a-widget-may-be-told-what-it-measured.md) and the
  stack **banks** what every frame reports. That it is last frame's is exactly
  right here: a toast has to have been on screen to be dismissed, so by the time
  the number is wanted it has been reported. `Measured`'s third rule holds too —
  a reflow is a `transform`, so the box it moves is laid out where it always
  was, which is why §3 asked for a transform in the first place.
- **The gap** is `toaster { gap: 8px }`, reported up from `render` beside the
  frame clock, through the channel ADR-0177 opened for the clock and for the
  same reason: it is a reading only `render` can take. Read rather than
  assumed, because a stylesheet that changed the gap and nothing else would
  otherwise leave the whole stack reflowing to somewhere it is not.

A toast dismissed before a frame ever painted it has no height, and the stack
reads that as **no hole** rather than as a hole of nothing. The check is on the
height and not on the total: the gap alone is a real number, and 8px in a
direction nobody asked for is worse than the jump this replaces.

### Not an `AnimationController`

§3 says "explicit controller", and `book/src/TODO.md` has had the imperative
`AnimationController` down as a specification without a subject since
[ADR-0081](0081-a-perpetual-loop-has-no-state.md) took two of its three away —
the spinner and indeterminate progress ship as functions of the frame clock with
no state at all. Toast reflow was one of the two it had left, on the grounds
that it has "a start, an end, and an interruption to reverse from".

Two of those three are `Phase`, which already ships and already runs on the
frame clock. The third — the interruption — turned out to be arithmetic rather
than a mechanism:

```java
var left = entry.reflow == null ? 0
        : entry.reflow.distance() * (1 - entry.reflow.phase().progressAt(now));
return new ToastBox.Reflow(left + distance, new Phase(ENTERING, REFLOW_MILLIS));
```

Read what is left, add the new distance, start again. That is ADR-0081's finding
one level up: a controller here would be a per-element copy of the time for a
consumer that needs three lines of it. The overlay enter/exit sequence is the
one subject the specification has left, and it stays on the list.

`Phase.Kind.ENTERING` for a movement that is neither an arrival nor a departure,
because what the kind actually selects is "runs once, then settles" — and
settling itself is what stops `isAnimating` asking for frames forever.

## Consequences

- **§7 is complete**, and §3's movement clause has its one implementation. A
  column of `message`es still cannot have it, for ADR-0175's unchanged reason:
  a banner has no owner to hold the list.
- **`isAnimating` had to learn about the second clock**, which is
  [ADR-0176](0176-a-dialog-is-a-widget-and-showing-one-is-not.md)'s bug waiting
  to happen again: a toast that has settled but is still travelling would be a
  widget nobody repaints, and a widget nobody repaints does not move — it stands
  still for 160ms and is then somewhere else. A golden would photograph that
  happily, so the assertion is on `isAnimating` directly.
- **The golden had to be taken in a real window**, which is new for this widget
  and is the direct consequence of the first decision above. Every other picture
  in `ToastGoldenTest` is of a column on its own, and that column is
  top-anchored — so it photographs the *newer* toast moving, which is the
  opposite of what a pinned stack does. Overlay placement is not assertable as a
  number, which
  [`HudGoldenTest`](0100-a-window-has-a-layer-above-its-application.md) found first.
- **`Measured` has a fifth consumer**, and the first whose reason is not its own
  geometry but a sibling's. `book/src/TODO.md` calls it "a door every widget can
  now open and almost none should"; a stack that must move its survivors by an
  exact distance is one of the few that should.
