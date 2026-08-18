# ADR-0112: A menu follows the pointer, and lights for the keyboard

- **Status:** Accepted
- **Date:** 2026-08-18
- **Relates to:** `docs/core-widgets.md` §7 and §8, `docs/design-system.md` §2.2,
  corrects [ADR-0106](0106-a-menu-is-a-widget-and-opening-one-is-not.md)

## Context

Two faults in the same menu, reported together, and they are opposite halves of
one question — *what does the pointer being somewhere mean?*

1. **A submenu did not close when the pointer left the row that opened it.** It
   closed when another row with a submenu opened one, and never otherwise, so
   moving down a menu left a submenu hanging over the rows below it.
2. **The first row always looked hovered**, from the moment the menu opened.

And one cosmetic: the tooltip was too small to read comfortably.

## Decision

### Every row reports the pointer arriving, not only the ones with children

[ADR-0106](0106-a-menu-is-a-widget-and-opening-one-is-not.md) gave an item an
`onOpenSubmenu` and handed it only to rows that had one. That is the wrong half of
the relationship: **a submenu is closed by the pointer moving to a sibling**, and
most siblings have no submenu of their own.

So every row is handed `onHovered`, and the menu decides what arriving means:

- a row with a submenu opens it,
- a row without one **collapses** whatever this menu had open.

Both go through the one hover-intent timer, because they are one gesture:
travelling down a menu past three rows with submenus opens none of them, and
arriving on a plain row puts away what the row above had opened.

The renaming is the point. `onOpenSubmenu` described what the *caller* wanted;
`onHovered` describes what the item knows. An item cannot know whether the pointer
arriving should open something, close something or do nothing — that is a fact
about the menu it is in.

### Focus and the highlight are two things

`Popup` focuses a menu's first row as it opens, so that an arrow key has somewhere
to start. It did so through `moveFocus(1)`, which reports the move as the
**keyboard's** — and `controls.css` lit `item:focus`. So every menu opened with a
pointer had its first row picked out, which reads as a menu that has already
chosen.

Two changes, and each is wrong without the other:

- `PointerRouter.moveFocus(direction, fromKeyboard)` — the one caller that moves
  focus with nobody pressing anything says so.
- `item:focus-visible` rather than `item:focus` — the highlight is the keyboard's
  affordance, which is what `:focus-visible` has meant since §2.2 defined it.

The result is what every desktop menu does: open it with the mouse and nothing is
picked out; press `Down` and the row it lands on lights up.

### A tooltip is read at arm's length

`caption` is §1.4's size for secondary text *under* a control, where the reader
has the control itself for context. A tooltip is the only text on screen at the
moment it is read, and 11px of it is a squint. It takes `body` now, with 8px and
12px of padding rather than 6 and 10.

## Alternatives considered

- **Closing a submenu on the item's own pointer-exit.** The obvious fix, and it
  flickers: the pointer leaves the row on its way *into* the submenu, which is a
  window of its own and reports nothing to the row it came from. Every menu that
  does this has a "safe triangle" heuristic to compensate; arriving on a sibling
  needs none.
- **Closing it immediately, without the intent delay.** Travelling down a menu
  would open and close each submenu in turn, which is the flicker the delay exists
  to prevent — in the other direction.
- **Not focusing the first row at all** until an arrow is pressed. Then the first
  `Down` has to mean "focus the first row" rather than "move to the next", and the
  row it lands on depends on which of the two it was.
- **Keeping `item:focus` and having the launcher clear focus on a pointer move.**
  It makes focus follow the pointer, so `Enter` would run whatever the mouse last
  passed over.

## Consequences

- **A keyboard `Right` into a submenu still waits 150 ms**, because it goes
  through the same intent path as a hover. Recorded when the timer was written and
  still true.
- **`Left` does not close a submenu.** The arrow that opens one has no opposite
  yet: it needs a callback from the item to the *popup it is in*, which is one
  more thing `Menus` would have to wire.
- **A tooltip is bigger than a menu row's text**, which is deliberate and worth
  watching: the two are read in different circumstances, and if it starts to look
  heavy the answer is `--gb-font-tooltip` rather than going back to `caption`.
