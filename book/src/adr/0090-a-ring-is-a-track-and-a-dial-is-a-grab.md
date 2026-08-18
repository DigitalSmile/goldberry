# ADR-0090: A ring is a track and a dial is a grab

- **Status:** Accepted
- **Date:** 2026-08-18
- **Relates to:** `docs/design-system.md` §3, `docs/core-widgets.md` §3, extends [ADR-0089](0089-a-knobs-gesture-is-a-rate.md)

## Context

[ADR-0089](0089-a-knobs-gesture-is-a-rate.md) built `knob` to the letter of §3:
270° arc indicator, vertical drag at 200px per range, wheel, keyboard, detents.
Put in front of someone, two things were wrong with it that no assertion could
have said.

**It read as a gauge, not a knob.** §3 asks for an "arc indicator" and
`core-widgets.md` §3 for a rotary control, and between them they say what the
*value* is and never say which way the thing is **pointing**. An arc alone tells
you how full something is. Nothing on the dial turned, so nothing about it
suggested you could turn it.

**The ring did nothing.** A slider's track is clickable — press anywhere and the
thumb comes to you. A knob's ring is the same 270° of travel drawn round a
circle, and clicking it was inert: the only way to reach a value was to grab and
drag.

## Decision

**The dial carries a pointer.** `Box.Mark` gains a `POINTER` kind — a radial line
at the mark's `start` angle, running `0.35 → 0.78` of the box's radius. A
proportion rather than a length, for the reason the tick and the dash are
proportions: the same drawing has to be right on §3's 32px knob and its 48px one.
It stops short of the centre because a line through the middle of a dial reads as
a diameter rather than as a direction, and short of the rim so it does not join
the ring outside it — two strokes meeting is a join, and a join says the two are
one thing.

It is a **mark on `knob-dial`, not a part of its own**, which is the first time
that has been the right answer since `CheckMark` went the other way. A part is a
node because two things must be styled or *moved* independently
([ADR-0073](0073-a-composite-is-one-tab-stop.md)); the pointer is neither — one
colour, one angle, and the angle is a painter argument rather than a `transform`.

**Clicking the ring positions the value; clicking the dial does not.** The ring
is a track and behaves like one. The dial is the thing you grab, and a press that
jumped before the drag started would move the value out from under the gesture
about to set it.

**The boundary between them is `localPart()`, not a constant.** The control has
to know where the dial ends, and it cannot: a widget has no idea how it was laid
out, and the inset is the stylesheet's (`knob-arc { padding: 5px }`), so an
application that restyled it would move a boundary this file had hard-coded.
[ADR-0080](0080-a-value-is-measured-along-a-part.md) already answered exactly this
question — the router measures `PointerEvent.local()` against a named part — so
`knob` names `knob-dial` and "outside the dial" is `hypot(dx, dy) > radius`,
derived from the geometry that was actually painted. The drag is unaffected: it
reads `dragY()`, which is the window's.

**The jump fires on `CLICKED`, not on `PRESSED`**, and that is what makes it
compose with the drag. A press is the first event of *both* gestures and cannot
know which one it is. The router synthesizes `CLICKED` only when the press and
the release landed on the same node ([ADR-0058](0058-a-press-captures-the-pointer.md)),
and the remaining ambiguity — a drag that ended where it began — is settled by an
8px slop, which is `Toggle`'s answer to the identical question.

**A click in the 90° gap resolves to the nearer end.** The gap at the bottom is
where a user clicks to mean *all the way down* or *all the way up*, and a gap
that refused every click would make the bottom of the control dead.

## Alternatives considered

**Jump on the press, like a slider does.** It is the obvious symmetry and it
fights the anchor: the router reads `gestureAnchor()` *before* dispatching the
press ([ADR-0089](0089-a-knobs-gesture-is-a-rate.md)), so a drag that began with a
jump would continue from the value the jump replaced — the knob would snap to the
click and then snap back as soon as the pointer moved.

**Let a press anywhere jump, dial included.** One rule instead of two, and it
makes the dial unusable as a grab: every drag would start by throwing the value
to wherever the finger landed.

**Pick the dial boundary as a fraction of the radius** — the shipped inset puts it
at `11/16 = 0.6875`, so `0.7` would work today. Rejected because it is a constant
that silently agrees with a stylesheet: an application that changed
`knob-arc { padding }` would get a band that no longer matched its own drawing,
and nothing would say so.

**Make the pointer a `knob-pointer` part rotated by a `transform`.** Consistent
with how the checkbox's mark was promoted to a node. Rejected because the
rotation is about the *dial's* centre and the part would sit at the dial's top,
so `transform-origin` would have to be expressed as a percentage of the child's
box — arithmetic that has to be redone for every diameter, to make a node that
nothing needs to style separately.

**Build the circular drag instead.** Still the right answer to a different
question, and still deferred for ADR-0089's reason: it needs the accumulated
angle, a second piece of gesture state, to decide what crossing the gap means.
Click-to-position gets most of the benefit — reaching a distant value without a
long drag — for none of that.

## Consequences

**`Box.Mark` has a kind that is not a shape but a direction**, and `sweep` is
meaningless for it. Documented on the constant rather than enforced; a `POINTER`
built with a sweep is not an error, because a record that validated the
irrelevance of one component for one kind would be a worse thing to read.

**A click on the ring is subject to detents**, where a wheel line is not. The
jump goes through the same `detented()` the drag does, so a knob with detents
snaps on a click exactly as it does under a finger. The rule that keeps the two
apart is what the gesture *is*: a click and a drag both say "put it there", and
"there" is what detents adjust; a wheel line says "one more", and a step that
snapped would make some clicks of the wheel do nothing.

**`knob` now names a `localPart()`**, so its `local()` is the dial's box and not
its own. Nothing else in the control reads `local()`, but anything added later
will get the dial unless it says otherwise — the same trap `slider` documents.

**Three goldens moved and one scene grew a pointer.** `knob-travel.png` is the
image that says the pointer and the arc agree: at every value the line points at
the end of the arc, and a travel that started at nine o'clock instead of
seven-thirty would be wrong by 22.5° everywhere — which is not enough to notice
in a picture, and is why `KnobTest` asserts that nine o'clock is a **sixth** of
the way round rather than a quarter.
