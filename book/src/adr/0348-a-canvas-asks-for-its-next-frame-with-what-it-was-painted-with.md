# 348. A canvas asks for its next frame with what it was painted with

Date: 2026-09-17

## Status

Accepted. Closes `docs/gaps.md` G41. Extends
[ADR-0081](0081-a-perpetual-loop-has-no-state.md) and
[ADR-0228](0228-a-phase-is-asked-whether-it-is-still-running.md).

## Context

§1.7's frame loop is idle when nothing moves. A widget that draws from the clock
keeps it turning through `Paints.isAnimating()`, which is how a spinner stays a
spinner (ADR-0081). `Canvas` did not override it, so a canvas painted from
`CanvasStyle.nowMillis()` was painted once. An application's tile floor that
settles, with each tile dropping in on a stagger, showed only its first frame.

An application cannot close this on its own. Implementing `Paints` itself means
writing a second `Canvas`. A timer that calls `host.repaint()` repaints the whole
window on a clock the frame pacer cannot see, so ADR-0271's lateness measurement
would count every one of those frames as late.

The question also cannot be a boolean on the description. A settle ends by
itself when the last tile lands, and "has it landed" depends on the time. A
boolean would make the application rebuild the widget to turn it off.

## Decision

**`Canvas.animating(Predicate<CanvasStyle>)`, asked by the renderer once per
frame, straight after `render`, with the `CanvasStyle` the painter was given.**

- `Paints` gains `isAnimating(ComputedStyle, Context)`, defaulting to the
  no-argument form. Every existing widget keeps its answer untouched, and the
  renderer calls only the new form.
- The renderer asks it **inside the same window `render` runs in**, before
  `currentElement` is cleared, so a context read by the answer is still this
  node's. That is the rule ADR-0288 set for binding a painter, applied to a
  second reader.
- It is still asked **after** `render`, which is ADR-0228's rule. The frame that
  draws the last tile landing answers false, and the loop goes quiet on the next
  frame rather than one frame later.
- The canvas snapshots `context.canvasStyle(style)` again for the predicate
  instead of keeping the painter's copy. It is four values in a record, and
  keeping the copy would mean state on a record that has none.
- Reduced motion is the painter's to honour. The predicate is handed
  `reducedMotion()`, and `style -> !style.reducedMotion()` is a loop that stops
  for a user who asked it to. The canvas does not guess, because a still
  drawing that needs no animation and one that loops are both legitimate for
  such a user.

## Consequences

- `Canvas` is a four-component record. The three-component constructor stays,
  so every call site written before this compiles unchanged.
- `new Canvas(floor::paint).animating(style -> style.nowMillis() - mounted < 2000)`
  is the whole of an animated canvas. The showcase's Motion screen is one
  (ADR-0354).
- Markup still names no painter, so `animating` has no markup form, for the
  reason the painter has none.
