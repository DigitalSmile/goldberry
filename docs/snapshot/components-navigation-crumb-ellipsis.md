<!-- Destination: book/src/components/navigation.md, `## `breadcrumbs``: a new
     paragraph after the one that ends "…Pressing that button opens a menu of
     the hidden crumbs." -->

A trail that is still wider than its row after folding gives way at the
current crumb. That crumb shrinks, and its name ends in `…`. Every crumb before
it keeps its whole name. `crumb:checked` sets `flex-shrink: 1`, so a
stylesheet can choose another crumb to shrink, or none.
