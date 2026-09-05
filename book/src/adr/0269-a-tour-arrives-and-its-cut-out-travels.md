# 269. A tour arrives, and its cut-out travels

Date: 2026-09-05

## Status

Accepted. Closes the third `tour` entry, and **corrects** the `TabPhase` entry it
depended on, which had been describing a promotion that already happened.

## Context

Two entries, one of which is the other's stated blocker:

> **A tour has no arrival or exit.** §1.7's overlay curve wants one to arrive
> rather than appear, and stops change instantly — §5's row asks for the veil
> cut-out to `translate` and resize between stops. That is `TabPhase` again: the
> enter/exit lifecycle built for one widget, wanted by a third.

> **The enter/exit lifecycle is a tab's own, not the toolkit's.** `TabPhase` is
> what §1.7's "overlay enter/exit lifecycle" asks for, built for one widget:
> `toast`, `dialog` and `popover` all want the same thing, and promoting it should
> wait for the second consumer rather than be guessed at from the first.

**The promotion happened two records ago.** `TabPhase` is
`widgets.core.Phase` — moved there by
[ADR-0166](0166-a-raised-thing-is-told-apart-by-its-edge.md), whose own javadoc says *"there was
never anything tab-shaped in it"* — and the `closing → removed` half was
extracted into `widgets.core.Departure` by
[ADR-0234](0234-the-overlay-lifecycle-is-a-departure-and-a-phase.md). Six families use one or
both: `tabs`, `carousel`, `collapse`, `toast`, `dialog` and `message`.

So both of the consumers the entry named as *wanting* the mechanism have been
using it for milestones. What was missing was not a promotion. It was a `tour`
that used it.

That is the fifth entry in this section whose stated blocker had expired, and the
first where the expiry was hiding a second entry behind it.

## Decision

**Two phases, and they belong to different things.**

### The arrival is the tour's

One `Phase` for the whole tour, not one per stop. §3.1 says a tour's card
animates "as `popover`", and that row is *"`opacity` 0→1, `translateY` −4→0,
`scale` 0.98→1 from anchor origin, base"*. Two of the three are built.

**The scale is not, and deliberately.** `transform-origin` resolves against a box
the painter measures, and a card scaling from its own centre rather than from its
anchor reads as a pop rather than an arrival. `popover` itself has the same gap
for the same reason, so this is consistent rather than incomplete.

A tour arrives **once**. A card that faded in again at every stop would be a
sequence that restarts rather than advances, and a test asserts the second stop
holds the same `Phase` instance as the first.

### The travel is the cut-out's

§3.1's tour row is *"stop change: veil cut-out `translate`+size **base**"*, and
both halves fall out of interpolating **one rectangle**: a target that moves and
changes size does both at once. Doing it as one rectangle rather than as a
translate and a resize is what keeps the ring, the veil's hole and the card
agreeing on every frame — they are three drawings of the same geometry, and three
separate animations would let them disagree mid-flight.

`beginTravel` runs **before** the index moves, because `anchorOf` has to answer
the stop being *left* — the rectangle the travel starts from. After the move it
would bank the destination as the origin and animate nothing.

### Interpolated in `render`, in two places, on purpose

The frame clock reaches a widget in `render` and nowhere else, so the node that
draws a thing is the only one that can know how far through the travel it is.
`TourStop` does the arithmetic for the ring and the card; `TourVeil` does the
same arithmetic for its hole, from the same two rectangles and the same `Phase`.

Handing the veil an already-interpolated rectangle was the alternative and is not
available: `children()` builds the veil, and `children()` has no clock.

## What the arch sweep caught

`AnimationSweepTest` failed twice, and both were real:

- **`TourVeil` held a `Phase` and did not override `isAnimating`** — *"a widget
  that holds a phase says it is animating"*. Without it the veil is painted once
  at whatever the loop caught and left there.
- **The tour package had no test naming `isAnimating`** — *"a widget that stopped
  asking for frames would still pass every golden"*.

Neither would have been caught by an image, which is what that sweep exists for.
It found them within a minute of the phase being added.

## The goldens did not move

`TourGoldenTest` warms five frames on a **system** clock, which pass in
microseconds — so a 160ms arrival would have been photographed at whatever
opacity the loop happened to catch, differently on every machine. It has a
virtual clock now, advanced past the duration, which is `GalleryGoldenTest`'s
answer to the same problem.

With that, both tour goldens match **unchanged**: the settled tour is the picture
it always was, and the animation is what happens on the way there.

## Alternatives considered

- **A `transition` on the ring's inset.** The cascade never sees these
  rectangles — they are computed from an anchor the router reported — so there
  are no two styles to interpolate between. That is `Phase`'s founding argument
  and it applies here unchanged.
- **A phase per stop.** It makes the card re-arrive on every `Next`, which is the
  behaviour §5 is asking to replace.
- **Animating the ring and the veil separately.** Two phases over one geometry,
  which can only ever agree by accident.
- **Promoting `TabPhase`.** What the entry asked for, and it was done in ADR-0166.

## Consequences

- **A tour fades and rises in, and its cut-out slides and resizes between stops.**
  §3.1's tour row is built bar the scale, and §1.7's overlay curve reaches the
  last widget that was appearing rather than arriving.
- **`TourVeil` gained two components and an `isAnimating`.** It keeps a
  two-argument constructor, so every caller that has nothing to travel from —
  which is the tour's first stop and every test — is unchanged.
- **The `TabPhase` entry is corrected rather than closed by this**: it was
  describing a promotion that ADR-0166 had already made, and both consumers it
  named were already using the result.
- **Five tests**, including the one `AnimationSweepTest` requires by name and the
  one asserting the arrival is the *tour's* rather than the stop's — which is the
  difference between a sequence that advances and one that restarts.
