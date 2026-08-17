# ADR-0070: The cascade resolves invalidated nodes, and invalidation is a subtree

- **Status:** Accepted
- **Date:** 2026-08-17
- **Relates to:** `docs/ARCHITECTURE.md` §5, §8; builds directly on
  [ADR-0069](0069-the-render-tree-is-retained.md); leans on
  [ADR-0052](0052-state-lives-on-the-element-and-rebuilds-are-deferred.md) for
  node identity and on [ADR-0049](0049-the-css-engine-stops-at-computedstyle.md)
  for what a resolved style is

## Context

`docs/ARCHITECTURE.md` §5 has described the frame loop the same way since before
any of it was built:

> → **style resolution (invalidated nodes)** → Yoga layout (incremental)

The parenthesis was aspirational. What ran was style resolution of *every* node,
every frame: for each one, `StyleResolver.cascade` matched every selector in
every stylesheet right-to-left with backtracking, and `customPropertiesFor`
walked to the root doing the same cascade again at every ancestor — so a node at
depth *d* against *R* rules cost *O(d × R)* selector matches, per frame.

It did not matter while the Yoga tree was rebuilt every frame, because that was
the same order of cost. Once [ADR-0069](0069-the-render-tree-is-retained.md)
took layout from 190 µs to 7 µs, the cascade was **135 µs of the 148 µs** left —
the whole frame, essentially, and by a wide margin the next thing to measure.

Split three ways on the same showcase-shaped tree (15 elements, linux-x64):

| | median |
|---|---|
| `resolve` — selector matching and `var()` | ~330 µs |
| of which `customPropertiesFor` alone | ~140 µs |
| `ComputedStyle.of` — tokens to typed values | ~48 µs |

(Measured in a tight loop, so the absolute numbers run high against the same work
inside a frame — [ADR-0045](0045-a-frame-is-not-a-benchmark-iteration.md)'s
effect. The *split* is what they are for: matching dominates, and the walk to the
root is a large part of it.)

## Decision

**A node's resolved style is cached on its element, and thrown away when
something that could decide it changes.** Elements already persist across
rebuilds and already carry the pseudo-classes the cascade reads
([ADR-0052](0052-state-lives-on-the-element-and-rebuilds-are-deferred.md)), so
they are the only place with the right lifetime.

The cache is checked against two things, both **by identity**:

### 1. The resolver — which makes a theme swap free

An application changing theme builds a new `WidgetRenderer` over the new
stylesheets. Every cached style was produced by the old resolver, so every entry
misses at once. There is no invalidation call anywhere, no "stylesheets changed"
event to remember to fire, and no way for a hot reload to leave a stale style
behind. The same is true of the reload path in
[ADR-0051](0051-kdl-is-parsed-here-and-reloading-is-forgiving.md).

### 2. The inherited style — which makes inheritance invalidate itself

A child caches against the *instance* it inherited from. Because the parent's
style is cached too, an unchanged parent hands down the same instance every
frame; a parent that re-resolved hands down a different one, and its children
re-resolve without anything telling them to. `color` and the typography inherit
([ADR-0066](0066-a-weight-is-a-face-and-color-inherits.md)), and this is the
whole of keeping them correct.

### 3. Invalidation is a **subtree**, and that is not conservatism for its own sake

`Element.setPseudoClass` invalidates the node **and everything under it**. The
cheaper thing — invalidate only the node whose state changed — is wrong, and
wrong silently:

```css
checkbox:hover check-indicator { border-color: var(--gb-checkbox-border-hover) }
```

Hovering the checkbox restyles the **indicator**. The checkbox's own resolved
style need not change at all, so the inherited-identity check in (2) cannot see
it, and the indicator would keep a stale style for the life of the window. That
rule is not hypothetical — it is in `controls.css` today.

The same applies to a rebuild: a new widget can carry different classes or a
different id, which changes what matches it *and*, through a descendant
combinator, what matches anything below it. `Element.update` invalidates the
subtree rather than diffing attributes, because a comparison that missed a case
would produce a node styled by a rule that no longer applies to it.

Working out which descendants a given rule could actually reach is real
machinery — an invalidation set per rule, keyed by the selector's rightmost
compound. It would be worth building if invalidation were hot. It is not: the
walk is pointer-chasing over the element tree against a cascade pass that costs
hundreds of times more, and it happens at most once per pointer move.

### 4. One hook, not six

Every route that can change what a selector matches goes through
`setPseudoClass`: `:hover` and `:active` from the router, `:focus` and
`:focus-visible` from focus traversal, `:disabled`, `:checked` and
`:indeterminate` mirrored from the widget by the renderer. So that is the single
place that invalidates, rather than six places that each have to remember.

It only fires on an **actual change**, and that matters more than it looks:
`WidgetRenderer` mirrors three pseudo-classes onto every styled element on every
frame. If a redundant set invalidated, the cache would miss on every frame for
every control and this record would be worth nothing. There is a test for
exactly that.

## Consequences

- **The CPU a frame spends before rasterizing falls from 148 µs to 3.5 µs**, and
  the cascade half of it from 135 µs to about 2.5 µs. Taken with ADR-0069 that is
  **354 µs → 3.5 µs**, a factor of a hundred, for a static frame of a
  15-element tree.

  | frame CPU before rasterization | median |
  |---|---|
  | before any of this | 354 µs |
  | with the paragraph cache | 260 µs |
  | with the retained render tree | 148 µs |
  | with the invalidation-driven cascade | **3.5 µs** |

- **Rasterization is now the frame.** With Blend2D pinned to one thread a whole
  frame is about 320 µs at 960×640, essentially all of it painting. That is what
  damage tracking and layer promotion are for, and after two rounds of this it is
  the honest next target — there is nothing else left of consequence.
- **Layout got faster too, for free.** `layout + walk` with a fresh box tree each
  frame fell from 7.2 µs to about 4.2 µs, because a cached `ComputedStyle` hands
  `Box.style` the *same* `Decoration`, `Transform` and `Insets` instances every
  frame, so ADR-0069's guards compare equal on a reference check instead of
  field by field.
- **Stale styles are the new failure mode**, and they are silent: a stale style
  is a perfectly valid style. `StyleCacheTest` is therefore written in pairs —
  one test that the cache is used, one for each way it has to be dropped — and it
  ends with an equivalence test asserting that a renderer ten frames warm agrees
  with one seeing the tree for the first time.
- **A composition node caches nothing**, because the renderer passes its
  ancestor's style straight through rather than resolving one. So a null cache on
  a node says nothing about the subtree below it, and `invalidateStyle` must not
  short-circuit on null. It does not, and there is a test that fails if it ever
  does.
- **`WidgetRenderer.resolver()` is package-private and exists for the test.** The
  alternative was asserting on colours, which would also be right for the wrong
  reason — a cache that never hit would pass every behavioural test in the file.

### What this does not do

- **`customPropertiesFor` still walks to the root** and re-runs the cascade at
  every ancestor when it does run. It is now amortised almost to nothing by the
  cache, but the first frame and every invalidated subtree still pay it, and it
  is *O(depth × rules)* where it could be *O(rules)* with the same caching
  applied per ancestor. Worth doing when a deep tree makes a first frame
  visible.
- **Nothing measures the hit rate in a real application.** The benchmark shows
  what a static frame costs; a window where the pointer is moving invalidates a
  subtree per motion event, and no number says how often that is. A counter on
  the renderer would be the cheap way to find out.
- **The invalidation is per element, not per property.** A `:hover` that changes
  only `background-color` re-resolves the whole style, including the typography
  and the layout half that no `:hover` rule in the toolkit touches. Splitting the
  cache by property would be a much finer instrument and is not obviously worth
  its complexity.
