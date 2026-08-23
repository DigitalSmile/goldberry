# 177. A toast is a queue, and the stack is the widget

Date: 2026-08-23

## Status

Accepted. Builds `docs/core-widgets.md` §7's `toast`, which closes the overlay
group.

## Context

§7: "non-modal notifications: queued, timeout with hover-pause, optional action
button, stacking corner configurable; announced via semantics (live region)".

Every one of those is a behaviour **over time**, which is what makes a toast a
different kind of thing from everything else in §7. A `message` is a description
an author writes where it goes. A `dialog` is opened, answered and removed by an
application that knows exactly when each of those happens. A toast is raised by
something that has no idea what else is on the screen, and everything
interesting about it happens afterwards without anybody watching.

## Decision

### The value is not a widget

A `Toast` is a record — text, an optional action, a timeout — and there is no
`toast` node an author can write. §7 draws the line itself: a message is "part
of the layout … about the thing next to it", so an author writes one where it
goes; a toast is "transient, floats over the window … about something that just
happened", so nobody writes one anywhere.

That is also why there is no `@Markup("toast")`. A document is a description of
a screen; a toast is a thing that happened, and a screen that described one
would describe it again on every reload.

### The stack is the widget, and it owns the queue

One `Toaster` per window, put in the window's own corner overlay layer by
`Toasts.at` — the layer `hud` has occupied since ADR-0100. An application holds
a `ToastController` and raises values through it, which is
[`ScrollController`](0120-a-widget-scrolls-itself-into-view.md)'s and
`FormController`'s arrangement and here for the sharpest version of their
reason: **whatever raises a toast is by definition somewhere else.** A save
handler deep in a view model has no widget tree, no `Host`, and nothing it could
reasonably be given — what it has is a field.

Holding the list is what lets the stack do the two things a lone banner could
not, and both were filed as gaps against
[ADR-0175](0175-a-banner-says-its-kind-twice.md):

- **keep a toast alive past its own dismissal**, so it fades out with nothing
  outside it having to know; and
- **know what its siblings are**, which is what §3's "siblings reflow via
  `translate`" needs and what nothing else in the catalog is in a position to
  do.

The first is built here. The second is not — see the consequences.

### "Queued" means a cap, and three is a judgement

§7 says "queued" and does not say how many. Four notifications stacked in a
corner is a wall of text nobody reads; one at a time makes a burst take half a
minute to get through. Three is the default and the number is on the widget, so
an application that disagrees says so once.

A toast on its way out has **given up its place**, so the next one comes forward
while the old one is still fading and the stack briefly holds four. The
alternative — waiting for the exit to finish — makes a burst of notifications
stutter, and the overlap is 160ms.

### Every clock here is the frame clock

`Host.after` gives a timer and no way to ask how much of it has run. A pause that
resumes therefore needs to know what time it is, and the only clock a widget has
is the one `render` is handed — so the stack node reports `nowMillis` on every
frame and the state remembers the last reading. `carousel` reads the motion
preference the same way and for the same reason.

That is what makes §7's hover-pause a **pause** rather than a restart. A toast
you glanced at for two seconds gets its remaining three back, not another five.
Restarting would be one line shorter and is a different promise.

### The corner decides three things, because they are one decision

Where the stack sits, which edge a new toast slides in from, and which end of the
column is the newest — a stack at the bottom grows upwards and one at the top
grows down. All three come off `Corner`, and the third is a **CSS rule**: the
node describes its children oldest-first and `toaster.top-start` is
`column-reverse`. A widget that reversed the list would have to reverse it again
for the keyboard.

### The action button takes the toast with it

Pressing "Undo" runs the handler **and** starts the exit, which is `Menus`' rule
for a menu command: choosing what a thing offered is finishing with the thing.
An application never writes the dismissal, and a toast that stayed after its one
button had been pressed would be waiting for a second answer it has no way to
take.

## Consequences

- **§7 is done.** `tooltip`, `popover`, `tour`, `hud`, `message`, `dialog` and
  `toast` — the whole overlay group, and both places an overlay can go.
- **The sibling reflow is still not built**, and this is the widget that finally
  *could* build it. §3 asks for "siblings reflow via `translate`, base (explicit
  controller — the one sanctioned movement effect)": when a toast in the middle
  goes, the ones above it should travel to their new places rather than jump.
  What it needs is the departing toast's **height**, which the stack can have —
  `Host.anchor(id)` returns the painted rectangle of a node, so the shift is one
  lookup and a `Phase` per surviving sibling. It is a whole mechanism rather than
  a detail, and it is the last thing §3 asks of this group.
- **A toast is not announced.** §7's "live region" is the AccessKit bridge and
  M5's, like every other widget's semantics — and it is the one widget in the
  catalog where the absence really costs something, because a notification
  nobody sees is the case a live region exists for.
- **A toast has no kind**, which is §7's omission followed rather than an
  oversight. A message has four because it has to be told apart from three other
  things it might be saying; there is only ever one toast saying what just
  happened. An application that wants a red one has to say so in words.
- **360 is a width and not a maximum**, because the subset has no `max-width` —
  the gap ADR-0176 filed. Here it is nearly a virtue: three toasts of one width
  read as a stack where three sized to their contents read as a pile. It stops
  being a virtue for a one-word toast.
- **Nothing dismisses a toast by clicking it.** §7 gives it an action button and
  no ×, so a toast with `Duration.ZERO` and no action can only be removed by
  `clear()`. That is the specification's shape and it is worth knowing before
  somebody ships a notification nobody can get rid of.
