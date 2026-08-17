# ADR-0078: A focus scope has an axis

- **Status:** Accepted
- **Date:** 2026-08-17
- **Relates to:** `docs/design-system.md` §7.2; `docs/core-widgets.md` §3, §5, §7;
  closes the question left open by
  [ADR-0073](0073-a-composite-is-one-tab-stop.md)

## Context

`Handles.focusScope()` was a `boolean`, and **both** arrow pairs roved inside any
scope. ADR-0073 shipped it that way deliberately and wrote down why it would not
last:

> Both arrow pairs rove, which is right for a radio group — its direction is the
> stylesheet's, and `.inline` flips it — and will be wrong for a menu bar, where
> `Down` should open a menu rather than move along the bar.

A radio group genuinely has no axis. Everything else in `docs/core-widgets.md`
that will be a scope does: `menu`, `tabs`, `select`'s popup list, a toolbar.

## Decision

`Handles.focusScope()` returns a `FocusScope`: `NONE`, `HORIZONTAL`, `VERTICAL`
or `BOTH`. `radio-group` answers `BOTH`; the default is `NONE`.

### The axis is the widget's, even though traversal is the router's

ADR-0073's argument was that traversal belongs to the router because *which node
an arrow reaches is a property of the group's shape, and the radio the focus is
on cannot see its siblings*. That still holds and is unchanged — the router still
walks the tree, still finds the scope, still derives the entry point from
`:checked`.

What the widget adds is one fact only it has: **what it means by the other pair.**
A vertical menu's `Right` opens a submenu; a menu bar's `Down` opens a menu; a tab
list's `Down` moves into the panel. The router cannot know any of that, and the
widget cannot do the traversal. Each says the part it knows.

### It only matters on the path where the widget declines

This is the subtle half, and it is why the boolean survived four controls.

Arrows are dispatched to the focused chain **first** and only reach the router if
nobody consumed them. So a menu bar that handles `Down` itself works fine under
`BOTH` — the router never sees the key.

The axis decides what happens when the widget **declines**: a menu item with no
submenu does not consume `Right`, and a `BOTH` scope would then quietly slide
focus to the next item. The user asked to open something and the selection moved
instead, with no error anywhere. That is the failure mode this prevents, and it is
the kind that reads as a toolkit bug rather than as a missing feature.

So `HORIZONTAL` and `VERTICAL` are about the arrows a scope **leaves alone**, as
much as the ones it answers. `moveFocusWithinScope` returns `false` for an axis
its scope does not rove, and false means *unhandled* — nothing happens, which is
the correct behaviour for a key the widget already declined.

### `Home` and `End` belong to no axis

They reach the ends of any scope, on any axis, because they name **a position in
the set** rather than a direction on screen. The router passes a null axis for
them, and `FocusScope.roves(null)` is true for every scope but `NONE`.

### `NONE` means "not a composite", not "a composite that roves on nothing"

The default has to be the first, or every focusable node inside any widget would
stop being its own Tab stop the moment someone added an enum value. Asserted
directly, because the two readings differ only in a case no arrow key visits.

### Why not a per-key hook on the widget

The alternative was to let a widget handle arrows itself and have the router do
nothing — no scope, no axis. That is what a widget can already do by consuming
the key, and it is not enough: it puts the traversal back in the widget, which is
the thing ADR-0073 established the widget cannot do, because a menu item cannot
see its siblings any more than a radio can.

## Consequences

- `menu`, `tabs`, `select`'s popup and a toolbar can each declare the axis they
  actually have. Four widgets unblocked by an enum.
- `radio-group` is the **one** composite in the catalog that legitimately answers
  `BOTH`, and now says so explicitly rather than by being the only implementer of
  a boolean. Its reason is on the method: its direction is its stylesheet's, and
  `.inline` flips it.
- The tests assert the **unhandled** result and not just the focus position —
  `assertFalse(keyPressed(...))` — because "focus did not move" is also true of a
  scope that moved it and moved it back, and the distinction is the whole
  decision.
- **Open: nothing declares an axis yet.** `radio-group` is `BOTH` and no other
  scope exists, so `HORIZONTAL` and `VERTICAL` are covered by `FocusScopeTest`'s
  bare widgets and by nothing shipping. That is deliberate — the mechanism is
  cheap now and expensive once four widgets have worked around its absence — but
  it means the first real menu is where the enum earns its keep or turns out to
  need a fifth value.
- **Open: a scope cannot say "the other axis leaves the scope".** ARIA's tab list
  moves focus into the panel on `Down`; here that is the widget's job to
  implement by consuming the key, and it has no way to ask the router to move
  focus somewhere outside the scope. `tabs` is where that gets faced.
