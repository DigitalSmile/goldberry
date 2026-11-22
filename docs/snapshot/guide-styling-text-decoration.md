# `text-decoration` in full

Goes into `book/src/guide/styling.md`, `### Text flow`. Add the second line to
the sample block, and replace the sentence that ends "`text-decoration` or
`text-decoration-line: none | underline | line-through`" with the paragraphs
below.

```css
.misspelled { text-decoration: wavy underline var(--gb-danger) }
```

---

`text-align: start | center | end`. `left` and `right` are refused, and so is
`justify`.

`text-decoration` is a shorthand for four properties, written in any order:

- `text-decoration-line: none | underline | line-through`, one or both;
- `text-decoration-style: solid | double | dotted | dashed | wavy`;
- `text-decoration-color`, which follows the text's colour until one is
  written;
- `text-decoration-thickness: auto | from-font | <length> | <percentage>`. A
  percentage is of the element's font size, and `auto` and `from-font` both
  take the face's own thickness.

The shorthand resets the parts it does not name, so `text-decoration:
underline` after `text-decoration: wavy red line-through` draws a solid
underline in the text's colour. `text-underline-offset: auto | <length> |
<percentage>` is not part of it. It moves an underline's top edge to that
distance below the baseline and leaves a line-through where it is. `overline`
is not drawn, and a declaration that asks for it is dropped.

A decoration reaches the text inside the element that declares it, together
with its style and colour. `button.link { text-decoration: underline dotted }`
draws a dotted line under the label inside the button. Dots, dashes and waves
keep their rhythm across a line written in several styles, so a bold word in
the middle of an underlined sentence does not restart the pattern.
