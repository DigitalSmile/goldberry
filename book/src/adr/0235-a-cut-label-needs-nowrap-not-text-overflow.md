# 235. A cut label needs `nowrap`, not `text-overflow`

Date: 2026-08-30

## Status

Accepted as a **diagnosis**, and **acted on** by
[ADR-0255](0255-a-label-that-does-not-fit-is-cut-not-wrapped.md), which added the
`white-space` this record named and left unbuilt. What it says below about *why*
clipping alone cannot cut a label is unchanged and is still the reason; what has
changed is the last sentence of the Decision — the property has a consumer that
is not a comment, four of them, so it was built.

No behaviour changed *here*: four comments did.

## Context

Three widgets document the same limitation in almost the same words, and a fourth
comment repeats it:

> The cost is that a label longer than its cell overflows it, because **nothing in
> this toolkit clips**. — `option`

> a label longer than the field ellipses rather than pushing the chevron out —
> except that **nothing in this toolkit clips yet**. — `select-value`

> it depends on something this toolkit does not have: `overflow: hidden`.
> **Nothing clips a box here**. — `ProgressFill`

> §8's subset has no `text-overflow` and **nothing in this toolkit clips**, so
> there is no third behaviour to choose. — `TODO.md`, on a menu row

Every one of those sentences is false. `overflow: hidden` shipped with
[ADR-0114](0114-a-clip-is-a-rectangle-the-painter-carries.md); it is read by two
engines — Yoga for sizing and the painter for the clip — it reaches hit testing,
and four rules in `controls.css` plus `text-input`, `text-area` and `scroll` use
it today.

So the obvious move is to clip a menu row and be done. It does not work, and *why*
it does not work is the whole of this record.

## The finding

**A clipped label wraps instead of being cut.**

`Box.text` is a **measured leaf**: Yoga calls back into the text stack with the
available width, and the paragraph lays itself out to fit. So the moment anything
narrows the box the text is in, the text is re-measured at the narrower width and
breaks onto a second line. Clipping never gets a chance — there is nothing
overflowing to clip.

That is why ADR-0148's fix for a squeezed menu row was `flex-shrink: 0` on the
*text* and on the *row*: a box that never narrows is a paragraph that never
re-wraps. The label overflows the menu window, and a label one word too wide is
legible where a menu of two-line rows is not.

Three attempts, each failing in a way worth writing down:

1. **`overflow: hidden` on the row.** The row does not shrink (`flex-shrink: 0`),
   so nothing is clipped horizontally — and the one thing it *did* clip was the
   showcase's 20px icon in its 16px column, which a golden caught immediately.
   Clipping a row punishes the overhang the toolkit already tolerates.
2. **A clip box around the label, shrinking.** The wrapper shrinks, the text
   inside it is re-measured, and the label wraps — reintroducing exactly the
   defect ADR-0148 fixed, from the other direction.
3. **The same, `flex-direction: row` and `align-items: center`.** Fixes a second,
   separate bug found on the way — a box's default direction is Yoga's `column`,
   in which `align-items` is the *horizontal* axis, so a wrapper left at the
   default stretches to the row's height and drops the label to the top of it —
   and does nothing about the wrapping, because that is a measure-time decision
   and not a flex one.

**What is missing is `white-space: nowrap`**: a way to tell the text stack to
measure a paragraph at its natural width whatever width it is offered. With that,
a clip box works and an ellipsis becomes reachable. Without it, no arrangement of
`overflow` and `flex-shrink` can cut a label, because the label is never too long
for the box it is in.

## Decision

**Correct the four comments and leave the behaviour alone.**

A wrong diagnosis repeated in four places is worse than the defect it describes:
it sends the next person to implement `text-overflow`, which would not have
helped, and it tells them clipping is unavailable when three widgets depend on
it.

**No `white-space` property is added here.** It is a text-stack change, it wants a
consumer that is not a comment, and §8's subset has grown one property at a time
against a named need — which is the rule that kept the subset small enough to
believe in. *(It got four:
[ADR-0255](0255-a-label-that-does-not-fit-is-cut-not-wrapped.md) counts them and
builds it. This paragraph is what it had to answer, and the rule is satisfied
rather than broken — the need was named before the property was written.)*

**`ProgressFill`'s note becomes a choice rather than a limit.** Its indeterminate
sweep travels there-and-back because the off-the-edges version needed clipping;
clipping exists, so that drawing is now available and changing a shipped
animation is a design decision rather than a bug fix.

## Alternatives considered

- **Shipping `overflow: hidden` on `select-value` and `option` anyway.** The build
  stayed green, and green only means no golden covers a label that long. Shipping
  a change that might silently turn an overflow into a two-line wrap, with nothing
  demonstrating an improvement, is worse than shipping nothing.
- **Adding `white-space: nowrap` now.** The honest next step, and a text-stack
  change with a layout-property surface, an inheritance question and its own
  goldens. Bundling it into a comment fix would hide it.
- **Measuring the label and truncating it in Java**, one frame late through
  `Measured`. It works — `scroll` and `select` already read last frame's geometry
  — and it puts a text-layout decision in five widgets instead of one property in
  the cascade.
- **Leaving the comments.** They are load-bearing: each one tells a reader not to
  attempt something that would in fact work.

## Consequences

- **Four comments now say what is true**, and each names the property that is
  actually missing.
- **Two `TODO.md` entries keep their subject and lose their reason.** The menu-row
  entry said "§8's subset has no `text-overflow` and nothing in this toolkit
  clips, so there is no third behaviour to choose"; the third behaviour exists and
  the missing property is a different one. The indeterminate-bar entry said
  nothing clips; something does.
- **The next attempt starts three failures ahead.** All three are recorded above,
  and the second and third are traps anybody would fall into in the same order.
- **A separate bug was found and not fixed**, because nothing currently hits it: a
  `Box.of()` wrapper defaults to Yoga's `column` direction, so `align-items` on it
  centres horizontally. Any future clip box has to say `flex-direction: row`.
