# ADR-0735: A field's spelling is checked per line, by its text, off the UI thread

- **Status:** Accepted
- **Date:** 2026-11-22
- **Relates to:** ADR-0733 (the decisions, D2, D6, D7 and D8 built here),
  ADR-0734 (the decoration a misspelling is drawn with)

## Context

Phase 2 of W3.7 is the part of spell checking that needs no engine and no
field: the interface an engine implements, the registry an application
installs dictionaries in, and the service a field asks which words of a line
are misspelled. A field paints on the UI thread, a check costs a dictionary
lookup per word, and a suggestion can take tens of milliseconds, so none of
it may run where the frame is drawn.

## Decision

1. **`dev.goldberry.text.spell`, exported from `:core`.** It holds:
   - `SpellChecker` (`locale`, `isCorrect`, `suggest`), called from
     background threads, so an implementation that is not thread-safe locks;
   - `SpellCheckerProvider`, a service (`uses` in `:core`'s module), which
     opens a Hunspell `.aff`/`.dic` pair from two streams;
   - `SpellCheckers`, one registry per process. `SpellCheckers.load` finds the
     engine and fails with "add goldberry-spell" when there is none.
2. **A checker answers its locale, its language, and the language
   elsewhere.** The lookup is exact, then the language alone, then any
   checker for the same language. A field with no `lang` uses the default
   locale. D6 also named the toolkit's labels' locale, but `Labels` carries
   no locale, so that step is dropped. A language with no checker is logged
   once and checks nothing.
3. **The personal dictionary is the application's (D7).** A
   `PersonalDictionary` per language is held in memory, safe from any thread,
   with `addAll`, `words()`, a revision and change listeners, and is neither
   read nor written to disk. A word added in small letters accepts its
   capitalised forms.
4. **`WordScanner` decides what is a word.** A word is a run of letters and
   marks, with an apostrophe inside it kept and a hyphen splitting it. These
   are skipped:
   - the whole of a space-separated piece holding `://`, starting `www.`,
     holding `@` or starting `#`;
   - a run with a digit;
   - a single letter;
   - a word in capitals, and one with a capital after a small letter;
   - the scripts that do not space their words (Han, kana, Thai, Lao, Khmer,
     Myanmar, Tibetan).
5. **`SpellCheck` is one field's service, and its answers are kept by the
   line's text.** A line seen before is answered at once, and a new one is
   answered empty and checked in the next background batch. Only one batch is
   out at a time, and lines asked for meanwhile wait for the next. Listeners
   are told on the UI thread. The answers are kept for 2048 lines, least
   recently asked for first out. A field asks only for the lines it draws, so
   the lines in view are checked first without a priority queue. *Ignore*
   (for the field's life) and any change to the personal dictionary throw
   every answer away, and a batch that lands after either is dropped.
6. **Threads are a value, `SpellCheck.Threads`.** `application()` gives a
   virtual thread per batch and the UI thread. `IMMEDIATE`, used off the UI
   thread and in tests, answers before `misspellings` returns, so an
   offscreen render draws its squiggles on its one frame.
7. **The caret rule is `SpellCheck.outside`.** A misspelling the caret is in
   or just after is left out.
8. **`PseudoElement` gains `SPELLING_ERROR` and `GRAMMAR_ERROR`.** The second
   is reserved, and a CSS name is the constant's in lower case with hyphens.

## Consequences

- The fields (phase 3) and the menu (phase 4) are written and tested against
  `WordListChecker` in `:core`'s test fixtures, before any engine exists.
- An exhaustive `switch` over `PseudoElement` names two more cases.
- A field drawn before its checks land shows no squiggles for a frame. A
  line that is edited loses its squiggles until its new text is checked,
  rather than showing old ones in the wrong places.
