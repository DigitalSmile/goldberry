# ADR-0637: A popup is styled from the widget that opened it

- **Status:** Accepted
- **Date:** 2026-10-18
- **Amends:** [ADR-0103](0103-a-popup-is-a-second-tree-in-a-second-window.md)

## Context

ADR-0103 made a popup's contents the root of a second element tree in a second
window. Nothing inherits into that root, and no selector reaches it from the
window that opened it. `TODO.md` had carried that as an open entry. It first
proposed handing a tooltip its anchor's style, which was wrong: a tooltip pins
its typography (ADR-0263). What remained was an application that wants
`.sidebar menu item` or `.toolbar > popover` to mean what it says, the way a
browser's `popover` is styled from where it sits in the document.

The user chose to let selectors reach in (2026-10-18), knowing that the
toolkit's own sheet had been written for isolation.

## Decision

**A descendant or child combinator steps out of a popup's root into the element
that opened it.** `StyleElement` gains `selectorParent()`, which is `parent()`
everywhere except at a popup's root, where it answers the opener.
`ElementTree.openedFrom(opener)` sets it. The matcher's two upward combinators
walk `selectorParent()`, and nothing else does.

- **Properties are still not inherited.** `:root` still matches the popup's own
  root, so it resolves the theme's tokens itself. Custom properties, container
  queries, siblings and `:has()` stay inside the tree they are in.
- **The toolkit's base layer does not reach in.** An audit of `controls.css`
  found the rules it would break: `scroll:hover scroll-thumb` showing a select
  list's thumb whenever the page under it was hovered, `field-body text-input`
  and `field:invalid text-input` styling the hex field in a color picker's
  board, `tree-row:hover check-indicator` reaching a context menu's check marks,
  `message-actions button` reaching a popover opened from a message. Each is a
  control styling its own parts, and a popup is not a part of the control that
  opened it. So the resolver matches `TOOLKIT_BASE` rules with
  `SelectorMatcher.Reach.OWN_TREE`, and every other layer with
  `THROUGH_OPENER`. Rewriting each rule with child combinators would have
  changed how they match inside the window too.
- **The opener is known, not guessed.** For an anchor id it is the element with
  that id. A context menu's is the element whose menu opened, a tooltip's the
  element it describes, a submenu's its row (`Popup.find`), so a chain runs
  through nested menus. A popup opened against a bare rectangle names its
  opener with `OpenedFrom.call(context, ...)`, a `ScopedValue` that every
  opening method on `Host` reads. That avoids an overload per method. `select`,
  the date, time and color pickers, and a text field's suggestions do this from
  their own `State.context()`. A caller's name wins over the toolkit's.
- **The popup follows the chain while it is open.** After each frame of the
  owner window, and after each popup's own, a popup compares a `SelectorChain`
  of its opener (each node by identity, with its type, id, classes, states and
  sibling position) against the one it was last styled under. If it moved, the
  popup restyles and measures itself again. A popup opened from nothing pays
  nothing. An opener that has left its tree ends the chain.

Rejected:

- **Inheriting the opener's properties.** It would draw a tooltip on a heading
  at the heading's size, the reason ADR-0263 pins it, and a menu's rows in
  whatever colour the button had.
- **Finding the opener from the anchor rectangle.** A submenu's rectangle is
  synthesized and matches no region, and a control and its first child often
  share one.
- **Letting every layer reach in and fixing `controls.css` rule by rule.** Every
  future toolkit rule would then have to be written with popups in mind.

## Consequences

- An application can style a popup from the part of the window it came from,
  and a rule keyed on a class or state up the chain applies or stops applying
  while the popup is showing.
- An application rule that already used a descendant combinator, say
  `.sidebar button`, now also matches inside a popup opened from under it. That
  changes how things look, which the release's breaking changes do not list.
- No golden moved: no toolkit rule reaches in, and no pictured popup is opened
  under an application rule that does.
- `OpenedFromMatchingTest` holds the matcher to both combinators, `:root`,
  `:has()`, siblings and the two reaches. `SelectorChainTest` holds the chain's
  equality. `PopupOpenedFromTest` runs the launcher for an id, a rectangle with
  `OpenedFrom`, a class arriving and leaving while open, and a toolkit-layer
  rule that stays out. `OpenedFromWidgetsTest` checks that a `select`'s list and
  a submenu are reached through their openers.
- The `TODO.md` entry *A popup's contents inherit nothing from the widget that
  opened them* moves to *Answered*.
