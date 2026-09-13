# 311. Margin is room outside, and `auto` is the half that mattered

Date: 2026-09-14

## Status

Accepted. Closes the `margin` entry in `TODO.md`, which since
[ADR-0244](0244-a-child-may-say-where-it-sits.md) had recorded the property as
having *no live consumer* — and which was right about the case it was looking at
and wrong about the one it was not.

## Context

`margin` has been in §8's layout list since the first day and in §10's subset
never. It is the third of the four properties a widget reached for and did not
find — `border-bottom`, `currentColor`, `margin`, `max-width` — each written into
a stylesheet, silently discarded, and found by looking at a picture
([ADR-0215](0215-a-property-the-engine-drops-is-a-rule-that-does-nothing.md)).

Nothing about it was hard. Yoga has had `YGNodeStyleSetMargin` since
[ADR-0029](0029-yogas-node-api-and-who-owns-a-node.md) bound the node API, and
`Yoga` binds it **with its `auto` call**, which two other keyed length properties
are deliberately bound without. What was missing was a component on `Box`, a
component on `ComputedStyle`, and four lines in `RenderObject.apply`.

The `TODO.md` entry closed the case on the wrong evidence. `tab-new` wanted a
margin to sit somewhere other than the top of its row; `align-self` answered that
(ADR-0244), the entry recorded "no live consumer", and the property stayed out.
But `align-self` is the **cross** axis. On the main axis a box that wants to
centre itself, or to sit at the far end of a row its container is not arranging
for it, has no spelling at all:

- `justify-content` is the *container's* decision about all of its children at
  once, so one child cannot opt out of it.
- A spacer box with `flex-grow: 1` works and is a box in the tree that draws
  nothing, exists for the layout engine, and has to be remembered by whoever
  reads the document later.

That is what `margin: 0 auto` and `margin-left: auto` are for, and the binding
already had the call.

## Decision

**`margin`, `margin-top`, `margin-right`, `margin-bottom` and `margin-left`,
resolving into an `Insets` on `ComputedStyle` and a component on `Box`, applied
per edge in `RenderObject.apply`.**

CSS's 1-4 value shorthand, the same one `padding` and `inset` take, over the same
`Insets` and through the same helper.

**`auto` is a value here.** `Length.AUTO` on an edge reaches Yoga's own
`YGNodeStyleSetMarginAuto` and absorbs the free space on that side, which is what
centres a box on the main axis and what pushes one to the end of a row.

**Negative margins are allowed and not clamped**, unlike a radius or a border
width. A negative margin means something: it pulls a box over its neighbour,
which is how a row of overlapping avatars is written and how a control escapes
one edge of its container's padding.

**No margin collapsing**, and that is not a shortcut. CSS collapses adjacent
vertical margins in *block* layout and never in flex, and this is a flex engine —
so 10 and 6 between two boxes is 16, which is both what Yoga does and what the
specification says. It is asserted rather than assumed, because it is the first
thing an author who learnt CSS on documents expects to be wrong.

**A box with no margin costs one comparison.** `RenderObject` skips the four
foreign calls wholesale when a first apply sees `Insets.ZERO`, which is
[ADR-0181](0181-a-box-may-say-how-small-and-how-large.md)'s arrangement for
`limits` and is worth more here: Yoga's own default margin is zero, margin is
rarer than padding in this catalog, and nothing in the catalog wears one today.

## Two defects found on the way, both older than this change

Neither is about `margin`. Both were reachable before it and both are fixed here,
because the property could not be correct without them.

### `padding: auto` closed the window

Yoga's setters come in pairs, a value one and an `auto` one, and four of them have
no second half: there is no `YGNodeStyleSetPaddingAuto` and no
`YGNodeStyleSetMinWidthAuto`. `Yoga` binds those without their auto call and
refuses an `auto` **by name** rather than dropping it silently — which is the
right choice for a binding and made `padding: auto` in a stylesheet an
`IllegalArgumentException` thrown in the middle of a layout pass. A window
closing over one typo, from a property the engine claims to support.

`CssLength.parse` reads `auto` for any length, so this was reachable from
`padding`, `padding-*`, `inset`, `top`/`right`/`bottom`/`left`, `gap` and all four
of `min-`/`max-width`/`height`. §8's rule for a value the engine cannot honour is
to **drop the declaration and say so**, and the only place that decision can be
made is where the declaration is read. `ComputedStyle` has a `fixed()` beside its
`length()` now, and the ten properties above go through it. `width`, `height` and
`margin` do not: Yoga binds all three with their auto call.

### The cascade returned its winners in hash order

Nothing between `StyleResolver.resolve` and `ComputedStyle.apply` re-orders, so
the order properties come out in **is** the order they are applied in. A
`padding` applied after a `padding-left` overwrites the edge the longhand set,
which is right when the shorthand was written second and wrong when it was not.

`cascade()` collected its winners into a `HashMap`, so which way round any given
pair came out was whichever way their property names' buckets fell. `padding` and
`padding-left` happened to come out the right way round. `inset` and `left` did
not: **`inset: 8px; left: 20px` resolved to 8px on all four edges**, silently
dropping the longhand, and had since `inset` arrived. No stylesheet here writes
both — checked — so nothing was visibly broken; an application's sheet would
simply have got the wrong answer with nothing to blame.

It is a `LinkedHashMap` now, filled from the already-sorted match list, with a
`remove` before each `put` — because `LinkedHashMap` keeps a re-put key at its
*first* position and the position that matters is the winning declaration's.
Three rules naming `padding-left`, then `padding`, then `padding-left` again must
end with the longhand last.

This is what the `margin-left: 20px` test failed on, which is the only reason it
was found: `margin` is the first property added to the engine that has four
longhands over a value a shorthand also sets.

## Alternatives considered

**Keep the entry closed and write a spacer box.** What the toolkit does today,
and it works. Rejected because it is a box in the tree that draws nothing and
exists to be measured — a layout trick the document has to carry, where CSS has a
declaration for it. A spacer also cannot centre: `flex-grow: 1` on both sides
centres only while neither side has anything else in it.

**Refuse `auto` and take the lengths only.** Rejected, and it would have made this
change almost pointless. `align-self` already covers "sit somewhere else on the
cross axis", which is what the `TODO.md` entry measured the demand by; the main
axis is the gap, and `auto` is the whole of the answer to it.

**Clamp negative margins to zero**, the way `border-width` and the corner radii
are clamped. Rejected: those are clamped because a negative one is meaningless and
arrives from arithmetic that went wrong. A negative margin is a technique.

**Put the `auto` refusal in `Yoga` — drop it there instead of throwing.**
Rejected. A binding that silently ignores what it was told is the worse failure:
the declaration would be gone with nothing reporting it, which is the exact
condition `StyleLint` and `SupportedPropertyTest` exist to catch, and they can
only catch it if the cascade is what refuses. Refusing by name is right where it
is; what was wrong was letting the value get that far.

**Fix the cascade order by sorting property names.** Rejected as the wrong shape:
the order that matters is the declarations', not the alphabet's. Preserving the
sort that was already being computed costs nothing and is the order CSS specifies.

## Consequences

**§8's layout list is complete except `flex-basis`, `align-content` and
`aspect-ratio`.** `margin` was the one on it with a binding already in place.

**Three `Insets` on `Box` and on `ComputedStyle`.** `RecordWitherTest` needed a
third distinct value so a wither writing into the wrong one of the three cannot
round-trip; that test is what verified all fifty-odd rewritten constructor calls
across the two records, and it found nothing, which is the useful outcome.

**`inset: 8px; left: 20px` now does what it says**, which is a behaviour change to
any stylesheet that wrote both. Nothing in this repository did — the toolkit's own
sheets are linted and would have failed — but an application's might, and it would
have been getting the wrong answer.

**Ten properties now drop `auto` instead of crashing.** A stylesheet that wrote
`min-width: auto` — which is valid CSS, and what that property computes to on a
flex item in a browser — took the window down. It is a dropped declaration with a warning now.
That is still not CSS's behaviour, and it is the honest one for an engine whose
layout library has no such setting.

**Nothing in the catalog uses a margin yet**, exactly as nothing wears an
elevation after [ADR-0310](0310-a-shadow-is-a-stack-of-rectangles.md). The
property exists and `controls.css` reads it nowhere. Rewriting spacer boxes as
margins is a change to the layout of real widgets and belongs in its own change,
with its own goldens.
