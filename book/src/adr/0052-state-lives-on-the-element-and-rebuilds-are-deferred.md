# ADR-0052: State lives on the element, and rebuilds are deferred

- **Status:** Accepted
- **Date:** 2026-08-16
- **Relates to:** `docs/ARCHITECTURE.md` §5, §8, §11; [ADR-0004](0004-three-tree-retained-declarative-model.md), [ADR-0020](0020-one-ui-thread-and-virtual-threads-behind-it.md), [ADR-0047](0047-a-frame-nobody-sees-costs-full-price.md), [ADR-0049](0049-the-css-engine-stops-at-computedstyle.md)

## Context

[ADR-0004](0004-three-tree-retained-declarative-model.md) chose the three-tree
model and then said so, in its own consequences:

> **Open, and the largest gap in the current design:** the state and rebuild API.
> […] the stateful-widget lifecycle, the rebuild scheduling, and how a state
> change marks the tree dirty are not specified. This is the API every user
> touches and it needs its own record before M2.

This is that record. It covers the widget shapes, where state lives, what
`setState` does, when rebuilds happen, and how the element tree became the thing
the CSS cascade talks to.

## Decision

### Three widget shapes, not one

`Widget.Stateless`, `Widget.Stateful`, `Widget.Leaf` — three interfaces rather
than one with nullable methods, so the switch in `Element.describe()` is
exhaustive and a widget that implements none of them fails loudly instead of
rendering as nothing.

`Leaf` is toolkit-facing: it produces children directly and paints. `Stateless`
and `Stateful` are what applications write.

### State lives on the element, and is created once

`createState()` runs when an element is first mounted, never on a rebuild. That
is the entire reason the element layer exists — ADR-0004's rejection of the
two-tree model was that "there is nowhere to hang state and lifecycle across
rebuilds".

`State.widget()` is re-read rather than captured, because a rebuild can hand the
same state a **new widget value** when a parent re-describes it with different
arguments. `didUpdateWidget(previous)` is the hook for reacting to that.

`setState` after `dispose()` **throws**. A callback that outlived the widget that
registered it is a leak, and the alternative — silently doing nothing — is the
version nobody finds.

### `setState` mutates now and rebuilds later

The mutation runs immediately; only the rebuild is deferred. Code after
`setState` sees the new value, which is what every author expects, while the
build is coalesced with everything else in the frame. Ten `setState` calls in one
handler cost **one** build, which is asserted.

This is the same shape as the rest of the toolkit's frame discipline: a repaint
request is coalesced ([ADR-0024](0024-a-repaint-must-wake-the-loop.md)) and a
frame is paced to the display ([ADR-0047](0047-a-frame-nobody-sees-costs-full-price.md)).
Rebuilding inside `setState` would mean a handler that touches three fields
builds three times and paints frames nobody sees.

### Flush is shallowest-first, and gives up

`ElementTree.flush()` sorts dirty elements by depth before building. A parent's
rebuild can replace a child's whole subtree, so building the child first is work
thrown away — or worse, a build on an element about to be unmounted.

A `setState` *during* a build is legal and has to settle before the frame paints,
so flush repeats. It stops after ten passes and **warns**: a build that dirties
itself every pass is an application bug, and a frozen window with nothing in the
log is a terrible way to report one.

### Reconciliation is by type and key, and keys win

Type mismatch or key mismatch replaces the element. Keyed children are matched by
key wherever they moved; unkeyed ones by position.

The subtle rule, and the one with a test named after it: **an unkeyed description
may not adopt an element that a key claimed.** Without that, reordering
`[keyed, plain]` into `[plain, keyed]` lets the unkeyed description at position 0
steal the keyed element, and two nodes silently swap their state.

### The element tree is what the cascade talks to

`Element implements StyleElement`. This is what makes
[ADR-0049](0049-the-css-engine-stops-at-computedstyle.md)'s engine usable: the
cascade asks an element for its type, classes and ancestors and gets answers that
survive a rebuild. Pseudo-classes live on the **element**, not the widget, so a
button does not stop being hovered because its parent re-described it.

`Element.type()` returns **null** for a widget that is not `Styled`, and that is
deliberate. An earlier version derived a kebab-case name from every widget class,
which would have put every private composition class into the cascade as a
selectable type — renaming an internal `Wrapper` would break a stylesheet that
never named it. A node with no type matches no type selector and carries no
classes, so it is invisible to everything but a descendant combinator passing
through it, exactly as an unstyled `<div>` is.

## Alternatives considered

- **Hooks, in the React sense.** Rejected: they need a stable call order per
  build and a scheduler that owns the notion of "current component", both of which
  are far more machinery than a mutable object on an element that already exists.
- **`setState` rebuilds immediately.** Rejected above — it defeats coalescing and
  it makes the cost of a handler proportional to how many fields it touches.
- **Rebuild the whole tree from the root each frame.** Simple, and it throws away
  the reason for having an element tree. Also quadratic in depth for a leaf-level
  change.
- **State as an observable `Property<T>` only**, with no `setState`. §9 does want
  a `Property<T>` for KDL's `bind`, and it will be built on this rather than
  instead of it: a property that marks its element dirty is exactly `setState`
  with a nicer face.
- **Deep-first flush.** Rejected: a shallower rebuild can unmount the deeper
  element that was about to be built.

## Consequences

- **`bind`, focus and semantics now have somewhere to live.** All three need node
  identity across rebuilds, which is what the element tree provides and what
  §7.2's retained focus and §13's accessibility tree were waiting for.
- **`flush()` has no caller in a window yet.** The frame loop does not consult
  `needsBuild()`, because there is no widget-driven window to consult it for. The
  hook is one call and it lands with the first control.
- **Nothing renders yet.** ADR-0004's third tree — render objects owning a
  `YGNode` and a `ComputedStyle` — is not here. `Element` produces no `Box`. That
  is the next piece, and it is the one that makes the parity invariant testable,
  because it needs widgets that actually paint.
- **The `Element` API is wider than an application should need.** `rebuild()`,
  `update()` and `unmount()` are package-private; `markNeedsBuild()` and
  `setPseudoClass()` are public because input and animation will call them from
  outside. If that turns out to be the wrong line it is a cheap one to move.
- **`Widget.key()` is `Object`, compared with `equals`.** A `String`, an
  `Integer` or a record all work. Typed keys would be tidier and would make the
  common case — a list index or an entity id — noisier to write.
