# 183. A combobox is a select you can type in

Date: 2026-08-23

## Status

Accepted. Builds §3's `select autocomplete=#true`, which
[ADR-0182](0182-a-select-may-hold-more-than-one.md) left as the one part of §3's
`select` line still unbuilt.

## Context

§3: "`autocomplete=#true` makes the closed control an editable `text-input`:
typing filters the options, the popup stays open and narrows, `Esc` restores the
last committed value rather than clearing, and a free-typed value is refused
unless `free=#true`."

ADR-0182 built the free-text half of autocomplete — a `text-input` that offers
suggestions under itself — and deferred this one, because the combobox form asks
a question the free-text form does not: **where does the editing state live?**

## Decision

### The editor is a real `text-input`

§3 says "makes the closed control an editable `text-input`", and it is meant
literally: `SelectField` holds a
[TextInput](../../../widgets/src/main/java/io/github/digitalsmile/goldberry/widgets/form/textinput/TextInput.java)
as a child. Everything an editable field needs — the edit model, the undo
history, the clipboard, the caret's blink, IME — already lives there and has
rules in it ([ADR-0167](0167-a-field-owns-its-caret-and-the-model-is-told.md)). A
second editor grown inside `select` would be a second copy of those rules, and
the first one to drift would drift silently.

That the cascade then sees a `text-input` inside a `select` is not a wart; it is
what §3's sentence describes, and it means an application's `text-input` rules
apply to the thing that is one.

### One Tab stop, and the plate delegates

The field is **not focusable** when it holds an editor, and `delegatesFocus()` is
true. Without both, a combobox would be two Tab stops where a document wrote one
control; with them, the editor is the stop and a press on the field's own chrome
— its padding, its chevron — hands the keyboard to it. That is `field`'s
mechanism ([ADR-0170](0170-a-document-names-an-object-and-a-label-hands-focus-down.md))
reached for its own reason: the thing that takes the press is a *sibling* of the
thing that should end up focused.

Two keys change meaning as a result:

- **`Space` types a space.** §3 lists `Space` as a way to open a *closed*
  control, and a combobox is not one.
- **A click opens rather than toggling**, and is not consumed. A click in a
  combobox is a user putting the caret somewhere; closing the list under them
  because it happened to be open would take the choices away mid-gesture, and
  the editor underneath needs the same click to place its caret.

`Esc` is handled on the **bubble** phase, so the editor keeps whatever it wanted
first — the line every control in this catalog draws.

### The offered text is the whole mechanism

`SelectState` holds one nullable string: what the user has typed, or null while
the control is showing the committed value. It is handed to the `TextInput` as
that widget's `value`, and `TextInputState.follow` overwrites the field **only
when the offered value changes**.

That one property does all the work. Typing is never fought, because the offered
text is what was typed. `Esc` restores by setting it back to null, which is a
change, so the field is overwritten with the committed label. Choosing an option
clears it for the same reason. No new rule was needed anywhere.

### Refusing is a blur-time decision

§3's "a free-typed value is refused unless `free=#true`" needs a moment to happen
at, and the moment is the keyboard **leaving**: that is when a half-typed value
stops being an attempt and starts being an answer. Heard through
`onFocusWithin` rather than `onFocusChanged`, because the thing that has the
keyboard is the editor *inside* the field — this node never had it to lose.

A `free` control keeps what was typed and reports it through `change`. Every
other one puts the committed value back, because a combobox is a **set** of
values and text naming none of them is a mistake rather than a new member.

### Filtering is still the application's

The control raises what was typed through `query` and renders whatever options it
is handed back. §3 gives the reason and it is the same one ADR-0182 recorded for
the free-text form: a remote-backed autocomplete is then the same widget with a
slower model, and nothing in the toolkit has to guess what "matches" means for a
street address or a species name.

The popup **narrows rather than reopening**, on `Popup.content` — which ADR-0182
built for `select multiple` and which this is the second consumer of.

## Consequences

- **`select` now has eleven components**, and this is the second control to reach
  the size where [ADR-0181](0181-a-box-may-say-how-small-and-how-large.md)'s
  positional-constructor argument applies. `RecordWitherTest` covers `Box` and
  `ComputedStyle` and not the widgets; extending it to every record with withers
  is the obvious next move and was not made here.
- **`tree=#true` is the last of §3's `select` line still unbuilt**, and it waits
  on `tree`, which does not exist.
- **The editor is not told to select-all on focus.** A `text-input` reached by Tab
  selects everything, which is right for a field whose value you are replacing and
  is what a combobox wants too — it comes free, because the editor is a real
  `text-input` and that is its rule. Worth stating because it was not designed
  here; it was inherited, and it happens to be correct.
- **The editor is drawn as the select's interior**, not as a control sitting in
  one. Left alone it would have brought a `text-input`'s border, fill, radius and
  focus ring inside the `select`'s own — which was checked rather than assumed —
  so `controls.css` strips all four and lets it grow into the room the chevron
  leaves. The focus ring stays the outer control's, because what the user focused
  is a `select`.
- **A guard caught the comment before the code.** The base stylesheet "must name
  no colour of its own" is asserted as *contains no `#`*, and a comment quoting
  KDL's `=#true` fails it. Cheap to fix and worth knowing: the check reads the
  whole file, comments included.
