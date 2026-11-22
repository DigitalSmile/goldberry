# ADR-0736: `spellcheck` and `lang` are attributes every node carries, and pass down

- **Status:** Accepted
- **Date:** 2026-11-22
- **Relates to:** ADR-0733 (D3, D5 and D6, built here, D6 amended), ADR-0734
  (the decoration drawn), ADR-0735 (the service a field asks)

## Context

Phase 3 of W3.7 makes the three fields that take prose draw their misspelled
words: `text-input`, `text-area` and `rich-input`. Two questions came first:
where a field is told to check, and what a field with no language checks in.
A third came from the code. `text-input` and `text-area` draw through text
boxes built from parts, and `rich-input` draws through `Editor`, so one
answer has to reach both ways of painting.

## Decision

1. **`spellcheck` and `lang` are on `Attributes`,** as one `Spelling`
   component (`check`, `lang`, each unset by default), because in HTML they
   are global attributes. Markup reads `spellcheck=#true`/`#false` and
   `lang="en-GB"` on any node. Java has `Attributes.spellcheck`, `lang` and
   `spelling`, and `Attributed.spellcheck`/`lang` on every widget.
2. **Both pass down, as in HTML.** `Spelling.of(context)` walks from the
   field up the element tree and takes each half from the nearest node that
   says it. `panel spellcheck=#true` checks every field inside, and a field's
   own `spellcheck=#false` wins. Unset means off (D5 (b)). A password never
   checks.
3. **D6 amended: the machine's locale is not read.** `DeterminismTest`
   forbids `Locale.getDefault()` in the text and widget layers, so that a
   golden taken in one country matches one taken in another. A field with no
   `lang` uses `SpellCheckers.defaultLanguage(Locale)`, which the
   application sets. Without one, it uses the only checker installed when
   there is exactly one, and nothing when there are several.
4. **`::spelling-error` is a part, `SpellingError` (`text-spelling-error`).**
   It is a carrier like `Value.carrier` and draws nothing. A field has one
   only while it checks, so a field that does not check keeps the parts and
   indices it always had. The theme says
   `::spelling-error { text-decoration: wavy underline var(--gb-danger) }`.
   The part's colour is used only when a sheet changed it, and its background
   when it has one.
5. **One helper in `:widgets`, `form.spelling`, not exported.**
   `FieldSpelling` keeps a field state's `SpellCheck` across builds, makes it
   again when the resolved checker changes, follows `SpellCheckers.onChange`,
   and calls the state back on the UI thread. `SpellingInk` reads the part's
   box and turns a check's answers for a range of the text into `SpanPaint`s,
   leaving out the word the caret is in while the field has focus.
6. **Each painting path gets the spans its own way.**
   - `text-area` lays them over the visible slice it draws.
   - `text-input` lays them over its `Value` part's box in `render`, so
     `Value` is unchanged.
   - `rich-input` gets them through a new `Editor.overlay(LineOverlay)`. That
     overlay leaves out text under code, links, mentions and an application's
     own kinds, and its state bumps the painter's revision when a check lands,
     so a painter compared by value repaints.
7. **`SpanLayers.over` merges a field's own spans with the misspellings.** The
   misspelling gives the lines, and the word keeps its own colour and
   background, so a misspelled bold or highlighted word stays bold or
   highlighted.

## Consequences

- `Attributes` has a thirteenth component. The twelve-argument constructor
  stays and means no spelling.
- A field that does not check builds and draws exactly as before, and
  `:example:test` passed with no golden retaken.
- `rich-input`'s state asks a check for each hard line it paints, from the
  cache on every frame after the first.
- Offscreen, the check answers at once (`Threads.IMMEDIATE`), so a single
  render has its lines. In a window the first frame after an edit has none on
  the edited line, until its check lands.
