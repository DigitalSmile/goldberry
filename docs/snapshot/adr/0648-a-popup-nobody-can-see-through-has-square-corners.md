# ADR-0648: A popup nobody can see through has square corners

- **Status:** Accepted
- **Date:** 2026-10-18
- **Amends:** [ADR-0632](0632-a-popup-window-has-room-for-its-panels-shadow.md)

## Context

`TODO.md` carried *A popup's transparent corners need a compositor*. A popup's
frame is cleared to transparent and its panel drawn with rounded corners, so
without a compositor the four corners outside the radius are whatever the
platform leaves there: black on an uncomposited X11 desktop. The entry kept a
fallback in reserve, filling the frame with the panel's own colour for square
corners. ADR-0632 then gave the toolkit the answer it needed to choose:
`Backend.seeThroughPopups`. The shadow's room was withheld on it, and the entry
noted that nothing used it for the corners yet.

## Decision

**Where a popup's transparent pixels would not be seen through, its panel is
drawn with square corners.** `HostedWindow` hands `Popup` the same answer it
withholds the shadow on, and `Popup` squares the panel's corner radii before
painting. The panel's background and border then fill the whole window, so there
is nothing outside them to come out black.

Squaring the radii was chosen over filling the frame with the panel's colour
first: a fill under a rounded border leaves a rounded line inside a square plate,
and the radii already say what a square panel looks like.

## Consequences

- A menu, a `popover` or a `tooltip` on an uncomposited X11 desktop has square
  corners and no shadow. With a compositor, and on every backend that answers
  true, nothing changes.
- The headless backend answers false unless told otherwise. No golden image moved:
  the widget goldens paint panels directly, not through a popup window.
- `PopupShadowTest` holds both answers: a square, opaque corner without
  transparency and a rounded one with it.
- Windows and macOS have still not been checked by eye. Both backends answer
  true there.
