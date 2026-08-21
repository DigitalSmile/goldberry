# 167. A field owns its caret, and the model is told

Date: 2026-08-21

## Status

Accepted. Opens `docs/core-widgets.md` §4, closes the clipboard hole
[ADR-0019](0019-the-backend-spis-first-cut.md) left open, and adds a per-window
platform call nothing had needed before.

## Context

§4's `text-input` is the first widget in the toolkit whose state is not a value
an application can hold. Every control before it — `checkbox`, `slider`,
`select`, `segmented` — has a state that *is* the model's: one bit, one number,
one key, read down through `bind=` and reported back through `change=`, with
nothing left over ([ADR-0063](0063-data-flows-down-events-flow-up.md)). A field
has a caret, a selection, an undo stack and a scroll offset as well as its text,
and none of those is the application's business.

Three things also turned out not to exist, and each was only discovered by
trying to write the widget:

1. **`SDL_StartTextInput` was not on the export list.** SDL3 delivers no
   `SDL_EVENT_TEXT_INPUT` to a window that has not asked for one. `SdlEventBuffer`
   could read the event, `Window.handleTextInput` routed it and `KeyboardTest`
   exercised it — and on a real SDL window it had never once arrived.
2. **There was no clipboard.** ADR-0019 left it out of the backend SPI on the
   rule that an interface with no consumer gets designed twice, and said it would
   come back when something wanted it.
3. **A `Paragraph` could not say where a caret goes.** It has the prefix sums —
   wrapping is built on them — and no way to ask.

## Decision

### The field holds the text; the model is told

The edit lives in the element, and every change the user makes is reported
through `change=`. A `bind=` value is the **initial** text and an override: a
value that differs from what the field holds is somebody else's and takes the
field, one that matches is the echo of the user's own keystroke and is ignored.

That test needs a second half, and it is not obvious. Comparing the incoming
value against the field's text is not enough, because an **unbound** field's
`value=` is a constant the widget was built with — so every rebuild would
overwrite whatever had been typed. So the state also keeps what the widget last
offered, and adopts only a value that has *changed* since the last build.

It also has to be in `build` rather than in `didUpdateWidget`. A `bind=` value
firing does not replace the widget: the property notifies, the element is marked
for build, and the widget is the same object it was
([ADR-0062](0062-a-binding-is-a-subscription-the-element-owns.md)).
`didUpdateWidget` would miss the case the mechanism exists for.

### The editing model is a value, and it is tested without a widget

`TextEdit` is `(text, anchor, caret)` and every operation returns a new one.
Three things follow, and the third is the one that mattered:

- A `State` holds one and swaps it, which is how every other stateful widget
  here works.
- Undo is a **stack of states** rather than a log of inverse operations, so
  nothing has to know how to reverse a word delete.
- Every rule in §4 — what `Backspace` does to a selection, where `Ctrl+Left`
  lands, what a double-click selects — is testable with no font, no frame and no
  window. Forty-five of this branch's tests need nothing but the model.

The cost is a string copy per keystroke. For a single-line field that is a few
hundred characters of `arraycopy`, next to nothing beside the shaping the same
keystroke causes. A `text-area` large enough for that to matter wants a rope,
and would want one whether or not this were a value.

Two offsets rather than a caret and a length, so a selection dragged
right-to-left keeps its direction. No selection is `anchor == caret`, so there
is no separate flag to fall out of step with the offsets.

**Everything steps by grapheme**, through `java.text.BreakIterator`'s character
instance — the same class `Paragraph.offsetAt` uses, so the model and the
geometry cannot disagree about where a caret may sit.

### A run of keystrokes is one `Ctrl+Z`, and the rule is one comparison

`EditHistory` folds consecutive changes of the same kind into one entry when
**the new change starts where the last one ended**. Everything else falls out of
that one test and is written down nowhere:

- Moving the caret breaks the run, because the next keystroke then starts from a
  state the last one did not leave.
- So does clicking, for the same reason.
- So does a value arriving from the model.
- Typing after deleting starts a new entry, because the kinds differ.

Editors that coalesce on a **timer** have the defect where pausing mid-word
splits the undo. This cannot: a long typed run is one undo however long it took.
A paste or a cut never folds, because each is one thing somebody did on purpose.

### The field names intents; it does not build edits

The seam between the node that takes the keys and the state that holds the text
passes *intents* — `move(LEFT, byWord, extend)`, `deleteBefore(byWord)` — rather
than finished `TextEdit`s. Handing an edit across would have been shorter, and it
is wrong for a reason worth stating: **the field's edit is not the field's text.**

A `password` draws bullets, and the caret and selection it draws are offsets into
*those*. A field applying `edit.backspace()` to what it was drawing would delete
a bullet and leave the password a row of them. The first version did exactly
that, and the test that caught it was the one asserting a masked field still
holds what was typed.

The intent seam also puts the one rule a masked field has in a single place: a
row of bullets has no words in it, so `Ctrl+Left` in a password goes to the
start rather than stepping by an amount that says how long the words are.

A `Mask` maps display offsets to real ones and back, one bullet per **code
point**, and is the identity for an ordinary field — which pays for none of it.

### The caret blinks on a timer, not on the frame clock

A `spinner` draws itself from `Context.nowMillis()` and answers `isAnimating`,
which asks for a frame every frame
([ADR-0081](0081-a-perpetual-loop-has-no-state.md)). That is right for something
that moves continuously and badly wrong for a caret, which changes **twice a
second**: a field with focus would run the loop at the display's rate for as long
as a form was open, and §1.7's "the frame loop is fully idle when no animation is
active" would be false for every window with a form in it.

So the blink is one one-shot timer, rescheduled — `carousel`'s arrangement
([ADR-0165](0165-a-divider-translates-and-a-rotation-has-three-brakes.md)) — and
costs two frames a second instead of a hundred and twenty. It goes solid on every
edit and every caret move, because a caret that blinks out mid-word is a caret
nobody can find.

The distinction generalises: **`isAnimating` is for motion, and a timer is for a
state that changes on a schedule.** They look alike and their costs differ by two
orders of magnitude.

### Three parts, placed by measurement rather than by layout

```
text-input          the field. Clips, focuses, takes the keys and the pointer
├── text-selection  the highlight, behind the text
├── text-value      the text, or the placeholder
└── text-caret      the insertion point
```

Each is absolutely positioned by the field from `Paragraph.widthBetween`, because
where they go is a *measurement* — a caret's x is the width of the text before it
— and no selector and no flexbox can express it. Yoga is told where they are; it
is not asked. That is `segmented`'s indicator travelling on a grid
([ADR-0099](0099-an-indicator-travels-on-a-grid.md)), applied to something that
moves per keystroke rather than per selection.

The highlight is **behind** the glyphs so selected text keeps its own colour and
stays readable, which is why `--gb-selection` is a translucent background token
rather than a pair of them.

**An absolutely positioned child here is placed against the border box while the
clip is the padding box**, so every child's `left` carries the field's padding.
Without it the first character of every field is drawn under the padding and
clipped away — which is precisely what the Forms screen's first golden image
showed, in all six fields at once, and what no unit test had asked about.

### A paragraph answers both directions, and a line at a time

`widthBetween(start, end)` and `offsetAt(lineStart, lineEnd, x)`, over the prefix
sums wrapping already keeps. Both take a **line's range** rather than one offset,
so a wrapped paragraph works without a second pair for `text-area`; a caret's x
on a line is `widthBetween(line.start(), offset)`.

There is no `caretX(offset)`, because it would be `widthBetween` with one
argument fixed and a wrapped paragraph has no single left edge to fix it to.

`offsetAt` walks by grapheme cluster and returns the **nearest** caret position.
That is the whole of the past-the-midpoint rule rather than a second one: the two
caret positions bracketing a glyph are its edges, so the midpoint is exactly
where the nearer one changes.

### The clipboard is text, and `SDL_free` is bound

`Clipboard` reads text, writes text and says whether there is any. Images and
files are a **transfer negotiation** rather than a value — the owner advertises
formats and serialises on demand — so admitting them means admitting lazy
providers, format lists and cancellation, none of which has a consumer. When a
widget wants an image on the clipboard, this interface will hear about it from
that widget, the way this one heard about text.

A backend with no clipboard reports `Clipboard.none()` rather than an empty
`Optional`: every caller of a missing clipboard would otherwise write that class,
and a copy that quietly did nothing is the honest behaviour of a session with
nowhere to put it. The headless backend's is a **real in-memory clipboard**, so a
copy/paste test tests the widget rather than the stub.

`SDL_GetClipboardText` returns a string the caller owns, from SDL's allocator.
Handing that to `free(3)` is undefined wherever SDL was built against a different
allocator — which on Windows is the normal case — so `SDL_free` is on the export
list beside it. It is the only allocator call this toolkit binds and it exists to
close exactly this loop.

Reads are not cheap: on X11 and Wayland `text()` is a round trip to the
application that owns the selection. `hasText()` is the cheap question, and
nothing polls.

### Text input follows focus, not the window

`BackendWindow.textInput(boolean)`, off by default. Asking is what raises an
on-screen keyboard on a tablet and what tells an IME where its candidate window
goes, so a toolkit that turned it on at window creation would put a keyboard over
every phone screen showing a button. A field turns it on when focus arrives and
off when it leaves; a read-only field never asks, and a window with nothing
editable in it never asks at all.

### A filter judges the result, not the keystroke

`TextFilter.accepts(String)` is asked about the **whole value the edit would
produce**. That is the difference between a filter that works and one that looks
like it does: a numeric field testing keystrokes accepts `1-2-3`, because every
character is legal, and rejects a pasted `-5`, because the minus arrives with no
digits after it. Asking about the result gets both right, and gets pasting right
for nothing — a paste is one edit like any other.

A filter rejects; it does not rewrite. One that silently corrected would move the
caret out from under somebody mid-word, and would make the field's contents
depend on the order the characters arrived in.

## Consequences

- **§4 is open.** `field`, `form`, validation, `text-area`, the pickers,
  `code-input` and autocomplete all reuse `TextEdit` and `EditHistory`, which are
  the parts with rules in them, and all of them are ordinary widget work now.
- **The clipboard exists for everything else**, and `Host.clipboard()` is how a
  widget reaches it — the door `BuildContext.host()`
  ([ADR-0140](0140-a-widget-may-reach-its-window.md)) opened.
- **Committed text arrives on a real window for the first time**, which means
  every path that was written for it and never exercised now is.
- **IME preedit and RTL editing are still M5.** Committed text from an IME works
  today, because the platform hands over finished characters and a field takes
  them like any others. What is missing is the underlined *in-progress* text,
  which needs a second string the field draws and does not hold, and
  `SDL_SetTextInputArea` to tell the IME where to put its candidates.
- **A caret is not in any golden image.** It blinks on a timer, so an image with
  one in it would be an image of whichever half of the blink the test caught.
  What a caret *does* is the unit tests' business and what a field *looks like*
  is the golden's.
- **A field is one line, and the parts know it.** `text-area` wants one
  `text-selection` per line rather than a different part, and `Paragraph`'s two
  new methods already take a line's range — so neither is a rewrite.
