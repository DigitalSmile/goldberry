# 500. A drag held at the edge carries the viewport on

Date: 2026-09-30

## Status

Accepted. Closes the `goldberry-html` entry "dragging a selection past the edge
of a viewport does not scroll on". Builds on
[ADR-0301](0301-a-selection-is-geometry-the-frame-already-had.md)'s selection,
[ADR-0439](0439-a-viewport-is-found-by-walking-up-from-the-target.md)'s
`ScrollScope` and [ADR-0081](0081-a-perpetual-loop-has-no-state.md)'s frame
clock.

## Context

Both content views select, copy and highlight. A drag to the bottom of the
`scroll` around one stopped selecting. The entry read this as "does not scroll
on", and there were two faults behind it, not one:

- **Nothing moved the viewport.** A pointer held still sends no events, so a
  drag has no way to say "keep going" unless something runs on its own clock.
- **The selection itself stopped at the edge.** Every word is clipped to the
  viewport. `WordGeometry.at` skips a word whose clip does not contain the
  pointer, so a pointer below the pane is over no word and `at` answers
  nothing. `Selection.extendTo` ignores nothing. So a drag that went past the
  bottom and came back into the pane's width froze the selection wherever it
  had last been inside.

`text-area` had a different version of the same want. It scrolls its own offset
rather than living in a `scroll`. A drag past its bottom selected the line under
the pointer **outside** the control, and then the caret chase in `laidOut`
scrolled to it. So it did scroll, but only while the pointer moved, and by a
jump as far as the pointer had gone past the edge. Held still, it stopped.

The entry named the touchpad as "the interesting part": what an edge scroll
should do with a touchpad's fractional deltas.

## Decision

**One mechanism, `EdgeScroll`, in `:widgets`' `core.scroll` package**, beside
the viewport it moves. Both content views and `text-area` hold one. It is a
timer plus a clamp, as the entry said:

- **The timer is the frame clock.** The widget that owns the drag calls
  `tick(nowMillis)` from its `render` and answers `isAnimating()` with
  `isScrolling()`. That is how `ScrollGlide` and `ScrollFade` already move. A
  frame moves by speed × the time since the last frame. The first frame of a
  run only starts the clock, a late frame moves at most 50 ms worth, and a
  target that refuses a step (the end of the document) stops the frames until
  the pointer moves again.
- **The clamp is `x()` and `y()`**: the pointer pulled back inside the viewport,
  a pixel short of its far edge. What is selected is what is under that point.
  This is the fix for the second fault above, and it applies whether or not
  anything scrolls.
- **The speed is linear in the distance past the band's inner edge**: 10 px/s
  per pixel, capped at 2400 px/s. The band is 16 px inside the viewport, or an
  eighth of it for a small one. The band is there because a pane that fills a
  maximised window has nothing below it for the pointer to reach. At the
  viewport's own edge the speed is 160 px/s, about eight lines a second.

**The touchpad answer: the speed is the pointer's, never the wheel's.**

- A wheel or a two-finger scroll during a drag scrolls the way it always does,
  one-to-one and in the viewport's own units. The selection follows what it
  brings in: the document re-asks what is under the held point every frame of a
  drag, and `text-area` re-asks after the wheel moves it. The wheel does not
  start the edge, stop it, or change its speed. Feeding fractional wheel deltas
  into a velocity would add momentum to a precise gesture.
- The speed comes from a position, so a mouse, a pen and a touchpad behave the
  same.
- The steps are fractional and are applied unrounded. At 144 Hz the viewport's
  edge moves 1.1 px a frame and the band's inner part much less. Rounding would
  stop a slow edge dead, and saving the fractions up for a whole pixel would
  move it in jerks. The offsets are already `double`s.

**The viewport is found, not wired.** On a press the document asks
`ScrollScope.enclosing(event.target())` and holds `scope::nudge`. The
`selection-host` becomes `Located` so that it learns the viewport's rectangle,
which is its clip. `ScrollScope.nudge(dx, dy)` is new: it moves at once, along
the viewport's axis only, and reports whether anything moved. It is not
`ScrollController.scrollBy`, which glides for 240 ms. A glide restarted on every
frame of a drag would never arrive anywhere, and this is direct input.

**A `text-area` drag stops at the lines wholly on screen.** The new
`AreaEditor.dragTo` clamps the row to the fully visible lines. Without that, the
caret would land on the half-shown line at the bottom, the chase would scroll a
whole line into view, and an edge moving a pixel a frame would move a line a
frame. A press still takes the line it was on, half-shown or not. The edge's
step is assigned to the offset inside `render`, before `laidOut` reads it, so
the frame that takes the step draws it.

## Consequences

- A drag held below or above a pane carries it on at a speed the reader
  controls with the pointer, and it stops on the release, when the pointer comes
  back inside, or at the end. For `text-area` it also stops on focus loss.
- A drag past the edge of a document now selects to the word at the edge even
  when the viewport cannot move. The selection no longer freezes at the last
  word the pointer was over.
- A `text-area` drag far past its bottom no longer jumps. It selects to the last
  line on screen and scrolls from there. That is a behaviour change, and it is
  the intended one.
- The document's selection lags the scroll by one frame. The geometry is the
  last paint's, so what is selected is what the reader saw under the pointer. A
  `text-area`'s wash lags the same frame, because its edit is rebuilt next
  frame.
- A drag re-hit-tests the document once per frame while it is held. That is the
  same linear scan a pointer move already costs, and only during a drag.
- `EdgeScroll` is public in an exported package. Anything else that drags a
  selection, a list's rubber band or a table's range, can hold one.
- Tests: `EdgeScrollTest` covers the speed, the band, the clamp, the stepping
  and `nudge`. `SelectionTest$HeldAtTheEdge` drives both views through a real
  frame loop on a virtual clock and checks the offset per frame against speed ×
  time. `TextAreaTest$HeldAtTheEdge` does the same for `text-area`, plus the
  no-jump rule and a wheel mid-drag.
