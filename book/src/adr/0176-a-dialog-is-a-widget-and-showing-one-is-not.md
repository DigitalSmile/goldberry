# 176. A dialog is a widget, and showing one is not

Date: 2026-08-23

## Status

Accepted. Builds `docs/core-widgets.md` §7's `dialog`, and the two mechanisms it
needed that did not exist: a focus trap, and a way to focus something by name.

## Context

§7: "modal: scrim over the window, focus trap, `Esc` = cancel-role button,
`Enter` = default-role button; **platform button order** … applied by the
dialog's action bar automatically. Sizes to content with min/max."

`dialog` is the largest name left in M3, and it was blocked rather than merely
unbuilt. TODO.md had three separate entries waiting on the same missing thing:
"`Host.focus` still does not exist … Something that wants to focus a control
from a handler — a dialog putting the caret in its first field, a form jumping
to its first error — still cannot."

## Decision

### A dialog is a widget, and showing one is not

[ADR-0106](0106-a-menu-is-a-widget-and-opening-one-is-not.md)'s title, one group
later, and the argument is unchanged. A modal needs the **window** — something
has to cover it, dim it and take its pointer — and a widget has no window. So
`Dialog` describes one and `Dialogs.show(host, dialog)` puts it on a `Host`,
which is `Menus.open`'s split exactly.

It is not written into an application's own tree either, and that is the second
half of the same point: a dialog written inline would be laid out where it was
written, and a modal is not somewhere in a column.

### Modality is geometry for the pointer and a declaration for the keyboard

The scrim is a **filling** `Overlay`, and that is the whole of the pointer's
modality: a filling overlay takes every press wherever it draws, which `tour`'s
veil discovered ([ADR-0121](0121-a-tour-is-a-veil-and-a-sequence.md)) and which
nothing had to be added for.

The keyboard has no position, so geometry cannot answer it. `Handles.isModal()`
is the half that has to be said out loud, and the router reads it:

> While something modal is mounted, the focused node is inside it.

That is one sentence and one method. `traversalRoot()` returns the deepest modal
in the tree, so Tab enumerates the dialog instead of the window; and `focus()`
redirects any request that lands outside it to the first thing inside. Enforcing
it **in `focus()`** rather than at each of the routes that move focus is the
decision worth writing down — the routes are Tab, a press, a roving arrow, a
control focusing itself and whatever asks next, and a trap that covered four of
five would be no trap.

Nothing is registered when a dialog opens, so nothing has to be unregistered.
The answer is recomputed from the tree, which means a dialog removed by *any*
route gives the keyboard back — including one removed while it was closing, and
one removed by an application that never heard of the trap.

Two modals resolve **topmost-first**, scanning children in reverse: two dialogs
are two overlays on one window and the later one draws on top, so walking
forwards would hand the keyboard to the one underneath.

### `Host.focus(id)` resolves a container to the first thing in it

The programmatic door three TODO entries were waiting on, and it takes an **id**
for `Host.anchor`'s reason: a widget has no element and never will, and an id is
the one name a description and a tree agree on. It is also a name a *document*
can write, so it works for a KDL screen.

The rule that makes it useful is the fallback: a node that cannot take focus
resolves to the **first focusable thing inside it**. A dialog's panel is not
focusable — a panel that were a Tab stop would be a stop with nothing to do on
it — so without that rule the one caller that most needs this method could not
use it. "Focus this dialog" and "focus this form" now mean what a caller
intends.

It is refused for anything outside an open modal. A trap that a stray call could
step around is a trap with a hole in it.

### The roles are values, and the order is the theme's

§7 asks for two things a plain row of buttons cannot give: `Esc` and `Enter`
press *particular* buttons, and the bar orders itself by platform. Both need the
dialog to know which button is which, so `DialogAction` carries a `Role` —
`AFFIRMATIVE`, `DISMISSIVE`, `NEUTRAL` — and the dialog refuses to build with two
of either, at construction, where every other document error is refused. Two
default buttons is a dialog where `Enter` is a coin toss.

The class follows from the role rather than from the author: an affirmative is
`button.primary` everywhere in an application, and nobody has to remember.

**The order is one CSS declaration.** The bar writes its buttons in a canonical
order — neutral, dismissive, affirmative — and a theme that wants Windows' order
writes `dialog-actions { flex-direction: row-reverse }`. That is what §7's
"theme-controlled" has to mean here: `children()` runs before style resolution
and long before anything has asked the platform anything, and a widget that read
the operating system to lay itself out would be a widget whose golden images
differ per machine.

### Both keys are on the bubble phase

`Handles.onKeyCapture`'s own doc comment says "where a dialog swallows Escape
before the thing inside it sees it", and this dialog does not do that. A control
inside a dialog that means something by a key keeps it by consuming it: `Enter`
in a `text-area` inserts a line, `Esc` in an open `select` closes the list. A
dialog that took either on capture would break the control it contains, and the
control is the reason the dialog is open.

A press on the scrim is the same event as `Esc` — both mean "the dismissive
one" — and a dialog with no dismissive action answers to neither, which is what
a question that must be answered wants.

### Closing runs before the application is told

`design-system.md` §1.7: an overlay runs `opening → open → closing → removed`,
"the element stays mounted through `closing`, **input is disabled the instant
closing starts** (no ghost clicks), removal fires on animation end".

Every route out — a button, `Esc`, the scrim — goes through one method that
starts the exit, stops taking input, and runs the application's handler when the
animation ends. So a handler that removes the overlay immediately still gets the
fade, and an application never writes a line about the animation.
[ADR-0175](0175-a-banner-says-its-kind-twice.md) reversed the same order for
`message` on the same day and for the same reason: nothing outside holds a
closing overlay, so it has to outlive its own dismissal itself.

## Consequences

- **§7 is one widget from done.** `toast` is what is left, and it inherits both
  halves of this: the fade-then-tell order, and a `Phase` per entry. What it adds
  is a queue, which is also what will let it solve the reflow neither this nor
  `message` can.
- **`min-width` and `max-width` are not in the CSS subset**, and §2 asks a dialog
  for both. Yoga has the setters and `Box` has no field for them, so this is a
  gap in the style engine rather than a decision about dialogs. What holds
  meanwhile: the scrim's padding is a de-facto maximum, and the minimum is
  genuinely missing — a dialog with three words in it is three words wide.
- **`isModal` has one consumer**, which is one fewer than a mechanism should
  have. A `wizard` step and a `sheet` are the plausible seconds. It is tested in
  `:core` against bare widgets rather than through `dialog`, so the second
  consumer finds a mechanism rather than a dialog-shaped hole.
- **`Host.focus` closes three TODO entries and opens none.** A form jumping to
  its first error is now two lines an application writes; nothing in the toolkit
  does it yet.
- **Nothing restores focus when a dialog closes.** Focus was somewhere before the
  dialog opened and lands nowhere in particular after — the trap releases and the
  focused element is simply gone. Every real toolkit puts it back where it was,
  and doing so means remembering the previously focused element across the
  dialog's life, which is a fourth thing the router would hold.
- **A dialog is not announced.** §7 asks for "dialog with labelled title" as
  semantics, which is the AccessKit bridge and M5's, like every other widget's.
