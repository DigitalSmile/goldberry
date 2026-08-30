# 219. An item tells its menu what the keyboard did

Date: 2026-08-30

## Status

Accepted. Closes four `TODO.md` entries opened by
[ADR-0112](0112-a-menu-follows-the-pointer-and-lights-for-the-keyboard.md),
[ADR-0113](0113-a-submenu-is-placed-beside-its-menu.md) and
[ADR-0163](0163-a-menu-bar-owns-its-menus.md).

## Context

Four entries on `TODO.md`, filed against three different records, in different
words:

- **A keyboard `Right` into a submenu waits 150 ms**, because it went through the
  same hover-intent path as a pointer. "Wrong, and one line to fix once `Item` can
  tell a hover from a keypress."
- **`Left` does not close a submenu.** "The arrow that opens one has no opposite:
  it needs a callback from the item to the *popup it is in*."
- **`Left` and `Right` do not move between menus while one is showing.** "Once a
  menu is open the focus is in a **different window**, so the bar never sees the
  arrow — the same missing item-to-popup callback. Fixing either would probably
  fix both."
- **Nothing marks the row whose submenu is showing.** A row is `:focus-visible`
  when the keyboard is on it and `:hover` when the pointer is, and neither says
  "this is the branch that is open".

They are one missing sentence. An `Item` could tell the menu it is in exactly one
thing — `onHovered`, "the pointer arrived on me" — and every one of the four is
something else the row knows first and only the menu can act on.

## Decision

**`MenuSignals` replaces the single callback.** Four signals, each with a no-op
default, supplied by `Menus` and never by an author:

| signal | what happened | what a menu does with it |
|---|---|---|
| `hovered()` | the pointer arrived | open this row's submenu after §8's hover-intent delay, or put away what is showing |
| `open()` | `Right` or `Enter` on a row with children | open it **now** |
| `back()` | `Left` | close this submenu, or move to the menu on the bar's left |
| `forward()` | `Right` on a row with no children | move to the menu on the bar's right |

**The signals are what happened, not what to do.** `hovered()` does not mean
"open" — a row with no submenu sends it too, because that is how the menu knows
to collapse. The names describe the row's event; the menu decides the meaning.
This is why `Left` can mean two different things without the item knowing either
of them.

**A delay is for a pointer.** §8's hover-intent stops a submenu dropping out of a
pointer travelling past three rows on its way somewhere; a keypress has travelled
past nothing. `open()` cancels the pending timer and opens in the same frame.

**`Left` is the arrow that opened this menu, undone.** In a submenu it closes back
to the parent; at the root of a bar's menu it moves along the bar; at the root of a
*context* menu it does nothing, deliberately — a menu that vanished on an arrow
key would be a menu nobody could navigate.

**A bar's two arrows are the bar's, and it says so.** `Menus.Siblings(previous,
next)` is passed only by `MenuBarState`, only for the **root** menu of a heading.
A submenu gets none, which is what makes `Left` in one go back one level rather
than leaping to the next menu on the bar. The bar wraps at the ends and skips
headings that cannot open — a separator between two groups is not a menu, and
neither is a disabled one.

**A menu is an object now, not a bag of parameters.** `Menus.OpenMenu` holds the
popup, the stack, the parent menu and which row is open; `prepare` is a method on
it. Three of the four fixes need state that lived nowhere: which row's branch is
showing, and which menu is above this one.

**The open row is marked with a class.** `item.open`, the same shape
`menu-title.open` already uses for the heading whose menu is down — "the branch
that is showing" is not one of CSS's states, and inventing a pseudo-class would
put a menu's internals in the selector engine. The menu re-describes itself
through `Popup.content`, which reconciles from the root, so the keyboard keeps its
place and nothing flickers.

## Alternatives considered

- **A `boolean fromKeyboard` on the existing callback.** It fixes the 150 ms and
  none of the other three, and it makes the item's one signal mean two things
  depending on a flag.
- **Letting `Item` open its own submenu.** It needs a `Host` and a `Popup`, and a
  widget is a value — ADR-0106's whole argument, unchanged.
- **An `:open` pseudo-class.** Every pseudo-class in the subset is a state the
  element tree tracks for *any* widget; this one is a fact about a menu's own
  bookkeeping. `select` and `menu-title` already settled the precedent with a
  class.
- **Marking only the row the keyboard is on.** That is `:focus-visible`, and it is
  a different question: with the pointer in the submenu, the parent row is neither
  hovered nor focused and is still the branch that is open.
- **Closing the whole stack on `Left` at the root of a context menu**, so the key
  always does something. It makes an arrow key destructive, which no menu on any
  desktop does — `Escape` is the key that means "put this away".

## Consequences

- **Four `TODO.md` entries close together**, which is what the entries predicted:
  "fixing either would probably fix both" was right about all four.
- **`Item`'s `onHovered` component is now `signals`**, typed `MenuSignals` and
  never null. The accessor was only ever read by `Menus`, and `hovering(Runnable)`
  becomes `signalling(MenuSignals)`.
- **`Menus.open` has a fifth-argument overload** taking `Siblings`. Everything
  that is not a menu bar passes four arguments and behaves exactly as before.
- **Five tests, four of which fail against the old code**: the timing one asserts
  the submenu is open 100 ms after the key, inside the delay the old path would
  still have been waiting out; the mark is read in **pixels** off the parent
  popup's last frame, with the pointer moved out of that window first so that the
  only thing left on the row is the mark.
- **Which menu is showing is asserted by its height** — a three-row `File` against
  a one-row `Edit` — because a popup's content is in another window and a test can
  see its size but not its tree.
- **What is still open in this area**: a bare `Alt` tap does not activate the bar
  (`F10` does), and an accelerator is still unbound by key rather than by owner.
  Neither is about this callback.
