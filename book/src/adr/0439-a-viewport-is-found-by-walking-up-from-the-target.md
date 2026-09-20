# 439. A viewport is found by walking up from the target

Date: 2026-09-20

## Status

Accepted. Completes
[ADR-0120](0120-a-widget-scrolls-itself-into-view.md) at the seam it named and
left open, and closes the `TODO.md` entry "a `tour` cannot find the viewport its
target is in".

## Context

`docs/core-widgets.md` §5 asks a `tour` to scroll its target into view before it
places the card. A `Stop` names its target by id — an application holds ids, not
elements ([ADR-0108](0108-a-context-menu-is-a-name-on-a-widget.md)) — and until now
it also had to be handed the `ScrollController` of whatever viewport the target
lives in:

```java
new Stop("export-button", "Exporting", "…").within(settingsScroll)
```

Every application writing a tour had to know, for each stop, which viewport
encloses the widget that stop describes. That is a fact about the screen's
layout, restated by hand in a file that is usually written somewhere else
entirely, and it goes stale the first time somebody wraps a panel in a `scroll`.

The `TODO.md` entry recording this had been read against the code at least twice
and marked "it stands":

> Discovering it means walking from an element to its nearest scrolling ancestor.
> `BuildContext.findAncestorState` looks like the answer and is not: it walks up
> from the element being **built**, and what a tour needs is a walk up from the
> **target it names** — a different question, and one the tree offers no way to
> ask.

## Decision

### The entry's two premises are true and its conclusion is false

`findAncestorState` does walk up from the element being built, and a tour does
want a walk up from the element it names. What does not follow is that the tree
cannot be asked, because of two facts the entry never put together:

- **`Element` *implements* `BuildContext`.** The walk is `for (var current =
  parent; current != null; current = current.parent)` on the element it was
  called on. Nothing binds it to the node currently building; that is only where
  the caller usually happens to be standing.
- **A hit-test region carries its element.** `HitTest.Region.owner()` is "what
  the renderer tagged the box with — an `Element` in the widget stack", and
  `Host.anchor(id)` already returns a region. The tour was calling it on every
  build, for the rectangle, and throwing the owner away.

So the walk is one call, from a node the tour already had in its hand.

ADR-0120 half-wrote this down three hundred decisions ago and nobody read it
back:

> `BuildContext.findAncestorState` is added and used by nothing in the end — the
> downward case is what the catalog needed — but it stays, because it is how an
> application-level `scrollIntoView` from *inside* a scroll view reaches the
> viewport, and that is the case §1's wording is actually about.

That is this call, and the method was kept for it.

**And the same fact had already closed a different entry.** `TODO.md`'s answered
half contains "a tooltip's 500ms delay is a constant, and the token that would
replace it cannot be read", closed by
[ADR-0254](0254-a-build-may-ask-the-cascade-for-a-number.md) with the words *"the
launcher holds an `Element`, which **is** a `BuildContext`"*. So the observation
this entry needed was written down in the list itself, in an entry two screens
away, and neither re-reading found it. What that says about the list is more
useful than what it says about the tour: an entry's blocker is worth re-checking
against the *answered* half, because the thing that unblocks it may already have
been discovered for something else.

### `ScrollScope` is the walk, and it is not a `ScrollController`

`ScrollScope.enclosing(element)` answers the nearest enclosing viewport, or
empty. It is a new type rather than a static on `ScrollController` because the
two are opposite directions and only one of them is a handle somebody owns:

- a **controller** is created *above* a viewport and handed down into it, by
  whoever will need to scroll it later. It outlives frames, carries a listener,
  and reports a position.
- a **scope** is found *below* a viewport and points at the state that was
  already there. It has no identity worth holding and nothing to listen to.

Minting a `ScrollController` for the second case was tried on paper and rejected:
its `onChange` would never fire, because a viewport notifies the one controller
it was given. A handle where half the methods silently do nothing is worse than a
second, smaller type.

### One piece of arithmetic, moved to where both callers can reach it

`reveal`'s "how far is this rectangle out of view" lived on `ScrollController`
and operated on the state through a field. It moves to `ScrollState.reveal`, and
the controller delegates. Two callers doing the same subtraction is how two of
them end up disagreeing about what *in view* means — which is the argument
ADR-0120 made for putting it on the controller in the first place, applied again
now that the controller is not the only door.

### The controller still wins when an application named one

`Stop.within(controller)` is kept, and a stop that names one uses it. This is not
backwards compatibility for its own sake: the walk finds the **innermost**
viewport, and an application that names a controller may deliberately mean an
outer one — a row inside a list inside a page, where the interesting move is the
page's. Discovery is what happens when nobody said.

## Consequences

`Stop`'s fourth component is now the exception rather than the requirement, and
the three-argument constructor — which every stop in the showcase uses — went
from "a stop whose target is not inside a scroll view" to "a stop that lets the
tour find the viewport".

**The reveal path had no test before this.** `TourTest` drove everything a tour
decides against a stub host and never exercised the scroll, because a stub cannot
move pixels. It does now, against a real painted viewport whose regions the stub
host answers with — and the new test was checked failing first: row 20 sits at
`314.0` without the walk and inside the viewport with it.

**Nested viewports are revealed in the innermost one only.** A row brought into
view inside an inner list can leave that whole list scrolled out of the outer
one, and nothing here notices. That is exactly what a hand-wired controller did —
an application passes one controller, not a chain — so this is the old behaviour
with the wiring removed rather than a new promise, and it is written on
`ScrollScope` as the place it stops telling the truth. Walking the rest of the
way needs each viewport's own painted rectangle, which only the router holds and
only for nodes it has regions for.

**A `scroll` named by its own id resolves to itself**, which looks like a
contradiction of "the walk starts at the parent" and is not: an `id` written on a
`scroll` lands on the `ScrollViewport` the widget builds — `scroll` as a CSS type
is that node — so the first parent of the only element anybody can name is the
state that holds the offset. A tour stop naming a viewport therefore reveals the
viewport inside itself, costs one lookup and moves nothing. This was found by a
test asserting the opposite, on the assumption that the id was on the stateful
node; the assumption was wrong and the test now pins what actually happens.

**The scroll tests share one harness.** `ScrollControllerTest` carried a private
one and `ScrollScopeTest` would have been the second copy in the same package.
Lifting it found a leak on the way: the test that builds two harnesses in one
method was closing only the second, because each constructor overwrote the
field the teardown read.

The entry is the fourth in the 2026-09-19 batch to have been wrong about itself
rather than merely unbuilt, and the most expensive kind: it had been re-read and
re-confirmed, so the cost was two milestones of an API every tour had to carry.
