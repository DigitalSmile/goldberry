# ADR-0572: An absolute box capped by max-width is laid out at its cap

- **Status:** Accepted
- **Date:** 2026-10-06
- **Relates to:** the Gwent clone's issue list (GB-016)

## Context

Every HUD panel of the downstream's match screen is absolutely positioned and
capped by `max-width`: the log, the hovered card, the prompt, the action
panel. In each one a long `Text` wrapped when painted but was laid out one
line high, so the next child was drawn over its second line. `width` in place
of `max-width`, or a box that is not absolute, laid out correctly.

The cause is in Yoga 3.2.1, in `layoutAbsoluteChild`. An absolute child with
an `auto` width is measured before it is laid out. When its parent is a
column, Yoga constrains the measure to the containing block's width
(`FitContent`): the content is laid out inside the `max-width`, the text
wraps, and the height is right. When the parent is a **row**, Yoga measures
at max-content, with no available width at all. Nothing wraps, the width is
clamped to the cap afterwards, and the one-line height is passed to the
layout pass as exact. Yoga's flex-shrink then squeezes the children into it.
Reproduced against the compiled library: an absolute column capped at 220
holding two texts came out 220×34 with the texts 17 and 18 high, against
220×68 with `width: 220px`.

CSS calls the rule shrink-to-fit. The box is as wide as its content but no
wider than its cap, and its children are laid out at the width it ends up
with.

## Decision

**The toolkit corrects it in `paint.tree`, as it corrects Yoga's inset rule
in `ContainingBlock`.** A new package-private `ShrinkToFit` names the case:
an absolute box, `auto` width not derived from both `left` and `right`, a
`max-width` in points, and a row parent. During reconciliation each
`RenderObject` the case applies to, and that Yoga will lay out again, is
offered to it. After the layout pass, each offered box that Yoga laid out at
its cap (within half a point of rounding) gets the cap as its width, and the
tree is laid out once more. With a definite width Yoga lays the children out
at it, which is the case it gets right. A box narrower than its cap is left
alone: its content fitted at max-content, so nothing wrapped.

**The pin stays while nothing under the box changes.** Yoga marks a node
dirty when anything under it changes, and a dirty pinned node drops its pin
and is measured again. A static frame therefore costs nothing extra, and a
frame that changes a panel's text costs a second pass over the subtrees that
were pinned again.

**A percentage cap is left to Yoga.** It resolves against the containing
block, which can change size without dirtying the box, and a pin would then
hold a width the block no longer allows.

## Alternatives considered

- **Patching Yoga**, by removing `!isMainAxisRow` from the condition that
  constrains an absolute child to its containing block. It is one line, and
  it is the CSS-correct answer for every absolute child in a row, capped or
  not. But the build fetches Yoga unpatched, there is no patch step on three
  platforms to put it in, and it would change the width of every absolute box
  in a row that has no cap. That is a larger change than the issue asked for,
  and it would move goldens that nobody filed.
- **Pinning every frame and unpinning before the next.** Simpler, but it
  dirties the box every frame, so a static frame would re-lay it out, and
  the toolkit's promise is that a static frame costs Yoga nothing.
- **Asking the stylesheets to use `width`.** The downstream's panels want
  `max-width`, which is the layout they mean.

## Consequences

- An absolute box capped by `max-width` in a row is laid out line under line,
  and its width is its cap when its content reaches the cap, which is the
  width CSS gives it. Yoga's own column case shrinks the same box to its
  widest wrapped line. The heights agree; the widths differ, and the row's
  is the CSS answer.
- `ShrinkToFitTest` holds it at the render-tree level: the issue's scene,
  narrower content, a static frame, a changed line, the column case, a
  measured popup, and the cases the rule leaves alone. `StackMaxWidthTest`
  holds it with the issue's `Stack`, `Column` and `Text` and its stylesheet,
  and fails without the fix.
- `RenderObject.update` takes the parent `Box` in place of its padding, since
  the rule needs the parent's direction too.
- If Yoga ever constrains an absolute child in a row, the pin becomes a
  no-op: the first pass already gives the width it would pin.
