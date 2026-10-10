<!-- Destination: book/src/guide/styling.md, `### Text flow`. Replaces the two lines from
     "`text-align: start | center | end`, and `text-decoration` or" to
     "are refused for `text-align`, and so is `justify`." with the text below. -->

`text-align: start | center | end | left | right`, and `text-decoration` or
`text-decoration-line: none | underline | line-through`. `start` and `end` are
the edges a line begins and ends at, and `left` and `right` are the sides of
the box. Every line is set left to right, so the two pairs place a line the
same way. `justify` is refused with a warning, and the line keeps the alignment
it inherited.
