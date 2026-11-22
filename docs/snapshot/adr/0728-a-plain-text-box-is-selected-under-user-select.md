# ADR-0728: A plain text box is selected under `user-select`

- **Status:** Accepted
- **Date:** 2026-11-22
- **Relates to:** ADR-0605 (the CSS subset), the `markdown-view` and `html-view`
  selection (`SelectableDocument`), GB `rich-text` (`selectable=`)

## Context

`user-select` was parsed, inherited and carried on every box's `Presence`, and
only two widgets read it: `markdown-view` and `html-view`, whose
`SelectableDocument` drives a selection of its own. A `text` under
`user-select: text` could not be selected, and neither could a `rich-text`
unless its Java or markup said `selectable=#true`; the property did nothing on
either. A downstream board wrote `user-select: text` on its log panel and got
nothing, with no warning, because the property is a known one.

## Decision

1. **One selection per tree, in core** (`dev.goldberry.input.select.TextSelection`,
   exported). The renderer registers, box by box, every box that draws words
   (`Box.text`) under `user-select: text` or `all` and whose widget does not
   implement `Handles`; it hands back the box with the selection's highlight as
   a `SpanPaint` background in `--gb-selection`, and an I-beam where the
   stylesheet set no cursor. The router drives it **after dispatch, on what
   dispatch left** (`TextSelecting`): a press on a registered box starts it, a
   drag extends it, two clicks take a word and three the box; `all` takes the
   box at any click. A browser keeps one selection per document, and so does a
   tree.
2. **`auto` is not selectable for a plain box.** CSS's `auto` is selectable on
   the web; here a `text` is a label on a button, a heading over a card, a cell
   in a row that is pressed. Making every label selectable would turn presses
   into selections across the whole catalogue, so `text` and `all` opt in and
   `auto` keeps today's behaviour. Inheritance does the rest:
   `#log { user-select: text }` makes every text under the log selectable.
3. **A widget that handles input keeps its gestures.** A `text-input`, a
   `link`, a `rich-text` and the documents' `selection-host` decide what a press
   on their words means, so they are not registered. `rich-text` reads the
   property itself: `text` and `all` make it selectable without the flag,
   `none` stops one that has it, and `all` selects the whole paragraph at a click.
   Its box is focusable whenever it is selectable either way, for `Ctrl+C`.
4. **What a press does to the selection.** A press a widget consumed is the
   widget's and leaves the selection alone; so does a press on a control (a
   focusable `Handles` on the target's chain), as a browser leaves the page's
   selection alone when a toolbar button is pressed, which is what lets a Copy
   button act on it. A primary press anywhere else clears it; a secondary press
   never does, so a context menu opens over what is selected. A selectable box
   inside a focusable container, a scroll, is still selected by dragging.
5. **Copy.** `Ctrl+C` (`Cmd+C` on macOS, through `EditKeys`) copies the
   selection when the focused chain did not take the key, before the window's
   accelerators. A finished selection goes to the primary selection where the
   desktop has one. Moving the selection across boxes is not part of this: the
   selection is one box's, as `rich-text`'s is one paragraph's.

## Consequences

- `user-select: text` on any plain text works, and `-webkit-user-select` with
  it. A selection whose box the next frame did not draw selectable is dropped.
- The offscreen session's host has no clipboard, so copying is tested through
  the launcher on the headless backend (`UserSelectCopyTest`); the gesture is
  tested in a session (`UserSelectTest`) and the state alone in
  `TextSelectionTest`.
- `rich-text`'s state now hears the pointer even when nothing in it is pressable
  or selectable, because `user-select` is only known once a frame is styled; it
  follows hover only when a run is pressable, so a plain paragraph does not
  rebuild as the pointer crosses it.
- `ElementTree.textSelection()`, `Element.textSelection()` and
  `PointerRouter.textSelection()` are new; the package is exported so an
  application's own Copy can read `selectedText()`.
