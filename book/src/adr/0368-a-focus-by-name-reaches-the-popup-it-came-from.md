# 368. A focus by name reaches the popup it came from

Date: 2026-09-17

## Status

Accepted. Closes `book/src/TODO.md`'s "A `select tree=#true` has no typeahead".

## Context

The TODO entry said a flat select's open list reads letters on the capture
phase (ADR-0246) and a tree in the same panel did not. It framed the gap as a
design question: whether a prefix search descends into collapsed branches, and
what it means to match a node nobody can see.

`tree` had already answered that when its keyboard was finished: its typeahead
matches **visible rows only** and moves the focus without choosing (ADR-0209).
The select's panel did not stand in the way either. `SelectList` has no
list-level typeahead for a tree, so a letter reaches the focused tree row.

Reproducing it found the cause somewhere else. A tree moves its typeahead by
asking `host.focus("tree-" + id)`. A popup's element tree is built with the
window's `Launcher` as its host, and `Launcher.focus` asked only the window's
router, which has no such row. The letter arrived, the tree found its match, and
the focus request landed in the wrong window and did nothing. Anything in a
popup that focuses by name had the same defect.

A second, smaller one: a tree select opened on its first row whatever it held,
because `chosenId` only knew `option` rows.

## Decision

**`Host.focus(id)` tries the open popups, topmost first, before the window, and
a tree select opens on its chosen row.**

- `Popup.focusById` asks the popup's own router. `Launcher.focus` walks the open
  popups from the topmost down and returns at the first that finds the id, then
  asks the window. Topmost wins for the reason a key goes to it.
- `SelectState.chosenTreeRow` names `tree-<value>` when the value is a root and
  so is on screen when the list opens; otherwise the popup focuses its first row
  as before.

## Consequences

- A select's tree list takes a typeahead over its visible rows, which is the
  same behaviour a standalone `tree` has.
- A value inside a collapsed branch still opens the list on the first row;
  expanding a path to reveal it is a separate decision.
- An id present in both a popup and the window resolves to the popup. Ids are
  meant to be unique per window, and a popup is a window.
