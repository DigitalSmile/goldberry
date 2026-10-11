# ADR-0626: The showcase reads at 15px, and the reader picks the size

- **Status:** Accepted
- **Date:** 2026-10-11
- **Relates to:** ADR-0624 (the shell); `docs/showcase-redesign.md`

## Context

The user found long text in the showcase very hard to read. Body text was the
toolkit's 13px. Card titles were muted captions at 11px, and a screen's prose
was set at the `heading` rank to make up for it. Paragraphs ran the width of a
maximized window. The user chose 15px for body text.

## Decision

**A `ReadingSize`, Small, Medium or Large, is a sheet of the theme's type
tokens on `:root` in the application layer. The showcase opens at Medium
(15/22).**

| Rank    | Small | Medium | Large |
|---------|-------|--------|-------|
| display | 28/34 | 32/40  | 36/44 |
| title   | 20/26 | 24/30  | 28/34 |
| heading | 15/20 | 18/24  | 20/28 |
| body    | 13/18 | 15/22  | 17/26 |
| caption | 11/14 | 13/18  | 15/20 |
| code    | 13/18 | 14/20  | 16/24 |

- **Tokens on `:root`.** A token sheet reaches every tree the window draws,
  menus and dialogs included, where a class on the window's root would not.
  The same rule sets the root's own `font-size` and `line-height`, so a `text`
  nobody classed inherits the size. Small is the toolkit's own scale.
- **`app.reading`** is bound with `restyle = true`. View has the three sizes
  ticked, and `Ctrl+=` and `Ctrl+-` step through them. Control heights are the
  density's and do not change; the text grows inside them.
- **The showcase's own type** follows the ranks. A screen's heading is
  `title`. Prose is `body`, no longer borrowed from `heading`. A card's title is
  body at the strong weight in the text's own ink, not a muted caption. A card's
  summary is body in the muted ink. An intro paragraph stops at 760px.
- **Walls are wider:** `Wall.COLUMN_WIDTH` went from 360 to 440, and cards have
  20px of padding.

## Consequences

- `GalleryTextScaleTest` audits against **Small**. The renderer's 90%–150%
  promise is about the toolkit's scale. Medium and Large are 115% and 131% of
  it, inside the promise. 150% of Medium would audit a size nothing offers.
- `ShowcaseTypographyTest` resolves through the Medium sheet and checks that
  an unclassed `text` inherits 15px, and that the three sizes step 13, 15, 17.
- The guide's screen pictures show the larger type.
