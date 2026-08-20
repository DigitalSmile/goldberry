# 163. A menu bar owns its menus, and a value already outlives an opening

Date: 2026-08-20

## Status

Accepted. Builds `menubar` and the half of §8's accelerator that
[ADR-0106](0106-a-menu-is-a-widget-and-opening-one-is-not.md) deferred.

## Context

`docs/core-widgets.md` §8 asks for two things that have been marked *not built*
since ADR-0106, with the same sentence explaining both:

> **`menubar`** — in-window horizontal bar; `Alt`-style keyboard activation;
> arrows navigate. *Not built: it is the widget in this group that needs a menu to
> exist for longer than one opening, which is the same thing accelerator
> registration needs.*

and, of `item`:

> accelerator (displayed right-aligned *and* auto-registered in the window's
> shortcut map) … The accelerator is **displayed and not registered**: a shortcut
> has to work while the menu is shut, and a menu is built when it opens and thrown
> away when it closes — registration needs something that owns menus for longer
> than one opening.

That reasoning was right about the problem and wrong about what the problem was
made of. **What is built and thrown away is the popup**, not the menu. A `Menu`
is a `record` — an ordinary value, described by the author, no different from a
`Button` — and `Menus.open` builds a *second* tree from it to put in a window.
The description was never short-lived. Nothing was ever holding it, which is a
different complaint entirely, and the fix for it is a widget that does.

## Decision

**`menubar` is a row of `item`s, and holding them is the whole mechanism.**

```java
new MenuBar(
        new Item("File").submenu(
                new Item("Open…", this::open).accelerator("Ctrl+O"),
                new Separator(),
                new Item("Quit", this::quit).accelerator("Ctrl+Q")),
        new Item("Edit").submenu(
                new Item("Undo", this::undo).accelerator("Ctrl+Z")));
```

```kdl
menubar {
    item "File" {
        item press="app.open" accelerator="Ctrl+O" "Open…"
        separator
        item press="app.quit" accelerator="Ctrl+Q" "Quit"
    }
}
```

### There is no new markup

A bar's children are `item`s, and an `item` containing `item`s is a heading that
opens a menu — which has been the submenu syntax since ADR-0106 ("a nested `item`
*is* the submenu syntax and there is no `submenu` node to forget"). A `menubar` is
a row of the thing a menu was already made of. Nothing about declaring one is new
to learn, and no node was added to the catalog beyond the bar itself.

### The accelerators are walked from the description, not from a window

`Accelerators.in(widgets)` walks the `item` tree and yields every `Shortcut` with
the command behind it. `MenuBarState` binds them on `build` and gives them back on
`dispose`. **No menu is opened, and none needs to be** — which is the claim, and
which is why `MenuBarTest`'s central case fires `Ctrl+O` with nothing whatsoever
on screen.

Three kinds of row are passed over, and each absence is an ordinary thing to
write: no accelerator, no command (a row with a submenu leads somewhere rather
than doing something), and disabled — because a greyed row that still fires on
its key is worse than no accelerator at all.

An accelerator that does not **parse** is logged and skipped rather than thrown.
It is a typo; it is already being drawn beside the row where somebody can see it;
and a stylesheet error should not take a window down.

### A heading is not a menu row

`MenuTitle` is a separate widget from `Item` rather than a flag on it, because the
two answer the keyboard differently and that difference is what makes a bar a bar:

| Key | In a menu (`Item`) | In a bar (`MenuTitle`) |
|---|---|---|
| `Down` | move to the next row | **open this menu** |
| `Right` | open this row's submenu | move to the next heading |
| `Left` | nothing | move to the previous heading |

`Left` and `Right` are not the widget's at all — `MenuBarRow` is a **horizontal**
focus scope where a `Menu` is a vertical one
([ADR-0078](0078-a-focus-scope-has-an-axis.md)), so traversal comes free and the
two arrows left over are the two the bar wants.

A heading also has none of the three things that make a row a row: no tick
column, no accelerator on the right, no chevron.

### Hovering opens, but only once something is open

A heading opens on a click. Once a menu is showing, every *other* heading opens
on hover — which is what every desktop bar does, and why running along the bar
with a menu down does not need a click per menu. Hovering with nothing open does
nothing at all: a bar that dropped a menu because the pointer crossed it on the
way somewhere else would be unusable.

### `F10`, and why not `Alt`

§8 asks for "`Alt`-style keyboard activation". A bare `Alt` tap is a **modifier
released with nothing in between**, and a `Shortcut` here is a key plus
modifiers — `Key` has no `ALT` to name, deliberately, because `Shortcut`'s own
constructor refuses a shortcut that can never fire. So the binding is `F10`, which
is the companion activator on every platform that has the `Alt` one, and which
opens the first heading rather than merely focusing it: there is no `Host.focus`,
and a bar that took `F10` and did nothing visible would read as a broken binding
rather than a missing one.

## Alternatives considered

**A `MenuModel` the window owns.** What ADR-0106's wording implies: a registry on
the `Host`, menus put into it by name, the bar reading from it. It is a second
place a menu can live, with its own lifetime and its own staleness, and every
question it answers — what outlives an opening, what can be walked for
accelerators — is answered by the value the author already wrote.

**Register accelerators from `Menus.open`.** The registration would then exist for
exactly as long as the popup, which is the thing that does not work. Registering
on open and *not* unregistering on close would leave a menu's keys bound after a
window forgot the menu existed.

**Put the accelerator walk on `Menu` itself.** `menu.accelerators()` reads well and
puts a `Host`-facing concern on a value. `Accelerators` is a static utility for
`Menus`'s own reason: binding needs a `Host` and a widget must not have one
(ADR-0106).

**A `heading` or `menu-title` markup node.** More explicit than a nested `item`,
and it would make a bar's children a different shape from a menu's for no gain —
the nesting already means "opens this".

**Give `Item` a `bar` flag.** One widget, two keyboard maps chosen by a boolean.
Every method on it would then start by asking which one it is.

## Consequences

**`Host` grew `removeShortcut`.** The router had it; the window's front door did
not, because until now nothing bound an accelerator it would later want back. The
map is keyed by the shortcut and **not by who bound it**, so removing takes out
whatever is bound to that key — including somebody else's later binding. Two
things claiming `Ctrl+O` is already a conflict the last registration wins; this is
the same conflict at the other end, and it is written down rather than defended
against, because defending would mean the map remembering owners and a `menubar`
being the only thing that could ever use that.

**A collision inside one bar is logged, and the later row wins.** That is the
map's behaviour said out loud. Refusing the second would produce a menu whose
second `Ctrl+O` silently does nothing.

**`Menus.open` gained a placement overload.** A heading's menu hangs from the bar
with **no gap**, where a context menu stands 4px off the pointer so as not to open
underneath it.

**The tests found a third stub `Host`, and there is now one.** `SelectTest` and
`TourTest` each carried a near-identical hand-written `Host`, and adding two
methods to the interface would have meant editing both plus writing a third.
`TestHost` is the shared one and both now extend it; it records popups,
accelerators, overlays and anchors, and — like the real thing on SDL's `dummy`
driver — **opens nothing**, which is the branch ADR-0102 says a control has to
survive.

**Two things §8 asks for are still not built, and neither is a menu-bar
problem.** A bare `Alt` tap needs key-release tracking that `Shortcut` cannot
express. And **`Left`/`Right` do not move between menus while one is down**: the
open menu is a separate window with its own focus, so the bar cannot see the
arrows — which is the same missing item-to-popup callback that has kept `Left`
from closing a submenu since ADR-0112. Both are in `TODO.md`.

**The bar was the first new widget drawn through
[ADR-0162](0162-a-golden-is-checked-at-every-scale.md).** Six goldens, each also
checked at 2× and 1.5×, on the day they were written rather than after somebody
reports a HiDPI bug.

**`.open` is the accent fill, not a stronger overlay.** The first cut used
`--gb-overlay-active` over `:hover`'s `--gb-overlay-hover` — 16% against 8% — and
the golden showed the two are hard to tell apart, which is exactly the comparison
a bar puts in front of somebody, since the hovered heading is usually the one
*next to* the open one. Caught by looking at the image, which is what the image is
for.
