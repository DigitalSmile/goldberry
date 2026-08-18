# ADR-0103: A popup is a second tree in a second window

- **Status:** Accepted
- **Date:** 2026-08-18
- **Relates to:** `docs/core-widgets.md` §7, `docs/ARCHITECTURE.md` §4 and §7,
  builds on [ADR-0102](0102-a-popup-is-a-window-the-platform-may-refuse.md),
  applies [ADR-0080](0080-a-value-is-measured-along-a-part.md)'s finding about
  where geometry lives

## Context

[ADR-0102](0102-a-popup-is-a-window-the-platform-may-refuse.md) got a platform
window open and left three things undone, and said so: nothing painted into one,
nothing routed input to one, and nothing dismissed one.

All three are the same question — **what does a popup contain?** — and the
launcher's shape made it a real one. It owns one window, one element tree, one
render tree and one pointer router ([ADR-0093](0093-an-application-is-a-root-widget.md)),
and every one of those is per *window*.

## Decision

**A popup is a widget tree of its own, in a window of its own, painted by the
renderer of the window it belongs to.**

```java
host.popup(menu(), LogicalPoint.of(24, 120), LogicalSize.of(180, 132))
    .ifPresent(open -> this.menu = open);
```

`Popup` holds an `ElementTree`, a `RenderTree` and a `PointerRouter`, and a
`Window` over the `BackendPopup` — which is the whole trick: everything above the
SPI is identical for a popup and a window, so `Window.over(backendWindow)`
registers it with the runtime and the existing frame loop paints it, the existing
dispatch delivers its events, and its pointer goes through its own router.

### What is shared is the renderer, and that is deliberate

The stylesheets, the font book and the frame clock. A popup is themed by the same
cascade as the window that opened it, restyles with it, and animates on the same
tick — because it reads `() -> renderer`, the launcher's current one, rather than
a copy taken when it opened.

What is **not** shared is the tree. A popup's contents are a root, not a
descendant of the widget that opened them, so they inherit nothing from it: no
`color`, no `font-size`, and no descendant selector reaches into them. For a menu
that is correct — its items are a list, not part of a button's subtree — and the
showcase's `#menu` says so by carrying its own surface and its own edge, because
nothing above it will.

For a `tooltip` that wants the styling of the thing it describes it is a
limitation, and the answer when that widget is built is to pass the anchor's
resolved style in, not to reparent the tree.

### Light dismissal needs input the router will not deliver

§7 gives `popover` "light-dismiss on outside click/Esc". Neither reaches a
widget: an outside click usually lands on *nothing*, and `Escape` belongs to no
control in particular. The pointer router dispatches to the element under the
pointer and correctly does nothing when there is none — which is exactly the case
a menu must close on.

So `Window` gained one package-private hook, `InputWatcher`, called before
routing with "a press happened" and "a key went down". The launcher watches the
owner window; the popup watches its own, for `Escape` alone — once a menu has
taken focus the key goes to it and the owner never sees it. A press *inside* a
popup is deliberately not watched: that is someone choosing an item.

It is package-private because "see every press before anything else does" is not
something an application should be handed.

### A popup is anchored to a rectangle from the last frame

`Host.anchor(id)` returns the painted rectangle of a node, and a menu opens under
the button that opened it by asking for it. Where a button *is* is a fact about
the last frame — geometry exists after a paint and it is the router that has it
([ADR-0080](0080-a-value-is-measured-along-a-part.md)) — so the launcher keeps
the same `HitTest` capture it hands the router and answers from that.

By `id` rather than by element, for two reasons that point the same way: §7's
`tour` "names a target by id", and an application holds ids rather than elements.
A `popover` anchoring to *itself* will want the element form and will be built
with it.

Empty before the first frame and for a node that was not painted. The showcase
falls back to a corner rather than refusing to open, because a menu that does not
appear is a worse answer than one in the wrong place.

### Closing a window closes its popups, and that is not tidiness

The event loop runs until `backend.windows()` is empty. SDL destroys a window's
popups with it, leaving this side holding dangling handles and — worse — entries
in the window map, so an orphaned popup is a process that never exits. Both
backends now close a window's popups first, and `headless` does it for the same
reason rather than for symmetry: it is where that bug would otherwise pass.

## Alternatives considered

- **One element tree, with the popup's contents as a subtree of it.** What a
  browser does, and what makes a tooltip inherit its anchor's styling. It needs
  the render tree to be splittable — one subtree laid out and painted against a
  *different* surface at a different origin — which is real machinery in
  `RenderTree` and `HitTest` for a benefit only `tooltip` has asked for. Recorded
  as the thing to revisit when it does.
- **Painting the popup from the owner's paint callback.** One frame loop, two
  surfaces. It couples the two windows' repaint rates — a menu that animates
  would drive the whole window's loop, and a window that idles would freeze the
  menu — and the SPI already gives each window its own `requestFrame`.
- **Light dismissal as a widget** — a full-window transparent `scrim` under the
  popup that swallows the click. It is what a web toolkit does because it has no
  choice, it makes every popup cost a full-window overlay, and it cannot see
  `Escape` at all.
- **`Host.popup` returning a `Popup` and throwing when the platform has none.**
  The SPI's `Optional` is deliberate ([ADR-0102](0102-a-popup-is-a-window-the-platform-may-refuse.md))
  and hiding it at this layer would put the decision "what do I do without
  popups?" somewhere the application cannot reach it.

## Consequences

- **`select`, `menu`, `tooltip` and `popover` are unblocked at the widget layer
  too.** What each still needs is its own: placement policy, item semantics,
  keyboard traversal into and out of the popup.
- **A popup does not size itself to its content.** The caller gives a size, and
  the showcase's menu is 180×132 because someone measured it. Auto-sizing needs a
  measure pass without a surface to measure against, which Yoga can do and
  `RenderTree.update` currently cannot be asked for.
- **Placement is not policy yet.** Nothing flips a menu that would open off the
  bottom of the screen, which needs the display's work area — a call the SPI does
  not have.
- **Focus does not travel into a popup.** Its router has its own focus root, so
  `Tab` inside a menu works; what does not is opening a menu from the keyboard
  and landing in it, or returning focus to the button on close. §7's "each wraps
  a `focus-scope` and restores focus on close" is the widgets' to keep, and
  `focus-scope` exists ([ADR-0078](0078-a-focus-scope-has-an-axis.md)).
- **Two popups do not know about each other.** A submenu chain — where opening
  one closes its siblings but not its parent — is `menu`'s to arrange; the
  launcher's light dismissal closes all of them at once, which is right for one
  and wrong for a chain.
