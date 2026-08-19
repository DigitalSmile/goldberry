# 120. A widget scrolls itself into view

Date: 2026-08-19

## Status

Accepted. Completes `scroll` alongside
[ADR-0116](0116-a-scroll-view-is-a-clip-an-offset-and-two-extents.md),
[ADR-0117](0117-a-widget-may-be-told-what-it-measured.md),
[ADR-0118](0118-a-popup-that-does-not-fit-scrolls.md) and
[ADR-0119](0119-a-widget-may-be-told-where-it-is.md).

## Context

`docs/core-widgets.md` §1 gives `scroll` a `scrollIntoView(widget)` API. Three
things in the catalog want it and none can be finished without it: selecting a
tab the strip has scrolled past currently selects something the user cannot see,
`affix` needed the same geometry from the other side, and §5's `tour` "scrolls
the target into view and waits for the frame" before it places a popover.

The obvious reading is a method on the viewport, and it is the expensive one. For
a viewport to scroll an arbitrary descendant into view it must **find** it: a way
to name the target, a lookup from that name to a rectangle, and an answer for
what happens when the target is not built yet. None of that machinery exists, and
all of it would exist only for this.

## Decision

### The child asks, and the viewport moves by a distance

ADR-0119 already tells a widget its own rectangle and the rectangle that clips
it, which for anything inside a `scroll` is that viewport. The difference between
those two rectangles **is** the answer. So the direction is inverted: the thing
that wants to be seen — the one node that certainly knows where it is — computes
the distance, and the viewport is asked to move by it.

`ScrollController` is the handle:

```java
public void scrollBy(double dx, double dy);
public void reveal(LogicalRect self, LogicalRect clip);
```

`reveal` is `scrollBy` with the subtraction done, because every caller has the
same two rectangles and doing the arithmetic at each call site is how two of them
end up disagreeing about what "in view" means.

It scrolls **the least it can**. A row below the fold comes up to the bottom
edge, not to the middle: §1 asks for the target to be in view, and a reveal that
centred it would throw away everything the user was already looking at. When the
target is larger than the viewport the near edge wins, which is what every
browser does — the alternative shows a heading's bottom and hides the heading.

### A controller and not a wrapper — which is the second attempt

The first attempt was a `Reveal` widget wrapping whatever wanted to be seen. It
reads better than a controller, it needed no new API on `Scroll`, and it was
wrong: **a wrapper is a box, and a box in a flex row changes how that row is
sized.** Putting one around each tab header broke two tab goldens and two motion
tests immediately. The widget did exactly what it promised; the layout underneath
it was no longer the same layout.

That is not a bug to be fixed in the wrapper. There is no box that is guaranteed
transparent to flexbox — `display: contents` is the CSS answer and §8's subset
has no `display` — so any widget that inserts a node to observe geometry can
change the geometry it was inserted to observe.

§1 words this as an API rather than as markup, and that turns out to be the
load-bearing part of the wording. **An API adds no node.** A widget that wants to
be revealed implements `Located` itself, which it can do without gaining a
parent, and calls the controller from there. `Tab` does exactly this: it was
already a `Handles` node, and it is now a `Located` one too.

### The controller is created above the viewport

Not by the viewport's own state, which is the tempting shortcut. Whoever needs to
scroll a viewport is by definition somewhere else, and a controller created by
`ScrollState` would have to be reachable *downwards* — which is the direction
`findAncestorState` cannot look, since the scroll view a tab strip owns is the
strip's descendant rather than its ancestor.

So `TabsState` creates one, holds it for its lifetime, and hands it down through
`TabStrip` and `TabList` into the `Scroll`. An application does the same with a
field. A controller with no viewport attached is inert rather than an error,
because a controller existing for a frame before the `Scroll` that answers to it
is the normal order of construction and throwing there would make that order
load-bearing.

`BuildContext.findAncestorState` is added and used by nothing in the end — the
downward case is what the catalog needed — but it stays, because it is how an
application-level `scrollIntoView` from *inside* a scroll view reaches the
viewport, and that is the case §1's wording is actually about.

### A reveal is a request, not a constraint

`TabsState` holds one pending value, set when the selection changes and cleared
the moment it has been acted on. A strip that pulled the selected tab into view
on every frame would take the scrollbar away from the user for as long as
anything was selected, which is always. Exactly one header per build carries the
callback, so the router asks one node where it is rather than all of them.

## Consequences

The three-way geometry story is closed: extents on an event for the clamp,
extents once a frame for the thumb, a position once a frame for `affix` and this.
Each arrived with a consumer that could not be built without it and each answers
a question the others do not.

A reveal costs **two frames** — one to be measured, one to act — and lands
without animation. §3.1 gives `scroll` "`scrollIntoView` / programmatic: overlay
duration", so a revealed row should glide rather than jump; the offset is state
and nothing interpolates it. That is a transition on a value the cascade cannot
see, which is the same shape `TabPhase` solved for one widget and the second
consumer that would justify promoting it.

Nothing reveals **horizontally and vertically with different urgency**. A wide
table asked to reveal a cell scrolls both axes at once, which is right, and
scrolls the minimum on each independently, which occasionally moves a view
further than a person would have.

The wrong turn is worth keeping in mind beyond this ADR: **any widget that adds
a node to observe layout can change the layout it observes.** `affix` avoids it
by being two nodes deliberately, with the outer one load-bearing for the hole
anyway. A future widget that wants geometry without a node has to implement
`Located` on something already there, which is a real constraint on what such a
widget can be.
