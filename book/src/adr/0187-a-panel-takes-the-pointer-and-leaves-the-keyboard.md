# 187. A panel takes the pointer and leaves the keyboard

Date: 2026-08-23

## Status

Accepted. Fixes what [ADR-0186](0186-a-panel-that-hangs-off-a-field-is-not-a-menu.md)
got wrong and what it missed.

## Context

ADR-0186 stopped a suggestion panel stealing the keyboard by opening it as a
`TOOLTIP`-kind window — `NOT_FOCUSABLE` at the platform level. It worked, and it
made the panel **unusable**: a value could no longer be picked with the mouse or
the arrows.

That is the tooltip flag doing exactly what it says. `PopupKind.TOOLTIP` is for
something "shown, read and never interacted with", so platforms give it no input
at all. Borrowing it bought the focus behaviour and paid with every click.

And a `tree` in a popup still would not grow when a branch expanded, which
ADR-0186 also claimed to fix.

## Decision

### A third kind, because there were always three things

`PopupKind.ATTACHED`: `POPUP_MENU` **and** `NOT_FOCUSABLE`. A menu window in
every respect the window manager cares about — it takes the pointer, it is placed
and shadowed like a menu, it is in no window list — and unfocusable, so the
keyboard stays on the field it hangs off.

The two flags were always independent; only the enum forced a choice between
them. `MENU` is "the user is acting on this **instead**", `TOOLTIP` is "the user
is only reading this", and `ATTACHED` is "the user is acting on this **as well
as** the thing it hangs off" — which is what a combobox's list is, and which is
the case neither of the first two describes.

### A popup measures itself after **its own** tree rebuilds

ADR-0186 put the re-measure in `Popup.content`, which is the door the *opening
widget* pushes something new through. A `tree` expanding a branch does not go
through that door at all: it is a `setState` in the popup's **own** element tree,
which flushes inside `Popup.paint` and never reaches the widget that opened the
popup.

So the measure belongs where the flush is. `paint` re-measures whenever the tree
actually needed a build, which is also what stops it looping — a settled popup
measures nothing.

The general shape is worth naming: a popup has **two** sources of change, one
from outside and one from within, and a fix applied to one of them looks complete
until the other happens.

### `flex-wrap` is not in the subset, so the stylesheet stops writing it

`select.multiple` asked for `flex-wrap: wrap` and got "ignoring unsupported
property" once per frame and no wrapping. Yoga has the setter bound and `Box` has
no field for it — the same gap `min-width` was in before
[ADR-0181](0181-a-box-may-say-how-small-and-how-large.md), and the same fix would
close it.

Not taken here: this is a defect pass, and adding a layout property to the subset
is its own change with its own churn across `Box`, `ComputedStyle` and every
positional copy in both. The chips shrink instead, and a field with more than it
can show shows fewer of them whole. Filed.

## Consequences

- **Three focus-shaped mistakes in three ADRs**, and each was a real distinction
  the code already drew and I had not read: the router's focus versus the
  window's, and now the pointer versus the keyboard. `PopupKind`'s own
  documentation described all of it before any of these were written.
- **Still no test for any of it.** The window flags and the resize both live
  outside what the headless backend can reach. This is the fourth defect round
  found by running the application, and the harness `MenusTest` has for menus —
  real launcher, headless backend, posted events — remains the thing that would
  have caught all of them. It is now the single most valuable piece of undone
  work in this area, and it has been filed three times.
- **The popup still hangs when the application loses focus**, unchanged from
  ADR-0186 and for the reason recorded there. An `ATTACHED` popup is
  `NOT_FOCUSABLE`, so it cannot be what keeps `anyWindowFocused` true; a `MENU`
  one can.
