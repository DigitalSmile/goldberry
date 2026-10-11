# ADR-0608: `text-transform` changes what is drawn, one character for one

- **Status:** Accepted
- **Date:** 2026-10-11
- **Relates to:** `docs/css-plan.md` (CSS-24, CSS-25),
  `docs/css-support-2026-10-10.md` §5

## Context

`text-transform` is how a design system writes an overline, a tab label or a
button in capitals without the application writing its strings in capitals.
The subset had no property for it, nor for `letter-spacing`, which travels
with it in almost every rule that uses it.

CSS defines the transform on what is rendered. Two of its mappings change a
string's length: `uppercase` turns `ß` into `SS`, and some ligature
characters expand. A widget here holds its text and asks the renderer to
shape it, and an editable field then reads the shaped paragraph by offset:
the caret, the selection, a click, and the platform's composition all
address characters of the field's own text through it. A painted string of a
different length makes every one of those offsets point at the wrong
character, and a field would have to refuse the property or carry a map
between the two strings.

## Decision

**`text-transform` is applied where a widget's text is shaped, mapping every
code point to one code point of the same length, so the painted text and the
held text agree at every offset.**

- `uppercase`, `lowercase`, `capitalize` and `none`, in
  `dev.goldberry.text.flow.TextTransform`. `capitalize` title-cases the first
  letter of each word, a word starting after whitespace, a dash, a slash or an
  opening bracket, and not after an apostrophe.
- A mapping that would change the length keeps the character: `ß` stays `ß`
  under `uppercase`. Casing is the root locale's; there is no element language
  to choose another by.
- The transform, `letter-spacing` and `word-spacing` live in one record,
  `css.Lettering`, which is part of `Typography`: all three inherit, and all
  three are read by what shapes a paragraph. The renderer's `paragraph` seam
  applies them, and nothing below it knows a transform exists.
- An editable field takes all three like any other text, because its offsets
  stay true.
- `full-width` and `full-size-kana` are not read.

## Consequences

- A field's value, its clipboard copy and what assistive technology reads are
  the text as held; only the pixels change.
- German capitals keep `ß`. A sheet that needs `SS` writes the text that way.
- `letter-spacing` and `word-spacing` are folded into the shaped advances
  before anything is measured, so line breaking, the caret and the painter all
  see one set of widths, and the paragraph cache keys on them.

## Status

Done 2026-10-11: `TextTransform`, `Lettering`, `Typography.lettering`, the
three arms and longhands, `Paragraph.of(font, text, letter, word)`,
`ShapedRun.withXAdvances`, the cache key, the renderer's seam, and tests
(`SpacingTest`, `TextTransformTest`, `TextTransformRenderTest`, the
conformance table).
