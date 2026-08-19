# 142. A style handed down keeps its identity

Date: 2026-08-19

## Status

Accepted. Repairs [ADR-0070](0070-the-style-cache-and-what-it-cost.md)'s cache,
which had a hole in it from the day [ADR-0099](0099-an-indicator-travels-on-a-grid.md)
added the `inline` seam.

## Context

The frame was costing 10–15 ms in a window where nothing had changed, and worse
with a `tour` or a menu on screen. The obvious suspects — the popup's second
window, the veil, damage tracking — were all innocent. Measured on the showcase's
own tree, one `render` of a settled screen was **10 069 µs for 77 elements**, and
counting cache lookups said why: **56 of 72 styled elements missed on every
frame**, and every one of them missed for the same reason.

ADR-0070's cache is keyed on two things by identity: the resolver, and *the style
this node's parent handed down*. The second half is what makes inheritance
invalidate itself — an unchanged parent hands down the same instance, so its
children keep their entries, and a parent that really changed hands down a
different one and its children re-resolve without anything having to tell them.

The style a parent hands down is not the one it cached. [`Styled#restyle`] runs
**after** the cache — deliberately, because a widget-computed value must not be
cached (ADR-0099) — and it returns a whole `ComputedStyle`. Every widget that
writes an inline value therefore allocates a new one on every frame, whether or
not anything in it moved:

```java
// ScrollContent
public ComputedStyle restyle(ComputedStyle resolved) {
    var style = resolved.flexShrink(0);      // a new instance. Every frame. Always.
    …
}
```

So every node under a `scroll`, a `tab` or a `segmented` re-resolved on every
frame. In the showcase every screen is inside a `scroll`, which is to say: the
whole window.

## Decision

**A node hands its children the same `ComputedStyle` instance for as long as that
style keeps its value.**

```java
self = element.stableStyle(styled.restyle(self));
```

`Element.stableStyle` holds the last style this node handed down and returns it
again when the candidate is equal to it. `ComputedStyle` is a record of records,
enums and primitives, so that is a flat value comparison — against a re-resolve
that costs two orders of magnitude more.

The stored instance is deliberately **not** cleared by `invalidateStyle`. It is
not a cache of this node's answer; it is the identity its children are keyed on,
and dropping it would make every descendant re-resolve after an invalidation that
changed nothing they can see. A stale one that no longer matches is simply
replaced.

Measured, one render of a settled screen:

| screen | before | after |
|---|---:|---:|
| Controls | 10 069 µs | 294 µs |
| Values | 8 125 µs | 126 µs |
| Text | 2 607 µs | 50 µs |
| Overlays | 2 923 µs | 17 µs |
| Tabs | 5 036 µs | 22 µs |

## Consequences

**The cache now works where it was written to work.** ADR-0070 claimed style
resolution was the largest term in a frame and cached it; the claim was right and
the cache was reachable only by a tree with no inline value anywhere in it, which
the showcase stopped being the day `scroll` shipped.

**A widget's `restyle` no longer has to be careful.** It may allocate freely and
return a fresh style every time — which is the natural way to write one, and what
all seven of them do. The identity discipline the cache needs lives in one place
rather than in every widget that writes a value.

**A style that really moves still moves.** Scrolling changes the transform, so
the instance changes and the subtree re-resolves — the same conservative
behaviour as before, and the case where it costs something. Narrowing that to
"only the *inherited* properties changed" would fix scrolling too, and is not
done here: it needs a notion of which properties inherit, which the cascade has
and `ComputedStyle` does not, and inventing one for a case nobody has reported
would be guessing at the next problem while this one is measured.

**The test asserts the mechanism, not a duration.** `StyleIdentityTest` builds a
widget whose `restyle` allocates — the exact shape of `ScrollContent` — and
asserts its child is handed the *same instance* on the second frame, and a
different one when the value really changes. A timing test would pass on a fast
machine with the bug still in it.
