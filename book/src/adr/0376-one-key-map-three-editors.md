# 376. One key map, three editors

Date: 2026-09-17

## Status

Accepted. Closes `book/src/TODO.md`'s "Two key maps" — which was three by the
time it was read again.

## Context

`text.edit.Editor`, `text-input`'s `TextField` and `text-area`'s `TextAreaBox`
each held their own `onKey`, and each mapped the same keys to the same intents.
They agreed because each was written by reading the last: agreement by
inheritance rather than by construction, and nothing to keep it. A toolkit whose
two editors disagree about `Ctrl+Shift+Z` has a bug in one of them and no test
that can see it.

The entry's own answer was to converge the *editors* — the controls holding an
`Editor` instead of their own state machines — and its own objection was that
this is a rewrite of two controls with a hundred golden images and a full
interaction suite behind them. Both halves are right, and neither is a reason to
keep three tables: what was duplicated is the **keyboard**, and the keyboard is
the part that has no text model in it.

## Decision

**`text.edit.keys` holds the map. Each editor keeps its own text and answers
commands.**

- `EditKeys.of(event, surface)` is the one table. It returns an `EditCommand` —
  `Move`, `MoveLine`, `Delete`, `Type`, or one of six `Simple` accelerators — or
  null for a key no editor may take. `Tab`, `Escape` and a field's `Enter` are
  null, which is the half of the contract that keeps focus moving and dialogs
  closing.
- `EditSurface` is everything the map needs to know about the editor asking, and
  it is two questions: are `Up` and `Down` lines here, and is `Enter` a newline.
  `FIELD` answers no and no, `WRAPPED` yes and no, `DOCUMENT` yes and yes.
- `EditCommand` is sealed, so each editor's `switch` is exhaustive: a command
  added later fails to compile in the three places that have to answer it, which
  is what the three hand-copied tables could never do.
- `EditCommand.Simple.isEdit()` says which commands a read-only editor must
  refuse. That list was the other thing being kept in three places.
- A page stays the caller's: `MoveLine(lines, byPage, extend)` says "a page" and
  the editor multiplies by its own — `rows` for a `text-area`, ten for a canvas
  editor that has no viewport to measure.

## Consequences

- `EditKeysTest` asserts the table once, including the two redo spellings and
  that `Ctrl+Alt+V` is AltGr typing a character rather than a paste — which no
  test could state before, because there was no table to state it about.
- The three controls' own suites pass unchanged, which is the claim that this
  moved no behaviour.
- Converging the editors themselves is still open and is still a rewrite. It is
  now a smaller one: what is left to share is the text model, not the keyboard.
- An application writing its own editor on a canvas can read the same map, so a
  third-party editor agrees with the toolkit's by construction.
