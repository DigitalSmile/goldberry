# 312. The catalog puts the two new properties on

Date: 2026-09-14

## Status

Accepted. Spends what
[ADR-0310](0310-a-shadow-is-a-stack-of-rectangles.md) and
[ADR-0311](0311-margin-is-room-outside-and-auto-is-the-half-that-mattered.md)
built. Both of those ended with the same sentence — *nothing in the catalog uses
it yet* — and both said the reason was that using it moves every golden that
contains one of the affected widgets. This is that change.

## Context

`box-shadow` and `margin` landed with tokens, tests and no consumers. That is the
right way round — a property and its first use are two different risks — but a
property with no consumer is also a property nobody has checked against a real
widget, and the catalog was full of comments explaining what it would do if it
existed:

- `card`: *"§5 says shadow tokens and §10's subset has no `box-shadow`"*.
- `dialog`: *"Elevation 2 is an edge and a scrim, not a shadow."*
- `dialog-actions`: *"The margin is `padding-top` on this node rather than a
  margin on the panel, because §8's subset has no `margin`."*
- `affix:affixed`: *"§1.5's elevation"*, setting only a background.
- `tour-card`: *"§1.5's dialog elevation"*, setting only a radius.

Five rules describing a property they could not write. Each is a place the design
system was already specific and the engine could not follow.

## Decision

**Elevation on the five surfaces §1.5 names, and margin in the two places the
catalog was working around not having it.**

### The shadows

| | level | why |
|---|---|---|
| `card` | 1 | §1.5's "raised: menus, cards, popovers" |
| `card.interactive:hover` | 1 → 2 | §5's "hover-elevation optional via class" |
| `dialog` | 2 | §1.5 names dialogs for level 2 exactly |
| `tour-card` | 2 | a tour card is a dialog by another name, and its own comment said so |
| `toast` | 2 | below |
| `affix:affixed > affix-content` | 1 | §1.5 via §1.7's motion table |

`toast` is the one judgement here rather than a quotation. §1.5's ladder has two
shadowed levels: 1 for things raised off the page, 2 for overlays. A toast is in
the window's own overlay layer with the application's content directly under it
and **no scrim** between the two — it has left the page entirely, and level 1
over an arbitrary busy background does not say so.

**`card.interactive` transitions `box-shadow` as well as `border-color`.** Every
component of the shadow interpolates, so the blur and the offset grow with the
alpha. That is the difference between a card that rises and a stain that darkens
under a card which has not moved, and it is the first thing in the toolkit to use
ADR-0310's `transition: box-shadow`. `affix` uses it too, which is §1.7's
"detach/attach: `opacity` on the elevation shadow, fast" — a line that has been in
the motion table since before there was a shadow to put an opacity on.

**The edges all stay.** Not one of them was a placeholder. A shadow says "nearer"
by darkening what is underneath, and a card sitting on *another card* is sitting
on its own colour — where the shadow says almost nothing and the rim says it
exactly. The Panels screen puts three surfaces side by side and a nested card
inside a card, which is what that sentence looks like.

### The margins

**`dialog-actions`: `padding-top: 24px` → `margin-top: 24px`.** §2 says "top
margin 24" and the rule has carried a comment explaining the substitution ever
since. The picture does not change — the row has no fill and nothing to clip —
and the declaration now says what it means.

**`tour-card`'s footer loses its `Spacer`.** `TourStop` built
`[Skip][Spacer][Back?][Next]`; it now builds `[Skip][Back?][Next]` with
`margin-right: auto` on Skip. One widget fewer in the tree, and the picture is
**pixel-identical** — checked, by regenerating the tour goldens with the spacer
put back and comparing. It has to be: with *n* children and a gap *g* the spacer
absorbs `W − Σwidths − n·g` and with one child fewer the auto margin absorbs
`W − Σwidths − (n−1)·g`, which lands every button in the same place.

The margin goes on the **trailing** edge of the leading button, not the leading
edge of the trailing one, because the trailing group is one button or two
depending on whether there is a stop to go back to — and two auto margins split
the free space between them and open a hole in the middle of the pair.

**The showcase's notice bar likewise**: `#clear-notices { margin-left: auto }`
replaces a `Spacer` in `Notifications.bar`.

## What deliberately did not get a shadow

**`popover`, `menu` and `tooltip`**, which is exactly the list §1.5 names for
level 1 alongside cards. They are drawn in **popup windows created at the panel's
own measured size** (ADR-0104). A shadow is drawn outside the box that casts it,
so every pixel of one would fall outside the window and be clipped: the toolkit
would pay for a run of fills and draw nothing.

The fix is a popup window sized to the panel *plus* the shadow's reach with the
extra transparent, which needs a compositor that honours a transparent popup on
all three platforms — the same thing the rounded corners are already waiting on.
Their comments said "the subset has no `box-shadow`"; they say the real reason
now, which is a different and more durable one.

**`message`**, which is part of the column rather than over it, and **`hud`**,
which is a diagnostic plate that §1.5 does not put on the ladder.

**`spacer` is not deprecated.** It is a §1 widget an application writes in markup,
and a document has no stylesheet of its own to put a margin in. The showcase's
status bar keeps one on purpose, with the notice bar beside it as the other half
of the comparison: same shape, done with a margin. What changed is that *toolkit
code*, which does have a stylesheet, no longer reaches for a node to do a
declaration's job.

## Alternatives considered

**Give `popover` a shadow anyway and let it clip.** Rejected on measurement
rather than principle: a run of eight to thirty-one rounded-rectangle fills, per
popup, per frame, every one of them outside the window. It would be invisible and
not free.

**Put `--gb-elevation-3` on something.** Rejected, and it stays unused. It is the
"a thing the pointer is dragging" level and nothing in this catalog is dragged; a
token used by nothing is ordinary for a theme — the semantic hues have ranks
widgets do not use either — and inventing a consumer for it would be worse than
leaving it.

**Drop the edges now that there are shadows.** Rejected, and it is the tempting
one because the two look redundant against a page. They are not redundant against
a card: the Panels screen has `card.surface-demo` inside `card#surface-card`, and
there the shadow falls on the same colour it is cast by.

**Leave `dialog-actions` as padding.** Rejected as the whole point of ADR-0311.
The picture is identical and the declaration was lying about what it meant, which
is what the comment above it had been apologising for.

## Consequences

**Twenty-six goldens moved**, across `:widgets` and `:example` — every screen in
the gallery, because every screen is a wall of cards. They were reviewed rather
than accepted blind; the three worth naming are `card-hover.png` (the lift, beside
a card that did not), `affix-pinned.png` (the pinned header now casts onto the
rows sliding under it, which is the whole argument for `affix` in one image) and
`gallery-panels.png` (a card inside a card, where the shadow says nothing and the
rim does).

**`RuleBucketTest` caught a selector.** `tour-card > column > row .tour-skip` has
a rightmost compound that names no type, so the cascade would check it against
every element of every kind (ADR-0152). It is `button.tour-skip` now, folded into
the rule that was already there — two rules with one selector is its own small
defect.

**The catalog's frame cost did not move.** The style pass is what shadows would
have touched if anything and it does not see them; the *raster* pays, and pays
only where a shadow is. Two budget tests failed while `:widgets:test` and
`:example:test` ran concurrently and pass in isolation, which is worth writing
down because it will happen again: `FrameBudgetTest` measures wall-clock on a
machine Gradle is also running another test JVM on.

**Five comments stopped being wrong.** That is most of the value here. Each one
described a property that did not exist, and a reader had no way to tell which of
them were still true.
