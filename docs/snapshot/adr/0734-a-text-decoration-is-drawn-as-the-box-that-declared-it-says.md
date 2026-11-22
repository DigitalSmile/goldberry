# ADR-0734: A text decoration is drawn as the box that declared it says

- **Status:** Accepted
- **Date:** 2026-11-22
- **Relates to:** ADR-0733 (the plan whose D4 this builds)

## Context

`text-decoration` was lines only: `underline` and `line-through`, drawn as
rectangles at the face's own position and thickness, in the text's colour. A
declaration that named a style or a colour was dropped whole, on purpose, so
that `underline wavy red` did not quietly become a straight line in the
text's colour. A spell checker needs exactly that declaration: a wavy red line
under text drawn in its own colour.

## Decision

1. **The four longhands and `text-underline-offset` are read.** The longhands
   are `text-decoration-line`, `-style` (`solid`, `double`, `dotted`,
   `dashed`, `wavy`), `-color` and `-thickness` (`auto`, `from-font`, or a
   length or percentage of the element's own font size, not negative). The
   shorthand takes its four parts in any order, each at most once, and
   resets the parts it leaves out. It does not touch the offset. `overline` and
   `blink` are still refused, as are a part written twice and `none` beside a
   line.
2. **The cascade holds one value, `TextDecorations`:** the lines and a
   `DecorationPaint` (style, colour or the text's, thickness or the face's,
   offset or the face's). CSS propagates a decoration whole from the box that
   declared it, and here propagation is inheritance. One component inherits
   the line together with its pattern and colour, so a child's label is
   underlined as its parent said, whatever the child's own colour.
   `ComputedStyle.textDecoration()` returns it.
3. **The paint rides beside the lines.** `TextFlow`, `SpanPaint` and
   `MarkStyle` each gain a `decorationPaint` component, and their former
   constructors stay and mean `solid`. A span's line can therefore have a
   colour of its own.
4. **One painter, `text.decoration.DecorationPainter`.** `solid` is still a
   rectangle, so no existing picture moved. `double` is two lines a thickness
   apart, `dotted` round dots a thickness apart (squares below 1.5 units),
   `dashed` dashes three thicknesses long with a gap of two, and `wavy` a
   stroked sine with an amplitude of the thickness (at least one unit) and a
   period of six amplitudes. An underline grows down from its position. A
   line-through stays centred on the face's.
5. **Patterns are anchored to the frame's x, not to the piece.** A line of text
   is drawn in pieces, one per span and face. Dots, dashes and the wave fall
   where they would had the line been one piece, and each dot and dash
   belongs to the piece its start falls in.
6. **`currentcolor` is fixed when the style is computed,** as for every other
   colour here. No declaration at all means the line follows the text's
   colour.

## Consequences

- `::spelling-error` (ADR-0733) is plain CSS:
  `text-decoration: wavy underline var(--gb-danger)`.
- `ComputedStyle.textDecoration()` returns a `TextDecorations` where it
  returned a `Set`. Record patterns over `TextFlow`, `SpanPaint` and
  `MarkStyle` name one more component.
- A style or colour on a child with no line of its own changes the line it
  inherits. CSS would draw the parent's line unchanged. This is the price of
  propagation as inheritance, and it was already the price for the lines.
- The conformance table moves `text-decoration: underline red` and
  `text-decoration-color` to applied.
