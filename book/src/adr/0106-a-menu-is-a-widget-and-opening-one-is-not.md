# ADR-0106: A menu is a widget, and opening one is not

- **Status:** Accepted
- **Date:** 2026-08-18
- **Relates to:** `docs/core-widgets.md` §8, builds on
  [ADR-0104](0104-a-popup-is-measured-then-placed.md) and
  [ADR-0105](0105-a-tooltip-is-an-attribute-not-a-widget.md), applies
  [ADR-0073](0073-a-composite-is-one-tab-stop.md)'s pattern to
  a tree that spans windows

## Context

`docs/core-widgets.md` §8 gives a menu item four things — "label, optional icon,
accelerator (displayed right-aligned *and* auto-registered in the window's
shortcut map), checkable items, disabled state, nested submenus (hover-intent
timing)" — and one of them cannot be a widget's own business.

**A submenu is a second platform window.** Opening it needs a `Host`: something
has to measure the panel, place it beside the row that owns it, ask the platform,
and close it again. A widget has no `Host` and must not have one — it is a value,
described afresh every frame, and one holding the window it is drawn in would be
describing its own surroundings.

The same is true of the outer menu, and of what happens when a command is chosen:
choosing "Save" from a submenu of a submenu has to leave *nothing* on screen,
which is a fact about a stack of windows that no item in one of them can see.

## Decision

**`menu`, `item` and `separator` are widgets. `Menus.open(host, anchor, menu)` is
a call.**

```java
Menus.open(host, "file-button", new Menu(
        new Item("Open…", this::open).accelerator("Ctrl+O"),
        new Separator(),
        new Item("Recent").submenu(
                new Item("notes.txt", () -> open("notes.txt")))));
```

The widgets are ordinary: a document writes them, a stylesheet styles them, and
they draw in whatever they are put in. The opener is where the `Host` is.

### The opener rebuilds the tree, which is how composites work here already

`Menus.open` walks the menu's children and hands each item the thing only an
opener knows — a way to open its own submenu, and a command wrapped in "and close
the stack". That is exactly what `radio-group` does to its `radio` children
([ADR-0073](0073-a-composite-is-one-tab-stop.md)): the composite
supplies what the child cannot know, and the child stays a value.

Items get a generated id if they have none, because a submenu is anchored to its
row and an anchor is looked up by id. An author writing a menu should not have to
name every row that happens to lead somewhere.

### A submenu is anchored inside a popup, which needed a second `anchor`

`Host.anchor` answers from the main window's geometry and knows nothing about what
is in a popup. So `Popup` gained one of its own, returning the rectangle **in the
owner window's coordinates** — translated by the popup's own offset, because that
is the space `Host.popup` places in. Without the translation a submenu opens at
the right place relative to the wrong origin, which looks like a placement bug and
is a coordinate-space one.

### Every command closes the whole stack; a submenu closes its siblings

Two rules, and each is what a menu does everywhere. `Menus` keeps the stack of
popups it opened: a command closes all of them, and opening a submenu first closes
everything opened after its parent — so travelling down a menu past three rows
with submenus leaves one open rather than three.

Hover intent is 150 ms, from the loop's timer
([ADR-0105](0105-a-tooltip-is-an-attribute-not-a-widget.md)), and one pending
timer is shared: there is one pointer, so two menus cannot both be being hovered,
and a per-menu timer would let a submenu open after the pointer had moved to a
different menu entirely.

### The keyboard: vertical scope, and `Right` is not traversal

A `menu` is a **vertical** focus scope. `Up` and `Down` move between items;
`Left` and `Right` are deliberately not traversal, because in a menu they mean
"open that submenu" and "close this one", which is the item's business
([ADR-0078](0078-a-focus-scope-has-an-axis.md) is the record of a scope having an
axis, and this is the second widget to need the narrow one).

`Escape` belongs to the popup, not to any item, and already worked
([ADR-0104](0104-a-popup-is-measured-then-placed.md)).

### The tick column is always there

`item-check` is a part, built checked or not. A column that appeared with the
first tick would shift every label in the menu sideways the moment one row became
checkable — and a node that only exists while something is on cannot transition,
which is `radio-dot`'s reason ([ADR-0065](0065-a-part-is-styleable-and-not-constructible.md)).

### The accelerator is displayed and **not** registered

§8 asks for both: "displayed right-aligned *and* auto-registered in the window's
shortcut map". This is the display half.

Registration needs a lifetime a menu does not have. A menu is built when it opens
and thrown away when it closes; a shortcut has to work when the menu is *shut*,
which is the whole point of one. Something would have to own the menu for longer
than one opening — a menu *model* the window keeps — and that is a design §8 does
not describe. Recorded rather than guessed at.

## Alternatives considered

- **A `Menu` widget that opens itself,** holding a `Host` or reaching a global.
  It makes a widget stateful about its surroundings, and two menus built from one
  description would fight over which window they are in.
- **A menu drawn in the in-window overlay layer.** No popup, no placement, no
  second window — and clipped to the window, so a menu near the bottom edge shows
  half its items. That is the distinction ADR-0100 and ADR-0102 exist to draw.
- **Submenu items as children of the item's element.** They are in a different
  window with a different render tree; making them children would mean a tree
  spanning two surfaces, which `RenderTree` has no notion of and should not.
- **A `submenu` node in KDL.** Nesting `item` inside `item` *is* the syntax, so
  there is no second node to forget and no way to write a submenu that is not one.
- **Opening a submenu instantly on hover.** What the first version did, and it is
  wrong in the ordinary case: travelling down a menu opens every submenu on the
  way past.

## Consequences

- **`menubar` is not built.** §8's in-window bar with `Alt` activation is the
  remaining widget in the group, and it is the one that needs a menu to exist for
  longer than one opening — the same thing accelerator registration needs.
- **Context menus are not built.** §8 gives any widget `context-menu="menuId"`,
  which needs a registry of menus by id and a right-click path into it. The
  attribute would ride on `Attributes` exactly as a tooltip's text does.
- **A keyboard `Right` waits 150 ms**, because it goes through the same
  hover-intent path. Wrong, and one line to fix once `Item` can tell a hover from
  a keypress.
- **`Host` grew `after(delay, action)`**, which an application can use for
  anything and which is what the tooltip already used privately.
- **`GoldberryTestAccess` moved to test fixtures**, so `:widgets` tests can drive
  the real launcher against the headless backend — which is what the four tests
  behind this record do, with real posted clicks rather than direct calls.
