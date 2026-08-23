# 184. A tree is a list that remembers what is open

Date: 2026-08-23

## Status

Accepted. Builds `docs/core-widgets.md` §3's `tree` in a first cut, and
`select tree=#true` on top of it — the last of §3's `select` line.

## Context

`select tree=#true` "takes a `tree`'s model instead of a flat option list, so the
popup is a `tree` and a selection is a node", and it had been waiting on a `tree`
that did not exist.

§3 says a `tree` "shares `list`'s item-factory so it inherits the virtualization
work when that lands" — and `list` is not built either. So the model this needs
had to be defined here rather than inherited, which is the reason this ADR exists
at all: the shape chosen now is the one `list` will have to agree with.

## Decision

### The id is the whole model

§3: "expansion state is retained across rebuilds by node **id**, not by index — a
tree that collapsed itself when its model reordered would be the same defect list
keys exist to prevent."

So `TreeNode` requires an id, `TreeState` holds a `Set<String>` of what is open,
and `TreeRow` uses the id as its reconciler key. A model rebuilt with its branches
sorted differently, or with a node inserted at the top, leaves every open branch
open and every focused row focused. That is one decision paying three times.

### A chevron is drawn before anyone knows what is under it

`mayHaveChildren()` is separate from `children()` and it is not a convenience.
§3 asks for children "fetched when it first expands", and a node that had to know
its children in order to decide whether to draw a chevron would defeat that
entirely — a directory tree would stat the whole disk to draw its first row.

So a lazy node draws a chevron on the strength of *having a supplier*, and loses
it on the frame after it opens if the supplier answered with nothing. That is what
every file manager does and it is the honest reading of "may have".

The fetch happens in the toggle rather than in `build`, which is the rest of
"lazy": a build that fetched would fetch for every node on every frame, and a
supplier that reads a disk or a network must run when the *user* asks. `first` is
a promise too — a branch closed and reopened does not go back to the supplier.

### The indent is a box, not padding

§2: "indent 20 per level; chevron 16 in the indent gutter". The row draws a sized
`tree-indent` before its chevron rather than taking padding, because a stylesheet
cannot compute a depth — and because the row's background has to reach the left
edge. An indented padding would start the selection highlight 40px in, which reads
as a misaligned row rather than a nested one.

A leaf keeps the chevron's **box** and draws nothing in it, so a folder's label
and a file's label at one level line up. The golden is the argument: dropping the
box on leaves steps every leaf half a chevron left, which reads as a level of
nesting that is not there.

### `Left` and `Right` are the row's, `Up` and `Down` are the scope's

§3 calls the keyboard "the part that has to be right". `Right` expands and `Left`
collapses, and both belong to the row because only a row knows whether it is open.

The other half of each is where the flattening pays off. Rows are flattened
**depth-first**, so a parent is immediately followed by its first child — which
means `Right` on an already-open row needs to do *nothing*: it falls through
unconsumed to the vertical focus scope, which moves to the next row, which is the
first child. `Left` on a closed row is the one that needs help, and it asks the
host to focus the parent by name, because a row cannot reach another row's
element ([ADR-0176](0176-a-dialog-is-a-widget-and-showing-one-is-not.md)).

Horizontal roving is therefore not merely absent, it is forbidden: a scope that
took `Left` and `Right` would take the widget's entire keyboard.

### Leaf-only is a selection rule, not a checkbox

§3 gives a standalone `tree` `checkable="none|leaf|any|cascade"` that "adds a
checkbox per node", and gives `select tree=` a `checkable` that decides whether a
parent may be **chosen** — "leaf-only by default, because 'Europe' is usually a
heading and not an answer".

Those are two different features wearing one word, and only the second is built.
A parent in a leaf-only tree is still a row: navigable, openable, and not an
answer. It is `.heading` to a stylesheet rather than `:disabled`, because disabled
would say it is inert and it is not.

### The popup is the same panel with a different child

`select tree=` opens a `select-list` holding one `Tree` rather than a row per
option. One panel, so the surface, the edge, the radius and the
scrolls-when-it-does-not-fit are one decision instead of two — and it is exactly
what §3's sentence describes.

## Consequences

- **This is a first cut and §3 asks for more.** Not built: the checkbox per node
  with `cascade` propagating down and `indeterminate` upward — which §3 calls
  "the one place the tri-state checkbox is not a decoration" — `*` to expand every
  sibling, type-to-select across visible rows, multi-selection, and `Home`/`End`
  to the first and last visible rows. Each is additive and none changes what is
  here; they are filed rather than half-built.
- **§2's `rotate` on the chevron is two marks instead.** "Expand/collapse: chevron
  `rotate` base" is not available, because §8's subset has no `transform` on a
  **mark** — the wall `select`'s chevron hit and the reason `CHEVRON_DOWN` exists
  beside `CHEVRON_END` at all ([ADR-0141](0141-a-select-is-a-closed-control-and-a-list.md)).
  A closed row draws `>` and an open one draws `v`. The cost is the animation, and
  that is the whole cost.
- **A tree's model cannot be written in markup**, and `select tree=` therefore
  takes none from a document. A node carries a `Supplier` for its children, which
  is not a thing KDL can say; §3 calls it "a `tree`'s model" and a model is the
  application's. The Choosers screen is Java for this reason as well as the two it
  already had.
- **`Select` is twelve components wide.** ADR-0181's positional-constructor
  argument now applies to it more than to `Box`, and this change churned four
  call sites to prove it. `RecordWitherTest` still covers only `Box` and
  `ComputedStyle`; extending it across the widgets is overdue.
- **`list` will have to agree with `TreeNode`.** §3 says the two share an
  item-factory, and defining the model here means `list` inherits this shape
  rather than choosing its own — which is the right way round, but it is a
  commitment made by the widget that happened to be built first.
