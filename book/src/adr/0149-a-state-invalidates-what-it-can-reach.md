# 149. A state invalidates what it can reach

Date: 2026-08-19

## Status

Accepted. Narrows [ADR-0070](0070-the-style-cache-and-what-it-cost.md)'s
invalidation, which was conservative by an amount nobody had measured until
[ADR-0146](0146-a-hud-shows-where-the-frame-went.md) put the number on screen.

## Context

Reported as "if I just click on empty space the paint and styling keep adding a
lot of ms each click". Measured through the real launcher, on the showcase's own
tree: **74 of 78 elements re-resolved on every click**, and `style` sat at 12 ms
a frame.

`:hover` and `:active` apply to the whole ancestor chain — `.card:hover .title`
has to work — so a click on empty space marks every node from the deepest one to
the root. Each of those called `Element.invalidateStyle`, which throws away **the
whole subtree's** cached styles on the grounds that a descendant combinator can
make a node's match depend on an ancestor's state. For a node near the root, that
subtree is the window.

ADR-0070 said so plainly: "Conservative on purpose. Working out which descendants
a rule could reach is real machinery, and this walk is pointer-chasing against a
cascade pass that costs hundreds of times more." The walk is cheap; what it
triggers is not, and it triggered all of it.

## Decision

**A state change invalidates the subtree only when some rule can read that state
through a descendant combinator, on a node of this type.**

`StyleResolver` walks every selector once, at construction, and records which
pseudo-classes appear to the *left* of a combinator and on what type:
`checkbox:hover check-indicator` puts `checkbox` under `:hover`. Then

```java
if (resolver.reachesDescendants(pseudoClass, type())) { … invalidate the subtree }
```

Two answers are deliberately conservative. An ancestor compound naming **no
type** — `.section:affixed > affix-content`, in the showcase's own sheet — cannot
be narrowed, so its pseudo-class reaches everything. And a resolver that has not
been seen yet reaches everything, which is the first frame.

**A node with no CSS type reaches nothing**, and getting this wrong is what made
the first attempt worthless. The hover chain is full of composition nodes; a
compound that could match one names no type either, and every such compound is
already in the untyped set. Returning "unknown, be conservative" for them left
the whole tree re-resolving with the narrowing in place and the measurement
unchanged.

**The resolver comes from the tree, not from the node.** A node's own resolver is
half of its cache key and is cleared when the entry is thrown away — so the
question could not be asked at the moment it matters, which is a press whose
release arrives before the next frame. `ElementTree` carries the resolver the
renderer is using; that is a fact about the tree, and invalidating a style does
not change it.

Measured, clicking empty space on the showcase's Controls screen: **74 elements
re-resolved per click → 3**, and `style` from 12.4 ms → 2.5 ms.

## Consequences

**The remaining 2.5 ms is transitions, not the cascade.** `controls.css` puts
`transition` on most controls, and a click that changes `:hover` and `:active`
genuinely starts and settles them. That is work the frame asked for.

**A stylesheet can make this expensive again**, and honestly so: writing
`column:hover x` puts `column` back in the set for `:hover`. That is the rule
being paid for by the rule that needs it, which is the right way round.

**Only the state path is narrowed.** `Element.update` — a rebuilt widget with
possibly different classes — still invalidates the subtree wholesale, because
what changed there is the node's identity to the cascade rather than one bit of
its state.

**The test asserts by identity, not by time.** `StyleIdentityTest` marks `:hover`
on a parent no rule reads through and asserts the child was handed the *same*
`ComputedStyle` instance — the thing the cache is keyed on — and asserts the
opposite with `poisoner:hover recorder` in the sheet.
