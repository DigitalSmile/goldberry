# 289. A composition is not an edit

Date: 2026-09-12

## Status

Accepted. Closes `docs/gaps.md` G15. Opens G16.

## Context

G15: *"`SDL_EVENT_TEXT_INPUT` is bound and delivers **committed** text… The
composition — the underlined string being assembled, with its own cursor — is
`SDL_EVENT_TEXT_EDITING`, and it is **not bound at all**; neither is
`SDL_SetTextInputArea`… So today a Japanese, Chinese or Korean user typing into a
sticky sees nothing at all until they commit, and the candidate window opens
wherever the compositor guesses."*

Two facts make this smaller than it looks and one makes it larger.

Smaller: ADR-0285 already built the caret, the selection, the wrapped-line
geometry and the key map, and ADR-0281 already built the route from a platform
event to a focused canvas. Committed text already arrives — an input method's
*result* has always worked.

Larger: the composition is **not text**. It is a proposal. `にほんご` becomes
`日本語`, and every character of what was typed is replaced when the user picks a
candidate. A toolkit that treats it as typing is wrong in four places at once —
the document, the undo history, anything observing the value, and the screen,
which flickers as characters are inserted and withdrawn.

## Decision

### It is a third keyboard event, beside the other two

`KeyEvent` is a key. `TextEvent` is text the platform has finished translating.
`PreeditEvent` is the string in between, and it is a separate type rather than a
flag on `TextEvent` because the two have opposite obligations: one must be
inserted and the other must not.

`Handles.onPreedit` is a `default` that does nothing, so every widget in the
catalog is exactly as correct as it was — it receives committed text as before
and simply does not draw the underline.

**There is no capture phase**, unlike `onText`. A container reads what was typed
before its child does because a `select` filters on it and a menu navigates by it
(ADR-0246); nothing can usefully do either with characters that are about to be
replaced, and offering them would invite a widget to act on a guess.

### The composition is displayed inside the text and stored outside it

`Editor` holds `preedit` beside `edit`, never in it. What changes is `displayText()`
— the document with the composition spliced in at the caret — and that splice
lives in one method. Everything downstream follows from it: `paragraph()` shapes
the displayed text, so the words after the composition move along as they do in
every native field; `caret()` is measured at `edit.caret() + preeditCaret`, so the
caret sits inside the composition where the input method has it; and `pointerAt`
maps the hit back out, because a click during a composition lands in a string the
document does not contain.

Two things are deliberately different while composing:

- **No selection is drawn.** A composition replaces the selection when it
  commits, and every platform's input method collapses the highlight when one
  starts. Leaving it would highlight text that is about to go.
- **The converting clause is drawn like one.** `start`/`length` is the clause the
  input method is currently working on; it is filled with `Ink.selection` and the
  whole composition is underlined in `Ink.text`. No new colours: a composition is
  the same three things a selection is, drawn to say "not yet".

`onText` clears the composition **before** inserting, rather than waiting for the
empty `TEXT_EDITING` that says it ended. The two are not ordered against each
other on every platform, and waiting means an accepted candidate draws twice.

### The offsets are converted once, in the backend

SDL reports the clause in **UTF-8 bytes**; everything above the backend counts in
Java chars. `Sdl3Backend` converts, and the conversion is a span rather than a
length — six bytes is two characters in Japanese and six in ASCII, so a length
cannot be converted without knowing where it starts.

A composition is by definition not ASCII, so a layer that passed the bytes through
would be wrong for every user this feature exists for. Out-of-range offsets are
**clamped** rather than reported: a byte offset landing inside a character is not
something a caller can act on, and the nearest boundary is the only useful answer.

### The caret's rectangle is published by the router, not by the application

`SDL_SetTextInputArea` tells the platform where the text being typed is, so the
candidate window goes beside it rather than over it. Someone has to know where
the caret is, and only the widget does — so `Handles.caretArea()` is a **question
the router asks**, in the widget's own content coordinates, after every event that
could have moved a caret: a key, a character, a composition, a click, a focus
change.

The router translates to window coordinates and hands the result to the window
through `onCaretAreaChange`, which is `onTextInputChange`'s twin and exists for
the same reason: placing a candidate window is a platform call, the router must
not know about the platform, and the widget must not know about the window.

An unchanged rectangle is not republished, so a run of keystrokes inside one line
is not a run of platform calls. Focus leaving publishes `null`, which clears the
area — without it the platform keeps placing lists where a caret no longer is.

**A CSS-transformed ancestor is not compensated.** The hit-test region carries the
inverse of the paint transform and not the forward one, so a caret inside a
transformed subtree is reported where it was laid out. The candidate window is
then in the wrong place and nothing else is wrong, which is a better trade than
carrying a second affine through the hit test for a case an editable canvas does
not have.

## Consequences

- **One symbol and one struct were added** — `SDL_SetTextInputArea`,
  `SDL_TextEditingEvent` — plus the `SDL_EVENT_TEXT_EDITING` constant. The layout
  harness caught the constant being missing from the C shim on the first run,
  which is exactly what it is for: a Java event number that disagrees with the
  linked SDL dispatches on an event nothing sends, and looks like a platform with
  no input method.
- **`text-input` does not compose yet, and that is now G16.** Its editing model
  is `TextInputState` over `TextEdit`, and its caret and selection are absolutely
  positioned *boxes* rather than a painter — so the same feature is a different
  piece of work there, with a decision of its own about what a `password` field
  does with a composition. G15 asked for `Editor`, and `Editor` is what this
  closes; leaving the gap unnamed would have been the dishonest part.
- **The headless backend can drive one**: `composeText(text, start, length)`,
  `inputText(accepted)`, `endComposing()` — so a test can be a Japanese user
  without an input method being installed.
- **What cannot be tested here is a person choosing a candidate.** What is tested
  is everything else: the struct against the compiled library, the byte-to-char
  arithmetic including surrogate pairs, that the document and the undo history do
  not move while composing, that an abandoned composition leaves nothing behind,
  and that the caret's rectangle reaches the window in window coordinates.
- **`Editor.caret()` moved** — it is now measured against the displayed text. For
  everything that never composes, which is every existing caller, the displayed
  text *is* the text and nothing changed.

## Alternatives considered

- **Inserting the composition and deleting it on the next event.** What a toolkit
  does when it has no separate event. It is visible as flicker, it fills the undo
  history with keystrokes the user never chose, and it fires every `Property`
  bound to the value several times per character.
- **A flag on `TextEvent`.** One type with two opposite obligations — insert this,
  do not insert this — which every consumer would have to branch on and one of
  them would forget.
- **Making the application call `SDL_SetTextInputArea`.** Faithful to the entry,
  which proposed feeding it from `TextGeometry.caretAt`, and it would have meant
  every application that wants an IME remembering to do it after every keystroke.
  The router already asks the focused widget questions after every event; this is
  one more.
- **Carrying a forward affine through the hit test** so a transformed caret is
  reported where it is painted. Real, and it costs a field on every region for a
  case that is a transformed editable canvas. Filed rather than built.
