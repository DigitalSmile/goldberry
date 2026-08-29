# 214. A table is a list with columns

Date: 2026-08-29

## Status

Accepted. Closes §10, and takes `table` out of ARCHITECTURE §17's deferrals.

## Context

`docs/core-widgets.md` §10's entry for `table` was one sentence — "deferred
(ARCHITECTURE §17); it awaits the virtualization work. Recorded here so the name
is reserved in the registry" — and `design-system.md` §3 had no metrics row for
it at all. So this ADR is unusual in that **most of the work was writing the
specification**, which §5 requires before code: a spec, a §3 metrics row and a
§3.1 transitions row, in that order, and then the widget.

What it was waiting for arrived with ADR-0213. And what it was waiting for turned
out to be the whole answer: a table's rows *are* a list's rows with more than one
thing in them.

## Decision

**It is a `list`, composed rather than reimplemented.** `Table` builds a
`ListView` whose item-factory returns a row of cells. The selection models, the
typeahead, `Home`/`End`, the item context menus and the ten-thousand-row window
are inherited rather than written twice — so a bug fixed in one is fixed in both,
and `TableTest` asserts the *seam* rather than repeating `ListTest`.

This is what §10 meant by a table "awaiting the virtualization work" without
saying so: the thing it was waiting for was `list`.

**A column is a key, a header, a width and a cell-factory**, and the cell-factory
is `list`'s item-factory per column — the same function with the same rules, so
any widget is a cell.

**The width is a number or a share, and flexbox already had both.** A fixed
column is that many pixels and will not shrink; a weighted one is `flex-grow`
over a zero basis. The layout engine does the arithmetic, so there is no second
sizing model to keep in step with the first — and a zero basis rather than
`auto`, or a column of long strings would quietly outgrow its weight.

**One piece of code sizes a header and the cells under it.** They have to come
out the same width or the table is not a table, and the cheapest guarantee is
that both go through `Sized.apply` rather than through two functions that look
alike.

**Sorting is the application's.** A click on a sortable header reports **what the
sort would become** — not the column it landed on — because which way a second
click goes is a rule about tables rather than something every application should
restate. The rows arrive in whatever order the application hands back, and the
caret is drawn for whatever `Sort` it is given. The same split `select`'s
autocomplete draws: a table over a database sorts in the query, and one that
sorted its own copy would be showing a different answer from the one the query
would give.

**A caret slot is kept on every sortable header, drawn or not.** This is
`tree-chevron`'s rule — a leaf keeps the gutter so that labels line up — and here
what it prevents is worse than a misalignment: without it, sorting a column takes
16px away from *that column's own label* at the moment the reader clicks it, so
every header the sort visits shuffles its text. **The golden image is what found
this**, which is the argument for §5's rule that a picture comes before the
widget is called done.

**`Box.Mark.Kind.CHEVRON_UP` is new.** A third chevron for the second one's
reason — the subset has no `transform` on a mark — and here the two are not
decoration but the *value*: a caret pointing the wrong way says the column is
sorted the other way, which is a lie a rotation would make easy to ship.

**A header is a Tab stop only when it sorts.** §2.2 wants everything reachable,
and a header that does nothing is a label; a stop on it would answer no key,
which is worse for a keyboard user than not being there.

**No rule between the rows, and one under the header.** §1's restraint applied to
the densest thing in the catalog: a grid of lines is furniture competing with the
data in it, and the row height plus the hover wash already say where a row
begins. The line that stays is a boundary between two *kinds* of thing.

## Alternatives considered

- **Reimplementing the rows.** It would have let a cell be the focusable thing
  rather than the row — which is what a spreadsheet needs and what §10 does not
  ask for — at the cost of a second copy of the selection models, the typeahead
  and the virtualization, three months after the first copy was written.
- **A grid layout instead of flexbox rows.** §8's subset has no `grid`, and
  adding one for this would be a large change to the style engine to avoid a
  small one to the widget. The columns line up because one function sizes them,
  which is the property a grid would have given for free and is cheap to
  guarantee by hand.
- **Sorting inside the widget**, with a `Comparator` per column. It works for a
  table over a list in memory and is wrong for every other table, and the
  application that outgrew it would have to take the sorting back — which is an
  API break rather than an addition.
- **A three-state sort cycle** (ascending → descending → unsorted). Most desktop
  tables do not offer it and the ones that do disagree about what the third state
  means. `Sort.next` is a suggestion the application may ignore, so one that wants
  the third state returns null from its own handler.
- **A sticky header through `affix`.** Tempting, and it is what `affix` is for —
  but `affix` pins to the nearest `scroll`, and a pinned affix is not pushed out
  by the next one (a live `TODO.md` entry), so two tables on one screen would
  overlap their headers. Left until something asks.

## Consequences

- **§10 is complete**, and `table` leaves ARCHITECTURE §17's deferred list —
  which had it behind virtualization, correctly.
- **The header is not virtualized**, because it is outside the list entirely. It
  costs one row on every frame regardless of the model's size.
- **A cell cannot be focused**, only its row. Right for §10's grid semantics and
  wrong for a spreadsheet, which is a different widget.
- **Column resizing is not built.** §3's metrics row allows for it (`column
  resize: 1:1, like split-pane's drag`) and nothing has asked; the drag would be
  a `split-pane` divider between the headers, which is a widget that already
  exists.
- **Horizontal virtualization is not built either**, and is a different
  arithmetic — worth it past about fifty columns, which is past where a table is
  the right widget.
- **`Column.sortable` takes a boolean** rather than reading as `sortable()`,
  because a record's accessor already has that name. The same reason `Table`'s
  `selection` and `tree`'s `checkable` take theirs, discovered the same way — by
  the compiler refusing an invalid accessor.
