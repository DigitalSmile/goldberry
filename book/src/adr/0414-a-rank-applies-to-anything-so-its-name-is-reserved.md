# 414. A rank applies to anything, so its name is reserved

Date: 2026-09-19

## Status

Accepted. Closes the `TODO.md` entry opened by
[ADR-0153](0153-a-hud-is-a-widget-and-a-reading-is-a-part.md).

## Context

The entry is one incident and one prediction:

> A widget's CSS classes share a namespace with the design system's. A `hud`
> reading named `display` picked up §1.4's `.display` type rank and rendered at
> 28px. Renamed, and nothing prevents the next one: there is no prefix
> convention, no check, and the two sets of names are written in different files
> by different people.

The prediction was right, twice over. Both are fixed here, and neither was
visible in a sheet, a test or a review.

### `tree-row.heading`, which drew at the wrong size for as long as it existed

`TreeRow.classes()` adds `heading` to a row that cannot be chosen — a parent in a
leaf-only tree, which is §3's default, so this is every branch of every ordinary
tree. `controls.css` styles it:

```css
/* A parent in a leaf-only tree: still a row, still openable, and not an answer.
   Dimmed rather than disabled, because disabled would say it is inert. */
tree-row.heading {
  color: var(--gb-text-muted);
}
```

One property. Meanwhile `.heading` — §1.4's rank, written with no type on it
because a rank applies to anything — set `font-size: 15px`, `line-height: 20px`
and `font-weight: 600` on the same element, and all three inherit into
`tree-label`. So "Europe" and "United Kingdom" drew larger and bolder than
"Norway" and "Scotland" beneath them, inside a row whose height is
`--gb-list-row-height` and does not grow. The rule that was supposed to make a
branch *quieter* than a leaf made it louder.

Three goldens have been drawing it since the widget shipped.

### `skeleton-bar.title`, which was waiting

`Skeleton.Shape.TITLE.cssClass()` was `title`, put on the `skeleton` and on each
of its bars, and `skeleton-bar.title` sets a height. `.title` set 20px/26px/600
on both nodes behind it. Nothing drew text in either, and the bar's height is an
absolute `var(--gb-font-title)`, so **no pixel moved** — which is precisely why
it survived. It is the same defect with the consequence not yet attached, and the
first dimension anybody writes in `em` attaches it.

## Decision

### The convention is reservation, not a prefix

The entry guesses a prefix and the guess is wrong, for a reason worth stating:
**both sets of names are published vocabulary.**

- A rank is what an *application* writes: `text class="heading"`, `text
  style="title"`, `TextRank.HEADING`, and §1.4's table. Prefixing it to
  `.gds-heading` renames the toolkit's most-typed word in four places at once and
  breaks every application stylesheet that mentions it.
- A widget's class is what a *stylesheet* targets: `tree-row.selected`,
  `button.primary`, `hud-reading.paint`. Prefixing those to `.gb-selected`
  renames forty-odd published modifiers across 188 files and several thousand
  lines of sheet.

And a prefix on the widget side buys nothing, because **a widget's class is
already namespaced — by its type.** `selected` means nothing on its own;
`tree-row.selected` does. That is the asymmetry the whole convention rests on:

> §1.4's seven type ranks are the only class names the base layer styles
> **unqualified**. Those seven names are reserved. Every other class a toolkit
> widget mints is a modifier of its own type, is written with that type in front
> of it, and may not be one of the seven.

A `gb-` prefix would be a second namespacing mechanism stacked on one that
already works, paid for with a breaking rename of one of the two vocabularies.
Reserving seven words costs one rename per collision, and there were two.

### The check reads both sets rather than listing either

`ClassNamespaceTest`, four assertions, and the reserved set is never written down:

1. **The base sheet's unqualified class rules are exactly `TextRank`'s values.**
   Parsed by the real parser, matched as "one compound, one class, no type, no id,
   no pseudo-class" — which is what "applies to anything" means operationally
   rather than a proxy for it. An eighth unqualified rule is either a rank, and
   belongs in `TextRank` and in §1.4's table, or a widget class that forgot its
   type. This is the convention *as an assertion*, and it is what stops the check
   rotting when §1.4 grows.

2. **No class is written both unqualified and beside a type.** This is the
   `tree-row.heading` finding, read straight out of the sheet with no reflection
   and no instances — and it is the only one of the four that reaches a widget
   **part**. `tree-row` is not a registered node name and no inflater builds one.

3. **No registered widget's `classes()` or `classes(FrameStats)` is a reserved
   name.** The case the sheet cannot answer: a collision nobody styled, where
   there is no rule to read and the widget simply draws at the rank's size.

4. **No enum that mints a CSS class mints a reserved one**, except `TextRank`.
   This is the shape the `hud`'s `display` actually had, and the one the first
   three would still miss: the name was not a literal in a `classes()` body and
   the widget was not a registered node — it was an enum constant on a part, read
   through `cssClass()`. It is also how `skeleton-bar.title` was found.

`TextRank` is the one exception in (4), and it is the definition rather than a
weakening: an enum whose entire job is to spell §1.4's ranks has to spell them.

## Alternatives considered

- **Prefix the ranks.** Above. It is the cheapest change to make *and* the
  most expensive to have made, because the ranks are the vocabulary a document
  author types.
- **Prefix every widget class.** 188 files, ~5 000 lines of sheet, and it
  duplicates the type qualifier that already separates them.
- **Check the CSS only.** Assertion (2) alone would have caught both of today's
  collisions and costs nothing to run. It also cannot see a collision nobody
  styled, which is the *more* dangerous one — a widget wearing a rank with no
  rule of its own has nothing pointing at the problem at all.
- **Enumerate widget classes by scanning class-file constant pools.** Tried in
  outline and rejected: `Dialog`, `GroupBox` and `Collapse` all carry the string
  `"title"` because it is a **KDL property name**, so the pool cannot tell a class
  the widget mints from an attribute it reads. A check whose first three findings
  are false is a check somebody adds an exclusion list to.
- **Leave `skeleton-bar.title`, since no pixel moves.** It is a collision that
  has not cashed in yet, in a widget whose whole purpose is to be "sized from the
  typography token it stands in for" — the one place in the catalog most likely
  to grow a relative unit.

## Consequences

- **Three tree goldens move**: `tree-dark`, `tree-light` and
  `tree-cascade-dark`, 3.38 % of pixels each. Branch labels drop from 15px/600 to
  the 13px/400 of the leaves beside them, and stay dimmed, which is what
  `tree-row.heading`'s comment said it was doing all along. The goldens are
  **not** re-blessed here; they are the deliverable of this ADR and want a human
  to look at the diff.
- **`tree-row.heading` is `tree-row.group`.** Neither the class nor the selector
  appears in `docs/` or anywhere in `book/`, and no test asserted on it, so
  nothing outside the two files changed.
- **`Skeleton.Shape` mints `shape-text`, `shape-title`, `shape-circle` and
  `shape-rect`.** The whole family, not the one that collided: three of them are
  safe only because §1.4 happens not to have a rank called `circle`, which is not
  a property anybody is maintaining, and `skeleton-bar.text` read like the `text`
  *type* into the bargain. **§5's `shape="title"` is unchanged** — what an
  application writes is a published word, what a widget puts in a class set is
  the widget's own business, and that asymmetry is the whole reason this rename
  cost nothing. `SkeletonTest` asserts both halves.
- **A widget may no longer name a class `display`, `title`, `heading`, `body`,
  `body-strong`, `caption` or `mono`.** Seven words, and the failure message says
  which one and why.
- **The check does not police an application's own classes**, and cannot. An
  application that writes `class="caption"` on a node is *using* the rank, which
  is what ranks are for. What this constrains is the toolkit.
- **`.md-*` and `.html-*` were already right.** The two view modules invent the
  most class names of anything here — some fifty between them — and every one is
  prefixed, including `.html-caption`, which is one letter from a collision and
  does not have one. The convention this writes down is one two modules had
  already arrived at; what was missing was anything that said so.
