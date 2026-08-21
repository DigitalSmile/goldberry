# 165. A divider translates, and a rotation has three brakes

Date: 2026-08-20

## Status

Accepted. Finishes `docs/core-widgets.md` §5 with `split-pane` and `carousel`,
the two containers [ADR-0164](0164-elevation-is-an-edge-and-a-closed-section-is-absent.md)
left.

## Context

ADR-0164 built five of §5's seven remaining containers and said why the other two
were not among them:

> Both need something the five here did not: a drag with a retained position and
> a keyboard equivalent for the first, and a timed rotation that pauses on hover,
> on focus and under reduced motion for the second. Neither has a design question
> outstanding — they are the remaining work.

That was right about the *shape* of the work and wrong that there was no question
in it. Each turned out to have exactly one, and neither is the one you would
predict from the description.

## Decision

### `split-pane`: the divider translates, and the position is a fraction

**The gesture is a translation, not a position.** A slider reads its value
straight off the pointer, because the value *is* a position along a track
([ADR-0079](0079-a-slider-reads-the-pointer.md)). A divider cannot: the pointer is
somewhere inside a six-point bar, and mapping that to a fraction of the pane would
snap the divider so its centre jumped under the finger on every press — by up to
three points, which is visible and feels broken.

So it is the knob's arrangement
([ADR-0089](0089-a-knobs-gesture-is-a-rate.md)): the divider reports its current
offset as a `gestureAnchor`, the router hands that back on every event of the
gesture, and the new offset is `anchor + dragX`. This is the second widget to want
an anchor, **for a reason that is not the knob's** — a knob needs one because its
value has already moved by the second frame; a divider needs one because the
pointer's position inside the grab is not the value. Worth noticing, because it
says the mechanism generalises past the case it was built for.

**The position is a fraction and the minimums are pixels**, deliberately. A
divider a third of the way across should stay a third of the way across when the
window widens, which a stored pixel offset gets wrong. But "this list needs 160
points or its labels wrap" is a fact about *content*, and a fractional minimum
would let a narrow window squeeze it to nothing. So the fraction is clamped
against the pixels on every layout — which needs the measured length, and that
arrives through [ADR-0117](0117-a-widget-may-be-told-what-it-measured.md).

**The first pane is sized and the second grows.** Two `flex-grow`s in proportion
is the obvious answer and it does not work: `flex-grow` distributes the space
*left over* after content, so two panes with anything in them land where their
content puts them and the divider's fraction is ignored. §10's subset has no
`flex-basis` to say it with instead. So the first pane gets an explicit main-axis
size in logical pixels and the second takes the rest.

### `carousel`: three brakes, and one of them is not built

§5 in one sentence:

> **Nothing advances on its own unless `interval` is set**, and when it is, the
> rotation pauses on hover, on focus anywhere inside, and entirely under reduced
> motion — §1.7 rule 4 says nothing loops except explicit continuous indicators,
> and a carousel that moves while being read is the canonical violation.

`interval` defaults to off. When it is on, one **one-shot** timer is rescheduled
after each slide rather than a repeating one, so that "pause" means "do not
schedule the next" and needs no second mechanism to suspend. Every reason to stop
is checked in one predicate, and checked **again when the timer fires** — a timer
already in flight when the pointer arrives would otherwise advance one slide past
the moment it was supposed to stop.

Hover works completely. Reduced motion works completely — reported into the state
from `render`, which is the only place a `Paints.Context` exists, since a `State`
cannot ask for one.

**Focus does not, and that is the honest part.** Focus on the strip or on the
carousel's own controls pauses it; focus on a widget **inside a slide** does not,
because the cascade has no `:focus-within` and nothing tells a widget that focus
landed in its subtree. That is a real gap rather than a cosmetic one — somebody
who has tabbed into a slide is exactly somebody reading it — and it is in
`TODO.md` with what would close it.

## Alternatives considered

**Track the pointer's position for the divider, like a slider.** Simpler, no
anchor, no `Measured`. It jumps on every press, which is the thing.

**Store the divider's position in pixels.** Then a resize keeps the panes' sizes
and moves the *proportion*, which is right for a fixed sidebar and wrong for
everything else — and an application that wants the fixed-sidebar behaviour can
have it by pinning a width on the pane, where the reverse is not available.

**Unmount a collapsed pane**, as `collapse` unmounts a closed body. §5 asks for
collapse-to-*edge*, which is a size and not an absence, and a pane that lost its
state whenever somebody dragged the divider to the edge would be a surprise
nothing in the specification asks for. The two widgets differ on purpose and both
say so.

**A repeating timer for the carousel.** One `after`, cancelled and rescheduled, is
the same amount of code and makes every pause a matter of *not scheduling* rather
than of suspending something.

**Add `:focus-within` to the cascade to close the third brake.** It would work,
it would serve more than this widget, and it is a change to the selector engine,
the matcher and the router's focus bookkeeping — a `:core` change of real size in
the middle of finishing a widget group. Written down instead.

**Build a `CHEVRON_START` mark for `Previous`.** A second mark kind that has to
stay the mirror of the first forever. It is one `transform: rotate(180deg)`
instead, which cannot drift — `collapse-chevron` made the same trade for the same
reason.

## Consequences

**`EventLoop.Timer`'s constructor is package-private rather than private**, and
`TestTimers` in `:core`'s test fixtures hands them out. A widget that schedules
cannot be tested against a stub `Host` without something to return, and the
assertion that matters is that the timer was **cancelled** — a timer outliving the
tree that scheduled it is one of the two leaks a widget can cause. `TestFrames`
has exactly this arrangement over `Frame` and for exactly this reason.

**The divider's thickness is written in two places.** `SplitPaneView.DIVIDER` and
the `split-divider` rule in `controls.css` have to agree, because the first pane's
size is computed against it and a stylesheet that disagreed would put the second
pane's edge that many points out — silently. `SplitPaneTest` pins them together.
The same bargain `Menus` makes with `--gb-menu-item-height` (ADR-0117), and the
same reason it is not a token: a widget cannot read one.

**A build found a wasted wakeup.** `CarouselState.build` calls `schedule`
unconditionally, and at the last slide of a non-looping carousel that scheduled a
timer which would fire, move nothing, and stop. The fix was to fold "is there
anywhere to go" into the same predicate as the three brakes rather than test it at
the reschedule — the alternative was the same condition written twice, and the
copy in `build` was the one that was missing.

**A dot is not focusable.** Nine slides would be nine tab stops on top of the two
buttons, which is `tab`'s close-button argument (ADR-0107): the keyboard already
reaches every slide through the arrows, and the dots are a pointer affordance and
a position readout.

**§5 is complete**, and the showcase's Panels screen demonstrates all seven —
still with no Java behind it, because a `split-pane` and a `carousel` that keep
their own state need no more wiring than a `card` does.
