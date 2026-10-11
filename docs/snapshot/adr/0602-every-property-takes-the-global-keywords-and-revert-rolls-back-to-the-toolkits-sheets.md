# ADR-0602: Every property takes the global keywords, and `revert` rolls back to the toolkit's sheets

- **Status:** Accepted
- **Date:** 2026-10-11
- **Relates to:** `docs/css-plan.md` (CSS-02, CSS-12), `docs/css-support-2026-10-10.md`
  §1

## Context

`inherit`, `initial`, `unset`, `revert` and `revert-layer` were dropped
everywhere but `flex: initial`. They are the commonest way to undo a theme's
rule, and the property switch in `ComputedStyle` could not read them: a
keyword means "copy this property from that style", and the switch knows how
to parse each value, not where each property lives.

CSS-02 built that second half, the property table: every longhand, whether it
inherits, and a copier that moves exactly its value from one style into
another. `revert` and `revert-layer` raise one more question that CSS answers
with cascade origins, and goldberry has none. It has four fixed layers
(`TOOLKIT_BASE`, `THEME`, `APPLICATION`, `INLINE`), and they break ties
**after** specificity rather than ranking above it.

## Decision

**`inherit`, `initial`, `unset` and `all` are read through the property
table, and the cascade replaces `revert` and `revert-layer` before a style is
computed.**

- `inherit` copies the parent's value through the table, for a property that
  inherits and one that does not alike: `padding: inherit` is the parent's
  four sides, and `padding-left: inherit` the one. At the root it is
  `initial`. `initial` copies from the initial style. `unset` is either by the
  longhand's `inherited` flag. A shorthand expands to its longhands. `all` is
  the shorthand of every longhand and takes only these keywords.
- In a keyframe, which has no parent to hand, `inherit` keeps the element's
  own value.
- **`revert` treats the toolkit's base sheets as CSS treats the browser's.**
  In `THEME`, `APPLICATION` or `INLINE` it rolls back to the strongest
  `TOOLKIT_BASE` declaration of the property, as if the theme and the
  application had said nothing. In a base sheet it is `unset`.
- **`revert-layer` rolls back to the strongest of the declarations that lost
  to it from a lower layer.** Since layers break ties here, "lower" means a
  lower layer among the declarations the cascade had sorted below it. A
  revert that reaches another revert keeps going down.
- With nothing to roll back to, a property is removed from the cascade's
  answer, which leaves it unset.
- A revert reaches declarations **of the same name** only. `padding-left:
  revert` does not find a `padding` shorthand below it. Per-longhand reverting
  would need the cascade to expand shorthands, and nothing has needed it.
- The cost lands only on a winning declaration that is a revert: the cascade
  notes one as it collects the winners, and only then walks back.

## Consequences

- A child that writes `inherit` on a property that does not inherit reads
  more of its parent than the inherited half, and the renderer's style cache
  compares a parent on that half alone, for good reason: a `scroll` moves its
  transform on every frame of a gesture. So `StyleResolver.inheritsExplicitly`
  says whether any sheet writes such an `inherit`, and when one does, the
  style a parent hands down is compared whole. A sheet set without one keeps
  the narrow comparison and its speed. `StyleIdentityTest` holds both.
- `ComputedStyle.applies` answers for a keyword as for any value: true when
  the property is in the table. Lint therefore stops reporting
  `padding: inherit` as dead.

## Status

Done 2026-10-11: `GlobalKeyword` and the `all` shorthand in
`dev.goldberry.css.property`, `ComputedStyle.declare`, the revert pass in
`StyleResolver.cascade`, the whole-style hand-down in `Element.stableStyle`,
and tests (`GlobalKeywordTest`, `StyleIdentityTest`, `PropertiesTest`, the
conformance table).
