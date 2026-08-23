# 179. A popup says what it measured

Date: 2026-08-23

## Status

Accepted. Closes two entries in `book/src/TODO.md` — the menu that capped itself
by estimate ([ADR-0118](0118-a-popup-that-does-not-fit-scrolls.md)) and the
`select` whose list was clamped rather than scrolled
([ADR-0141](0141-a-select-is-a-closed-control-and-a-list.md)).

## Context

`Host.popup(content, anchor, placement)` has always been three steps, and
`Host`'s own documentation has always said so: **measure**, **place**, **open**,
"and each is separately observable"
([ADR-0104](0104-a-popup-is-measured-then-placed.md)).

The first of the three was not observable at all. The facility measured the
content, handed the number to `Placement`, and opened; a caller that needed to
know how big its content came out had no way to ask. `Placement` then clamps
anything taller than the work area to the near edge — which keeps the top
visible and silently drops everything below it.

Two callers needed the number and neither could have it:

- **`Menus` guessed.** It decided whether a menu would be taller than the screen
  from its row count times an assumed 34px, and wrapped it in a `scroll` if so.
  The guess rounded *up* so that it erred towards wrapping a menu that would have
  fitted rather than clamping one that would not — an invisible viewport, but a
  thumb and a wheel handler with no business being there. `--gb-menu-item-height`
  is 32, so the number was also kept in two places, one of which is a stylesheet
  a widget cannot read.
- **`select` did not try**, and a list with more options than the display is tall
  lost its bottom. The same defect `menu` had before ADR-0118, still shipping in
  the control §3 most expects to be long.

One guess and one gap, with one cause.

## Decision

### The facility reports, between the measure and the place

A new overload takes a `Host.Fit`: a callback handed what the content measured
and the rectangle it has to fit inside, which answers with the content to open.

```java
host.popup(list, field, Placement.BELOW, width, new Fitted("select-viewport"));
```

Returning the same widget is the ordinary answer and costs nothing. Returning
anything else costs a second measurement — the element tree is thrown away and
rebuilt, because everything measured against the old content is worthless. That
is the right way round: nearly every popup fits, and only the one that did not
pays.

The cost is worth stating plainly, because
[ADR-0104](0104-a-popup-is-measured-then-placed.md) went out of its way to build
the tree once ("a second one would also be a second lot of `initState`"). It
still is, for every popup that fits. A popup that does not fit is being rewritten
by its caller, and a tree built from content nobody is going to open is not worth
keeping.

### The facility asks rather than deciding

Two reasons, and neither is new:

- **Whether content that does not fit should scroll or be clamped is a fact about
  the content.** A menu that lost its last three commands is the worst kind of
  wrong; a tooltip that scrolled would be absurd, and would rather have been a
  dialog (ADR-0118).
- **`:core` could not act on the answer anyway.** A viewport is a widget and
  `:core` has none ([ADR-0092](0092-a-primitive-is-a-widget-like-any-other.md)).

So the reporting is in `:core` and the policy is in `:widgets`, which is the
split the module fence already draws.

### One policy, held once

`Fitted` is the answer both callers give, in `widgets.core.scroll` beside the
`Scroll` it builds: content taller than the room becomes a viewport of the room's
height, and content that fits comes back untouched.

The 8px margin it keeps at each end came out of `Menus` and was never anything to
do with menus — a panel flush against the top and bottom of the screen looks like
one that has been cut off even when it has not. Two viewport classes rather than
one (`menu-viewport`, `select-viewport`) so that a stylesheet can tell them apart
without either inheriting the other's future; both are `flex-grow: 0` today.

## Consequences

- **`ROW_ESTIMATE` is gone**, and with it the second copy of
  `--gb-menu-item-height`. The end-to-end test measures a twenty-row menu at 667px
  where the estimate said 696 — close enough that the estimate was never *wrong*
  on a full-height display, and 29px of a menu that had to be needlessly wrapped
  on a short one.
- **A `select` list longer than the screen scrolls**, which is the user-visible
  defect this was written for.
- **`Placement` still clamps**, and that has not changed: a caller that opens an
  oversized popup and offers no `Fit` gets the old behaviour, which is right for
  a facility that cannot know what its content means. What changed is that the
  two callers who *could* know now have the number they need to say so.
- **`Host` gained a fifth `popup` overload**, which is one more than a surface
  this wide wants. The alternative was a standalone `Host.measure(Widget)`, and
  it was refused on cost: it would build a throwaway element tree on **every**
  popup — running `initState` twice for every menu and every dropdown — to serve
  the rare case where the answer matters. A callback pays only when the answer is
  acted on.
- **`TestHost` consults the `Fit`**, driven by a `measuring(width, height)` knob,
  because a `Fit`'s only observable is the widget its caller decided to open and a
  test without a window has nothing to measure with. Unset, no `Fit` is consulted
  and every test that predates this sees what it saw before.
