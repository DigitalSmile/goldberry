# ADR-0737: A misspelled word's menu opens at once and fills in its suggestions

- **Status:** Accepted
- **Date:** 2026-11-22
- **Relates to:** ADR-0733 (D9, built here), ADR-0735 (`SpellCheck.suggest`),
  ADR-0736 (the fields that check)

## Context

D9 put a misspelled word's suggestions at the top of the field's own menu,
with *Add to dictionary* and *Ignore* after them, then the stock rows. A
suggestion searches the dictionary, which can take tens of milliseconds, so
it runs off the UI thread, and a menu opens on the press. Building phase 4
turned up a second problem. The window takes a secondary press to open the
menu, so `text-input` and `text-area`, which move no caret on a
non-primary button, never saw where a right-click landed. Their menu could
only be about the word the caret was already in.

## Decision

1. **The menu opens at once.** Until the suggestions arrive, its first row is
   a disabled "Finding suggestions…". `TextMenu.refresh()` then draws the
   rows again, and the menu puts the focus on the first row that can run,
   which is now the best suggestion. An offscreen or test check
   (`Threads.IMMEDIATE`) has them before the menu opens.
2. **`TextMenu` takes rows before its stock ones** (`before`, beside the
   `more` a rich field's Format submenu uses), separated from them.
   `SpellingMenu` in `form.spelling` builds them. It shows up to five
   suggestions, or a disabled "No suggestions", then *Add to dictionary* and
   *Ignore*. Four new `ToolkitLabel`s carry the words, so an application's
   translations cover them.
3. **A right-click puts the caret under the pointer, while the field checks
   and nothing is selected.** The anchor the window hands
   `ContextMenuOwner.openContextMenu` for a pointer is a zero-size rectangle at
   the pointer. The field maps it through where it was last located (`Located`,
   new on `text-area`) and places the caret as a primary press would. A field
   that does not check behaves as before. A selection is left alone, as
   `Selects` asks of a list. The keyboard's menu key acts on the caret's word.
4. **A suggestion replaces the word as one undo step** (`EditHistory.Kind.OTHER`).
   In `rich-input` the replacement takes the style marks its first letter
   had, so a misspelled bold word comes back bold, through `Editor.replace`.
5. **The word is the misspelling the caret is in or just after** (`holds`),
   found in the check's cached answer for the caret's hard line. The answer is
   there, because the field painted that line.

## Consequences

- `ToolkitLabel` has four more constants. An exhaustive `switch` over it
  names them, and a translation bundle may add
  `goldberry.addToDictionary`, `goldberry.ignoreSpelling`,
  `goldberry.noSuggestions` and `goldberry.findingSuggestions`.
- In a field that checks, a right-click moves the caret when nothing is
  selected. Elsewhere it does not, as before.
- Choosing *Add to dictionary* tells every field in the language: the
  personal dictionary's listeners make each `SpellCheck` forget its answers
  and redraw.

## Late suggestions and keyboard focus

A pending menu initially focuses *Add to dictionary*. When suggestions arrive,
Enter chooses the first suggestion unless the user has already navigated to a
command. Stable row keys keep the chosen command focused when several suggestions
shift its position. Clipboard updates continue to reconcile the rows in place.

The window tests release the spell checker after the pending menu paints. They
cover Enter with the initial focus and navigation both before and after the
suggestions arrive. Each wait runs on the event loop with a deadline so a missed
event reports its name to JUnit.
