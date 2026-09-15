# 319. A panel is not a menu

Date: 2026-09-15

## Status

Accepted. Closes `docs/gaps.md` G29. Narrows
[ADR-0104](0104-a-popup-is-measured-then-placed.md)'s forwarding
rule, and finishes what [ADR-0185](0185-a-list-that-hangs-off-a-field-does-not-take-the-keyboard.md)
started with `takesFocus(false)`.

## Context

ADR-0104 says the owner window forwards keys to whatever popup is open, and the
`Launcher`'s watcher spells out why:

> while a menu is open the keyboard belongs to it, whether or not the platform
> moved focus there — otherwise an arrow would move the selection in the window
> underneath the menu

That is right for a **menu**, which is up for as long as the user is choosing from
it and goes away as soon as they have. A *panel* is the other shape: a bar of
buttons floating over a canvas, positioned by number because it points at a
selection, and open the whole time something is selected — over a canvas somebody
is typing into. Every key it takes is a key the canvas did not get, and
`Popup.takesFocus` defaults to true, so it focused its own first control on opening
and a focused `Button` consumed `Enter`. Pressing `Enter` pressed a swatch instead
of breaking a line in the text underneath.

ADR-0185 had already met half of this, from the other side: a suggestion list under
a combobox must not focus its first row, because the field is what is being typed
into. `takesFocus(false)` is that fix, and it settles **only the opening**. It
stops `focusFirst` and nothing else, so:

- the owner still forwards every key to the popup, and
- a press inside the popup still focuses what it landed on, through the popup
  router's own `focusFromPress` — so clicking a swatch and then pressing `Enter`
  pressed that swatch again.

An application cannot patch around it. The forwarding happens in the launcher's
watcher, `Window.inputWatcher` is package-private, there is one per window, and an
application cannot decline a key on a popup's behalf because it never sees the key.

## Decision

**One flag, because "does this thing want keys at all" is one question.**

```java
host.attachedPopup(content, anchor, placement, minimumWidth, fit).keyboard(false);
```

`Popup.keyboard(false)` does three things, and it is the set of them that makes it
a *panel* rather than a menu:

1. **Nothing is focused when it opens** — `focusFirst` returns, as under
   `takesFocus(false)`.
2. **A press inside it focuses nothing.** The popup tells its own router
   `pressFocuses(false)`, and `PointerRouter.focusFromPress` returns early. This is
   the half `takesFocus` could not reach, because the press arrives at the popup's
   window and never passes through the `Popup` object.
3. **The owner does not forward keys to it.** `Popup.handleKey` declines
   immediately, and the launcher looks for the topmost popup that *wants* the
   keyboard rather than the topmost popup.

`topmostKeyboardPopup()` is a second question rather than a change to the first,
and the difference is deliberate: a press outside dismisses whatever is topmost,
panel or not, while a key belongs to the topmost thing that asked for one. A panel
floating over a menu therefore leaves the menu operable by arrows — which is what
"a panel is not in the keyboard's way" has to mean if it means anything.

**`Escape` is not this flag's business.** A panel that may be dismissed by input
still is: that is `lightDismiss`, and a panel that must survive a keystroke says so
there — in which case the launcher's `Escape` branch finds nothing to dismiss and
the key reaches the window. Declining keys and refusing to close are different
promises, and one flag for both would make the wrong pair inexpressible.

`PointerRouter.pressFocuses` is the press and nothing else. Traversal, `focus` and
`focusById` still do what they are told: a caller that asked for focus by name has
said what it wants, and a router that quietly refused would be a second rule to
discover.

## Consequences

`takesFocus(false)` keeps its meaning and its consumer. A suggestion list *does*
want keys — the arrows move through it while the field keeps the text — so the two
flags are not a ladder with one useful rung: `takesFocus` is about the opening and
`keyboard` is about the whole lifetime.

A panel that the platform gives real keyboard focus to is still a theoretical hole:
the key arrives at the popup's own window, and a window cannot hand a key back to
its owner. In practice `attachedPopup` opens `NOT_FOCUSABLE` windows
([ADR-0186](0186-a-panel-that-hangs-off-a-field-is-not-a-menu.md)), which is precisely
the kind a window manager will not focus, and the panel's router now dispatches
nothing on a press either.

## Alternatives considered

**Two flags — one for the forwarding, one for the press.** What the gap asked not
to have, in as many words: *"one flag rather than two, since 'does this thing want
keys at all' is one question"*. Two would also make it possible to set half of it,
and half of it is the bug.

**Let an application install its own input watcher.** It would mean exporting
`Window.inputWatcher`, which is one slot per window that the launcher owns; a second
installer would silently win or silently lose depending on order.

**Give the popup kind the answer** — a `PopupKind.PANEL` beside `MENU`,
`ATTACHED` and `TOOLTIP`. The kind is the *window* the platform makes, and it
already decides focusability at that level. Keyboard forwarding is a toolkit rule
above it, and tying them together would mean a panel could not be a tooltip-kind
window or vice versa.
