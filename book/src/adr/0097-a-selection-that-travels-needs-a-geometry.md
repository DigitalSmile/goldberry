# ADR-0097: A selection that travels needs a geometry

- **Status:** Accepted
- **Date:** 2026-08-18
- **Relates to:** `docs/design-system.md` §3 and §3.1, `docs/core-widgets.md` §3,
  `docs/ARCHITECTURE.md` §8, extends
  [ADR-0078](0078-a-focus-scope-has-an-axis.md), reuses
  [ADR-0080](0080-a-value-is-measured-along-a-part.md)'s finding about what a
  widget can know

## Context

`segmented` is the last control in `docs/core-widgets.md` §3 that does not wait
on M3's popups, and it looked like the cheapest one left. §3 says it outright:
"sharing `radio-group`'s model and invariant exactly — one Tab stop, arrows rove,
exactly one selected, `change` reports the value. **It is `radio-group` with a
different drawing.**"

The model transferred without a change. The drawing did not, and both halves of
what `design-system.md` asks for turned out to describe something the toolkit
cannot express — for two unrelated reasons, neither of which is a gap to be
filled in later.

**§3's row: "radius 8 outer, 0 between; 1px divider in `--gb-border`".** That is
the joined-buttons drawing: segments that meet, square where they touch, rounded
at the two ends of the bar. It needs a **per-corner** radius, and `ARCHITECTURE`
§8 is explicit that `ComputedStyle` resolves "`border-radius` / `border` /
`outline` (one radius, not per-side)". Nothing clips either — there is no
`overflow: hidden` in the subset and no clip in `Box` — so the usual escape of
drawing square-cornered fills inside a rounded, clipping parent is not available.
A square fill inside the bar paints **over** the bar's curve, and the result is
not subtly wrong: the selected end of the control looks like a corner that lost
its radius.

**§3.1's row: "selection indicator `translate` + width between segments, base —
`tabs`' effect, same controller".** Two things are wrong with it and only one is
about `segmented`. `width` is not on §1.7's whitelist and is not going to be:
"layout properties never transition — animating width/height would run Yoga per
frame", which is the same sentence that sends the sanctioned movement effects
through `transform`. And a `translate` would have to name a **distance** — how
far it is from the segment the selection is leaving to the one it is arriving at.
That distance is a fact about two boxes' laid-out geometry. A stylesheet cannot
write it, because segments are as wide as their labels; and a widget cannot
compute it, because [ADR-0080](0080-a-value-is-measured-along-a-part.md) is the
record of where geometry *is* available — the router, after a paint — and
`build`/`render` run before Yoga.

## Decision

### The bar carries the radius and the segment is inset inside it

```
segmented        height 32, radius 8, 1px --gb-border, padding 2
└── option       radius 4, padding-x 12, grow 1, no fill until :checked
```

Both numbers are derived rather than picked (Principle 3). The 2 is what fits a
28-high segment in a 32-high bar, which is exactly the arithmetic `toggle`'s
padding comes from; the 4 is §1.5's small-control radius, already the radius of
every focus ring in the catalog. The bar keeps `--gb-border` as its own 1px edge,
which is what tells a toolbar where the control ends.

**The divider goes with the joined drawing.** A divider separates segments that
meet; segments inset on every side are already separated, and a rule that drew
one anyway would draw it through the gap the inset made.

### The selection is a fill, and it does not travel

`option:checked` takes `--gb-segmented-selected-bg` and the foreground that fill
carries ([ADR-0087](0087-a-semantic-fill-brings-its-own-foreground.md)), and both
transition on `--gb-motion-fast` — §1.7's own duration for "selection". That is
what `list` row selection already does, and it is the whole of this control's
motion.

The travelling indicator waits for `tabs`, which is where §3.1 says the effect
comes from ("`tabs`' effect, same controller") and which is M3's. It will need
something that does not exist yet: a way for a widget to be told where its own
children were laid out last frame. That is a real feature with real costs — a
second read-back path, and a widget that is no longer a pure function of its
model — and it should be designed for the control whose specification actually
requires it, not smuggled in under the one that can do without it. This is
[ADR-0081](0081-a-perpetual-loop-has-no-state.md)'s argument in the same shape:
the machinery belongs to the milestone whose problem it solves.

### The axis is the widget's, and that is why this is not `radio-group.segmented`

`Segmented.focusScope()` is `HORIZONTAL` where `RadioGroup`'s is `BOTH`, and in
Java it is the only line that differs between the two controls. A group has no
axis because its direction is its stylesheet's — `.inline` flips it. A bar has
one: it is a row, no class turns it into a column, and `Up`/`Down` are therefore
not its keys to take. That is
[ADR-0078](0078-a-focus-scope-has-an-axis.md)'s rule applied for the first time
outside a menu, and it is the machine-checkable form of §3's "the two are not
substitutable in a layout".

### A segment is `option`, and it is a widget

`docs/core-widgets.md` §3 writes `option` for this control's children *and* for
`select`'s, so `option` is the node and the CSS type. It is a widget rather than
a part ([ADR-0065](0065-a-part-is-styleable-and-not-constructible.md)): a
document writes it, it takes the focus, and it means something on its own — which
is exactly the test a part fails.

Its content is boxes on its own node rather than child widgets, which is
`Button`'s shape and not `Radio`'s. A radio needs a child element because its
glyph carries a second background; a segment has one background, one radius and
one colour across the whole cell.

**`flex-grow: 1`, where `radio-group` chose `align-items: flex-start`.** The same
question, answered opposite ways, for a reason that is about what the two things
*are*: a group's options are separate controls that happen to be listed together,
so stretching one drags its focus ring and its hit target across empty space. A
bar is one object and its segments divide it — given a column, the plate fills
the width and the segments split it rather than huddling at the left of it.

## Consequences

**`design-system.md` §3 and §3.1 are amended rather than left describing
something that does not exist.** Both rows now say what ships, and the ADR is
cited from both. A specification that a stylesheet demonstrably cannot express is
not a backlog item; leaving it in place would mean every later reader has to
rediscover why the code disagrees with it.

**Per-corner radii stay unbuilt, and this is now the second control that would
have used one.** `button.square` was the first — §3 gives it radius 0 "where
buttons butt against each other", which is the same joined drawing seen from the
other side. If a third arrives, the subset is probably wrong; `SegmentedTest`
pins both radii so that the day it changes, this decision is revisited rather
than quietly outlived.

**A selected segment's hover and press need two pseudo-classes on one compound**
— `option:checked:hover` — which the selector engine already supported and
nothing had exercised. It is what keeps a selected segment selected-coloured
under the pointer: `option:hover` and `option:checked` have equal specificity, so
without it the fill would go grey and take a label drawn for the accent with it.
`checkbox` and `toggle` solve the identical problem with a descendant selector
because their fill is on a *part*; here the fill and the hover are on one node,
so specificity settles it and no extra selector is needed.

**The focus ring lands exactly on the bar's edge.** §2.2's ring is 2px at a 2px
offset and the inset is 2, so a focused segment's ring sits on the plate's own
border rather than inside it. It is legible — `segmented-focus.png` is the
evidence — and it is a coincidence of two numbers that were each derived
separately, so it is recorded here rather than relied on.

**`option` is a public widget in `…controls.segmented` and `select` will want
it.** Moving it to a shared package now would be guessing at the second
consumer's needs — `select`'s options carry a model, may be tree nodes, and are
rendered inside a popup — so it stays where its only caller is until there are
two, which is [ADR-0092](0092-a-primitive-is-a-widget-like-any-other.md)'s rule
about a reason that has not expired yet.
