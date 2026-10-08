# ADR-0575: An absolute child is placed against the padding box, inside the border

- **Status:** Accepted. Supersedes the finding of
  [ADR-0265](0265-yoga-measures-an-inset-from-the-border-box.md) and the rule of
  [ADR-0272](0272-an-absolute-child-is-placed-inside-the-padding.md)
- **Date:** 2026-10-08
- **Relates to:** the Gwent clone's issue list (GB-023)

## Context

`layout.Position#ABSOLUTE` promised that a box is "placed against its
containing block's **padding** box, which is CSS's rule". The downstream wrote
`button.panel-close { position: absolute; top: 10px; right: 10px }` in a
panel with `padding: 24px 32px`, and the cross landed 34 px from the top and
42 px from the right, inside the padding. The entry's repro: a 300×200 box
with `padding: 20px 40px` and a 2px border, and a 30×30 pin at
`top: 0; right: 0`, placed at (230, 20), which is the corner of the content.

The rule was a misreading, and it is older than the code. ADR-0265 recorded
Yoga placing `left: 0; top: 0` in a 12px-padded box at (0, 0) and set CSS's
answer beside it as (12, 12). CSS's padding box is bounded by the **outer**
edge of the padding, so CSS's answer is (0, 0) too: Yoga was right. ADR-0272
then "corrected" Yoga by adding the containing block's padding to every
inset in points, which put every absolute child against the content box, and
the widgets that place parts absolutely adjusted to that.

One thing does differ from CSS, and it is the border. The toolkit paints a
border over the padding, inside the box, and never gives it to Yoga
(`css.Border`), so Yoga's outer edge is the border's outer edge. CSS's padding
box starts inside the border. The toolkit's own definition already says so:
`ShadowGeometry.paddingBox`, where an inner shadow is drawn, is the border box
less each side's border width.

## Decision

**`ContainingBlock.insetFor(position, inset, blockBorder)` shifts each inset
the box named, in points, by the containing block's border width on that
side.** `RenderObject.update` passes its parent's `decoration().border()`.
Padding moves no absolute child. The repro's pin lands at (268, 2).

What did not shift before does not shift now: an undefined edge keeps the
static position (inside the padding, which is CSS's answer for it), a
percentage inset is left where Yoga puts it, and a relative or static box is
not touched. Two helpers write a part's inset in another box's coordinates,
from the style the widget resolved:

- `acrossBorderBox(inset, border)`: measured from the border box. `tab`'s
  underline and `window-root`'s overlays use it.
- `inContentBox(inset, padding, border)`: measured from the content box.
  `text-input`'s selection, value, caret and composition underline, and
  `text-area`'s clipping box, use it. Both controls place their parts exactly
  where they did before.

`table-grip` was `right: -12px` to reach the header's edge across its padding;
it is `right: 0` now.

## What stays

`RenderTree.clipFor` clips a box's children to its **content** box: the box
less its padding. CSS clips to the padding box and lets content show in the
padding. The two disagree only for a box with padding and `overflow` other
than `visible`. Moving the clip would move every padded scroller in the
toolkit, where rows scroll under the padding today, and `text-area` builds
its clipping box on it. It is not part of this change. Its doc comment now
says which box it is, rather than calling it the padding box.

## Alternatives considered

- **Keep the content-box rule and document it.** It is not CSS, and the
  downstream's house rule is to write the CSS the toolkit documents; the
  documentation said padding box.
- **Shift by nothing**, ignoring the border. A bordered panel's close cross at
  `top: 0; right: 0` would be drawn over the border. CSS places it inside.

## Consequences

- A stylesheet that relied on the content-box placement moves its absolute
  children out by the padding. In the toolkit that was `table-grip` alone;
  the widgets that place parts in code write the box they mean.
- One picture moved: the showcase's Styling screen, whose badge at
  `top: 6px; right: 6px` in a panel with `padding: 12px 40px 12px 12px` hung
  over the panel's middle and now sits 6 points from its corner. Its golden
  was retaken. No picture in the guide moved.
- `YogaLayoutTest` keeps asserting Yoga's raw answers; their commentary no
  longer calls them a contradiction.
