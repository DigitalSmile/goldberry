# 369. A knob turns round its dial from its own value

Date: 2026-09-17

## Status

Accepted. Closes `book/src/TODO.md`'s "The circular drag is not built, and §3 offers
it". Amends ADR-0089.

## Context

§3's `knob`: "Rotary: vertical-drag primary (circular-drag optional)". The
vertical drag shipped. The TODO entry held the circular one back: a circular drag
has to decide what happens when the pointer crosses the 90° gap at the bottom,
"and every answer is either a jump or a wrap that depends on which way round the
user went, which needs the accumulated angle, a second piece of gesture state".

The accumulated angle is only needed to recognise a jump, and a jump can be
recognised without it. A knob always knows its current value, and the pointer's
angle round the dial is already computed for a click on the ring.

## Decision

**`Knob.circular(true)` and `drag="circular"` make a drag follow the pointer's
angle round `knob-dial`, and a move is judged against the knob's current value.**

- `circularFraction(angle, current)`: in the 90° gap the value stays at the end
  nearer `current`; on the travel it is the angle's fraction, unless that is
  more than half the travel from `current`, which is a jump across the gap and is
  held at the nearer end too.
- So pushing past the top holds the knob at the top, and coming round through the
  gap to the bottom does not flip it; turning back along the travel resumes.
- Detents apply as they do to the vertical drag. The wheel, the keys and a click
  on the ring are unchanged. The vertical drag remains the default.

## Consequences

- A user who deliberately wants to go from the top to the bottom turns back along
  the travel, which is what a physical knob with a stop needs as well.
- The knob holds no new state; the record gains one flag.
