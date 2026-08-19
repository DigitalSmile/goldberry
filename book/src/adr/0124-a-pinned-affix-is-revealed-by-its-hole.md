# 124. A pinned `affix` is revealed by its hole

Date: 2026-08-19

## Status

Accepted. Repairs the meeting point of
[ADR-0119](0119-a-widget-may-be-told-where-it-is.md) (`affix`) and
[ADR-0120](0120-a-widget-scrolls-itself-into-view.md) (`scrollIntoView`), both of
which were correct alone.

## Context

Reported as "the buttons for affix stop working after a few clicks". They were
never working; the first press happened to be a scroll *forwards*, which any
measurement would have got right.

The showcase's jump buttons ask a section to scroll into view. ADR-0120's rule is
that the thing wanting to be seen measures itself, so the section's header
implemented `Located`, was handed its own rectangle and the viewport's, and
passed both to `ScrollController.reveal`.

That header is inside an `affix`. Which means that the moment its section starts
scrolling away, the header is **pinned to the viewport's edge** — it is sitting
at the top of the visible area, by design, permanently. A reveal measured against
it therefore concludes the section is already in view and scrolls nowhere, no
matter how far away the section actually is.

Two rules, each right, composing into something wrong:

- `affix`: your content stops at the viewport's edge.
- `scrollIntoView`: the thing that wants to be seen measures itself.

Follow both and a sticky header can never ask to be scrolled to, because by its
own account it has already arrived.

## Decision

**An `affix` hands out its hole, and the hole is what a reveal measures.**

`Affix.revealedBy(listener)` gives a caller the outer node's rectangle and the
rectangle that clips it, once a frame. The outer node is the hole §1 already
requires — the same-sized gap left behind so nothing jumps — and it travels with
the document precisely because it never moves itself. It is the only rectangle in
the widget that still means "where this section is".

The listener is a **door, not a policy**. `Affix` does no scrolling and holds no
controller: it forwards two rectangles, and the caller decides whether the
section wants showing and what to do about it. That keeps the widget generic and
puts the decision where the reason lives, which is the same split `Tab` uses for
the identical job.

Null by default, so exactly one affix per build is measured. A list of forty
sections has forty affixes and at most one of them is being revealed.

### Why not fix it inside `scrollIntoView`

Because there is nothing there to fix. The controller is handed two rectangles
and does arithmetic on them; both were accurate. The mistake was upstream, in
choosing which node to measure, and that choice can only be made by something
that knows the widget is an `affix` — which the controller deliberately does not.

### Why not have `affix` reveal itself

It would need a `ScrollController`, and then a rule for what "wants to be
revealed" means, and then a way for a caller to say when. All of that is the
caller's already: the showcase holds one flag and clears it when the reveal
lands. A widget that owned the policy would own a worse version of it.

## Consequences

`ADR-0120`'s rule stands with a caveat worth stating plainly: **the thing that
wants to be seen measures itself, unless something is deliberately holding it
somewhere.** `affix` is the only widget in the catalog that does that today. Any
future one — a docked panel, a frozen table column — will have the same problem
and now has the same answer.

The showcase's `SectionHeader` went back to being a plain node, which is the
small proof that the door is in the right place: the widget that had to know
about geometry no longer does.

**None of the eleven `AffixTest` cases could have caught this**, and neither
could the four in `ScrollingScreenTest` as first written: the failing sequence is
*scroll away, then ask to come back*, and every test asked to go somewhere new.
The test that finds it presses two buttons alternately, four times — which is
what the person who reported it did.
