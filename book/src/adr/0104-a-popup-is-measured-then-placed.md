# ADR-0104: A popup is measured, then placed

- **Status:** Accepted
- **Date:** 2026-08-18
- **Relates to:** `docs/core-widgets.md` §7, `docs/ARCHITECTURE.md` §4 and §7.2,
  completes [ADR-0103](0103-a-popup-is-a-second-tree-in-a-second-window.md),
  uses [ADR-0080](0080-a-value-is-measured-along-a-part.md)'s finding about where
  geometry lives

## Context

[ADR-0103](0103-a-popup-is-a-second-tree-in-a-second-window.md) put a widget tree
in a popup window and listed what was still missing. Three of those items were
the same shape — *the caller has to know something it cannot know* — and they
are what `docs/core-widgets.md` §7's `popover` is made of:

> **`popover`** — anchored floating panel (the `select` popup generalized):
> placement with flip/shift when near edges, light-dismiss on outside click/Esc;
> the primitive under menus, dropdowns, `date-picker`, `color-picker` and
> autocomplete.

- **A size.** `host.popup(content, at, size)` made the caller supply one, and the
  showcase's menu was `180×132` because somebody measured it by hand. Add an item
  and the number is wrong.
- **A position.** The caller could anchor to a rectangle
  ([ADR-0103](0103-a-popup-is-a-second-tree-in-a-second-window.md)'s
  `Host.anchor`) but nothing stopped the result opening off the bottom of the
  screen, or underneath a taskbar.
- **The keyboard.** A menu with no focus in it answers `Down` by doing nothing.

## Decision

**`host.popup(content, anchor, placement)` measures, places, and opens.**

```java
host.popup(new Popover(items), "menu-button", Placement.BELOW)
    .ifPresent(open -> this.menu = open);
```

### Measuring: two passes, and the second one is the whole record

`RenderTree.measure(box, scale, availableWidth, availableHeight)` lays a tree out
with no surface and reports the size it wants. Two floats rather than a
`LogicalSize`, because a size refuses `NaN` and "undefined" is exactly what has to
be expressible.

The trap is that **Yoga lays a root out at exactly the available size when that
size is definite.** There is no parent for the root to be "at most" of, so a bound
and a target are the same number. Measuring a menu against the window therefore
returns the window — which happened twice, once per axis, and both times it looked
like a placement bug:

- First against `window.size()`: the menu came out `960×640`.
- Then against `(windowWidth, NaN)`: `960×108` — the height was right and the
  width was still the window's.

So the measurement is: **nothing definite**, which gives the content's natural
size; and only if that is wider than the window, a second pass with the width
pinned, where a definite width is now what is wanted and a paragraph wraps at it
rather than running off the side. A menu is a few dozen Yoga nodes and this
happens once, when it opens.

The same trap bites a widget: `Popover.render` originally returned a growing box
so it would fill its window, and a growing root fills a definite available size.
It is content-sized now, and filling the window is not something it has to ask for
— the window was created at its measured size.

### Placing: three rules, no state

`Placement` is a record of a preferred side, a cross-axis alignment and a gap,
with one pure function on it: anchor rectangle, size, and the rectangle it must
stay inside, in; a point and the side it ended up on, out. It opens no window,
reads no display and knows nothing about popups, which is why every case of it is
a test rather than a screenshot.

1. **Preferred side**, `gap` away, aligned by `align`.
2. **Flip** to the opposite side only if it does not fit on the preferred one
   *and* does fit on the opposite one. Not "if there is more room the other way":
   a menu that changed sides on the strength of a comparison is a menu nobody can
   predict.
3. **Shift** along the cross axis until it is inside — which keeps the popup
   attached to its anchor's side while sliding it along. Flipping the cross axis
   would move it somewhere else entirely.

Still too big — a menu taller than the screen — and it is clamped to the **near**
edge, so the top of it is what survives. Making it scroll is `scroll`'s job and
`scroll` does not exist.

### The rectangle it must stay inside is the display's *work area*

Not the display's bounds. `SDL_GetDisplayUsableBounds` excludes whatever the
desktop has reserved, and the difference between the two rectangles is exactly the
taskbar a menu would otherwise open underneath. `BackendWindow` gained
`workArea()` and `position()`; the launcher translates the first by the second, so
a placement policy works entirely in the window's own coordinates — the same space
an anchor and a hit test are already in.

Both return `Optional`, because some drivers will not say and a headless backend
has no desktop. When either is absent the window's own bounds stand in: a popup
kept inside its owner is always on the screen, which is a worse answer and not a
wrong one.

`HeadlessBackend` has a pretend desktop of 1920×1040 — **40 logical pixels
reserved at the bottom**, so a test that confuses the work area with the display's
size fails. The window can be moved about on it, because a placement policy is
only interesting near an edge and "near an edge" needs an edge.

### The keyboard belongs to the open popup

A popup's router focuses its first focusable node after its first frame — a menu
whose first item is not focused answers `Down` by focusing the first item, one
keystroke later than every menu anywhere else.

And keys the **owner** window receives are forwarded to the topmost open popup
before its own router sees them. `Window.InputWatcher.keyPressed` returns a
boolean now: `true` takes the key. This is not belt-and-braces — whether a popup
has the platform's keyboard focus is per-driver (SDL gives a `POPUP_MENU` window
focus on some and not on others, and a tooltip must never have it), so without
forwarding an arrow key would move the selection in the window *underneath* the
menu on half the platforms.

`Escape` is taken by the watcher itself and closes the popup, which is the one key
that belongs to no widget.

### `popover` is the panel, not the opening

The widget in `:widgets` is the surface: background, border, radius, padding, and
a `class="menu"` shape for items that fill the width. Where it goes and when it
goes away is `Host.popup`, and that machinery serves `tooltip`, `select` and
`menu` equally — none of which is a popover, so it does not live inside one.

Being a widget is what makes it themeable, density-aware and writable from a
document; being *only* the panel is what stops three other widgets from having to
be popovers to get placement.

## Alternatives considered

- **A `maxWidth` on the box, measured once.** The clean version of the two-pass
  measure, and it needs `max-width` in §8's subset, which does not have it. Adding
  a CSS property to avoid a second Yoga pass over forty nodes is the wrong trade.
- **Placement inside `Popup`.** It would make `Popup` need the work area, the
  window position and the anchor, which is three things it otherwise never touches
  — and would make the arithmetic untestable without opening a window.
- **Flip by choosing the side with more room.** Predictable-looking and
  unpredictable in use: a dropdown near the middle of a tall screen would open
  upwards or downwards depending on pixels nobody is looking at.
- **Let the platform place it.** Windows and X11 both have menu-positioning
  conventions; SDL exposes none of them, and the three platforms disagree about
  flip behaviour. A toolkit that inherited that would have three menus.
- **Move focus to the popup window and let the platform route keys.** It is what
  the flags ask for, and it is not reliable: it depends on the driver, it is wrong
  for a tooltip by specification, and it makes "did my menu get the keys?" a
  question about the window manager.

## Consequences

- **`select`, `menu` and `tooltip` are ordinary widget work now.** Each still owns
  its own model, its item semantics and its keyboard map, and none of them has to
  solve size, position or dismissal.
- **A popup still cannot scroll.** A menu taller than the work area is clamped and
  loses its bottom. `scroll` is `docs/core-widgets.md` §1's and unbuilt, and it is
  the one thing between here and a `select` over a realistic option list.
- **`BackendWindow` grew two calls**, both `Optional`, and `libgoldberry` exports
  two more SDL symbols.
- **Nothing re-places an open popup.** Move the window with a menu open and the
  menu stays where it was put — `Popup.move` exists, and nothing calls it. A
  `popover` that follows a scrolling anchor is the case that will need it.
- **Two popups still do not know about each other**, so a submenu chain is
  `menu`'s to arrange: the launcher's light dismissal closes all of them at once.
