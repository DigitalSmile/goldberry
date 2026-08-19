# 152. The cascade looks at rules that could match

Date: 2026-08-19

## Status

Accepted. What [ADR-0146](0146-a-hud-shows-where-the-frame-went.md)'s breakdown
and [ADR-0151](0151-a-frame-can-say-what-it-did.md)'s counters found once
[ADR-0149](0149-a-state-invalidates-what-it-can-reach.md) had stopped the tree
re-resolving on every click.

## Context

With the invalidation narrowed, a click on empty space re-resolved **one or two**
elements — and `style` was still 1.5 ms. The trace said why in one line:

```
style 8.636 ms | elements 125, resolved 3 | cascade 5.133 identity 0.956 motion 0.176 boxes 1.467
```

**5.1 ms of cascade for three elements.** One style resolve cost 1.7 ms, and two
things were doing it:

- **Every rule in every sheet was matched against every element.** The showcase
  loads `controls.css`, a theme, a density sheet and its own — some two thousand
  rules — and `SelectorMatcher` was asked about all of them for a `text` node as
  readily as for a `button`.
- **Custom properties are collected by walking to the root**, and each level ran a
  full cascade. One node at depth ten was eleven cascades. `resolve` then ran a
  twelfth, because it asked for the custom properties and the declarations
  separately and both cascade the element.

ADR-0070 measured this term and cached its *result*; it never made the term
itself cheaper, and every cache miss paid the full price.

## Decision

Three changes, all inside `StyleResolver` except the last.

**Rules are bucketed by the type their rightmost compound names.** A selector's
rightmost compound is the one that must match the element being styled, so a rule
for `button` cannot apply to a `text`. Rules whose subject names no type —
`.primary`, `#gain`, `*` — go in an untyped bucket that is always tested, and a
rule with several selectors goes in every bucket any of them names, because a
rule is a unit and the cascade has to see it whole. Over-collecting is the only
safe error here.

**Custom properties are cached per element**, on the same scheme the computed
style already uses one level up (ADR-0070): keyed on the resolver and on the map
the parent handed down, both by identity. An unchanged parent therefore keeps
every entry below it valid without anything having to tell them. A node that
declares none hands down its parent's own instance, so a chain of nodes that
define nothing shares one — which is what keeps the identity stable through the
composition nodes that make up most of a tree.

**`resolve` cascades the element once.** It computes the declarations and hands
them to the custom-property collection, instead of each asking for its own.

Measured on the showcase's Controls screen: **one resolve 1.7 ms → 0.13 ms**, the
median cascade on a click frame **0.48 ms → 0.26 ms**, and a cold render of a
whole screen **112 ms → 52 ms** — which is what a tab switch pays.

## Consequences

**`StyleElement` grew two default methods.** They default to "no cache", so a
hand-written element in a test is unaffected and correct. The cache is on the
element because that is where the lifetime is: a map keyed by element inside the
resolver would outlive the elements it was about.

**The buckets are only as good as the stylesheet.** A sheet written entirely in
classes puts every rule in the untyped bucket and gets nothing. The toolkit's own
sheets are type-first, and this is a reason to keep writing them that way.

**Invalidating a style now invalidates the custom properties with it.** They are
matched by the same rules, so what changes one changes the other.

**The remaining term is the HUD measuring itself.** Three paragraphs a frame are
re-shaped in the showcase, and they are the HUD's own readings: strings that
change every frame cannot be cached by a cache keyed on the string. It cannot be
taken out of the measurement without lying about the frame the window painted, so
the caption says `this hud included` instead — which is
[ADR-0101](0101-a-diagnostic-must-not-be-the-thing-it-measures.md)'s rule kept by
being honest rather than by pretending.
