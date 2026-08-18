# ADR-0108: A context menu is a name on a widget

- **Status:** Accepted
- **Date:** 2026-08-18
- **Relates to:** `docs/core-widgets.md` §8, completes
  [ADR-0106](0106-a-menu-is-a-widget-and-opening-one-is-not.md), follows
  [ADR-0105](0105-a-tooltip-is-an-attribute-not-a-widget.md)'s shape exactly

## Context

`docs/core-widgets.md` §8, one line:

> **Context menus** — any widget takes `context-menu="menuId"`; opened by
> right-click or the keyboard menu key at the focused widget.

"Any widget" is [ADR-0105](0105-a-tooltip-is-an-attribute-not-a-widget.md)'s
problem again, and the answer is the same: it rides on `Attributes`, beside `id`,
`class`, the key and the tooltip's text.

What is *not* the same is who can act on it. A tooltip's plate is a `:core`
widget, because nothing else can draw it. A context menu's is a `menu` — a
catalog widget — and opening one means wrapping every item so that choosing it
closes the stack, which is `Menus`'
([ADR-0106](0106-a-menu-is-a-widget-and-opening-one-is-not.md)).

So the two halves of one feature sit on opposite sides of the module boundary.
`:core` is the only thing that can notice the right-click: it has the router,
which knows what is under the pointer, and the window, which is where a popup
goes. `:widgets` is the only thing that can turn a name into a menu.

## Decision

**The toolkit finds the name; the catalog says what it means.**

`Host.onContextMenu(handler)` is the seam, one sentence wide: the launcher walks
up from what is under the pointer on a secondary press, and hands over the name it
found and the point the click landed at. `Menus.contextMenus(host, menus)` is the
line an application writes:

```java
Menus.contextMenus(host, Map.of("row", rowMenu(), "canvas", canvasMenu()));
```

### The press is *taken*

`InputWatcher.pressed` returns a boolean now, and a context menu opening returns
true — so the press does not also travel to whatever it landed on. Right-clicking
a button should open its menu, not press it.

The same watcher is what light dismissal uses, which is why it learned the button
and the position: light dismissal only needs to know *that* a press happened, and
this needs to know which button and where.

### Anchored to the pointer, not to the widget

The rectangle handed over has no size. Two right-clicks in one list open two menus
in two places, which is what every desktop does — anchoring to the widget would
put both menus at the top-left corner of a list a thousand rows long.

### Walked upwards

A right-click on a button's *label* is a right-click on the button. The same walk
a tooltip does, for the same reason: the element a hit test reports is the deepest
one, and the attribute is usually on something above it.

### An unknown name is logged and ignored

Unlike `press=`, which is a compile-time-ish failure at inflation
([ADR-0051](0051-kdl-is-parsed-here-and-reloading-is-forgiving.md)'s strict
registry). The difference is when it is discovered: a `press=` typo is found when
the document loads, and a `context-menu=` typo is found on a right-click, where
throwing takes the window down. A menu that does not appear is a smaller failure
than an application that stops.

## Alternatives considered

- **A registry of menus in `:core`,** so the launcher opens them itself. It cannot:
  a menu is a `:widgets` widget and opening one is `Menus.open`, which wraps every
  item. `:core` would have to depend on the catalog it deliberately does not ship
  ([ADR-0092](0092-a-primitive-is-a-widget-like-any-other.md)).
- **A `context-menu` widget wrapping its target**, which some toolkits do. It puts
  an element between a node and its parent, so `panel > button` stops matching —
  the argument that keeps `bind` and `tooltip` on the widget rather than on a
  wrapper.
- **The widget holding the `Menu` itself** rather than a name. A widget is a value
  rebuilt every frame; holding a menu means rebuilding a menu every frame for the
  99.9% of frames where nobody right-clicks anything.
- **Throwing on an unknown name.** See above: the discovery point is a right-click,
  not a load.

## Consequences

- **The keyboard menu key does not open one.** §8 asks for "right-click **or** the
  keyboard menu key at the focused widget"; the key half needs `Key.MENU` in the
  key map and an anchor from the focused element's rectangle — which
  `Host.anchor`'s element-wise form would give, and which does not exist yet.
- **A right-click still does not select what it is over.** Every file manager
  selects the row you right-click before opening the menu; that is the
  application's to do in its handler today, because the toolkit has no notion of
  what "select" means for an arbitrary widget.
- **`Attributes` has five components.** Every wither has to preserve all of them,
  which the tooltip's arrival is the reason anyone now checks
  ([ADR-0105](0105-a-tooltip-is-an-attribute-not-a-widget.md)).
- **`menubar` remains unbuilt**, and it is now the only part of §8 that is: it
  needs a menu that outlives one opening, which is the same thing accelerator
  registration needs ([ADR-0106](0106-a-menu-is-a-widget-and-opening-one-is-not.md)).
