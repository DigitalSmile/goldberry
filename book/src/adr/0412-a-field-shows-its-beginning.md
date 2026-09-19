# 412. A field shows its beginning

Date: 2026-09-19

## Status

Accepted. Closes the `book/src/TODO.md` entry "A `text-input` holding a long
value shows its end, not its beginning" under *Content modules*, which is
[ADR-0297](0297-an-editor-fills-its-pane-and-a-split-knows-its-own-width.md)'s
last consequence written down and left. Finishes the job
[ADR-0326](0326-a-value-you-cannot-type-into-opens-at-its-beginning.md) started
for read-only fields, and **inverts one of its tests**. Two golden images move
and are not re-blessed here; see *Consequences*.

## Context

Three controls in this toolkit hold a value a reader might have to read, and
until today they had three different answers to "which end of it do I show":

- a `text-area` shows the **top** of its value until somebody touches it
  (ADR-0297);
- a read-only `text-input` shows the **head** of its value, always (ADR-0326);
- an editable `text-input` shows the **tail**.

The third is not a decision anybody made. `TextEdit.of` puts the caret at the end
of the value it is given — right, because that is where typing goes — and
`TextInputState.laidOut` scrolls to keep the caret in view from the very first
layout:

```java
var offset = Math.max(scrollOffset, caretAt - room + caretWidth);
```

From a scroll of zero and a caret at the end, that is the whole width of the
value less the box, on the first frame, before anybody has pressed anything. A
field handed a URL shows its query string; a field handed a path shows the file;
a field handed a sentence shows the end of it. The example's own Forms screen has
the case in it as a demonstration, and its golden shows exactly this:

> `agues of road, and a field that is not wide enough for it.`

— which is the tail of *"Eighteen hundred leagues of road, and a field that is
not wide enough for it."*

ADR-0326 fixed the read-only half and was explicit about not fixing this one: a
read-only field has "no 'where I left off' to preserve", and an editable one
does. ADR-0297 fixed the `text-area` and was explicit for a different reason —
"a field is not a document, and changing two controls on one screen's evidence is
how a fix becomes a regression somewhere nobody looked". Both were right to stop.
The question this ADR has to answer is the one both of them deferred, and it is
not "should there be a flag". It is **what a field should do by default**, since
the flag ADR-0297 gave `text-area` (`caretMatters`) is private state with no
markup, no CSS and no API on it.

## Decision

**An untouched field shows the head of its value. Its caret stays at the end.**

Those are two sentences and they are both load-bearing. Nothing moves the caret:
`TextEdit.of` is unchanged, an application reading `edit().caret()` sees what it
saw, and the four other controls that lean on caret-at-the-end keep leaning.
What changes is *when the field chases it*:

```java
var offset = scrollOffset;
if (caretMatters) {
    offset = Math.max(offset, caretAt - room + caretWidth);
    offset = Math.min(offset, caretAt);
}
offset = Math.clamp(offset, 0, Math.max(0, textWidth - room));
```

`caretMatters` is false until a press, a key, an edit, a composition or the focus
arrives — `text-area`'s flag, its name, and its argument.

### Why the head is the right default for a *field*

The entry's caution is that a field is not a document, so the answer that suits a
document need not suit a field. Three differences, and none of them argues for
the tail:

- **A value in a field is a value being shown to somebody before it is a value
  being typed.** A form pre-filled from a model is read first, and what a reader
  needs first is what the value *is*: the scheme and host of a URL, the label on
  a path, the first name in a name. The tail answers a different question.
- **The end is a keystroke away and the head was not.** The moment the field is
  focused — by `Tab`, which also selects everything, or by a click, which says
  where the caret goes — this chases the caret exactly as it always did. Nothing
  about typing, arrowing, selecting or pasting changes. Before, seeing the head
  meant pressing `Home` on a field you may not have wanted to touch.
- **It is what every text box on the web does**, which matters less as an appeal
  to authority than as an appeal to habit: a user who has never read this ADR has
  already learned what an `<input>` does with a long value.

And one difference that does argue the other way, which is why it is recorded
rather than waved past: **a field is often appended to**. Editing a file name,
adding to a number, correcting the end of a sentence — the tail is where the work
is. That case is exactly the case where the field is about to be focused, and
focus restores it. The default is for the frame *before* anybody has decided to
work; the moment somebody has, the old behaviour is back and it never leaves
again.

### The flag is private, and there is no attribute

No `head=#true`, no `text-input` property, no CSS. ADR-0326 refused the same
thing in the same words for the same reason — "a call site that has to say where
the caret goes is a call site that can forget to" — and a markup attribute here
would be worse than a forgotten one: it would be a second way to express
something the control can decide, written into documents that then disagree with
each other. A value has one sensible opening. If a downstream case ever needs the
other, it can be argued then, with the case in hand.

### Touched is set *before* the refusals

```java
private boolean apply(TextEdit next, EditHistory.Kind kind, boolean filtered) {
    touched();
    if (next.equals(edit)) {
        return false;
    }
```

This caught a real bug in the first draft. `End` on a field whose caret is
already at the end — which is every untouched field — produces an edit equal to
the one it has, so `apply` returned `false` early and the flag was never set:
pressing `End` to see the tail did nothing at all, twice. A key the user pressed
in this field is the field being worked in, whether or not the model moved. The
same goes for a keystroke a filter turns down.

### It does not forget

Losing the focus leaves the flag set. A field that reset on blur would jump back
to the head the moment the user tabbed on — at the exact moment they stopped
being able to correct it — and jump again when they tabbed back. Where the user
left the value is where the value is.

## Consequences

- **Two golden images move, and they are deliberately not re-blessed here.**
  `example/src/test/resources/golden/gallery-forms.png` — 2 574 of 1 800 000
  pixels differ (0.14 %), worst channel delta 184 — and `gallery-forms-light.png`
  — 2 574 of 1 080 000 (0.24 %), worst delta 191. The difference is one 341 × 14
  strip in both: the `text-input#long` on the *Longer than the box* card, which
  now reads `Eighteen hundred leagues of road, and a field that is no…` where it
  read `agues of road, and a field that is not wide enough for it.` Nothing else
  on either screen changed, and no other golden in the repo moved:
  `field-stacked`, `field-horizontal`, the colour picker's hex field and the two
  search boxes all hold values that fit. The pictures are a screen's owner to
  re-take; what matters for the record is that the pixel difference is the fix
  and is confined to it.
- **The showcase card still demonstrates what it says it does.** Its caption is
  "Press `End`, then `Home`, and watch the text move under the caret" — which now
  demonstrates *both* directions from a starting point a reader can read, instead
  of starting at the end with only `Home` to press.
- **Three existing tests changed, and one of them changed its mind.**
  `ReadOnlyCaretTest.editableStartsAtTheTail` asserted "an editable field still
  opens at the end, because that is where you type"; half that sentence survives
  and is still asserted — the caret *is* at the end — and the scroll that chased
  it is gone. `AsymmetricPaddingTest` and `TextInputTest.Scrolling` measure the
  chase, so both now focus the field first, which is what they were always
  really about.
- **A combobox's editor inherits it.** `SelectState` builds a `TextInput` for an
  autocomplete control, so a combobox holding a long committed label now opens
  showing the label rather than its end. That is the same fix and nobody asked
  for it, which is the argument for a default over an attribute.
- **A `password` field is unaffected in practice** — bullets are narrow and a
  password long enough to overflow is rare — and follows the same rule if it
  ever happens.
- **`text-area` and the read-only field are untouched.** All three controls now
  answer the same question the same way, by three mechanisms that stay different
  for the reasons their own ADRs give: a read-only field moves the caret, because
  there is no typing to come back to; the other two leave it and decline to chase.
- `FieldOpeningTest` holds it — the head on opening, the caret still at the end,
  a short value unaffected, a value the application pushes later also opening at
  the head, and then the five ways of touching it that put the tail back.
