# 146. A HUD shows where the frame went

Date: 2026-08-19

## Status

Accepted. Extends [ADR-0101](0101-a-diagnostic-must-not-be-the-thing-it-measures.md)'s
`hud` with the four stages a frame is made of.

## Context

The showcase spent a month painting at 10–15 ms with nothing on screen moving.
The HUD said `paint 12.4 ms`, which was true and useless: a total says a frame is
slow and says nothing about which part of it is. Finding the answer took a
purpose-built probe, a counter compiled into the renderer and two rounds of
guessing — and the answer, when it came, was that the style cache had stopped
hitting the day `scroll` shipped ([ADR-0142](0142-a-style-handed-down-keeps-its-identity.md)).

A number on screen would have said so on the first frame.

## Decision

**Four more readings — `build`, `style`, `layout`, `raster` — and one word for
the set of them.**

```kdl
hud readings="stages"
```
```java
host.overlay(Hud.stages(), Corner.BOTTOM_END);
```

The launcher's painter takes five `nanoTime` readings and hands the four
intervals to the frame ring, which keeps them in the same 60-slot window as the
paint time. Ungated by log level, for the reason ADR-0101 gives about the rate
itself: a diagnostic you have to reconfigure the process to see is one nobody
looks at, and five timestamps against a frame costing hundreds of microseconds is
not a cost worth a branch.

**The four do not add up to `paint`, and are not asserted to.** The hit-test
capture and the frame's own setup are in the total and in none of the stages —
neither large enough to name nor zero. Making them add up would mean either a
fifth "other" reading nobody can act on, or moving work around to make a display
tidy.

**Two decimals for a stage, one for a total.** `0.0 ms` cannot be told from a
stage that is not running, and telling those apart is the whole use of a
breakdown. Three ranks in the stylesheet — the rate bright, the total dim, the
stages dimmer and one size down — because six equally bright numbers on one plate
read as a wall.

## Consequences

**The showcase turns it on**, so the thing that gets looked at is the thing that
would have shown the defect.

**A `raster` that jumps when nothing moved is a buffer that stopped retaining**,
not a scene that got harder — the damage-tracking half of
[ADR-0072](0072-a-partial-repaint-needs-a-promise.md) made visible for the first
time.

**A `build` above zero is a widget dirtying itself every frame**, which is the
one failure this catalog has repeatedly produced and never had a reading for.

**`FrameStats` grew four methods with a default of zero**, so a source that does
not measure the stages — every one but the frame loop's own — reports zero rather
than lying. A HUD with no loop over it still draws dashes, which is the existing
distinction between "not measured" and "measured as nothing" (ADR-0101).
