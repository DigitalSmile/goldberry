# ADR-0644: `render` is not handed a width, and `Measured` stays

- **Status:** Accepted
- **Date:** 2026-10-18
- **Amends:** [ADR-0117](0117-a-widget-may-be-told-what-it-measured.md), [ADR-0167](0167-a-field-owns-its-caret-and-the-model-is-told.md)

## Context

ADR-0167 left a question on `TODO.md`: a `text-input`'s scroll offset uses the
previous frame's width, for one invisible frame after a resize. It was the
second widget to need a measurement `render` cannot have, and "a third would be
an argument for handing the width to `render` rather than to `Measured`".

The third came, and eleven more. Fourteen boxes implement `Measured` now,
among them `TextAreaBox`, `ScrollViewport`, `MasonryBox`, `MeasuredRow`,
`CanvasLayer`, `SplitPaneView` and `TableHead`. Nobody made the argument,
because none of them shows its one-frame lag.

## Decision

**`render` is not handed a width. `Measured`, last frame's size, stays the
way a widget learns how big it came out.**

- **The width does not exist when `render` runs.** `render` produces the box
  tree, and the box tree is what layout takes in. Handing `render` its width
  would mean laying out, rendering again with the answer and laying out again,
  a second layout pass on every frame something could be resized, to remove a
  lag no widget has shown.
- **The lag stays invisible by construction.** A resize is followed at once by
  another frame, and ADR-0117's rules keep what a widget does with the number
  from changing the number it is told: a scroll offset is a transform, and a
  masonry's column count changes its height and not its width.
- **A width a widget can know without layout is not `Measured`'s business.**
  `select`'s field (ADR-0359) and a segmented bar (ADR-0643) read the natural
  width of their own text at render time. That is the route when the number is
  a function of the model and the style.

Rejected:

- **A second layout pass for the widgets that ask for one.** It is the same pass
  with a flag in front of it. Nothing measured needs it, and it would be the
  first thing in the frame that runs layout twice.

## Consequences

- The `text-input` entry moves to *Answered*.
- A fifteenth `Measured` consumer is not a reason to reopen this. A consumer
  that **shows** its lag is, and the entry should name what it showed.
