# 233. Escape steps out of one menu

Date: 2026-08-30

## Status

Accepted. Closes a `TODO.md` entry opened by
[ADR-0103](0103-a-popup-is-a-second-tree-in-a-second-window.md).

## Context

> **Two popups do not know about each other.** A submenu chain — opening one
> closes its siblings but not its parent — is `menu`'s to arrange; the launcher's
> light dismissal closes all of them at once, which is right for one popup and
> wrong for a chain.

The first half was already built: `Menus` keeps the stack and closes descendants.
The second half is one line, and finding *which* line took most of the work,
because there are two `Escape` handlers and only one of them ever runs.

A `Popup` watches its own window and closes **itself** on `Escape` — correct for
a chain, and dead code in practice. Since [ADR-0189](0189-no-popup-holds-the-keyboard.md)
no popup holds the platform keyboard, so `Escape` arrives at the **owner**
window, whose watcher called `dismissPopups()` — every popup, at once.

So opening `File → Recent` and pressing `Escape` closed both. The submenu the
reader opened by mistake took the menu with it, and there is nothing to reopen
that menu with but the mouse.

## Decision

**`Escape` closes the innermost popup; a press outside closes the stack.**

The two gestures mean different things and the code had been treating them as
one. A press that lands somewhere else is the user pointing at something other
than the menu — the whole thing goes. `Escape` is the user stepping back out of
what they opened, one menu at a time.

**The topmost one that will actually go**, which is not always the topmost one. A
tooltip is `lightDismiss(false)` and refuses; stopping at it would leave `Escape`
doing nothing with a menu open underneath. So `Popup.dismissedByInput()` returns
whether it closed, and the launcher walks down until something does.

**Focus loss still closes everything.** The application is no longer in front;
there is no chain to step out of.

## Alternatives considered

- **Relying on the popup's own `Escape` watcher** and deleting the owner's. It is
  the tidier shape and it depends on the platform giving a popup window the
  keyboard, which ADR-0104 established is per-driver and ADR-0189 decided against
  entirely.
- **Letting `menu` handle `Escape` as a widget.** `Menus` has the stack and could
  close one level. It also would not fire: the key is taken by the owner window's
  watcher before any router sees it, which is the whole reason the watcher exists.
- **Closing the topmost popup even when it refuses light dismissal.** It would
  make `Escape` dismiss a tooltip, which is dismissed by the pointer leaving, and
  leave the menu under it open — a keypress that does the wrong one of two
  visible things.
- **Leaving it.** A submenu that cannot be escaped without losing its parent is a
  small thing that is wrong every single time.

## Consequences

- **`Escape` in a chain now behaves like every desktop menu**: out of the
  submenu, then out of the menu.
- **`dismissedByInput` returns a boolean**, which is also what makes "the topmost
  one that will go" expressible at all.
- **`dismissPopups` is unchanged** and still has two callers — the press watcher
  and the focus-lost check — which is the distinction this record is about.
- **A test in `MenusTest`** through the real launcher and real popup windows:
  hover a row with children to open the chain, `Escape`, assert one popup left,
  `Escape`, assert none. It fails at the first assertion without the change —
  0 where 1 is expected.
- **`Popup`'s own `Escape` watcher is still there** and still unreachable in
  practice. Left rather than deleted: it is correct for a driver that *does* focus
  popup windows, and it closes only itself, which is now the same rule the owner's
  handler follows.
