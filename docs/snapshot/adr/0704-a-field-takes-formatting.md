# ADR-0704: A field takes formatting

- **Status:** Accepted, as D3 and D4 of `docs/rich-text-input.md` (taken as
  recommended on 2026-11-14).
- **Date:** 2026-11-14
- **Relates to:** ADR-0703 (text carries marks), ADR-0697 (rich text that
  answers the pointer)

## Context

ADR-0703 gave the canvas `Editor` marks. An application with a form wants a
control: a box, a placeholder, focus, a height that follows the text, and a
stylesheet that decides what bold looks like. The plan ruled out more
arguments on `text-area` (W3.1), and asked where a mark's looks come from:
the cascade (D3 (a)) or a table in Java (D3 (b)).

## Decision

**`rich-input`**, a new widget in `widgets` `form.richinput`: `RichInput`
(value, `bind=`, `onChange`, placeholder, `rows`, `max-rows`, read-only,
disabled), a state that holds an `Editor`, and a `rich-input` box that draws
the editor through its painter. It takes the editing keys every field takes
and the formatting keys of ADR-0703, the wheel, the primary selection's
middle click, and the IME's composition and candidate area.

**The looks come from the cascade (D3 (a)).** Each set of kinds that occurs
in the text, and the set the next typed letter takes, is a `mark` node under
the field with a class per kind. The node is a carrier, as `text-area`'s
`text-value` is: it renders an empty paragraph in its resolved font with its
colour and rules, and its background as its fill, and the field turns those
into the `MarkStyle`s its editor draws with. So `rich-input > mark.bold.italic`
styles a combination the way CSS styles any element with two classes, and a
message and the composer it was typed in can share rules. The toolkit's
stylesheet styles every built-in kind; a spoiler is a wash the writer can
read through, not the reader's cover.

**Size.** The field wraps at last frame's content width, reported through
`Measured`; a change asks for one more frame, so the field settles in two
frames on mount and one per resize. It is as tall as its text's rows, between
`rows` and `max-rows`, and scrolls past that.

**The editor is made on the first frame**, in the font the cascade resolves.
A new font makes a new editor holding the same text and caret, whose undo
history starts again.

## Consequences

- A bound value is followed, and the echo of the field's own change through
  the model is not loaded back, so typing keeps its caret and history.
- The caret does not blink; it is drawn while the field is focused and
  editable.
- `Enter` inserts a newline. A composer that sends on `Enter` handles the
  key around the field until the field has an option for it.
- The formatting bar (D8), mentions (D7) and Markdown and HTML (phase 6) are
  the plan's later phases.
- The guide section is parked in `docs/snapshot/components-forms-rich-input.md`.
- Tested by `RichInputTest`: typing reported as a marked value, `Ctrl+B` and
  `Ctrl+Z`, `Ctrl+I` for what is typed next, the `mark` nodes, growing to
  `max-rows` and wrapping at the width, following a bound value, read-only,
  the semantics, and markup.
