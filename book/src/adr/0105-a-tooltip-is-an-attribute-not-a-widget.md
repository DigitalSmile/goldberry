# ADR-0105: A tooltip is an attribute, not a widget

- **Status:** Accepted
- **Date:** 2026-08-18
- **Relates to:** `docs/core-widgets.md` §7, builds on
  [ADR-0104](0104-a-popup-is-measured-then-placed.md), extends
  [ADR-0092](0092-a-primitive-is-a-widget-like-any-other.md)'s rule about what
  `:core` may ship

## Context

`docs/core-widgets.md` §7 describes `tooltip` in one sentence, and every clause
of it decides something:

> **`tooltip`** — attached by attribute (`tooltip="…"`) to **any** widget; shows
> on hover *and on keyboard focus* after delay; never focusable itself; plain
> text v1.

"Any widget" is the awkward part. Everything else in the catalog is a node
somebody writes; this is a property of every node that exists, including the ones
in an application's own module. And "after delay" needs something the toolkit did
not have at all: a timer.

## Decision

### The text lives on `Attributes`

`Attributes` already carries what every widget has and no widget decides — `id`,
`class`, the reconciler's key. A tooltip is the fourth of those, and putting it
anywhere else means every widget in the catalog carrying a field it never reads,
with thirty chances to forget one.

The record gained a component and kept its three-argument constructor, because
`new Attributes(id, classes, key)` appears in every widget and most of their
tests; a fourth positional `null` in four hundred places would be a worse change
than the one it avoids.

**Every wither had to be revisited**, and that was not obvious: `id()`,
`classes()` and `key()` all rebuilt the record and would have silently dropped the
new component. `new Target(...).tooltip("Save").id("target")` lost its tooltip,
and the symptom was a tooltip that never appeared — no error, nothing in a log.
The test that found it was already written and failing for what looked like a
timing reason.

### The loop grew a timer

`EventLoop.after(delay, action)` runs something on the UI thread later, and
shortens the next pump so the loop wakes for it. It is the loop's because the loop
is the thing that is asleep: a delay implemented by sleeping elsewhere would fire
on time and then wait up to a second for the pump to come back and notice.

Two consumers are named in the specification — a tooltip's delay and a submenu's
hover intent — and a toast's timeout is the third.

### The router says when, the launcher says what

The router knows what is hovered and what is focused, and opens nothing: it has no
window and no notion of one. The launcher owns the window and can open popups but
does not see input. So the router gained one hook, `onPointingChanged`, and the
launcher does the rest — cancel the pending delay, start a new one, and on firing
open a tooltip popup anchored to the element's painted rectangle.

**One listener, not a list.** A second would be a second thing deciding what a
hover means.

The target is found by walking **upwards** from the hovered element, because a
tooltip on a `button` has to survive the pointer being over the button's *label* —
which is a different element, and the one a hit test reports.

Hover wins over focus when both have one: reaching for the mouse is a more recent
statement of intent than the last thing tabbed to.

### The plate is a `:core` widget, and that is an exception with a reason

`TooltipPanel` is in `:core`, which [ADR-0092](0092-a-primitive-is-a-widget-like-any-other.md)
emptied of widgets. The exception is [WindowRoot](0100-a-window-has-a-layer-above-its-application.md)'s:
**nothing asks for this one.** There is no call site an application could pass a
widget to — the toolkit decides when a tooltip appears — and the thing deciding is
the launcher, which cannot see the catalog.

So `tooltip` is CSS-selectable and not KDL-constructible, like `window-root` and
like a part. Its *appearance* lives in `controls.css` with everything else that
has one, because where a type is declared and where it is styled are different
questions.

### It is never light-dismissed

A tooltip closes when the pointer leaves or focus moves, and **not** on a press.
A press that closed it would fire in the same gesture as the click on the thing it
is describing — closing it a moment before it was going to close anyway, and
taking the next tooltip's timer with it.

It also cannot end up under the pointer, by construction: it is placed outside the
anchor's rectangle and the pointer is inside it, whether it opens above or flips
below.

## Alternatives considered

- **A `Tooltipped` interface a widget implements.** Type-safe, and it makes
  "attached to any widget" false: every widget in the catalog would have to
  implement it, and a widget in an application's own module would have to know to.
- **A `tooltip` widget wrapping its target**, as some toolkits do. It puts an
  element between a node and its parent, so `panel > button` stops matching a
  button with a tooltip — the same argument that keeps `bind` on the widget rather
  than on a wrapper ([ADR-0062](0062-bind-is-a-path-and-nothing-else.md)).
- **The application opening its own tooltips**, with the toolkit supplying only
  the popup. It is a line of wiring per widget, and it puts the delay, the
  cancellation, the anchor and the dismissal in every application.
- **Sleeping on a virtual thread** instead of adding a timer to the loop. It works
  and it is one line; it also fires into a loop that may be parked for another
  second, which turns a 500 ms delay into anything up to 1.5 s.
- **A tooltip in the in-window overlay layer** rather than a popup window. Free of
  the platform, and clipped to the window — so a tooltip on a control near the
  bottom edge is cut in half, which is where tooltips most often are.

## Consequences

- **`Attributes` has four components**, and any code constructing one positionally
  with four arguments now has to mean it. The three-argument form still compiles
  and means "no tooltip".
- **The event loop has a timer**, which `menu` needs next for hover intent and
  `toast` will need for its timeout.
- **`PointerRouter` has one listener slot.** If a second consumer appears, this is
  where it will need a real listener list — and a decision about what it means for
  two things to react to one hover.
- **A tooltip does not follow the pointer**, does not have a maximum width beyond
  the window's, and is plain text — all three as specified for v1, and all three
  things `docs/core-widgets.md` §7 will want revisited when rich content arrives.
- **The delay is 500 ms and is not configurable.** §7 says "after delay" and does
  not say how long; a `--gb-tooltip-delay` token is a design-system question rather
  than an implementation one.
