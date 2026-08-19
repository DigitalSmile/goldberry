# 140. A widget may reach its window

Date: 2026-08-19

## Status

Accepted. Answers the gap [ADR-0100](0100-a-window-has-a-layer-above-its-application.md)
left open — "an overlay cannot be raised from inside the tree" — and narrows
[ADR-0106](0106-a-menu-is-a-widget-and-opening-one-is-not.md) rather than
reversing it.

## Context

ADR-0106 is right and stays right: a `menu` is a widget and *opening* one is a
call, because opening needs a `Host` — something to measure the panel, ask the
platform for a window and close it again — and a widget is a value described
afresh every frame. One holding the window it is drawn in would be describing its
own surroundings.

`select` breaks the analogy at the one place it matters. Opening a menu is
something an **application** does: a menu bar, a toolbar button, a right-click
handler. Opening a dropdown is something the **control** does, and nobody else
can — a user clicking a `select` has not asked the application anything, and a
control that needed `Selects.open(host, …)` wired up per instance would be a
control that does not work when a document writes it:

```kdl
select bind="app.theme" change="app.pick-theme" { option value="dark" "Dark" }
```

There is no application code on that line to hold a host with.

Two shapes were already in the tree and neither fits. `Tour` takes a `Host` as a
record component, handed in by `Tours.start(host, …)` — fine for a widget only
Java builds, and impossible for one a document writes. And `Wiring` could have
carried a host to the inflater; that puts a window in the thing whose job is
resolving *names*, and would still leave a Java-built `Select` with no way to get
one.

`BuildContext` is where the answer was, and ADR-0100 said so: "what it wants is
Flutter's `Overlay.of(context)`". It was not built then because there was one
consumer, and an interface designed against one caller is designed twice
([ADR-0019](0019-the-backend-spis-first-cut.md)). There are two now.

## Decision

**`BuildContext.host()` returns the window an element is being built into, as an
`Optional`.**

```java
@Override
public Widget build(BuildContext context) {
    host = context.host().orElse(null);          // captured, not read
    return new SelectField(…, this::toggle, …);  // which uses it on a click
}
```

The window is held on the `ElementTree` and not on each element, because it is
the same answer for every node in one tree and a different answer in a popup's:
`new ElementTree(root, host)`. The launcher passes itself when it builds the
application's tree and when it builds a popup's, so a `select` inside a menu
opens against the window that owns the menu.

**For acting, not for reading.** `BuildContext` is documented as deliberately
narrow — a build must be a pure function of its widget, its state and the context,
so that anything reachable through it is something the framework can invalidate.
A host does not fit that rule if a build *reads* from it: `Host.anchor` answers
from the last painted frame, and nothing invalidates a build that depended on it.
What a build may do is capture the host for a handler that runs later, which is
outside the build entirely. The javadoc says so; nothing enforces it, for
[ADR-0136](0136-an-application-is-values-actions-views.md)'s reason — mechanically
enforcing a recommendation turns it into a rule nobody agreed to.

**Empty is a normal answer**, and the reason this is an `Optional` rather than a
nullable getter. A widget test builds `new ElementTree(widget)` with no window
behind it, and so does every golden image. A control that threw there could not be
drawn at all, and one that silently did nothing would be indistinguishable from a
bug — so the contract is stated: no window, no popup, and the control draws its
closed form.

## Consequences

**A widget can now open a platform window, and must close it.** A popup is not a
value and is not collected with the tree, so a state that opens one closes it in
`dispose()` — otherwise an element that goes away leaves a window parented to
nothing. That is the first resource a widget in this toolkit owns, and it is why
`SelectState` has a `dispose` at all.

**A press that dismisses a popup no longer also activates what it hit.** This
fell out of the first control to open its own popup and is a general rule the
toolkit was missing: with a list open, the press on the field that dismisses it
must not also be read as "open it", or the control toggles twice and never
closes. The launcher already took the press for the secondary button
([ADR-0108](0108-a-context-menu-is-a-name-on-a-widget.md)); it now takes any press
that actually closed something, which is what every desktop does — the click that
puts a menu away does not also press the button underneath it.

**`Popup.focusOn(String)` exists**, because a control that has already chosen
opens on the row it chose. See
[ADR-0141](0141-a-select-is-a-closed-control-and-a-list.md).

**`Menus` does not change.** Opening a menu is still an application's call and
still takes a host explicitly, because that is what a menu bar and a context menu
actually are. What has changed is that a widget which must open something for
itself now can, and `toast`, `dialog`, `date-picker` and autocomplete all want
exactly this door.

**A widget that reaches for `host()` when it wants an ancestor is doing it
wrong.** `findAncestorState` is still how a descendant asks the thing it is inside
for something ([ADR-0120](0120-a-widget-scrolls-itself-into-view.md)); this is for
the case where the answer is not in the tree at all, because a second platform
window is not in anybody's tree.
