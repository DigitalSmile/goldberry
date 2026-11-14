# ADR-0680: Every button clicks, and any node takes a click

- **Status:** Accepted
- **Date:** 2026-11-09
- **Relates to:** the hover hooks on `Attributes` (`onPointerEnter`, `onPointerExit`), `pressable`

## Context

A downstream game gives the secondary button meanings everywhere: take a bar
of power away, toggle a door, cancel targeting. `PointerRouter` synthesised a
click only for the primary button, so a right press dragged off its target
and released still counted. The game also needed clicks on panels, rows,
tiles and a `canvas3d`, and `Attributes` offered hover hooks and drag and drop
but no click. Each clickable thing needed a wrapper widget of the game's own:
one more box in the tree, and code to keep in step with the input API.

## Decision

**`PointerEvent.Kind.AUX_CLICKED`.** A secondary or middle press and release on
the same node, by the rule `CLICKED` follows (released on the pressed node or
inside it, not dragged), is an `AUX_CLICKED`, and `button()` says which.
`CLICKED` stays primary only, as the DOM's `click` does beside `auxclick`.
Every control that acts on `CLICKED` means the primary button, and the
alternative, `CLICKED` for every button, would have had all 41 of them start
acting on right clicks. A secondary press on a node that names a context menu
is still taken by the menu and is no click.

**`Attributes.onClick(Consumer<PointerEvent>)`**, and `Attributed.onClick`, on
any widget:

- It hears `CLICKED` and `AUX_CLICKED`, with `local()` re-pointed at the
  node's own box, so the fraction of the box is in the event.
- It runs as the click bubbles, after the node's own handler, and consumes
  the click, so an ancestor's `onClick` does not hear it too.
- A control inside the node that consumes the click first keeps it.
- A node with only an `onClick` is not a Tab stop and has no role. A control
  a keyboard must reach is still a `pressable` or a `button`. The owner chose
  that trade on 2026-11-08: the box that is styled is the box that is
  clicked, and accessibility stays with the controls.

## Consequences

- `Attributes` gained an eleventh component. The ten-argument constructor is
  kept, and every wither carries `onClick` along.
- Markup has no `on-click` yet: a handler is not a KDL value, for the reason
  the hover hooks give.
- `WheelAndCaptureTest.secondaryDoesNotClick` now also asserts the
  `AUX_CLICKED` it gets. `ClickHookTest` covers the aux clicks, a right press
  dragged off, `onClick` on a plain node with its fractions, consumption
  against an ancestor, and an inner control keeping its click.
