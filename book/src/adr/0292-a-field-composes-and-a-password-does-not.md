# 292. A field composes, and a password does not

Date: 2026-09-12

## Status

Accepted. Closes `docs/gaps.md` G16, and finishes what ADR-0289 started.

## Context

G16 was opened by ADR-0289, which gave a `canvas` the ability to show what an
input method is composing and left the toolkit's own fields without it:

> `text-input` has its own editing model (`TextInputState` over `TextEdit`), and
> its caret and selection are absolutely positioned *boxes* rather than a
> painter's rectangles, so the same feature is a different piece of work there.
> … The one decision that is not mechanical is what a `password` does with a
> composition.

So this record has two jobs: carry ADR-0289's rule — **a composition is not an
edit** — into two more controls, and answer the password question.

`text-area` is here as well as `text-input`, and that was not optional.
`controls.css` says of the two: *"the two controls must not look like they were
designed by different people, and the surest way to that is one set of rules
rather than two that agree today."* Shipping composition in one and not the other
is exactly that.

## Decision

### The composition is a span, because the string is already in the display

Both fields already draw something that is not their value. `text-input` has
`Mask`, which turns `hunter2` into `•••••••` and carries two index tables so that
a caret, a click and a selection mean the same place in both. A composition is
the second instance of the same idea, and it needed no second mechanism: the
state splices the composition into the string it hands its box, and adds one
value saying which part of what you are drawing is not text yet.

```java
record Composing(int start, int end, int clauseStart, int clauseEnd)   // display offsets
```

The caret goes **inside** it, at `caret + preeditCaret`, which is where every
native field puts it — an input method walks a caret through the string it is
assembling.

### The highlight draws the clause, because there is no selection to draw

A composition replaces the selection when it commits, and every platform's input
method collapses the highlight when one starts. So the field's existing
`text-selection` part is free while a composition is open, and it draws the
**converting clause** — which is what a clause is: a selection inside the
composition's own little document.

One new part, `text-composition`, is the rule under the whole composition. It is
drawn *after* the glyphs where the highlight is drawn before them, because it is a
mark on the text rather than a wash behind it. Its thickness is the widget's and
not a token, on `text-caret`'s width reasoning inverted: a caret's width is a
matter of taste and has `--gb-caret-width`, where an underline is a hairline on
every platform that draws one, at every size.

`text-area` gets `maxRows` of them, exactly as it gets `maxRows` highlights and
for the same reason: a composition can wrap, and a run of wrapped text is not a
rectangle. The per-line rectangle arithmetic was already there for the selection;
it is now `spanRects` and is called twice.

### A `password` refuses to compose

This is the decision G16 said was not mechanical, and the answer is what the
platforms do: Windows disables the IME for an `ES_PASSWORD` edit control, and
macOS's `NSSecureTextField` refuses marked text.

The reason is not squeamishness about bullets. **A candidate window is a second,
unmasked window showing what is being typed**, drawn by the input method next to
the field. A masked field that composed would put the password on screen beside
itself, in a window the application does not own and cannot mask — and it would
do so on a control whose entire purpose is that the text is not on screen.

So `compose` returns `false`, the event is left unconsumed, and the field draws
nothing inline. **Committed text still arrives**, so the field still takes every
character an input method produces; what a user loses is the inline preview, and
that is the trade every platform has already made.

`readOnly` and `disabled` refuse on the same terms they refuse an edit.

### The field answers where its caret is

`Handles.caretArea()` (ADR-0289) is answered from the state, because only it has
the last frame's shaped paragraph and the scroll offset — and the router asks
after every event, not during a render.

The two controls answer differently, and deliberately:

- **`text-input` reports its whole content box.** A single-line field *is* the
  line being typed on, and the rectangle's job is to keep the candidate list clear
  of the text it would otherwise cover.
- **`text-area` reports the caret's line.** A candidate window kept clear of a
  ten-line control would be pushed a long way from the text it belongs to.

## Consequences

- **No golden image moved.** The new part renders an empty box when nothing is
  being composed, which is what `text-caret` and `text-selection` already do when
  they have nothing to cover.
- **`text-area`'s parts doubled**, from `maxRows + 2` to `2 * maxRows + 2`, and
  `TextAreaBox.render` stopped indexing from the end (`children.size() - 2`) and
  started indexing from `maxRows`. The parity test that asserted "the same three
  parts" now asserts the same four and their positions, which is a better test:
  it says what the tree *is* rather than what its last two entries are.
- **A placeholder gives way to a composition.** A field being composed into is
  not empty, however little of it is committed.
- **Losing focus ends a composition**, and nothing else would: the empty
  `TEXT_EDITING` that normally ends one goes to whatever has focus, which by then
  is something else.
- **`onChange` never fires for a composition**, which is the whole point restated:
  an application bound to a field hears the accepted candidate once, not one
  change per keystroke of a string that is about to be replaced.
- **Nothing about `Mask` changed.** It was already the right shape, and a
  composition being a second instance of "what is drawn is not what is held" is
  the reason this was 250 lines rather than a rewrite.

## Alternatives considered

- **Masking the composition in a `password`.** Bullets inline, and the candidate
  window still showing the plaintext beside them — the leak untouched and the
  user misled about where their password is visible.
- **Letting a `password` compose unmasked.** Honest and unusable: it would be the
  one control in the toolkit whose contents are on screen.
- **One underline in a `text-area` instead of `maxRows`.** A composition almost
  never wraps, and "almost never" leaves a rule under the first line of one that
  did. The highlight already pays this cost and states why.
- **Extending `Mask` to carry the composition.** Tempting — one splice instead of
  two — and wrong: a mask is per *character* and a composition is a *span*, and a
  `password` does not compose, so the one type that would have joined them is the
  one type where they never meet.
