# 217. A segmented control is joined again

Date: 2026-08-30

## Status

Accepted. Supersedes the drawing half of
[ADR-0097](0097-a-selection-that-travels-needs-a-geometry.md); depends on
[ADR-0216](0216-a-corner-is-four-numbers-and-a-lint-reads-values-too.md); keeps
the grid [ADR-0099](0099-an-indicator-travels-on-a-grid.md) built.

## Context

`docs/design-system.md` §3 asked for one drawing and `segmented` shipped
another. §3's row is "height 32 (28); segment padding-x 12; **radius 8 outer, 0
between; 1px divider in `--gb-border`**" — the joined-buttons look, where the
segments meet, are square where they touch, and the bar is round at its two ends.

ADR-0097 declined it, on two grounds:

1. **A per-corner radius did not exist.** §8's subset resolved one radius per
   box.
2. **Nothing clips.** There is no `overflow: hidden` in the subset and no clip in
   `Box`, so the usual escape — square-cornered fills inside a rounded, clipping
   parent — was not available, and a square fill in the corner of the bar paints
   *over* the bar's own curve.

What shipped instead was an inset pill: the bar keeps the radius, the segments sit
2px inside it, and §3's divider went with the joined drawing it belonged to. Both
rows of the design system were amended to describe it, and `SegmentedTest` pinned
the numbers with a note saying so — "the day a per-corner radius exists this is
the rule that should be revisited rather than quietly left behind."

ADR-0216 built per-corner radii, for a `group-box` header that could not be faked
with nodes. That is the day.

## Decision

**§3's drawing ships.** The bar is round at its ends and its segments meet.

**The second objection dissolved with the first, rather than being answered.**
Clipping was only ever needed to *cut* a square fill to the bar's shape. A fill
that rounds its own two outer corners is already that shape, so there is nothing
to clip — the same move ADR-0216's `group-box-title` makes one control up.

**The bar's padding is its border's width.** `padding: 1px` where it was `2px`,
which is not a spacing step off §1.3's ramp but the width of the line it clears:
it puts the track exactly on the bar's *inner* box, so a segment's fill stops
where the border's ink begins, and the 7px the segments and the pill carry is the
bar's 8 less that border — concentric, which is what makes a fill lie flat
against a rounded edge instead of poking through it.

**Which corners are kept is Java's; the radius they keep is CSS's.** Whether a
cell is at an end of the row depends on a **count**, and no selector can count
segments — the same argument ADR-0099 used for the cell width and the travel. So
`controls.css` declares `border-radius: 7px` on `option` and
`segmented-indicator`, and `SegmentedTrack.render` and
`SegmentedIndicator.restyle` square the corners that are not at an end, through
the new `Corners.inRow(atStart, atEnd)`. A theme changing the radius still
changes it; a theme cannot get the *geometry* wrong.

**The divider came back as a node.** §8's subset has one `border` and no per-edge
longhands, so "a line on the left of every segment but the first" is not a
declaration anything can write. `segmented-divider` is a box one pixel wide with
a background — `table-rule`'s answer (ADR-0215) and `separator`'s before it.

**It is out of flow.** A hairline in flow would take a pixel of the row, and the
row is a grid: three dividers between four segments make each cell
`(100% - 3px) / 4`, which no percentage names and which would break the travel
that depends on every cell being exactly `1/n`. Absolute, at `left: k/n%`, costs
the grid nothing.

**The two hairlines beside the selection fade out.** A line at the edge of the
filled pill draws a border between the selection and its neighbour, which is a
boundary the selection already is. Both go rather than one, so the control is
symmetric; and they *fade*, on §1.7's `fast`, because the pill takes `base` to
travel and a hairline that blinked would beat the movement that explains it.

**And the hairlines are painted under the pill.** The track's children are the
dividers, then the indicator, then the labels — a box tree has no z-order beyond
document order (ADR-0053), so that list is the stacking. Painted *after* the
pill, a divider would draw a line across the moving fill for the 160 ms it takes
to cross.

## Alternatives considered

- **Leaving the inset pill and closing the note.** It draws correctly and it
  looks like a current toolkit's segmented control. But the specification was
  amended *to* it under duress and says so, and the reason has expired: keeping it
  would mean a design system that records a constraint that no longer exists.
- **Hiding only the hairline the pill covers.** The pill covers the boundary on
  its left and abuts the one on its right, so hiding only what is covered is one
  line fewer of code and an asymmetric control — a line on one side of the
  selection and not the other, for a reason no reader could see.
- **Dividers in flow, with cells sized `(100% - (n-1)px) / n`.** `calc()` is not
  in the subset, and adding it for this is a parser feature to place a hairline.
- **A divider drawn by the segment as a left border.** Per-edge borders are the
  change ADR-0215 declined, for the reason it declined it: a single-edge border is
  a different drawing rather than a different number, and one control's hairline
  should not decide the box model.
- **`:first-child` / `:last-child` so the stylesheet could round the ends.** The
  §8 subset deliberately has no ordinal selectors — every one of them makes
  matching depend on sibling order, which is what makes invalidation expensive
  (ADR-0004's seam, kept small on purpose).

## Consequences

- **Ten segmented goldens changed, plus the gallery's two Controls screens.**
  The bar reads as one object with three cells rather than a plate with a pill
  inside it, which is what §3 asked for.
- **A `segmented-unset` golden is new**, and it is the only image in which every
  hairline shows: with three segments and the middle one selected, *both*
  dividers are beside the selection. That is not a bug and it is why the image
  exists — a reader who never sees a divider should be able to check that one is
  drawn.
- **The focus ring moved off the bar's edge.** ADR-0097 recorded, as a
  coincidence of two independently derived numbers, that a 2px ring at a 2px
  offset landed exactly on the bar's border when the segment was inset by 2. With
  the segment against the inner edge the ring sits just outside the bar and takes
  the segment's own corners — rounded at the ends of the row, square between.
  `segmented-focus.png` is the evidence that a middle segment's ring is still
  legible where it crosses the edge.
- **`Corners.inRow` is in `:core`**, not in `:widgets`, because the next two
  callers are already named: §3 gives `button.square` radius 0 "where buttons
  butt against each other", which is this drawing seen from the other side, and
  `tabs` will want it.
- **A test that counted past the track's parts had to stop.** Both
  `SegmentedTest` and `SegmentedGoldenTest` reached a segment as
  `children().get(index + 1)` — one past the indicator — and there are now `n`
  parts before the segments. They find an `option` by type instead, which is what
  they meant and does not change when the anatomy does.
- **`design-system.md` §3's row is amended back**, and the amendment is recorded
  rather than silently reverted: the row now describes the joined drawing again,
  with both ADRs cited, so a reader can see the constraint arrive and leave.
