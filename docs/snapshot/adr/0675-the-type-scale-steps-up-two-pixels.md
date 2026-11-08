# ADR-0675: The type scale steps up two pixels

- **Status:** Accepted
- **Date:** 2026-11-08
- **Relates to:** ADR-0626 (the showcase reads at 15px, and the reader picks the size)

## Context

The toolkit's default text was 13px body on an 18px line, and the rest of
the scale sat around it: display 28/34, title 20/26, heading 15/20, caption
11/14, mono 13/18. ADR-0626 had already found 13px hard to read in long text
and moved the showcase to 15px, but only the showcase. Every other
application, and a window with no stylesheet at all, still opened at 13px.
The user asked for the default size to go up by two everywhere.

## Decision

**Every rank of the type scale grows by 2px, in its size and in its line
height, so the gap between lines stays the same.**

| Rank    | Before | After |
|---------|--------|-------|
| display | 28/34  | 30/36 |
| title   | 20/26  | 22/28 |
| heading | 15/20  | 17/22 |
| body    | 13/18  | 15/20 |
| caption | 11/14  | 13/16 |
| mono    | 13/18  | 15/20 |

- **Points were read as pixels.** The request said "+2pt". The toolkit's
  sizes are logical pixels, and 2pt would be 2.67px, which renders at a
  fractional size.
- **`Typography.INITIAL`** is 15/20, so a window with no stylesheet still
  reads as the design system.
- **The Nord themes' tokens** (`--gb-font-*`, `--gb-line-*`) carry the new
  scale, in both files.
- **A canvas's fallback font**, used by a painter that is not on a canvas,
  goes from 14 to 16. It was a step above body before, and still is.
- **The tour card** used to write out the old heading, body and caption
  sizes. It reads the tokens now, so it moves with the theme.
- **The slider's readout is 48px wide**, where it was 40. Its width is fixed
  so the track does not move as the digits change, and 40 was `caption` at
  11px. At 13px and 150% text, "40%" wants 44, and `GalleryTextScaleTest`
  caught it overrunning.
- **A table cell is one line, cut with an ellipsis.** A row is `list`'s fixed
  height, so a cell that wrapped drew its second line over the row below. At
  13px nothing in the catalog's picture wrapped. At 15px "The Shire" did.
  `item` already made the same choice.
- **A HUD reading is at least a caption line tall.** A text box is sized by
  its face, 15.74 at 13px, and rounded outward to the pixel grid: to 16 or 17
  at 1x, to the half pixel at 2x. Three readings stacked moved the plate's
  edge between scales, and the light HUD's golden failed its 2x check.
  `min-height: var(--gb-line-caption)` makes every reading 16 at any scale,
  and larger text still grows it.
- **Some golden scenes got more room**, where the scene itself was too small
  for the new size: the dialog's window is 340 tall (at 300 the dialog met
  the scrim's height and scrolled its body under the first line), the HUD
  plate's frame is 240 wide, and the table's is 360.
- **Sizes stated on purpose stay.** The showcase's specimens named after
  their size (`.face-size-13`, `.sample-14`, the emoji sizes) and the
  lookalike screen's own type keep their numbers.
- **The showcase's `ReadingSize.SMALL`** is the toolkit's own scale and
  follows it. Medium (15/22) and Large (17/26) are unchanged, so Small and
  Medium now share a body size. Medium keeps the looser line and the larger
  headings.

## Consequences

- Every golden with text in it is retaken: the widget pictures, the book's
  pictures and the showcase screens.
- Text grows inside controls whose height comes from the density. A control
  that was sized around 13px text with no room to spare clips or wraps; the
  goldens are where that shows.
- `GalleryTextScaleTest` still audits the showcase at Small, which is the
  toolkit's scale. The 90%–150% promise is now made about 15px text.
