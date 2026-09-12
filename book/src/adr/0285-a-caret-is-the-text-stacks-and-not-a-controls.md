# 285. A caret is the text stack's, not a control's

Date: 2026-09-12

## Status

Accepted. Closes `docs/gaps.md` G6 apart from IME preedit, which is filed as
G15 and needs a native binding this does not have.

## Context

G6: *"`widgets.form.textinput` and `widgets.form.textarea` are complete editors
**as widgets**. What a board needs is a caret in a sticky: an editor over shaped
runs at an arbitrary canvas transform, with IME, selection, clipboard and
undo."*

The entry is right about the shape of the problem and wrong about how much was
missing, which is now the third time in this series (ADR-0281, ADR-0283). Two
things were already built and in the wrong place, and two were not built at all.

**Already built, in `:widgets`:** `TextEdit` — a string, a caret and an anchor,
with every movement and deletion as a pure function — and `EditHistory`, an undo
stack that folds a typing run into one step. Neither has ever named a widget, a
box or an element. Both sat in `io.github.digitalsmile.goldberry.widgets.form.textinput`,
which is to say: the rules of text editing lived in the module that draws text
*fields*, so anything editing text anywhere else had to reach into a control's
package or grow its own.

**Already built, in `:core`:** `Paragraph.widthBetween` and
`Paragraph.offsetAt`, which are the two measurements a caret is made of.

**Not built:** the *two-dimensional* half — where a caret is on a **wrapped**
paragraph, what `Up` means when lines are not the same length, and what shape a
selection is when it spans a line break. `TextAreaState` had ten lines of it,
tangled with scrolling and padding.

**Also not built:** a key map anything but a widget could reach. `TextField.onKey`
knows that `Ctrl+Shift+Z` is redo and that `Home` is the start of the visual
line; a canvas had no way to ask.

## Decision

### The editing model moves to the text stack

`io.github.digitalsmile.goldberry.text.edit`, beside the shaping and the layout it
is arithmetic over. `TextEdit` and `EditHistory` move there **unchanged** —
`:widgets` imports them from their new home, and `text-input`, `text-area` and
`code-input` are otherwise untouched.

### `TextGeometry` is the two-dimensional half

```java
TextGeometry.caretAt(paragraph, layout, offset)          // → Caret(x, top, height, line)
TextGeometry.offsetAt(paragraph, layout, x, y)           // a click
TextGeometry.moveLine(paragraph, layout, offset, ±1, desiredX)
TextGeometry.selectionRects(paragraph, layout, start, end)
TextGeometry.lineOf(layout, offset)
```

Static, and every method takes the paragraph and its layout together, because the
one bug this class exists to prevent is a caret measured against one wrap width
and drawn against another.

`desiredX` is a **column in pixels**, not a character count: walking down through
a short line and out the other side has to come back to the column it started in,
and that is a property of the *x* rather than of the offset.

`selectionRects` returns one rectangle per visual line, because a selection that
spans a wrap is L-shaped, and it widens a line whose selection runs past its last
character by a space — otherwise a selected newline is invisible, which is the
kind of thing nobody can name and everybody notices.

### `Editor` is the whole editor, without a widget

```java
var editor = new Editor(font).multiline(true).wrapWidth(240).clipboard(window.clipboard());

new Canvas((frame, size) -> editor.paint(frame, 8, 8, ink, focused), new Input() {
    public void onPointer(PointerEvent e) { editor.pointerAt(x, y, e.modifiers().shift(), e.clickCount()); }
    public void onKey(KeyEvent e)         { if (editor.onKey(e)) e.consume(); }
    public void onText(TextEvent e)       { if (editor.onText(e.text())) e.consume(); }
    public void onFocusChanged(boolean focused, boolean fromKeyboard) { … }
});
```

It holds a `TextEdit`, an `EditHistory` and the `Paragraph` it re-shapes when the
text changes — **one** shaping, which is what makes the caret, the hit test and
the paint agree by construction rather than by review.

Its key map is `text-input`'s, key for key, because two editors in one toolkit
that disagree about `Ctrl+Shift+Z` is a toolkit with a bug in one of them. A key
it does not handle is **not consumed**: `Tab` still moves focus, `Escape` still
closes what it closes, and `Enter` in a single-line editor still reaches a form.

`paint` draws the selection, the text and the caret in that order — the only order
that works — and draws the caret only when told to, because a blink is a clock and
a repaint and both are the application's.

### `Input` gained `onFocusChanged`, and the keyboard gained a switch

Everything else a canvas draws looks the same focused or not. A caret does not.
One default method on `Input`, passed through by `Canvas`.

And one more, which is the difference between this working and not: **the
platform produces no text until something says it is being typed into.**
`SDL_StartTextInput` was called by `TextInputState` from its own focus handler —
so the first thing to hold an editor outside the catalogue got keys and never a
character, which is exactly how this was found: the showcase's sticky did
nothing when typed into.

That call is the **router's** now, and it is the cursor's arrangement repeated:

- `Handles.wantsTextInput()`, default false — a widget declares that it is typed
  into.
- `PointerRouter.onTextInputChange(sink)`, beside `onCursorChange(sink)` — the
  router asks the focused widget on every focus change and tells whoever is
  listening, knowing nothing about the platform.
- `Window` wires that sink to the backend, which is the one wire between the two.
- `Input.wantsText()` → `Canvas.wantsTextInput()`, so a canvas says it in the
  same place it says everything else.

A board that wants arrow keys and not an on-screen keyboard leaves it false,
which is why this is declared rather than inferred from "has an `Input`".

### IME preedit is not in this, and is not close

`SDL_EVENT_TEXT_INPUT` — committed text — is bound and has always worked, which is
what `Editor.onText` takes and is most of what an input method does.
`SDL_EVENT_TEXT_EDITING`, the *preedit* string with its cursor and its underline,
is **not bound at all**, and neither is `SDL_SetTextInputArea`, which is how the
platform is told where to put the candidate window. That is a native binding, an
event route, a preedit model in the editor and a rendering convention — its own
decision, and M5 already owns "IME preedit". Filed as `docs/gaps.md` G15 rather
than claimed.

## Consequences

- **`TextEdit` and `EditHistory` changed package.** A breaking change for anyone
  who imported them from `:widgets`; nothing in this repository or in the
  application that asked for G6 did.
- **There are two key maps now**, this one and `TextField`'s, and they agree
  because they were written from each other rather than because anything enforces
  it. Converging them means making `text-input` and `text-area` hold an `Editor`,
  which is a rewrite of two controls' state machines and is filed in `TODO.md`
  rather than done during a feature.
- **No scrolling.** An `Editor` draws where it is told and does not know it has
  been clipped; `text-area` scrolls because it is a box with a viewport. A canvas
  that wants a long document scrolls its own transform, which it is already doing
  for everything else it draws.
- **No bidi caret.** `Paragraph.isBidiApproximate` says what the shaping does not
  promise, and a caret in mixed-direction text needs a visual-order walk that the
  toolkit does not have yet. Latin, Cyrillic and CJK are exact.
- **No placeholder, no validation, no focus ring, no border.** Those are what a
  *control* is, and `text-input` remains the answer for a form.
- **`TextInputState` no longer turns the platform's input off when it is
  disposed.** It used to, and with the router also tracking the state that was a
  stale-cache bug waiting: a focused field unmounting turned the platform off
  while the router still believed it was on, so the next field focused agreed
  with the stale answer and was never told. The router notices the unmount on the
  same frame through `refocus`, which is the one place that knows what has the
  focus *after* the field has gone.
- **It found a crash in the offscreen render.** The showcase's sticky is the
  first widget whose `State` owns a `Font` and closes it in `dispose`, and
  `Offscreen` was unmounting the tree before ending the frame — so a Blend2D
  worker was still rasterizing glyphs from a font that had just been destroyed.
  A SIGSEGV in a worker thread, intermittent, and invisible to every test that
  did not own a font. The order is stated and commented in `Offscreen` now
  (ADR-0284).
- **`Font` stays the caller's.** An editor holds one and closes nothing, which is
  why the showcase's sticky opens its font in a `State` and closes it in
  `dispose`.

## Alternatives considered

- **Leave the model in `:widgets` and document it.** An application already
  depends on `:widgets`, so it would have worked — and it would have said that
  the rules of text editing are a property of the widget catalogue, which is the
  same category error ADR-0279 corrected for flexbox and ADR-0283 for images.
- **Make `Editor` a widget.** Then it is `text-input` again, and the thing G6
  asked for — a caret inside a drawing, at the application's own transform — is
  exactly what a widget cannot be.
- **Put the geometry on `Paragraph`.** It is arithmetic over a paragraph *and* a
  layout, and `Paragraph` deliberately does not hold its layout: the same shaping
  is laid out at several widths by the measure pass. A class that takes both is
  the honest shape.
- **Have `Editor` own its own scrolling and clipping.** Two coordinate spaces and
  a viewport inside a class whose whole point is that the caller owns the
  transform.
- **Wait for IME before shipping any of it.** The preedit is one part of one
  writing system's input path; a caret, a selection, undo and a clipboard are
  every writing system's, and they work now.
