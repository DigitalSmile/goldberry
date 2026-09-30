# 505. A border has four sides, and takes no room

Date: 2026-09-30

## Status

Accepted. Adds per-side borders to `docs/ARCHITECTURE.md` §8's CSS subset — the
change [ADR-0215](0215-a-property-the-engine-drops-is-a-rule-that-does-nothing.md)
declined for one table's underline and said was "worth doing when something
needs an edge the subset cannot fake". Closes the table-rules half of
`book/src/TODO.md`'s `goldberry-html` entry.

## Context

`border` has been one width and one colour for the whole box since
[ADR-0064](0064-a-rounded-rectangle-is-four-cubics.md). Five rules reached for a
side and got nothing: `border-bottom` under a tab (ADR-0107) and under
`table-head` (ADR-0215), `border-left` on a quotation in both document views,
`border-right` down `text-area`'s gutter (ADR-0331), and a rule between a
document table's cells, which `TODO.md` records as "`border` is uniform, so
there is no `border-left`". Each was answered with a node — `tab-rule`,
`table-rule`, `html-quote-bar` — or with nothing, and `group-box-title`'s comment
has described a rule under the header since the widget shipped while no
declaration drew one.

A node stops being an answer at the table. A document's table has as many
columns as its author wrote and the builder does not lay out a grid, so a line
between each pair of cells is a border on one side of every cell or nothing.
`markdown-view` had already put a `first` class on each row's leftmost cell for
the rule it could not write.

Three facts decided the shape.

- **A border has never taken layout room here.** `BoxPainter` strokes it
  inside the box's edge, over the padding, and `RenderObject.apply` never calls
  `YGNodeStyleSetBorder` — bound per edge in `YogaNode.setBorder`, and
  unused. Every bordered widget's padding assumes this:
  `segmented`'s padding "is that edge's own width", and `TextAreaBox` insets its
  gutter strip by the border because the border is drawn over its padding.
- **Every golden with a border is the one stroked rounded rectangle.** A
  per-side model that changed how a uniform border draws would move every one of
  them.
- **The painter strokes, and a stroke has one width.** Sides that differ are a
  different drawing, not a different number — ADR-0215's own objection.

## Decision

**The subset has CSS's side properties.** `border-top`, `-right`, `-bottom` and
`-left` take `border`'s grammar over one side; `border-width` and `border-color`
take CSS's 1-4 side form in `padding`'s order; `border-{side}-width` and
`border-{side}-color` set half of one side. `ComputedStyle.with` handles all of
them, so `StyleLint` and `SupportedPropertyTest`, which ask the real engine,
learnt them with no edit.

**Style stays a word inside a shorthand.** `border` has always read a style
keyword and drawn it solid, logging anything but `solid`, with `none` and
`hidden` as a zero width. The side shorthands do the same, and there are no
`-style` longhands — `border-style` included, which was never a property here
either. A `-style` longhand that set nothing would be a declaration the lint
passes and the painter ignores. `StyleLintTest`'s example of an unsupported
property is `border-style` now, since `border-bottom` stopped being one.

**Later wins, per side.** The cascade applies declarations in the order they
won (ADR-0311), so `border: 1px solid; border-left: none` is three sides and
`border-left: 2px solid; border: 1px solid` is four equal ones. A more specific
rule's `border` is applied after a less specific rule's `border-left`, whatever
the source order. A side's shorthand resets what it does not name on that side:
`border-left: red` is a 0px left side, as `border: red` is a 0px border.

**The value is `Border`, four `Line`s, and `Decoration` carries one.** It
replaces `Decoration`'s `borderWidth` and `borderColor` components. The
accessors went with them rather than being kept as derived answers, for
[ADR-0216](0216-a-corner-is-four-numbers-and-a-lint-reads-values-too.md)'s
reason: with four sides, "the border's width" has no correct return value.
`Decoration.border(width, argb)`, `borderWidth(double)` and `borderColor(int)`
still set all four, and the old seven-argument constructor is kept as a
uniform-border convenience.

**A border takes no room, and a side takes none either.** Nothing reaches Yoga.
A cell with `padding: 6px 8px` and `border-left: 1px` has its rule over the
first pixel of its padding and its text where it was. In CSS's words this is
`border-box` sizing with the border laid over the padding rather than inside
it; the subset has no `box-sizing` and gains none. The per-edge binding stays
bound and unused.

**Four equal sides are the old drawing.** `Border.isUniform()` compares four
lines, and a uniform border goes down the same stroke `BoxPainter` always drew:
one rounded rectangle inset by half the width. That is decided from the value,
so a border spelled as four longhands takes it too, and `BorderPaintTest`
checks the two spellings pixel for pixel.

**Sides that differ are filled, one region each** (`BorderPainter`). A side is
the band between the outer edge and the inner edge — the outer edge inset by
each side's own width — cut off at each end where it meets its neighbour.

- **Square corners are mitred as a browser mitres them**: the boundary runs
  from the outer corner to the inner one, so a 1px top against a 4px left meets
  it on the diagonal from `(0, 0)` to `(4, 1)`.
- **With a radius, it is an approximation.** The inner corner is a circle of
  the outer radius less the *wider* of the two sides beside it, where CSS makes
  it an ellipse with each side's own width taken off its own axis; `Corners`
  are circles. The corner's arc is split between its two sides in proportion to
  their widths, by parameter along the cubic, with de Casteljau so the pieces
  are exact pieces of the arc. Equal widths split at the arc's midpoint, which
  is exactly where the mitre of a concentric corner falls; a zero on one side
  gives the other the whole corner, tapering into it, which is what a browser
  draws for a lone `border-left` on a rounded box. The error is bounded by the
  difference between the two widths.
- **One fill per colour.** Two anti-aliased fills meeting on a diagonal each
  cover part of the pixels along it, and part over part is not all, so a border
  in one colour with two widths would have a faint seam down every mitre. Every
  side of one colour goes into one path and is filled once; between two
  different colours there is a seam, as in every browser.

**`border-color` transitions every side.** Its animated value is the `Border`
itself, each side's colour mixed through OKLCH like every colour transition,
and only the colours are written back, so a width is never animated. A width
change with every colour held starts nothing.

**The consumers.**

- **Document tables are ruled.** In `html.css` and `markdown.css` a row draws
  the line above it and a cell the line before it, and `first` — on the top row
  and each row's leftmost cell, set by the builder because the subset has no
  `:first-child` — stops either drawing over the table's own border, where a
  square line would also show outside the rounded corner. A caption is the
  first thing in an HTML table when it has one, so the head under it is ruled
  off from it.
- **And their columns are equal.** Each row is a flex row of its own and
  nothing shares a column between rows, so while a cell's share followed its
  content a column started a few pixels further along in one row than in the
  next. Space hid that; the rule before a cell drew it as a line with a step at
  every row, which the first render showed. Cells are `flex-basis: 0` now, an
  equal share of the row — what both stylesheets' comments said they wanted
  while `flex-basis` was missing, and it has been in the subset since ADR-0373.
- **A quotation's bar is its `border-left`.** The 2px widget and the 12px gap
  beside it became `border-left: 2px` and `padding-left: 14px`, and the goldens
  with quotations in them did not move, which is the evidence that the side
  drawing is the fill it replaced.
- **`group-box-title` has its rule.** `border-bottom: 1px solid var(--gb-border)`,
  inside the header's own padding.
- **The other nodes stay nodes**, each for a reason that is not the subset any
  more, and their comments say so. `table-rule` sits *under* `table-head`'s 36;
  as a border it would sit inside it and move every row up a pixel for a picture
  that is already right. `tab-rule` has the travelling indicator drawn over it,
  and a border on the list would be painted before the tabs. `tab-indicator`
  travels by a `transform` (ADR-0377), which a border on one tab cannot do.
  `segmented-divider` fades on its own when the pill reaches it. `text-area`'s
  gutter is told from the text by a step of surface, which was a choice and not
  only a workaround. `menubar` still wants no rule.

**The specification says all of this first**: `docs/ARCHITECTURE.md` §8 has a
"Borders are per side" entry beside the paint properties, and the parenthesis
that said `border` was "one width and one colour, not per-side" is gone.

## Consequences

- **Seven golden images changed, each by the rule it gained**, and nothing
  else: `html-light`, `html-dark`, `markdown-light`, `markdown-dark` and
  `markdown-selection` by the table's inner rules (and, in the Markdown ones,
  a right-aligned `400` that moved one pixel to line up with the `600` under it
  now that the columns are equal); `group-box-dark` and `gallery-panels` by the
  line under a group box's title. One golden is new, `borders`: a uniform
  border, a lone `border-bottom`, two widths in one colour, four colours and
  four widths, and the same rounded. Every other golden in the four modules
  matches its reference, and re-blessing the four golden classes these belong
  to rewrote no other file in them — which is what says the uniform drawing did
  not move.
- **A table's columns are equal shares now**, not shares of their content. A
  table with one long column and one short one wraps the long one sooner than it
  did. That is the trade for rules that line up, and it is the one the
  stylesheets were written for.
- **`Decoration.borderWidth()` and `borderColor()` are gone.** Four call sites
  moved: `TextAreaBox` reads each side it insets against, `Animatables` animates
  the `Border`, and two widget tests compare a `Border`.
- **Only a non-uniform border costs anything new**: a handful of small arrays
  and up to four fills per box per frame. A document table's cells are that case, all
  square, all one colour, so each is one fill of one or two rectangles.
- **An author coming from CSS will find a border that does not push content
  in.** It never did here, for `border`; the specification now says so in so
  many words, beside the properties that make it more likely to be noticed.

## Alternatives considered

- **Handing the widths to Yoga, as CSS does.** It is the binding's purpose and
  would make a side push content in like a browser's. It would also move every
  bordered box in the catalog by its border's width, double-count the edge in
  every widget whose padding already includes it (`segmented`, `text-area`),
  and change every golden with a border — a box-model change
  across the toolkit, riding on a feature that needs none.
- **Keeping one stroke and drawing a side by clipping.** Blend2D clips to
  rectangles, not paths, and a rectangular clip cannot give a mitre; four
  clipped strokes would also put a seam wherever two of them met.
- **Stroking each side as a line.** Right for a lone side on a square box and
  wrong everywhere else: two strokes of different widths meet in a notch or an
  overlap rather than a mitre, and neither follows a corner's curve.
- **Elliptical inner corners, as CSS specifies.** Exact, and a second kind of
  curve in a toolkit whose `Corners` are circles (ADR-0216). No consumer draws a
  rounded box with sides of different widths; the golden that does is there to
  pin the approximation, not because anything ships one.
- **A `border-style` longhand that accepts `solid` and refuses the rest.** It
  would make `border-style: none` a way to turn a border off, which `border:
  none` already is, and every other value would be a declaration the lint
  passes and the painter cannot honour.
- **Horizontal rules only between a table's rows.** No layout change and no
  golden but the rules — and `TODO.md`'s entry is about `border-left`, which is
  the rule a reader of a table with left-aligned columns misses. With the
  columns equal, the vertical rule is right; without them, it was the thing that
  showed they were not.
- **Converting `table-rule` and `tab-rule` to borders for tidiness.** Both draw
  correctly, and each would move pixels or lose an ordering the node gives for
  free; a node that is right is not a workaround to be retired.
