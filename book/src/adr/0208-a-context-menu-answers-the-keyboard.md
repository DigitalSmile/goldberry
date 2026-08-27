# 208. A context menu answers the keyboard

Date: 2026-08-27

## Status

Accepted. The half of ADR-0108 that did not ship.

## Context

`docs/core-widgets.md` §7 says a context menu is "opened by right-click **or the
keyboard menu key** at the focused widget". ADR-0108 built the first half and
recorded the second as not built, which left the catalog with one entry whose
only way in is a pointer — in a toolkit whose §2.2 requires everything to be
reachable, and which has spent ADR-0199 and ADR-0206 making a *chart* readable
without one.

It is also the smallest of the outstanding keyboard gaps, and it was left because
of a real difference rather than an oversight: a right-click has a *point* and
the keyboard does not.

## Decision

**`Key.MENU` is bound, and so is `Shift+F10`.** The first is SDL's
`SDLK_APPLICATION` — the key between `AltGr` and `Ctrl`, whose scancode comment
in SDL's own header reads "windows contextual menu, compose". The second is the
companion binding on every platform that has the first, and the only one on the
platforms that do not, which is every Mac. Both, rather than either: a keyboard
with a menu key still has users who reach for the pair.

**Bare `F10` is not taken.** It is the menu bar's (ADR-0163), and an application
with both would open a context menu where it meant to activate its bar.

**It starts at the focused element**, and walks up from there to the nearest
widget that named a menu — which is the same walk the pointer's half does from
the hovered element, extracted so there is one copy of it. "A right-click on a
button's label is a right-click on the button" and "the menu key on a focused
button is that button's menu" are the same rule.

**It is anchored to the focused element's painted rectangle**, not to a point,
because there is no point. The menu therefore hangs off the bottom of whatever
has the focus ring, which is where the reader is already looking. The rectangle
comes from the last painted frame for `Host.anchor`'s reason (ADR-0054): a key
event has no way to reach the geometry, and a placement needs it.

**Only while nothing is open over the window.** With a menu already showing, the
keyboard belongs to the menu — the same rule the arrow keys already follow
(ADR-0104).

**Nothing focused opens nothing**, and so does a focused widget that named no
menu. A keyboard with no position has nothing to ask about, which is the
keyboard's version of a right-click over empty space.

## Alternatives considered

- **A `Shortcut` registered through `Host.addShortcut`.** It would put the
  binding in the same map an application's accelerators live in, where
  `removeShortcut` is keyed by the shortcut rather than by who bound it — so an
  application binding `Shift+F10` for its own reasons would silently take the
  context menu with it, which is a live entry in `TODO.md` rather than a
  hypothetical.
- **Anchoring to the focus ring's centre** rather than its rectangle. A menu
  emerging from the middle of a wide row is a menu whose top-left corner is
  nowhere in particular; a rectangle lets `Placement` flip and shift it against
  the work area with the same arithmetic every other popup uses.
- **Anchoring to the caret** inside a text field, which is what a native text
  control does. It needs the caret's rectangle to reach a key handler that is
  four layers above the field, and the field would have to publish it; worth
  doing when something asks, and the widget's rectangle is not wrong in the
  meantime.
- **Binding the menu key inside the router** so a widget could handle it. A
  context menu is opened by the *application* (ADR-0108's split: only `:core` can
  notice, only the catalog can build one), so a widget-level key would arrive in
  the layer that cannot act on it.

## Consequences

- **`Key` has one more enumerator**, and it is the first one bound for something
  other than navigation, editing or an accelerator.
- **The walk is shared.** `openContextMenu(Element, LogicalRect)` is what both
  halves call, so a change to which ancestor wins changes both — which was the
  point of extracting it rather than writing the loop twice.
- **`docs/core-widgets.md` §7's context-menu row is complete.**
- **A focused element that has not been painted yet opens nothing.** Ordinary and
  unreachable in an application: the focus arrives through a frame.
