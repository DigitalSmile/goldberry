<!-- Destination: book/src/components/collections.md, `## `list``: the paragraph
     after the one that begins "**Rows are virtualized by row height.**", and
     one row in the **Java** table after `virtualized()`, `virtualized(double)`. -->

**Rows that are not one height are virtualized by measuring them.**
`virtualized(RowHeights.estimating(64))` builds the same window, measures each
row as it is laid out, and counts every row it has not built at the estimate,
so a timeline of five thousand messages costs a window per frame. Heights are
remembered by each item's identity, so rows prepended above keep theirs, and a
change of width forgets them. When a row above the line being read turns out
taller or shorter than it was counted, the enclosing `scroll` moves by the
difference and the line stays where it was. A `scroll` with `anchor="end"` or
`preserve-on-prepend` does that correcting itself. `Home`, `End` and
type-to-select scroll the row they reach to the top. The rows carry the class
`measured` and are as tall as their content, never shorter than
`--gb-list-row-height`. Space rows with padding, because a row's height is its
border box and a `margin` or a `gap` between rows is not counted.

| `virtualized(RowHeights)` | Build only the visible window of rows that vary in height, measured as they are built. |
