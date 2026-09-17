# 365. An overflowing tab strip pages from its ends

Date: 2026-09-17

## Status

Accepted. Closes `book/src/TODO.md`'s "A tab strip scrolls, and has no chevrons at
either end". Adds `ScrollController.position()` and `onChange`, and a
`CHEVRON_START` mark.

## Context

A tab strip's headers sit in a horizontal viewport (ADR-0118), so a strip wider
than its window can be reached by the wheel or by dragging the thumb. A tab bar
conventionally also has a chevron at each end. The TODO entry named what was
missing: a chevron "needs to know it is at an edge, which is `scrollIntoView`'s
missing question again". A `ScrollController` could move a viewport but could not
say where it was.

## Decision

**A controller reports its viewport's position and says when it changes, and a
tab list that overflows puts a `tab-pager` at each end that pages through it.**

- `ScrollController.Position` holds the offsets, the overflows and the viewport's
  size, with `overflowsX()`, `canScrollLeft()` and `canScrollRight()`.
  `position()` answers `NONE` with nothing attached. `onChange(Runnable)` sets a
  single listener, because a controller has one owner. `ScrollState` notifies it
  when the offset moves (by any route) and when its measured extents change.
- `TabsState` listens and keeps the last position. `TabList` builds
  `[rule, pager, viewport, pager]` while the headers overflow and `[rule,
  viewport]` otherwise.
- A pager pages by 80% of the viewport's width, never less than 40px, through the
  controller, so the move glides (ADR-0363). It is `:disabled` at the edge it
  would page past, is a `BUTTON` named "Earlier tabs" or "Later tabs", and is not
  a Tab stop: the strip is one stop, and a selected tab already reveals itself.
- `Box.Mark.Kind.CHEVRON_START` is `CHEVRON_END` mirrored, a kind rather than a
  transform for `CHEVRON_UP`'s reason.

## Consequences

- The pagers take width from the viewport, so a strip near the threshold has two
  stable states: overflowing with pagers, or fitting without. Which one it is in
  depends on the side it was approached from, and it does not oscillate.
- `gallery-basic-narrow` and `gallery-icons-narrow` are re-blessed: their strips
  overflow and now show pagers.
