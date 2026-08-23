# 180. The keyboard goes back where it was

Date: 2026-08-23

## Status

Accepted. Builds §7's "restores focus on close" for the modal case, and fixes the
bug underneath it. Answers one entry in `book/src/TODO.md` and corrects two more.

## Context

`docs/core-widgets.md` §7 says each overlay "wraps a `focus-scope` and restores
focus on close". Nothing did, and three TODO entries said so: a modal, a menu and
a `select` each failed to give the keyboard back.

Looking at the modal case first turned up something the entry did not describe.
`Element.unmount()` tells the element tree — `tree.forget(this)` — and tells
nothing else. The router is not a listener. So when a dialog closed:

```
after the modal closes:            x
  is that element still mounted?   false
```

The router went on **holding** the element that had left the tree. Not "focus was
not restored": focus was pointing at something that no longer existed, receiving
key events, and keeping its whole dead subtree reachable. The missing restoration
was the visible half of a stale pointer.

## Decision

### The router never holds an element that is not in the tree

`PointerRouter.refocus()`, called once a frame from `updateRegions` — the hook
that already runs after every paint and already carries `notifyMeasured` and
`notifyLocated`. If the focused element has left the tree, the router lets go of
it.

This is right well beyond dialogs, and that is the argument for doing it as its
own rule rather than as part of the restoration: a tab that switched, a list that
shortened and a dialog that closed all strand the same pointer, and none of them
has anything to do with modality.

Public rather than private, unlike its two neighbours, because the question is
about the **element tree** rather than about the frame that was painted: a test
that closes a dialog without drawing anything still needs the answer, and passing
an empty region list to get it would throw the hit-test snapshot away.

Being a frame late costs nothing. Nothing can press a key between a tree flushing
and the frame it produces, which is
[ADR-0117](0117-a-widget-may-be-told-what-it-measured.md)'s argument for
`Measured` in a second place.

### One slot, written at one moment

`restoreTo` is the first state the focus trap has held, and `book/src/TODO.md`
was right to flag that as the cost. Everything else about the trap is a question
about the tree asked fresh — `deepestModal` walks it on every focus change, which
is exactly why a dialog opened from inside a dialog gives the first one back for
nothing. A remembered element cannot be derived that way: **what had focus before
a modal opened is a fact about the past**, and the tree does not record it.

So it is kept as small as it can be:

- **One slot**, not a stack. A nested modal closing keeps the answer rather than
  spending it, so the outermost answer wins — which is the one the user will
  still be looking at when everything has closed.
- **Written at exactly one moment**: the branch in `focus()` where the trap takes
  the keyboard off something outside the modal. Focus moving *within* a modal
  never overwrites where it came from.
- **Allowed to go stale on purpose.** `refocus` drops it the moment what it
  points at leaves the tree, rather than anything having to keep it true. A
  dialog opened from a row that the dialog's own action then deletes is the case,
  and it is ordinary rather than exotic.

The `fromKeyboard` flag is remembered with it. §7.2 keeps `:focus` and
`:focus-visible` distinct, so giving the keyboard back has to give back the state
it was in: a dialog dismissed with `Escape` leaves the ring where the user last
saw it, and one dismissed with the mouse does not make one appear.

### Nothing to go back to means letting go

A dialog opened from a menu command, or on a window's first frame, had no
previously focused element. There is nothing to restore, and the router clears
focus rather than holding a corpse — which is what a press on empty space already
does, and is legal with a modal up because a null focus is reachable from
anywhere.

## Consequences

- **The two popup entries were wrong about the cause, and are corrected rather
  than closed.** A probe through the real launcher — a focusable widget that logs
  every focus change, a menu opened over it and closed — produced no focus loss
  at all:

  ```
  focusing the opener from the keyboard:
    opener GAINED focus (keyboard)
  opening a menu:
  closing it:
  done.
  ```

  A popup gets its own tree and its own router; nothing in the open or close path
  touches the owner's. So the owner keeps its focus throughout, and there is
  nothing at the router level to restore. What may still be missing is
  **platform** keyboard focus — SDL gives a `POPUP_MENU` window focus on some
  drivers and not others — and the headless backend cannot show that. The entries
  stay open, saying that instead of what they said before.
- **`refocus` is the router's fourth per-frame job**, and the only one that can
  change focus. That is worth knowing when reading `updateRegions`: a frame can
  now move the keyboard, where before only input could.
- **`hovered`, `pressed` and `captured` are not swept.** They can go stale the
  same way and none of them has a demonstrated bug: `hovered` is recomputed from
  the regions on the next pointer move, and the other two are cleared on release.
  Left alone deliberately rather than overlooked — the sweep would be three lines
  and no test could justify them yet.
