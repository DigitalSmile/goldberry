# 315. A rebuild is not a restyle

Date: 2026-09-14

## Status

Accepted. Narrows [ADR-0070](0070-the-cascade-resolves-invalidated-nodes.md)'s
invalidation at the one caller that was throwing the cache away wholesale, and is
what [ADR-0313](0313-a-frame-pays-for-what-is-on-screen.md)'s measurements were
hiding.

## Context

ADR-0313 measured a *settled* frame of the icon sheet — a frame where nothing had
changed — and took the raster from 18 ms to 4.4. That was real and it was the
wrong frame. The complaint was *"the icon view is slow"*, and an icon view is slow
while somebody is **scrolling** it.

So: one wheel notch on the showcase's icon sheet, every stage timed, 1280×900,
one Blend2D thread.

| wheel frame, icon sheet | ms |
|---|---|
| flush (widget rebuilds) | 2.0 |
| **style** | **66.6** |
| layout | 2.7 |
| paint | 4.8 |
| hit-test snapshot | 2.6 |
| **total** | **78.6** |

Eighty milliseconds for a wheel notch, of which 85% is the cascade. A settled
frame of the same screen styled in 3.5 ms, so a wheel event made the style pass
**eighteen times** more expensive.

The cause is four lines that have been in `Element.update` since the element tree
was written:

```java
void update(Widget next) {
    var previous = widget;
    widget = next;
    invalidateStyle();      // this node's cached style AND its whole subtree's
    subscribeToBinding(previous);
    if (state != null) state.update(next);
    rebuild();
}
```

`invalidateStyle` recurses to every descendant and nulls its cached style. The
comment above it says why, and then says why it is unconditional:

> Invalidated wholesale rather than by comparing attributes: a rebuild is already
> the expensive path, and a comparison that missed a case would produce a node
> styled by a rule that no longer applies to it.

A rebuild *is* the expensive path when it rebuilds something. What a `scroll`
rebuilds is **two nodes** — the viewport and the content box, whose transform
changed — and the 4709 nodes underneath were being invalidated, re-described and
re-cascaded for a translation none of them can see.

## Decision

**Two guards at the top of `Element.update`, and one question in the middle.**

```java
void update(Widget next) {
    var previous = widget;
    if (next == previous) {                 // (1)
        if (needsBuild) rebuild();
        return;
    }
    widget = next;
    if (matchesDiffer(previous, next)) {    // (2)
        invalidateStyle();                  //     the subtree
    } else if (RESTYLES.get(next.getClass())) {
        invalidateOwnStyle();               // (3) this node only
    }
    …
}
```

### (1) The same description is not a description

A widget is a value. A parent that rebuilt for its own reason hands its children
back the very objects it was holding — `Scroll` keeps its `List<Widget>` in a
record field, so `ScrollContent` wraps the same instances and every tile under it
arrives at `update` identical to the one already there. The same instance
describes the same node with the same children: there is nothing to invalidate,
nothing to re-describe and nothing below it to walk.

A rebuild this element's own state asked for is still owed — `markNeedsBuild` put
it in the tree's dirty set and that is not the reason it is here.

This is Flutter's `child.widget == newWidget` short-circuit in `updateChild`,
arrived at from the same direction.

### (2) A selector can ask three questions about a node

`StyleElement` is deliberately the smallest set of questions a selector can ask,
and the list is short: `type()`, `id()`, `classes()`, `parent()` and
`hasState()`. `parent` cannot change here; `hasState` lives on the element and
survives a rebuild — that is what `setPseudoClass` is for.

So a re-description that leaves **type, id and classes** alone cannot change what
matches anything below it, and the subtree keeps its styles. That is exactly the
seam [ADR-0149](0149-a-state-invalidates-what-it-can-reach.md) opened — *"the narrow
half of `invalidateStyle`, for the caller that has asked whether the subtree can
be affected and been told no"* — arriving at the caller that needed it most.

The inherited half needs nothing added: a child's cache is keyed on the instance
its parent handed down, so a node whose own style really did change hands down a
different one and its children re-resolve because of it (ADR-0142, ADR-0248).

### (3) What is left is `restyle`, and almost nothing overrides it

If the selectors match the same, the *cascade* produced the same thing. The only
remaining way a re-description can change a style is `Styled.restyle`, which runs
after the cascade and reads the widget — §8's seam for a number no selector can
express, and the whole catalog overrides it twice.

`RESTYLES` is a `ClassValue<Boolean>`: reflection once per widget class, then a
field read. For every other node — a `text`, an `icon-tile`, a `row` — the answer
is `false` and the style survives the rebuild untouched.

## Alternatives considered

**Compare the widgets for equality.** It is the obvious generalisation of (1) and
it is quadratic: a `Row`'s `equals` walks its children, so comparing every node
against its predecessor compares every subtree once per level.

**Compare `type`/`id`/`classes` and always `invalidateOwnStyle`.** This is (2)
without (3), and it is what shipped first. It took the wheel frame from 78.6 ms to
15 ms and left the virtualized sheet re-cascading its whole window on every notch —
295 nodes at ~35 µs each, which is 10 ms of frame spent re-deriving styles that
could not have moved. (3) is what made the difference between "much better" and
"nothing to do".

**Teach the invalidation which rules could reach a descendant.** The real answer,
and real machinery: an index from a rule's ancestor part to the nodes it could
match. ADR-0070 called the subtree walk "conservative on purpose" and said the
same thing. Still true, and (2) makes it much less urgent — the walk now happens
only when a node's own selector surface changed.

## Consequences

**The measurement**, same machine, same notch:

| wheel frame, icon sheet | before | after |
|---|---|---|
| flush | 2.0 ms | 0.1 ms |
| style | 66.6 ms | 4.2 ms |
| **elements re-resolved** | **1556** | **4** |

**And it is not only the icon sheet.** Every screen in the gallery re-cascaded
everything under a `scroll` on every wheel event; the sheet is where it was big
enough to see. Any widget that rebuilds for its own reason — a `tabs` changing its
selection, a `text-input` blinking a caret — used to re-cascade everything beneath
it.

**`sameAppearance` grew five comparisons** in ADR-0313 and they are load-bearing
here too: `flex-wrap`, `align-self`, `limits`, `overflow` and `elevated`.

**What is now expensive to get wrong.** `matchesDiffer` is a list of what a
selector can ask about a node. A selector added to §8's subset that reads
something else — `:nth-child`, a sibling combinator, an attribute selector — makes
it incomplete, and the failure is a node keeping a style that no longer applies,
which is a perfectly valid style. `StyleElement`'s own comment already says the
subset stops where it does because *"every one of them forces the matcher to know
about ordering, and ordering is what makes invalidation expensive"* — this is that
sentence becoming load-bearing rather than explanatory.

The same is true of `RESTYLES`: it is a reflective question about one method, and
a widget that computed a style somewhere other than `restyle` would keep a stale
one. There is nowhere else to compute one, and `Paints.render` is documented as
the wrong place for exactly this reason (ADR-0099).

**Both are tested against the mechanism rather than against a colour.**
`StyleCacheTest` reads `Element.cachedStyle` directly — it is in the `widget`
package so that it can — and the three new cases are: same classes keeps the
subtree's cache, a `restyle`-ing widget is still invalidated, and an identical
widget is not a rebuild at all. Each fails without its guard.
