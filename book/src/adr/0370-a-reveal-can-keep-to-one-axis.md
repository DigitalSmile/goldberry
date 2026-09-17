# 370. A reveal can keep to one axis

Date: 2026-09-17

## Status

Accepted. Closes `book/src/TODO.md`'s "A reveal moves both axes at once" as an
option, with the default unchanged.

## Context

`ScrollController.reveal` scrolls the least it can on each axis independently.
ADR-0120 argued that is right, and the TODO entry agreed. Its one complaint was
that it "occasionally moves a view further than a person would have": a wide table
asked to show a cell slides sideways as well as down. Nobody had asked for an
axis to take priority.

## Decision

**`reveal(self, clip, axes)` moves only along `axes`; the two-argument `reveal`
is `reveal(self, clip, ScrollAxis.BOTH)`.**

- The distances are computed as before, and the one for an axis `axes` does not
  include is dropped before `scrollBy`. `ScrollAxis` already names the three
  choices, so no new type is needed.
- The measurement ahead of a glide (ADR-0363) applies to both axes before the
  filter.

## Consequences

- A caller that only means "bring this row into view" passes `VERTICAL`, and the
  horizontal position the user chose is left alone.
- `ScrollControllerTest`'s harness takes an optional stylesheet, so a grid can be
  wider than its viewport.
