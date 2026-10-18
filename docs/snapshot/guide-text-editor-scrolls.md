# An editor scrolls inside the box it is told about

Held back until the release (ADR-0647). One piece.

## `book/src/guide/text.md`, `## Selection and editing`, after the paragraph that begins "The editor is `text-input`'s key map"

Tell an editor how tall its box is with `viewportHeight` and it scrolls: a
keystroke, a click or a drag that carries the caret past the box's edge moves the
text by the least that shows the caret's line, and `paint` draws from there,
clipped to the box. A wheel is `scrollBy(dy)`, which answers whether anything
moved. Points stay in the text's own space, so a pointer handler adds
`scrollTop()` to the pointer's y before calling `pointerAt`.
