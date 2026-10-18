# ADR-0647: An editor scrolls inside the box it is told about

- **Status:** Accepted
- **Date:** 2026-10-18
- **Amends:** [ADR-0167](0167-a-field-owns-its-caret-and-the-model-is-told.md)

## Context

`TODO.md` carried *An `Editor` does not scroll*. It drew where it was told and
did not know it had been clipped. The entry's answer was that a canvas with a
long document moves its own transform. But nothing told the canvas where the
caret had gone. A caller had to read the caret after every key, compare it with
its viewport, keep an offset of its own, shift the pointer by it, and clip.
`PageUp` and `PageDown` already needed the box's height
(`viewportHeight`), and moved the caret off the box without moving anything
else.

## Decision

**An editor told its `viewportHeight` keeps a `scrollTop` that follows the
caret.** When the caret has moved or the text has changed since the scroll was
last read, the scroll moves by the least that brings the caret's line inside the
box, and it always stays between zero and however far the text reaches below
the box. `paint` draws from there, clipped to the box's height, and skips the
hard lines that do not reach into it. A wheel is `scrollBy(dy)`, which answers
whether anything moved so the caller knows whether to consume it, and
`scrollTop(value)` moves the view without moving the caret. That view stays
until the caret next moves.

- **Points stay in the text's own space.** `pointerAt`, the caret and the
  selection rectangles mean what they meant, and a caller converting a pointer
  adds `scrollTop()`. Changing what a point means would have broken every
  caller to save one addition.
- **Opt-in by the height it already took.** An editor told no height is never
  scrolled, so the one caller in the repository, the showcase's sticky, and
  every test that sets no height draw exactly as before.
- **Vertical only.** A long line is wrapped by `wrapWidth`. An unwrapped
  single line wider than its box is a field, and `text-input` scrolls its own.

## Consequences

- The clip is a band across: as wide as the wrap width, or `100,000` logical
  units when there is none, because a clip is a rectangle.
- `EditorScrollTest` holds following, the wheel, the clamp, and that nothing
  is drawn outside the box.
