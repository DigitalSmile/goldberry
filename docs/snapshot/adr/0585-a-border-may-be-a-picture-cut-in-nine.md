# ADR-0585: A border may be a picture cut in nine

- **Status:** Accepted
- **Date:** 2026-10-08
- **Relates to:** the Gwent clone's issue list (GB-024),
  [ADR-0584](0584-a-background-layer-may-be-a-picture-sized-and-tiled-and-the-stylesheet-reads-it-through-the-image-cache.md)

## Context

A box's edge was a `border` of one colour a side. The downstream's UI kit is
painted as nine-slice sprites — panels with ornamented corners, buttons in
four states, fields, tabs, banners — each with its slice insets in a
manifest. Nine `image` widgets per panel would draw them from the game's
code, and each state's sprite would be swapped by hand rather than by
`:hover` and `:disabled`.

## Decision

**CSS's `border-image`**, as **`dev.goldberry.css.image.BorderImage`**, a
component of **`Decoration`** so the cascade picks a state's sprite like any
other property. Its parts:

- **`border-image-source`**: `none` or one `url("…")`, resolved through
  `StyleImages` as a background picture is, with its `@2x` variant and a
  `#xywh=` region. A gradient is not read.
- **`border-image-slice`**: one to four numbers or percentages and `fill`.
  A number is in the 1x picture's pixels, and is doubled for the `@2x` one.
- **`border-image-width`**: numbers (border widths), lengths, percentages of
  the area, and `auto` (the slice at the picture's density).
- **`border-image-outset`**: numbers and lengths, drawn, and counted in a
  box's ink so damage and culling cover it.
- **`border-image-repeat`**: `stretch`, `repeat` (centred, ends cut) and
  `round` (a whole number of tiles), one or two values. `space` is not read.
- **The shorthand**, `<source> || <slice> [/ <width> | / <width>? /
  <outset>]? || <repeat>`, which resets what it does not name.

**`NineSlice.pieces`** is the arithmetic, apart from the drawing so it is
tested by numbers: slice lines clamped to the picture, the area grown by the
outsets, widths shrunk together when two opposite ones overflow it, corners
stretched into their rectangles, edges scaled across their thickness and
tiled along their length, and the middle — only with `fill` — scaled as the
top and left edges are. A 48-pixel corner of a 1x sprite drawn 48 wide, or of
its `@2x` sprite at 200%, is pixel for pixel.

**The painter** places every piece's edges on whole device pixels so pieces
share their seams, draws a stretched piece as one blit and a tiled one under
a rectangle clip, and stretches a piece that would need more than 4096 tiles.
**A drawn border image replaces the border's colour stroke.** While the
picture loads, or when it cannot be read, the border is drawn from its
colours, which is CSS's rule for an image that cannot be shown. As in CSS a
border image is not clipped by `border-radius`, and `border-width` still
alone decides layout.

## Alternatives considered

- **A `nine-slice` widget** taking an image and insets. It would be a second
  way to draw an edge, outside the cascade, and the states would be the
  application's to swap.
- **Outset parsed and held at zero**, as first planned. Drawing it cost one
  more term in `BoxInk`, which already grows a box's ink for its ring and
  shadow.

## Consequences

- `Decoration` has a seventh component; the six-argument constructor stays
  and supplies `BorderImage.NONE`, and every wither carries it.
- The initial `border-image-width` is `1`, the border's width: a box with no
  border draws nothing until a width is given, as in CSS.
- Tests: `BorderImageParserTest` (each longhand, the shorthand in any order,
  refusals, and `:hover` swapping the source while the plain rule's slice
  stays), `NineSliceTest` (corners, edges, middle, density, widths, overflow,
  outsets, repeat and round), and goldens of a 48-pixel-corner panel on three
  sizes with `round` and `repeat`, at 100% (`css-border-image`, swept across
  scales with no `@2x` beside it) and at 200% from the `@2x` sprite
  (`css-border-image-2x`, whose thread only the 2x pixels carry).
