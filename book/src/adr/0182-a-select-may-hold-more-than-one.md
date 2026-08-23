# 182. A select may hold more than one, and a field may suggest

Date: 2026-08-23

## Status

Accepted. Builds §3's `select multiple=`, §4's free-text autocomplete, and the
way out of a toast that §7's shape left missing.

## Context

Three entries in `book/src/TODO.md`, and they turned out to share one shape:
**a control that offers a set of things has to be able to give one back.**

- **`select multiple=`** was "deferred as scope" and needed nothing unbuilt.
- **Autocomplete** was waiting on `text-input`, which has since shipped.
- **A toast could not be dismissed by clicking it**, so one with `Duration.ZERO`
  and no action button was removable only through `ToastController.clear()` — a
  notification nobody can get rid of.

## Decision

### A toast's plate is its own dismiss affordance

§7 gives a `message` a dismiss × and gives a toast an action button and nothing
else, and that shape was followed exactly. What the omission of a × meant is that
a toast does not need a *second* affordance competing with its action for a
360×40 plate — not that a persistent one should be undismissable.

So the plate itself is the affordance. It costs no vocabulary, no glyph and no
room; it makes every toast dispellable rather than only the persistent ones; and
the click was already being swallowed, because the plate is hit-testable and a
click on it never reached the application underneath and simply did nothing.

The trade-off is real and is worth stating: a click aimed at the action button
that misses it dismisses without acting. The button is told first — a click
bubbles from the node it hit — so a hit is never lost, and dismissing twice is
already ignored.

### `change` is a toggle when a select holds many

The selection is a **set**, and `Select.resolvedAll()` reads it the way
`resolved()` reads one value: a bound `Collection` becomes the strings its
elements stringify to, and anything else becomes a single value — so a model that
starts as one value and becomes a list is not a different kind of binding. The
order is the **options'** rather than the model's, so removing a chip and putting
the value back does not move it to the end of the row.

What is reported is the value the user touched, through the `Consumer<String>`
every other valued control already uses. In this mode that is a **toggle**: the
set is the application's, so asking for a value it already holds can only mean
taking it out. One channel rather than two is what keeps a chip's × and a click
on an already-chosen row from being two ways of saying one thing — and what keeps
`multiple` inside the shape §9's binding already has.

**The list stays open** while values are picked. The whole point of the mode is
picking several, and a list that shut after each one would make three values three
round trips through a popup that has to be measured, placed and opened again each
time.

### A popup's content may change while it is open

Which the toolkit could not do. A popup is an element tree of its own with its own
build schedule ([ADR-0103](0103-a-popup-is-a-tree-in-a-window.md)), so a
`setState` in the widget that opened it reaches that widget's tree and nothing in
the window the popup is drawn in — and the only way to show a popup something new
was to close it and open another, which flickers and loses the keyboard's place.

`ElementTree.update(Widget)` re-describes a tree's root and reconciles from there,
so the elements, their state and their focus survive. `Popup.content(Widget)` is
the door. §4's autocomplete needs it for the same reason and says so out loud:
the popup "stays open and **narrows**".

### The suggestions are a `SelectList`, and the rows commit rather than follow

§4: "attaches a `popover` of suggestions to the field: the widget raises the
query, the application supplies the list, and the field's text is never rewritten
without the user choosing."

All three fall out of the shape rather than being enforced. The field reports what
was typed through `change` and is handed a list back by being **rebuilt**;
choosing a suggestion reports *that* through the same `change`. Nothing here sets
anything ([ADR-0063](0063-data-flows-down-events-flow-up.md)), so a handler that
ignores a suggestion leaves the field exactly as the user typed it.

`Option.inAList()` is what makes the panel right for this: the arrows move the
focus and `Enter` commits, so a user arrowing through suggestions never has the
field rewritten under them. Follow-the-focus is a `select`'s behaviour and is
wrong here for exactly that reason.

**Filtering is the application's**, which §3 already argued for the combobox form:
a remote-backed autocomplete is then the same widget with a slower model, and
nothing in the toolkit has to guess what "matches" means for a street address or a
species name.

### A field learns where it is, and asks for one frame when it does

A popover is anchored to a rectangle no widget can compute and only the painted
frame knows, so `TextField` implements
[`Located`](0119-a-widget-may-be-told-where-it-is.md) as `SelectField` already
did.

The rebuild it asks for is the part worth recording. Without it, a field focused
with suggestions already in hand offered nothing until some *unrelated* frame
rebuilt it: the rectangle arrives after the paint, and §1.7's idle loop was never
going to ask for another one. So `located` asks for a build — but only when
something is waiting to be shown and only when the rectangle **changed**, which
settles in one frame rather than driving the loop. That is what `Located`'s "a
widget told where it is must not move itself" is really asking for: the rebuild
describes the same field at the same size, so the next rectangle is equal and
nothing more is asked.

## Consequences

- **`select autocomplete=#true` is not built.** §3's combobox form makes the
  *closed control* an editable `text-input` — typing filters, `Esc` restores the
  last committed value, and a free-typed value is refused unless `free=#true`.
  The suggestion machinery it needs now exists and is proven by the free-text
  form; what is left is hosting an editable field inside `SelectField` and the
  commit/restore rules over it, which is its own decision about where the
  editing state lives. `select tree=#true` still waits on `tree`.
- **Autocomplete is Java-only in v1.** §4 says the application supplies the list,
  and the list arrives by rebuilding the widget in answer to `change` — a channel
  a document does not have. A document may write the field; it simply offers
  nothing under it. Giving markup a named suggestion source is a decision about
  `Wiring`, not about this widget.
- **`SelectList` is public and in the wrong package**, which is a wart taken
  knowingly. `Option` was moved into a package of its own the day it had two
  callers, and this now has two; the CSS type it carries is `select-list`, so
  moving it means renaming a type in every stylesheet and every golden rather
  than editing one file. Filed rather than done.
- **A chip is a `badge` and is not the `badge` widget.** `controls.css` names both
  types in one rule so the metrics are stated once. It could not simply *be* a
  `Badge`, which is a leaf with text and no children, where a chip has to hold a
  × beside its label.
- **A chip's × is not focusable**, which is `TabClose`'s decision for
  `TabClose`'s reason: a `select` is one Tab stop, and a focusable × per chip
  would make a five-value select six stops where a document wrote one control.
  The keyboard's way to remove a value is to open the list and press `Enter` on
  it, which toggles.
