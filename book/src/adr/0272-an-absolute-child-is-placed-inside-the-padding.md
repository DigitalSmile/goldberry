# 272. An absolute child is placed inside the padding

Date: 2026-09-06

## Status

Accepted. Implements the fix [ADR-0265](0265-yoga-measures-an-inset-from-the-border-box.md)
priced and located, and removes the two compensations it named.

## Context

ADR-0265 established the fact and did not act on it. An absolutely positioned
child's containing block is, in CSS, the **padding box** of its nearest
positioned ancestor — and `overflow: hidden` clips to that same padding box, so
the two agree by construction. Yoga implements one path of two: given no insets
it places the child at the padding edge and is right; given an inset it measures
that inset from the **border** box and is wrong by the padding.

That record priced the fix — "parent pushes padding to absolutely positioned
children in `RenderObject`, minus `text-input`'s and `text-area`'s compensation,
plus whatever goldens move" — and said it was worth doing deliberately rather
than as a rider on an investigation. This is that commit.

## Decision

**`ContainingBlock` owns the rule, and it is applied where the inset goes onto
the node.**

`RenderObject.update` now takes its containing block's padding, resolves the
inset through `ContainingBlock.insetFor(position, inset, blockPadding)`, and puts
*that* on the Yoga node. A parent passes its own `box.padding()` down when it
reconciles its children; the root passes `Insets.ZERO`, because the window has no
padding to be placed inside of.

**On the style, not on the answer.** A correction applied after the layout pass —
shifting each child's computed rectangle by its parent's resolved padding — is
the version that handles percentages, and it is wrong. With `left` *and* `right`
given, Yoga derives the child's **width** from the containing block's width less
the two insets; shifting both insets makes that width the padding box's, and a
correction applied afterwards could have moved the child and could not have
resized it.

**Per edge, and only the edges the box named.** Yoga's fallback for an edge with
no inset is the static position, which already includes the padding and is
already right. Defining an edge in order to correct it would replace a right
answer with a placement nobody asked for. The trailing edges shift too and in the
same direction: `right: 0` has to stop at the far padding edge, so the padding is
*added* there as well.

**Percentages are not corrected**, on either side of the sum. A percentage inset
resolves against a size the layout pass has not produced yet, and a length in
points cannot be added to it before then. This is the restriction
`TextField.leftPadding` already stated for the same reason, and it is now stated
once, in the class that owns the rule.

**And `acrossBorderBox` is the way out**, because two places mean the border box
and had been getting it by accident:

- **`tab`'s underline.** `left: 0; right: 0` inside a `padding: 0 12px` header now
  means 24 points narrower than the tab. An underline that stops short of its own
  label is not an underline. `Tab.render` widens the indicator using **its own
  resolved padding**, so `density-compact.css` moves it without mentioning it —
  a `tab-indicator { left: -12px }` in the stylesheet would be the same 12
  written twice, three rules apart, and both would have to change together.
- **The overlay layer.** A toast pinned 12 points from a corner means 12 from the
  corner of the *window*. `window-root { padding: 16px }` is an application saying
  where its own widgets start, and a toast is not one of them; a filling overlay
  means the window too. Without this the toast golden moved by 16 points on both
  axes, which is how it was found.

## What was removed

`text-input` added its own left padding to all three of its children's `left`, and
`text-area` added its left and top to every selection rectangle, to the value and
to the caret. Both are gone. Keeping either would have counted the padding twice
and started the text a padding's width too far in — which is why ADR-0265 insisted
the removal happen in the same commit as the fix.

Both controls still *read* their padding, for the three things that are not
placement: the width the text wraps at, the room the scroll offset has to leave,
and turning a pointer's x into an offset into the text.

## Consequences

- **No damage flag was added, and that is checked rather than assumed.** The only
  thing that shifts a child without touching its own box is its **parent's**
  padding — which `sameAppearance` compares, so the parent is `selfChanged` and
  its rectangle is damaged. And `collectDamage` reports a node whose remembered
  rectangle differs from its current one, which is a comparison of results rather
  than of styles and does not care why the node moved. A flag was written first,
  then deleted when removing it failed to break anything.
- **The guard moved from the declared inset to the applied one.** `RenderObject`
  compared `previous.inset()` against `box.inset()` to decide whether to call
  Yoga; that is now the wrong question, because the value on the node is not the
  value on the box. It keeps `appliedInset` instead. `ContainingBlock` returns its
  argument **by identity** when nothing shifts — which is almost every node — so
  the comparison stays a reference check and no absolute node costs an allocation
  per frame.
- **The golden tail was smaller than priced and pointed somewhere else.**
  ADR-0265 expected movement across `text-input`, `text-area`, `segmented`, `tour`
  and `scroll`. Every one of those is unchanged: the two text controls because
  their compensation came out in the same commit, and the other three because
  their parents genuinely have no padding. What moved was `tabs` and `toast`, and
  neither was in the list — the second group was surveyed for parents with padding
  *today*, and a tab and an overlay layer both had some.
- **`YogaLayoutTest`'s two tests are deliberately unchanged.** They assert Yoga's
  raw answer, which is still (0, 0), because they are about the compiled library.
  `AbsolutePlacementTest` is the toolkit's half and asserts (12, 12). If Yoga ever
  fixes its inset path, the `:natives` tests fail first and say so.
- **The next widget that places a child absolutely inside a padded box gets CSS**,
  which is the whole point. `text-input`'s comment said the next one "will not
  know to" compensate; there is nothing to know now.

## Alternatives considered

- **Correcting the computed rectangle instead of the style.** Priced above: it
  cannot resize a child pinned on both edges, and that is a real CSS shape rather
  than a hypothetical one — it is how a `text-area`'s value box would be written
  if it did not already carry an explicit width.
- **Resolving percentages by deferring the shift to a second layout pass.** Two
  passes for a case no widget in the toolkit writes, and Yoga does not offer a
  hook that would make the second one cheap.
- **Leaving `tab` and the overlay layer to the stylesheet.** A negative `left` in
  `controls.css` is the same number written twice and a density file obliged to
  change both. `acrossBorderBox` reads the padding that is already resolved.
- **Clipping to the border box** — rejected in ADR-0265 and still: it would
  contradict CSS twice rather than once, and hit testing reads `overflow` too.
