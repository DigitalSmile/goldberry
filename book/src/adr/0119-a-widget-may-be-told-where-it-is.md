# 119. A widget may be told where it is

Date: 2026-08-19

## Status

Accepted. The third and last of the geometry facilities, after
[ADR-0116](0116-a-scroll-view-is-a-clip-an-offset-and-two-extents.md) (extents on
an event) and [ADR-0117](0117-a-widget-may-be-told-what-it-measured.md) (extents
once a frame). Builds on
[ADR-0114](0114-a-clip-is-a-rectangle-the-painter-carries.md), whose clip turns
out to be the other half of the answer.

## Context

`affix` is `docs/core-widgets.md` §1: a child pinned to an edge of the nearest
`scroll` "once the child would have scrolled past it", leaving a same-sized hole
behind so nothing below it jumps.

Every geometry facility so far carries a **size**. `Extent` on an event answers
"how big are these two boxes" when input asks; `Measured` answers "how big did I
turn out" once a frame. A scrollbar needed nothing else — a thumb's length is a
ratio of two heights and its position is a ratio of two offsets, both of which
the scroll view already knows.

`affix` needs a **position**, and one that no widget can compute. "Has this
header scrolled above the top of the viewport" is a comparison between where the
header was painted and where the viewport's top edge is. The first is a fact
about the layout *and* every transform above it; the second is a fact about a
node the header cannot see.

## Decision

### One more interface, answering the other question

```java
public interface Located extends Widget {
    void located(LogicalRect self, LogicalRect clip);
}
```

`self` is this widget's border box **as painted** — for a node inside a scroll
view, where it has been scrolled to rather than where it was laid out, which is
the whole point. `clip` is what the nearest `overflow` above it confines it to.

Delivered once a frame and only on a change, exactly as `Measured` is, from the
one place that holds the painted rectangles.

**The clip is why this is small rather than large.** The obvious implementation
of "the nearest scroll view's rectangle" is an ancestor walk with a cast, which
couples `:core`'s router to a widget in `:widgets` and answers nothing for a node
inside two of them. ADR-0114's clip is already exactly that rectangle, already
computed, already carried on every region for hit testing — so the question was
answered before it was asked, and the answer composes with nesting for free.

Nothing clips, and the router answers **the window's rectangle** rather than
null. An `affix` outside any scroll view is then pinned to the window, which is
what a toolbar at the top of a page means, and it costs a branch nobody has to
write.

### The rule, and the shape that enforces it

A widget told where it is must not move itself: it would be told a new position,
move again, and oscillate at the frame rate forever. That is `Measured`'s rule 3,
and it bites much harder here, because moving is precisely what `affix` wants to
do.

The way out is structural rather than a rule anyone has to remember. `affix` is
two nodes:

```
affix            the hole. Measured, and never moves
└── affix-content  the child. Translated by however far it has lifted
```

The outer node's position is a function of the layout alone, so the inner one
sliding under it changes nothing that is reported. The second frame reports what
the first did and the router notifies nobody.

This is the same shape the hole needs anyway — §1 asks for a same-sized gap left
behind — so the constraint and the requirement turn out to be one thing. That is
the argument for it being the right shape rather than a workaround.

It is also why `located` is handed the rect **including** this widget's own
transform rather than excluding it. Excluding it would make the contract safe for
a widget that breaks the rule, which is worse: it would work, one frame late, and
be impossible to reason about.

### `:affixed` is a pseudo-class

§1 asks for one and is right to. A stylesheet cannot express "this node is
currently over another one", and the moment a header lifts is exactly when it
should gain a shadow. Mirrored onto the element from `Styled.isAffixed()` the way
`:checked` and `:disabled` are, so a stylesheet, a hit test and the widget agree
without three of them asking separately.

## Consequences

There are now three ways for a widget to learn geometry, which is two more than a
toolkit should need and exactly as many as have been earned: each arrived with a
consumer that could not be built without it, and each answers a question the
others do not. The table in `Located`'s documentation is the map, and the rule
they share is the one ADR-0116 wrote: read geometry to interpret an input or to
draw an indicator, never to decide a size.

`affix` pins on one axis. §1's `edge=` takes all four and all four work, but an
affix pinned to two edges at once is not expressible — nobody has asked, and the
widget would need two shifts and a rule about which wins.

**A pinned child does not stop at its section's end.** A sticky header
conventionally gets pushed out by the *next* header, and this one stays pinned
until its own subtree scrolls away entirely. Doing better needs the affix to know
about its sibling, which is a relationship nothing in the widget tree expresses
today.

`Located` is walked per frame over the regions, alongside `Measured`'s walk. Two
passes over the same list rather than one, because almost nothing wants both
answers and merging them would cost every node the union of two checks to save
one iteration of a list that is already being built.
