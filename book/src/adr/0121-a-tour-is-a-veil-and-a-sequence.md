# 121. A tour is a veil and a sequence

Date: 2026-08-19

## Status

Accepted. The last of the `scroll` group, after
[ADR-0119](0119-a-widget-may-be-told-where-it-is.md) and
[ADR-0120](0120-a-widget-scrolls-itself-into-view.md). Reuses
[ADR-0100](0100-a-window-has-a-layer-above-its-application.md)'s overlay layer
and [ADR-0108](0108-a-context-menu-is-a-name-on-a-widget.md)'s "a target is a
name".

## Context

`docs/core-widgets.md` §5's `tour`: a guided sequence of popovers over real
widgets, each stop naming a target by id with a title, a body and
Back/Next/Skip. The window dims outside the target with a veil cut to its rect.
It scrolls the target into view before positioning. `Esc` skips the whole tour,
and a target that is not in the tree is skipped with a warning rather than
throwing.

Two of those had no mechanism. A veil "cut to a rect" wants a mask, and §8's
subset has no path, no mask and no `clip-path`. And an overlay in the layer is
pinned to a corner and sized to its content, which is right for a `hud` and
exactly wrong for something that has to cover everything.

## Decision

### The veil is four rectangles

Above the target, below it, and the two beside it spanning the gap between those
two. They tile the window exactly and leave the target uncovered. No mask, no
path, no new property in the subset — four absolutely positioned boxes, which is
what the subset already does well.

**The workaround is better than the thing it replaced.** Nothing is drawn over
the target, so it stays live: it takes the pointer and the keyboard normally, and
a stop that says "click Save to continue" can be obeyed. A single masked
rectangle would have had to arrange an exception to itself to allow that, and the
exception would have had to be described in terms of hit testing rather than in
terms of paint.

The bands consume the pointer. That is what makes a tour modal without anything
declaring it so: everything the veil covers is unreachable because the veil is
over it, and the one thing it does not cover is the thing the stop is about.

They tile without overlapping, which matters because they are translucent — a
doubled band would be visibly darker, and the seam would trace a rectangle
around nothing.

### `Host.fill` is one flag, not a second placement path

An overlay's insets already decide where it goes: two sides is a corner, and four
is a fill. So `Overlay.filling` sets a flag and `WindowRoot` chooses
`Insets.all(0)` instead of the corner's. No new placement code, no new concept in
the layer.

### The card is not a `popover`

§5 calls a tour "a guided sequence of `popover`s", and the word is doing less
work than it looks. `popover` is the *panel* half of an anchored floating thing;
its other half — measure, flip, shift, open a platform window, light-dismiss — is
precisely what a tour must not do (ADR-0104). A tour's card lives inside the
window, over a veil that is also inside it, and dismisses on its own buttons
rather than on an outside click.

Placement is flip-and-shift done in six lines rather than reused, because
`Placement` positions a *window* against a display's work area and this positions
a box inside another box. Same idea, different coordinate space, and sharing it
would mean teaching it about both.

### The target is resolved every frame

A stop names a widget and the anchor is read from the painted frame on **every
build**, not once when the stop opens. A window that resizes, a list that
scrolls, a panel that reflows — all of them move the thing being described, and a
veil cut where the widget used to be is worse than no veil at all.

That also makes "skipped with a warning" fall out rather than being handled: a
target that is not on screen is simply one `anchor` does not answer, and the
build walks on to the next stop. A tour is documentation, and documentation going
stale must not take the window down.

### Starting one takes a `Host`

`Tours.start(host, stops)`, exactly as `Menus.open(host, …)` does and for the
same reason (ADR-0106): resolving an id against the painted frame and putting
something on the window are both things a widget tree cannot do to itself.

### It needed a picture, and the picture found the bug

`TourTest` drives fifteen cases against a stub host and every one of them passed
while the card was drawn down the entire left edge of the window, one pixel from
the top, stretched to the full height.

`Insets` is in CSS order — top, right, bottom, left — and the placement passed
left and top. Anchoring a box by its top *and its bottom* stretches it; anchoring
it by nothing horizontal puts it at the origin. Every assertion about what the
tree contained was true throughout, because the defect was entirely in two
numbers' positions in an argument list.

So `TourGoldenTest` exists, and it is the right kind of test for this widget
rather than a belt-and-braces one: a veil is a fact about **pixels**, and which
region is dimmed and which is not is not a question the widget tree can be asked.

## Consequences

`tour` is the first widget to use the in-window overlay layer for something that
is not a corner badge, which is what `Host.fill` exists for and the only reason
it does.

**A tour cannot find the viewport its target is in.** §5 asks it to scroll the
target into view, and `Stop` takes an optional `ScrollController` for the
application to supply. Discovering it automatically means walking from an element
to its nearest scrolling ancestor, which is a `:core`-to-`:widgets` dependency
the toolkit does not have — the same wall ADR-0120 turned around to avoid, and
here there is nothing to turn around because the tour is not the thing being
revealed.

The card's height is **estimated** when deciding whether it fits below its
target. Measuring it would need the measure-then-place machinery ADR-0104 built
for popup windows, which works on windows rather than on boxes. Being wrong puts
a card above its target when it would have fitted below, which is a placement
nobody will notice and not a defect anybody can see.

`tour-band`'s opacity is a fixed 0.55 rather than a token. §1.2 has a scrim and
this is one; a `--gb-scrim` would be the right name and there is one consumer,
which is ADR-0019's argument for waiting.
