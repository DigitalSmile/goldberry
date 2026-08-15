# ADR-0004: Three-tree retained declarative model

- **Status:** Accepted (recorded retroactively)
- **Date:** 2026-08-15
- **Relates to:** `docs/ARCHITECTURE.md` §5, §11

## Context

The programming model is the part of a UI toolkit that users touch every day, and
it is the hardest thing to change later — it determines what every widget, every
piece of app code, and every test looks like.

Two families were realistic. *Immediate mode* rebuilds and redraws the whole UI
each frame: trivially simple state handling, but it re-does layout and paint
work constantly, and it fits poorly with accessibility trees, retained input
focus, and CSS-style cascading — all of which assume a persistent node identity.
*Retained mode* keeps a node tree: efficient and accessible, but classic retained
OO toolkits (Swing, SWT) push mutable state into the widget objects themselves,
which is the source of most of their bugs.

Flutter's answer is to split the difference, and it has been proven at scale.

## Decision

Adopt the three-tree model:

1. **Widgets** — immutable Java records with a pure `build()`. Cheap to
   construct, cheap to throw away, diffed by type and key.
2. **Elements** — the mutable instantiation of a widget. Holds state, owns
   lifecycle, and is what persists across rebuilds.
3. **Render objects** — one per visual node. Owns a `YGNode`, a `ComputedStyle`,
   and the paint logic.

App authors write in the declarative widget layer and mostly never see the other
two. The element tree is what gives node identity to focus, semantics, and
animation; the render tree is what layout and paint operate on.

## Alternatives considered

- **Immediate mode (Dear ImGui-style).** Rejected: incompatible with a real
  accessibility tree (§13), with retained focus (§7.2), and with CSS cascade and
  transitions (§8). It also burns CPU continuously, which contradicts ADR-0002's
  premise that CPU rasterization is affordable *because* frames are rare.
- **Classic retained OO (Swing/JavaFX-style mutable widgets).** Rejected:
  mutable widget graphs make state and invalidation the app author's problem, and
  the resulting bug class is well documented across thirty years of toolkits.
- **Two trees (widget → render object, no element layer).** Rejected: without a
  persistent middle layer there is nowhere to hang state and lifecycle across
  rebuilds, and node identity has to be reconstructed by matching, which is
  exactly the fragile part.

## Consequences

- Rebuilds are cheap, so app code can rebuild freely; the diff decides what
  actually changes. Layers and damage tracking (§5) build naturally on top of
  render-object identity.
- Three trees is genuinely more machinery than two, and the widget/element/render
  distinction is the main thing new contributors have to learn.
- Java records give immutable widgets with no boilerplate, which is what makes
  this model pleasant in Java rather than merely possible.
- **Open, and the largest gap in the current design:** the state and rebuild API.
  `docs/ARCHITECTURE.md` says elements hold state and mentions a `Property<T>` in
  §9, but the stateful-widget lifecycle, the rebuild scheduling, and how a state
  change marks the tree dirty are not specified. This is the API every user
  touches and it needs its own record before M2.
