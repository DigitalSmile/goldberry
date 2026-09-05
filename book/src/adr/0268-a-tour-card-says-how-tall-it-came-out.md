# 268. A tour card says how tall it came out

Date: 2026-09-05

## Status

Accepted. Closes the second of the three `tour` entries. The first is verified
and stands; the third is `TabPhase`'s promotion and is not this.

## Context

> **A tour card's height is estimated, not measured.** It decides whether it fits
> below its target from a constant. Measuring needs the measure-then-place
> machinery ADR-0104 built, which works on *windows* rather than on boxes. Being
> wrong puts a card above its target when it would have fitted below.

The last sentence is the defect and the middle one is wrong about why.

`ADR-0104`'s machinery does work on windows, and it is not what this needs.
`TourStop` already banks the **window's own rectangle** from the frame before,
through `Located`, and uses it to clamp the card horizontally. The card is one
node further in, and `Measured` is the same door: *"here is what you turned out
to be"*, reported after a frame is laid out.

So the mechanism was already in the file. This is the fourth entry in this
section whose stated blocker had expired or was never right — the tooltip delay,
the popup-inheritance answer, `masonry`'s column count, and now this.

## Decision

**`TourCard` reports its height; `TourState` banks it; `TourStop` uses it, and
falls back to the estimate on the first frame.**

`ESTIMATED_HEIGHT` stays and its meaning narrows: it is what the first frame
decides with, before anything has been laid out and had a height to report. It is
no longer the number for every frame.

### It cannot oscillate, and the reason is not a promise

`Measured`'s third rule — *what it triggers must not change what it reports* — is
the one that has kept the mechanism to two implementations. It holds here **by
construction**: the card's width is fixed at 280 (so the sequence does not
shuffle between stops) and its content is the stop's own title, body, counter and
buttons. None of that depends on whether the card was placed above its target or
below it, so the height it reports is the same either way.

That is the same shape as `masonry`'s argument — *"the columns are equal width,
so a card's height does not depend on which column it is in"* — and unlike a
scrollbar's, which obeys the rule by being absolutely positioned. A test asserts
it rather than a comment claiming it.

### Half a pixel of hysteresis

`cardMeasured` ignores a change under 0.5, so a height that rounds differently
between frames does not schedule a rebuild for ever. The same threshold
`TextAreaState` uses on its width, for the same reason.

## Alternatives considered

- **Leaving the estimate.** It is wrong in one direction only — a card placed
  above when it would have fitted below — which is why it survived: the tour still
  works, and the stop points at the right thing, from the wrong side.
- **Measuring in `render` from the paragraph.** A card is a title, a body, a
  counter and a row of buttons in a padded box; adding those up means
  reimplementing the layout in the widget, and getting a different answer from
  Yoga the first time a stylesheet changes the padding.
- **The measure-then-place machinery.** What the entry proposed. It opens a
  *window*, measures its content and places the window — three of which a tour
  must not do, for the reason `TourCard`'s own javadoc already gives about not
  being a `popover`.
- **Making `TourStop` stateful.** It is a record whose values `TourState`
  computes, and it already receives one banked measurement as a component. A
  second is a component, not a state.

## Consequences

- **A tall card is placed above and a short one below**, from the card's actual
  height. The test's fixture is chosen so the two answers differ — below is 186,
  a 132-tall card needs 330 and fits in a 400 window, a 260-tall one needs 458 and
  does not — because any target where the estimate and the measurement agree would
  pass against the old code.
- **`TourCard` gained a second component and became `Measured`.** It is the
  toolkit's fourth implementation of that interface, after the scrollbar, the
  masonry and the toast stack, and the third whose argument for rule 3 is *"the
  thing measured does not depend on what the measurement decides"*.
- **One frame of settling**, which nobody sees: the first frame places from the
  estimate and the second from the measurement, at 60 Hz. Where the two agree —
  which is most stops — there is no second frame at all, because the banking
  ignores a change under half a pixel.
- **The first `tour` entry stands and is now verified.** A tour still cannot find
  the viewport its target is in: `findAncestorState` walks up from the element
  being *built*, and what a tour needs is a walk up from the **target it names**,
  which is a different question and one the tree cannot answer.
