# 224. A right-click selects what it is over

Date: 2026-08-30

## Status

Accepted. Closes a `TODO.md` entry opened by
[ADR-0108](0108-a-context-menu-is-a-name-on-a-widget.md).

## Context

Every file manager selects the row you right-click before it opens the menu. The
toolkit did not, and the entry that recorded it said why:

> that is the application's to do in its handler today, because the toolkit has
> no notion of what "select" means for an arbitrary widget.

That is true and it is not the end of the argument. The toolkit has no notion of
selection — but the **widget under the pointer** does. A row in a `list` knows
what selecting it means, knows whether it is already selected, and already
reports through a callback the application owns. What was missing was not a
concept of selection; it was a **moment** at which a widget could be told the
gesture had happened to it.

Leaving it to the application is also worse than it sounds, because the rule is
not "select the row". It is:

- select the row you right-clicked, **unless it is already in the selection**, in
  which case leave the selection alone;
- do it *before* the menu opens, so a menu built from the selection reads the new
  one and the row is drawn selected in the frame the menu appears over.

Both halves are easy to get wrong by hand, and the failure mode of the first —
right-clicking one of five selected files collapses them to one — destroys
exactly what the user was about to act on.

## Decision

**A widget says what selecting it means; the launcher says when.**

`io.github.digitalsmile.goldberry.input.handler.Selects` is one method,
`selectForContextMenu()`, beside `Handles`, `Located` and `Measured` in the
package of things a widget implements to hear about input.

**The launcher's existing walk supplies the moment.** `openContextMenu` already
walks from what the gesture landed on up to the nearest widget that named a menu.
It now also remembers the **deepest** `Selects` it passed, and asks it — once —
immediately before opening. So a right-click on a cell inside a row targets the
row, by the same rule that makes a right-click on a button's label a right-click
on the button.

**Nothing is asked when no menu opens.** A selection that changed with nothing to
show for it is a gesture with no visible cause, and a right-click over a widget
that named no menu is meant to do nothing at all.

**The keyboard's menu key shares it**, because it shares the walk (ADR-0208). The
menu key on a focused-but-unselected row selects it. That is the same rule seen
from the other device — the menu acts on what it opened over — and the two
disagreeing would be worse than either behaviour on its own.

**`ListRow` and `TreeRow` implement it**, in four lines each: nothing when the
row is unselectable or already selected, otherwise report with `Modifiers.NONE`.
A `table` inherits it, because a table is a `ListView` whose item-factory returns
a row of cells.

**It asks rather than selects.** The row reports through the same callback a
click reports through, and the application's answer is what the next frame draws
(ADR-0063). Nothing about this makes a widget hold a selection.

## Alternatives considered

- **Leaving it to the application**, which is what shipped. It makes every
  application re-derive the already-selected rule, and an application that gets
  it wrong loses the user's selection at the exact moment they were about to act
  on it.
- **A `Consumer<Element>` on `Host.onContextMenu`**, so the application is handed
  what was clicked and decides. It is the same work moved: the application still
  has to know that the thing it was handed is a row, and which list it is in.
- **Making the router deliver a `CLICKED` for the secondary button** so rows
  handle it in `onPointer` like any other click. Tempting, and wrong in two ways:
  the press that opens a context menu is deliberately taken by the launcher
  before the router sees it (ADR-0108), and a row that treated a right-click as a
  click would have to re-derive "unless already selected" *and* would fire for
  right-clicks that open no menu.
- **Selecting on the press and undoing it if no menu opened.** Two frames of a
  selection nobody asked for, to save one field on the walk.
- **A wider `Selectable` contract** — "are you selected", "select yourself",
  "what is your value" — so the toolkit could reason about selection generally.
  That is a notion of selection, which the entry was right to say the toolkit
  does not have and does not need: one method that fires at one moment has no
  invariants to keep.

## Consequences

- **A fifth interface in `input.handler`**, and the first one there that is a
  *request* rather than a report. `Handles` is told what happened; this asks for
  something to happen.
- **Two widgets implement it and a third inherits it.** `ListRow`, `TreeRow`, and
  `table` through `ListView`. Nothing else in the catalog has a selection to
  disturb.
- **The launcher's walk grew one field and one branch**, and its cost is one
  `instanceof` per ancestor on a gesture that already walks them.
- **A behaviour change for applications that already did this by hand.** One that
  selects in its own `onContextMenu` handler will now see the row selected before
  its handler runs — which makes its own call redundant rather than wrong, since
  asking for a selection that is already the selection reports the same set.
- **`@Nullable` is not part of it**: `selectForContextMenu()` takes nothing and
  returns nothing. A widget that wants to know *where* it was clicked cannot ask,
  and nothing in the catalog wants to — a row is the unit either way.
- **What is still open**: a right-click over a *drag* selection, and a
  right-click that should extend rather than replace on the platforms that offer
  it. Neither is in `docs/core-widgets.md`, and both would need the modifiers,
  which this deliberately does not carry.
