# ADR-0697: A rich-text run is pressed, revealed and selected

- **Status:** Accepted
- **Date:** 2026-11-14
- **Relates to:** GB-021's `rich-text` and `run`

## Context

A message bubble needs mentions, hashtags and links that are pressed, a
spoiler that is covered until pressed, and words that can be selected and
copied (Tessera's G-03, here T-03). `RichText` was a stateless leaf: it joined
its runs into one paragraph and coloured each run's stretch, and it heard no
input. The `:html` views select text, but over their own word model, which
`:widgets` cannot reach.

The plan left open whether the selection model is shared with `content.select`.

## Decision

`RichText` is **stateful** over a `rich-text` leaf, `RichTextBox`, the
arrangement `ListView` and `ListBox` have. The state keeps the paragraph the
last frame laid out, and a pointer becomes a place in its text through the
same `TextGeometry` `text-area` reads: the event's content-box position, the
paragraph laid out at that width, the offset, and the run whose stretch holds
the character under the pointer.

- **Pressed runs.** `Run.onPress(Runnable)`. A click on the run's words, on
  any line they reach, runs it; the space beside them does not. The run
  carries `pressable`, which the toolkit's stylesheet gives the link ink and
  the pointer. The pointer over a run takes that run's `cursor`.
- **Spoilers.** `Run.spoiler(true)` carries `spoiler`; covered, it draws its
  words in no colour on a block of the muted ink. A press reveals it for the
  life of the widget and adds `revealed`; that press runs no handler. How a
  reveal animates is the application's, through `run.spoiler.revealed`.
- **Selection.** `RichText.selectable(true)` makes the paragraph focusable;
  a drag selects, `Ctrl+A` selects it all, `Ctrl+C` copies through the host's
  clipboard, and losing the focus clears it. A drag that selected something is
  not a press. The highlight is the `rich-text-selection` part's background,
  read off its box and not drawn as a box.
- **Backgrounds in the paragraph.** `SpanPaint` has a fifth component,
  `background`: a fill a line box tall behind the stretch on every line it
  reaches, drawn before the line's glyphs. A run's own `background` uses it,
  and so do the selection and the spoiler's cover. The four-argument
  constructor stays.

The selection is **not** shared with `content.select`: a `rich-text` is one
paragraph, and its geometry is the paragraph's. A selection across several
widgets remains the content views' alone.

## Consequences

- `RichText` is a `Widget.Stateful`, with a `selectable` component; the
  two-argument constructor stays. `Run` has `onPress` and `spoiler`; its
  two-argument constructor stays. Record patterns over either name the new
  components, and `role()` and `accessibleName()` are the leaf's now.
- A line taller than the first font's line height is hit by the first font's
  line height, which is what `TextGeometry` assumes; a paragraph of one size
  is exact.
- Tested by `RichTextInputTest` (a press on a mention and none beside it, the
  hovered run and its pointer cursor, a spoiler covered by pixels and revealed
  without a press, a drag selecting through a word, `Ctrl+A`, no selection on
  a paragraph that is not selectable), `ParagraphJoinTest` (a background a
  line box tall, nowhere else) and the existing `RichTextTest`.
