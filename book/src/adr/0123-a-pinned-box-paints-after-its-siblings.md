# 123. A pinned box paints after its siblings

Date: 2026-08-19

## Status

Accepted. Amends [ADR-0053](0053-the-render-tree-is-a-box-tree-for-now.md)'s "no
z-order" in one narrow place, for
[ADR-0119](0119-a-widget-may-be-told-where-it-is.md)'s `affix`.

## Context

`affix` pinned correctly and was unreadable. The header stopped at the viewport's
edge exactly as §1 asks and the rows of its own section slid straight over the
top of it.

`AffixTest` passed throughout. Every assertion it makes is about a *position* —
where the hole is, where the content is, whether `:affixed` is on — and every one
of them was true the whole time. What was wrong was **paint order**: a box tree
has none beyond document order (ADR-0053), the affix sits at index N of its
section, and the rows at N+1 are drawn afterwards.

A background does not fix it. The rows are painted later, so they paint over
whatever the header has.

This is not an exotic case. It is how `position: sticky` works in every browser,
and the reason it works is a rule this toolkit does not have: **a positioned
element paints above its non-positioned siblings.** CSS puts sticky headers in
the positioned layer, and that is the entire mechanism.

## Decision

`Box` gains one boolean:

```java
public Box elevated(boolean value)
```

A box that carries it is painted **after** its siblings. `AffixSlot` sets it
while, and only while, it is pinned.

### It is not a z-order, and ADR-0053 still holds

There is no stacking context, no `z-index`, and no ordering *among* elevated
siblings — they keep document order relative to each other. It is one bit meaning
"draw me last", which is the whole of what a pinned header needs and
substantially less than a layer.

Two passes over the child list rather than a sort: the flag is rare, the lists
are short, and a comparator would define an ordering between elevated siblings
that this deliberately leaves undefined.

### Layout is untouched

Yoga sees the children in the order it was given. Only the painter reorders. That
is what makes the flag safe to toggle every frame — a header lifting must not
relayout the list it is in, which would be the one thing worse than being painted
under it.

### The hit test reorders with the painter, or not at all

`RenderTree`'s two walks — the paint and the placed-box walk the hit test is
built from — both do the same two passes. They have to: a box drawn on top that
was not also *clicked* first would be a header you can see and point straight
through, which is a worse bug than the one this fixes because it is invisible
until someone tries.

## Consequences

`overflow` clips an elevated box exactly as it clips any other, because the clip
is accumulated on the way down and paint order does not change the walk's shape.
A pinned header is still confined to its viewport.

The flag is set by a widget and **not by the cascade**. No declaration says
`elevated`, and none should: it is a fact about what a widget is currently doing
rather than about how it looks, and a stylesheet that could set it would be able
to reorder painting from a hover rule.

`AffixGoldenTest` exists because of this. `AffixTest`'s eleven cases were all
true while the widget was unusable — the difference between "the header is at the
top of the viewport" and "you can read the header" is not visible to any
assertion about the tree, and an image is the only thing that can tell them
apart.
