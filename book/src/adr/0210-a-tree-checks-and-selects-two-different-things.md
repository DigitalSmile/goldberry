# 210. A tree checks and selects two different things

Date: 2026-08-27

## Status

Accepted. The last two of `tree`'s five leftovers — §3's `checkable=` and its
selection models.

## Context

ADR-0184 shipped `tree` and named five things §3 asks for that it did not build.
ADR-0209 finished the keyboard three. What was left was the checkbox per node with
`cascade` and `indeterminate`, and multi-selection.

Multi-selection was recorded as **blocked**: §3 says a tree shares `list`'s
selection models and `list` is not built. That reading was too strict, and the
precedent against it is `tree`'s own — ADR-0184 defined the *node* model here for
exactly the same reason, and wrote down that `list` will have to agree with it.
The selection models are the shape every desktop list has, which is what makes it
a small promise to make on `list`'s behalf.

The checkbox needed a question answered first, and the question is in the design
document rather than in the code. §3 spends the word `checkable` twice. On
`select tree=` it is a rule about which rows are an **answer** — "leaf-only by
default, because 'Europe' is usually a heading and not an answer". On a standalone
`tree` it "adds a **checkbox** per node". Those are not the same feature and the
existing code had already picked the first, as `Tree.anyNode`.

## Decision

**Selecting and checking are two values, reported through two callbacks.** The
selection is where the reader *is*; the checks are what they have *marked*. A file
manager where those were one thing could not copy six files, because opening the
seventh folder would clear the list. So `Tree` carries `selected`/`onSelect` and
`checked`/`onCheck`, and a tree may have either, both, or neither.

**`checkable` is the checkbox axis and `leafOnly` is the answer axis**, under two
names. The disagreement is recorded in `ARCHITECTURE.md` §17.1 rather than
resolved by picking one, because both of §3's sentences describe something real
and it is the *word* that is doing two jobs.

**A cascade parent's state is derived, never stored.** `Checkable.CASCADE` reads a
branch from what is under it: all children checked is `CHECKED`, none is
`UNCHECKED`, anything else is `MIXED`. A stored parent bit would go stale the
moment one child was unticked, and the row would then claim "all of these" while
showing one that is not.

**A lazy branch nobody has opened reads its own membership.** It has no known
children to derive from, and fetching a model the user has not asked for in order
to draw a checkbox is the one thing a lazy tree must not do.

**Clicking a mixed branch asks for all of it.** `Checkbox.Value.toggled()` has said
so since it shipped — "the user is asking for 'all of them', which is the only
reading of a click on a partial selection that is ever what was meant" — and this
is its second caller rather than a second copy of the rule.

**The box borrows `check-indicator`.** It is already a 16px square that draws a
tick, draws a *bar* for the mixed state, and takes its colours from `:checked` and
`:indeterminate` rules a theme has written. A `tree-check` part wraps it to add
the hit target and the click, because the indicator is a `Paints` leaf with no
handler and a row that merely nested one would have a box you could look at and
not tick.

**Ticking consumes the click**, which is `TreeChevron`'s rule and the same mistake
it avoids: a tick that also selected would make the box unusable in a
single-selection tree, since every tick would move the highlight. `Space` ticks and
`Enter` still chooses — `checkbox`'s own split, for `checkbox`'s own reason, that
`Enter` belongs to a dialog's default action.

**Selection reports the whole set, even when it holds one.** A `Shift` range is
computed over the flattened visible rows, which only the tree can see, so an id on
its own would be an answer the application could not turn back into a selection.
The three-argument constructor unwraps it again, so `select tree=` and every
existing caller see the `String` they always saw.

**`Ctrl` toggles, `Shift` sweeps, a plain press replaces**, and the anchor is the
last row chosen *without* `Shift` — so a run of shifted presses sweeps from one
end rather than growing from wherever it last stopped, which is what makes an
over-shot range recoverable without starting again. `Ctrl+Enter` and `Shift+Enter`
are the keyboard's halves of the same two gestures; `Alt+Enter` is deliberately
left alone so an application's accelerator on it still reaches the window.

**`Selection.NONE` makes nothing an answer** and leaves the rows navigable and
openable — for a tree whose real answer is its checkboxes, where a selection
highlight would be a second thing claiming to be the choice.

## Alternatives considered

- **One value: checked *is* selected.** It is what a `select tree=` wants and it
  is wrong for a `tree`: §3 asks for both, and the copy-six-files case is the
  ordinary one rather than the exotic one.
- **Storing the cascade parent's bit** and propagating on every change. It is
  fewer traversals and it is a second source of truth for one fact; the drift is
  invisible until a screenshot shows a ticked folder over an unticked file.
- **Fetching a lazy branch to derive its checkbox.** It makes the mixed state
  exact and it makes drawing a tree fetch a model — which is precisely what
  `mayHaveChildren` exists to avoid, since a directory tree would stat the whole
  disk to draw its first row.
- **A second check indicator of the tree's own.** Two things to keep in step, and
  the reasoning about the tri-state lives in the first one.
- **Reporting the pressed id plus the modifiers** and letting the application
  compute the set. It hands out a problem the application cannot solve: a `Shift`
  range runs over rows whose order and visibility are the tree's.
- **Keeping the selection inside the tree** so `Ctrl` had something to toggle
  against. It is what makes the widget uncontrolled, which is ADR-0063's line —
  and it is what the two tests that failed first were assuming. They were rewritten
  to apply the answer back, which is what an application does.
- **A `TreeOptions` record**, as `ChartOptions` did for charts (ADR-0202), rather
  than nine components. The same argument applies and the trigger has not been
  met: the charts bundled when the *next* feature would have made seven on each of
  three widgets, threaded by hand through four places. A tree is one widget and
  its withers already keep the public surface at one call per feature.

## Consequences

- **`Tree` has nine components**, and the three-argument constructor is what
  almost every caller uses. `selected` is a `Set` inside and a `String` at that
  door, because a caller with one selection has a value rather than a set of one.
- **`select tree=` is untouched.** It builds the three-argument form and receives
  a `String`, exactly as before.
- **The default is unchanged in every direction**: `Selection.SINGLE`,
  `Checkable.NONE`, leaf-only. Every existing golden is byte-identical; the new
  one is a cascade tree with a partly-ticked branch, which is the one state a
  picture is the only proof of — that the mixed mark is a bar and not a greyed
  tick.
- **`Selection` and `Checkable` are defined in `panel.tree`**, and `list` will
  have to agree with them when it arrives — the same debt ADR-0184 took on for the
  node model, taken knowingly and in the same place.
- **The `checkable` disagreement is now in `ARCHITECTURE.md` §17.1**, which is
  where a word doing two jobs in the design documents belongs.
- **`tree` owes nothing further from §3.** All five leftovers are built.
