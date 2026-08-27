# 209. A tree finishes its keyboard

Date: 2026-08-27

## Status

Accepted. Three of the five things ADR-0184 shipped `tree` without.

## Context

`docs/core-widgets.md` §3 says of `tree` that "keyboard is the part that has to be
right", and then lists what right means: `Right`/`Left`, `Home`/`End` to the first
and last **visible** rows, `*` to expand every sibling, and type-to-select across
visible rows only. ADR-0184 built the first pair and left the other three, along
with `checkable` and multi-selection.

The three left over have one thing in common, and it is why they were left: each
needs to know about rows the focused one cannot see. `Right` and `Left` are a row's
own business — only the row knows whether it is open — but the first row of the
whole flattened list, and the siblings of this one, and the next row whose label
starts with a letter are all facts about the *tree*.

## Decision

**All three are callbacks the tree hands down**, in the shape `onOut` already had:
the row reports the key and the state, which flattened the list, answers it. `onEnd`
takes a direction, `onSiblings` takes nothing, `onType` takes what was typed.

**`Home` and `End` mean the flattened list**, not the viewport. `End` in a scrolled
tree goes to the last row of the model and the focus ring asks the scroller to
follow (ADR-0120) — which is what every tree does and what `Ctrl+End` means in
every document.

**`*` expands siblings and not descendants.** That is the reading that makes it
useful on a big tree: it opens the level so the reader can see across it. A key
that opened everything underneath would hang a lazy tree by fetching its whole
model on one keystroke. A lazy sibling's supplier runs here exactly as it does for
one opened by hand, which is the one place this key costs anything and the one
place it is doing what was asked.

**`*` and type-to-select both arrive as `TextEvent`.** `*` is a *character*, and
the key it sits on differs by layout — `Shift+8` on a US keyboard, its own key on
a numpad, neither on AZERTY. Asking for the key would be asking for the physical
position, which §7.1 says this toolkit does not answer. Typeahead wants what was
typed for the same reason `select`'s does (ADR-0141): one character can take
several keys, and a tree of French cities has to answer to a dead key like
everything else.

**Type-to-select matches visible rows only**, which is §3's own wording and is the
rule that keeps it honest: a search that opened branches to find a match would be a
search, and a tree with a lazy model cannot have one without fetching everything.

**Typing moves the focus and does not choose.** A tree reports what the user asked
for and selects nothing itself (ADR-0063); typing is a way of getting somewhere and
`Enter` is what chooses. That is the same split `select`'s open list draws, where
arrows move and `Enter` commits.

**The typeahead's three cases are `select`'s**, including the middle one: the same
letter again asks for the *next* row starting with it rather than searching for
"ss". A user pressing `s` four times to reach the fourth `s`-word is relying on it.

## Alternatives considered

- **`Home`/`End` on the tree's own node** rather than as a row callback. The tree
  is not focusable — every row is, so the arrows rove between them (§7.2) — so
  there is no node for the key to arrive at.
- **`Home`/`End` meaning the first and last rows *in view*.** It is what the words
  could mean and it is not what any tree does; it also makes the key's effect
  depend on the size of an ancestor the tree cannot see.
- **`*` as `Key`-based `Shift+DIGIT_8`.** It is right on exactly one layout.
- **Type-to-select selecting rather than focusing.** It would make a keystroke
  commit a value in a controlled widget, which is the thing ADR-0063 exists to
  prevent — and it would make `Enter` redundant on the row the typing landed on.
- **Searching the whole model, opening branches to reveal a match.** A genuinely
  useful feature and a different one: it is a filter, it needs a query the tree can
  show, and it cannot exist on a lazy model without fetching all of it.

## Consequences

- **`TreeState` keeps the flattened list in a field**, written by `build` and read
  only by the handlers. Safe because by the time a handler runs it is the list on
  screen; a build never reads it.
- **The typeahead is keyed by id**, not by `indexOf`. A `TreeNode` is a record and
  equality is over every component, while the id is the whole model here
  (ADR-0184) and is what the rest of the class already keys on.
- **`TestHost` gained `forgetFocusRequests()`.** `focusRequests()` hands back a
  copy, so a test that made several moves and cleared it between them was clearing
  a list nothing was writing to — a test that could not fail.
- **Two of `tree`'s five leftovers are still open**, and they are the two that are
  not keyboard work: the checkbox per node with `cascade` and `indeterminate`, and
  multi-selection. The second is genuinely blocked — §3 says a tree shares `list`'s
  selection models and `list` is not built — and the first is a widget's worth of
  work rather than a gap in one.
