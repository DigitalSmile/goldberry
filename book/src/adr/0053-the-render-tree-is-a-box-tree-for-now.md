# ADR-0053: The render tree is a box tree, for now

- **Status:** Accepted
- **Date:** 2026-08-16
- **Relates to:** `docs/ARCHITECTURE.md` §5, §9, §11; [ADR-0004](0004-three-tree-retained-declarative-model.md), [ADR-0045](0045-a-frame-is-not-a-benchmark-iteration.md), [ADR-0046](0046-what-present-actually-does.md), [ADR-0049](0049-the-css-engine-stops-at-computedstyle.md), [ADR-0052](0052-state-lives-on-the-element-and-rebuilds-are-deferred.md)

## Context

[ADR-0004](0004-three-tree-retained-declarative-model.md) describes three trees.
Two of them are built: widgets, and the element tree of
[ADR-0052](0052-state-lives-on-the-element-and-rebuilds-are-deferred.md). The
third is specified as "one render object per visual node. Owns a `YGNode`, a
`ComputedStyle`, and the paint logic."

A `Box` already owns a style and `BoxPainter` already builds Yoga nodes from a
box tree — that pairing predates the widget layer and is what every rendering
test and every golden image runs through. Writing a second, retained render tree
now would mean two ways to get pixels out of a style, and the older one is the
one that is tested.

## Decision

**The render tree is materialized as a `Box` tree per frame, and this record says
so plainly rather than letting it look like the design.**

A widget that appears on screen implements `Paints`: given the `ComputedStyle`
the cascade resolved for its element and the boxes its children produced, it
returns its box. `WidgetRenderer` walks the element tree, resolves a style per
node, and assembles the result.

Three things fall out that are worth stating:

- **Composition nodes contribute nothing.** A `Widget.Stateless` describes others
  and produces no box, so the renderer passes through it. The box tree is
  therefore shallower than the element tree, and a wrapper costs nothing at paint
  time — which is what makes composition free enough to use liberally.
- **A widget owns the part of its layout that is its identity.** `Row` sets
  `flex-direction: row` after applying the style, so a stylesheet cannot turn a
  `row` into a column. Everything else — colour, padding, gap, size — is the
  stylesheet's. A name that a stylesheet can falsify is worse than no name.
- **`Spacer` defaults to `flex-grow: 1`** unless the cascade set a grow. Taking
  the free space is what a spacer is *for*, and requiring `spacer { flex-grow: 1 }`
  in every stylesheet would make the widget pointless.

## Alternatives considered

- **Retained render objects now, each owning a `YGNode`.** The end state, and
  premature. It duplicates `BoxPainter` while nothing yet measures the difference,
  and [ADR-0045](0045-a-frame-is-not-a-benchmark-iteration.md) exists precisely
  because this repository optimized against a number it had not taken.
- **Skip the render layer and paint from elements directly.** Rejected: paint
  would then need Yoga node lifetimes threaded through the element tree, and the
  element tree would own two responsibilities that invalidate on different
  schedules.
- **Let stylesheets set `flex-direction` on `row`.** Rejected above. It is the one
  property these widgets refuse to delegate.

## Consequences

- **The parity invariant is now enforced.** §11 requires every built-in to be a
  Java record, a KDL node and CSS-styleable, "enforced by test".
  `WidgetParityTest` iterates the built-ins and checks all three, including that
  a Java-built and a KDL-built widget are `equals` — which records make a
  checkable claim rather than a slogan.
- **A golden image now covers the whole stack.** `widget-tree.png` goes KDL →
  widgets → element tree → cascade → boxes → Blend2D. Six stages, one image.
- **The box tree is rebuilt every frame, and that is the cost to reclaim.** No
  render object survives a frame, so nothing knows what changed — which is
  exactly what damage tracking needs
  ([ADR-0046](0046-what-present-actually-does.md) measured it at about a
  millisecond) and what retained render objects would provide. That is the
  argument for building them, and it should be made with a measurement.
- **`Paints.Context` exists to be widened.** Today it offers a font. The display
  scale, an icon catalog and a text-measurement cache all belong there, and
  putting an interface in the signature now means adding them will not touch
  every widget.
- **Five primitives, not §11's full catalog.** `text`, `row`, `column`, `panel`,
  `spacer`. Enough to make the invariant testable and the stack demonstrable;
  `button`, `checkbox` and the rest need input, which does not exist yet (§7).
