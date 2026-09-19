# 432. A menu is anchored by the name it was opened with

Date: 2026-09-19

## Status

Accepted. Closes the first of the two `TODO.md` entries
[ADR-0270](0270-a-popup-is-placed-again-when-its-window-moves.md) left behind, and
is the prerequisite for
[ADR-0433](0433-a-popup-whose-anchor-leaves-goes-with-it.md), which is the one
worth having.

## Context

ADR-0270 gave a popup the ability to follow a widget that scrolls under it, and
then gave it to exactly one caller. Its own last-but-one consequence says so:

> **A `popover` follows; a `menu` and a `select` do not.** Following is a
> property of having been opened **by id** […] `Menus` and `SelectState` resolve
> their anchor to a rectangle themselves because they need a minimum width and a
> `Fit` as well, and there is no `Host` overload that takes all three.

`Host` had five popup overloads. Four of them take a `LogicalRect`, and they are
the ones that accumulated the parameters: a floor under the width for a dropdown
([ADR-0145](0145-a-dropdown-is-as-wide-as-what-it-drops-from.md)), a `Fit` so
content taller than the screen can be handed back wrapped in a viewport
([ADR-0179](0179-a-popup-says-what-it-measured.md)). The fifth takes a `String`
and takes nothing else. So a caller with a name and a viewport had to choose, and
both of them chose the viewport — `Menus.open` did

```java
host.anchor(anchorId).flatMap(anchor -> host.popup(content, anchor.painted(), placement, 0, VIEWPORT))
```

which is the by-name overload written out by hand, minus the one thing the
by-name overload is for. A menu hanging off a heading in a scrolling list stayed
where the heading used to be drawn, and the reason was a missing signature.

The entry that recorded this was right that nothing had asked for it, and gave
the reason a dropdown gets away with it: a menu is dismissed by a press
elsewhere, and the wheel over an *open* one scrolls the menu's own list rather
than the window beneath. The gesture that exposes the gap is narrow — a wheel
over the owner window while a menu is up, or an application that scrolls its own
content while one is — and it has never been reported.

It is still worth building, and the honest reason is not this entry. It is the
next one. **A popup that cannot follow cannot be asked what to do when the thing
it is following leaves**, and that question — the anchor scrolls out of sight and
the menu is left pointing at a widget that is no longer drawn — is a real defect
with three plausible answers. ADR-0433 picks one. It can only pick one for the
popups that follow, so this ADR is what decides how much of the catalog that
decision covers.

## Decision

### One overload, taking a name and the other two things

```java
Optional<Popup> popup(Widget content, String anchorId, Placement placement, float minimumWidth, Fit fit);
```

`Menus.open(host, anchorId, …)` passes the name through it, and a menu opened
against a heading now travels with that heading for the same reason a `popover`
does: the name is a question the next painted frame can answer again, where a
rectangle is only ever the answer it already was.

### It is a `default`, which `minimumWidth` deliberately was not

ADR-0145 added `minimumWidth` as an interface method rather than a default,
"so every implementation says what it does with the floor", and paid two test
stubs for it. This one goes the other way, and the difference is what the
parameter *is*. A floor is something an implementation has to do — it lands in
the middle of a two-pass measurement, and a host that ignored it would be silently
wrong. A name is not: resolving it is `anchor(id)` followed by the rectangle
overload, and an implementation that wrote that out by hand could only write it
out differently. That is precisely the duplication `Menus` was carrying.

What a real `Launcher` adds on top of the default is the **remembering** — the
entry in `placements` that carries the name rather than the rectangle, which is
what `replacePopups` re-resolves. That is not behaviour a caller can observe on a
host with no popup windows at all, which is what every other implementation of
`Host` in this repository is.

### `select` is not a customer, and the entry was wrong about why

The entry, and ADR-0270 before it, names `SelectState` alongside `Menus` as a
caller that resolves its anchor by hand because it needs a width and a `Fit`.
`SelectState` does need both. It does not resolve an anchor by hand, and giving
it this overload changes nothing, because **a `select` has no id to open by**.

`SelectField` is [`Located`](0119-a-widget-may-be-told-where-it-is.md), and its
own class comment has said since it was written why that was chosen over an id:

> Anchoring by `id` was the other way and it is worse here: a `select` that a
> document gave no `id` would have to be given a generated one to be able to open
> itself, and two of them in one window would then depend on that generation
> being unique.

ADR-0145 says the same thing from the other end — "no new plumbing, and no id to
anchor by". So the rectangle a `select` opens against does not come from
`Host.anchor` at all; it comes from the frame, through `located(self, clip)`, and
the control keeps it in a field. That is a different mechanism with a different
freshness, and it is not fixed by an overload.

The consequence is that a `select` still does not follow, and the decision in
ADR-0433 does not reach it. That is stated rather than repaired here: repairing
it means either generating ids for anonymous controls, which ADR-0119 refused
with reasons that have not changed, or a second anchoring mechanism, which is a
decision of its own and nothing has asked for one.

## Alternatives considered

- **Following by id only when a document wrote one.** A `select` with an `id`
  would follow and its neighbour without one would not: the same widget with two
  behaviours, decided by something a stylesheet author wrote for an unrelated
  reason, and no way for a user to tell which they have. A bug that is intermittent
  across instances is worse than one that is uniform.
- **A `Supplier<LogicalRect>` on the popup instead of a name.** General enough
  for `select` to hand over its `Located` rectangle, and it is the wrong
  generality twice: the supplier closes over the widget state that opened the
  popup, so a popup outliving its opener holds it alive; and the rectangle it
  would supply is still the one `located` last reported, which ADR-0270 already
  rejected as one frame late at the start of a scroll.
- **Making `Menus` keep the name itself and re-place the popup by hand.** It
  would work, and it would be the second implementation of `replacePopups` — in a
  module that cannot see the window's paint, timed off a build rather than off a
  frame. The facility that has the frames is the one that should be asked.
- **Leaving it, as the entry proposed.** Defensible on its own: the gesture is
  narrow and nobody has reported it. Not defensible once ADR-0433 is on the
  table, because a decision about what a following popup does when its anchor
  goes is worth very little if one widget follows.

## Consequences

- **A `menu` follows its anchor**, and a context menu still does not: one is
  opened against a name and the other against the point the pointer was at, which
  is a rectangle and always was. `Menus.open(host, LogicalRect, …)` is unchanged.
- **A submenu still anchors to a rectangle**, and must: it hangs off a row inside
  the popup above it, which is not a node this window painted and not something
  `Host.anchor` can find. The private `open` in `Menus` now takes *how to open* as
  a function rather than an anchor, so the name form and the rectangle form share
  the rest — the rows have to be described against an `OpenMenu` that does not
  exist until the popup does, and that wiring is the same either way.
- **`Host` gained a default and lost a method body.** The three-argument by-name
  overload is now a default too, delegating to this one with a zero floor and no
  `Fit`, so the two cannot drift.
- **More frames end in a re-placement.** `replacePopups` runs at the end of any
  frame with an id-anchored popup open, and menus are now in that set. The cost is
  one `anchor(id)` scan of the last capture per open popup per frame, and only
  while a menu is showing.
- **A `select` list still stays where the field used to be.** Named above, not
  fixed here, and now the only widget in the catalog for which that is true.
