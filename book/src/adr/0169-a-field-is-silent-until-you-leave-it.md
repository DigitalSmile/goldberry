# 169. A field is silent until you leave it, and a form is found by its fields

Date: 2026-08-21

## Status

Accepted. Opens the rest of `docs/core-widgets.md` §4, adds one pseudo-class the
specification asked for, one notification to `Handles`, and closes an open gap in
[ADR-0165](0165-a-divider-translates-and-a-rotation-has-three-brakes.md).

## Context

`text-input` ([ADR-0167](0167-a-field-owns-its-caret-and-the-model-is-told.md))
is a control. §4's `field` and `form` are the contract around it — the label, the
required marker, the message slot, and the gate a submission passes through.

Three things had to be decided before any of that could be built, and one thing
did not exist.

**When does a field validate?** §4 says "on blur and on submit". Nothing told a
container that focus had left its subtree: `Handles.onFocusChanged` is about the
node itself, and a `field` is not the node that takes the keyboard.

**How does a field learn the value?** A validator is over a value, and a field
contains a control rather than owning one.

**How does a form find its fields?** They are anywhere in its subtree — inside
rows, inside cards, inside a `collapse`.

## Decision

### `:focus-within`, as a notification

`Handles.onFocusWithin(within, fromKeyboard)`. The router walks the chain of the
element that lost focus and the one that gained it, and tells **only the
difference**.

That last part is the whole design. Focus moving between two controls inside one
`field` leaves that field's subtree focused throughout, and a field told "left"
and then "entered" would validate on a move that never crossed its boundary. So
an ancestor of both is told nothing — which means a field holding one control and
a field holding three behave identically, and neither has to filter anything out.

It is a **second** method rather than a change to `onFocusChanged`, because the
two are different questions and a control that wants both should get both. A
focused node is inside its own subtree and is told twice; `:focus` and
`:focus-within` are both true of a focused node in CSS for exactly that reason.

**It had two consumers before it was written**, which is what made it a
mechanism rather than a guess. The second is `carousel`: ADR-0165 shipped two of
§5's three brakes and recorded the third as a real gap — "focus on a widget
*inside a slide* does not pause it, because the cascade has no `:focus-within`
and nothing tells a widget that focus landed in its subtree". That is now one
line, and somebody who has tabbed into a slide is exactly somebody reading it.

### Silent until blur, live from then on

A field says nothing until the user has finished with it once — however wrong its
value is. A form that reddens an empty screen is a form that has been ignored
before it was read.

After it has complained once, it re-checks on **every change**, so the message
goes away the instant the value is fixed. That asymmetry is in no specification
and every good form has it: a field that validated as you typed would call an
email address invalid after the first letter and stay red until the last; a field
that waited for a second blur to forgive you is one you have to leave and come
back to.

Submitting is the third moment, and the only one that makes an unvisited field
speak — otherwise a form submits with an untouched required field empty.

### A field reads its control's binding

A field learns its value from `Widget.binding()` — the `bind=` the control inside
it already reads. Nothing is written twice, and there is no new channel from a
control to its field.

It walks its children **one level**, deliberately: walking the subtree would find
a binding on something incidental — a `text` in a hint under the control — and
validate that instead.

A field with no bound control **validates nothing**, `required` included. That
reads like a trap and the alternative is worse: failing forever gates a form on a
control somebody can type into and never satisfy. It is the same shape as a
`menu` that is only ever opened registering no accelerators, because nothing is
holding it ([ADR-0163](0163-a-menu-bar-owns-its-menus.md)).

### The fields find the form

Each field registers with the nearest enclosing form through
`BuildContext.findAncestorState`, which has been on that interface since the
element tree was built and **this is its first consumer**. `TabsState` looked at
it and said it "looks the wrong way" — right for tabs, where a strip has to
enumerate its panels, and exactly right here: a field knows one form, a form knows
however many fields a document wrote, and looking up needs no subtree walk and no
knowledge of what to skip.

A `LinkedHashSet`, so a field that rebuilds does not register twice and the order
still holds — which makes the error summary read top to bottom.

### `:invalid` is a pseudo-class, because the specification asks for one

`docs/core-widgets.md` §1 lists the states and adds: "plus `:invalid` for form
controls — **an addition to the CSS engine's pseudo set**". So it is one, rather
than the `.invalid` class `select.open` settled for — that one was a class
because §8's subset had no pseudo-class meaning "expanded" and inventing one for
a single widget would be inventing a language. Here the language already says it.

It sits on the field **and** on the control, and both are wanted: a stylesheet
asks for `text-input:invalid` to redden a border and for `field:invalid` to reach
the message under it, and no selector in §8's subset walks from a child back up
to a parent.

A field is invalid exactly when it has something to say. There is no second flag,
so a message and a failure cannot disagree.

### A validator returns a message, not a boolean

The reason is the point: a field that goes red without saying why is a field
somebody has to guess at. `Result.invalid("")` throws for the same reason.

`and` reports the **first** failure rather than all of them, because a message
slot is one line and three complaints about one value is a worse message than the
first one. It also short-circuits, which matters as soon as a rule parses a date.

A pattern validator lets an **empty** value through. "This must look like an email
address" and "this must be filled in" are two rules, and a pattern that also
refused emptiness would turn every optional field with a format into a required
one.

### What `submit` carries, and what it does not

§4 says `form.submit()` "raises a typed event with bound values". The event here
carries **nothing**, and this is a deliberate departure.

A `bind=` reads *from* the application's model, so the bound values are already
the application's — an event carrying them would hand an application its own data
back. The toolkit could not name them anyway: `Widget.binding()` is an
`Observable` and not a path, which is precisely what makes `bind=` a read-only
channel ([ADR-0063](0063-data-flows-down-events-flow-up.md)).

### A form is submitted through a controller

`FormController`, the arrangement
[`ScrollController`](0116-a-scroll-view-is-a-clip-an-offset-and-two-extents.md)
already uses, and here for its reason: what submits a form is by definition
somewhere else. A button *inside* the form could find it the way a field does; a
button in a dialog's action bar or a toolbar could not, and that is where Save
usually is.

A detached controller reports `isValid() == true` rather than false — a form that
does not exist has nothing wrong with it, and a Save button that disabled itself
waiting for one would be disabled on the first frame of every window.

`isValid()` is **side-effect free**, and separate from `check()` for that reason:
a form that reddened every field to work out whether to enable a button would
redden them before anyone had typed a character.

### Stacked labels are the default; the column is the class

§4 words it the other way — "consistent label column (or stacked labels via
class)". The column is the one that needs a *number*: every field's label has to
be the same width, and §8's subset cannot say "as wide as the widest of these".
So a default label column would be a default that is wrong until it is
configured. `field.horizontal` and `--gb-field-label-width` are what a form that
wants one writes.

## Consequences

- **`carousel` has its third brake**, and the entry ADR-0165 left open closes.
- **`findAncestorState` has a consumer**, and its shape is confirmed by the one
  case that fits it rather than by the case that did not.
- **`TestHost.after` no longer throws.** It could not survive `text-input`: a
  focused field blinks, so every test of anything containing one would have had to
  know about carets. It records the timer and hands back one that is never due.
- **Markup cannot hand a controller to a widget**, and `form` is the second
  widget to want to — `scroll` was the first. The showcase's form therefore has
  no Save button, and demonstrates the half a document *can* express: the marker,
  the message, and blur. That absence is a real report and is in TODO.md.
- **A `Validator` is over a `String`.** What a user typed is text until something
  parses it, and a validator is exactly the thing that decides whether it can be.
  A `date-picker` will want `Validator<LocalDate>` over its parsed value, which is
  a second seam and not a change to this one.
- **`text-area`, the pickers and `code-input` inherit all of it** — a `field`
  validates whatever is inside it, and the only thing it asks of a control is a
  binding.
