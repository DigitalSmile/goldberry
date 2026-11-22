# ADR-0733: Spelling is checked by Lucene's Hunspell, in a module of its own

- **Status:** Accepted
- **Date:** 2026-11-22
- **Relates to:** ADR-0734 (phase 1, the decoration the misspelling is drawn
  with), ADR-0686 (`dev.goldberry.i18n`, whose locale a field falls back to)

## Context

Tessera asked for spell checking in its text fields (T-25). On 2026-11-08 the
owner chose D18 (b), bundled Hunspell, over the platforms' own checkers. The
plan's row assumed Hunspell's C++ library, built into a native library of its
own as `:media` builds its decoders. Planning W3.7 on 2026-11-22
(`docs/spell-checking.md`) turned up a second reader of the same dictionaries.
Lucene 10.6.0's `org.apache.lucene.analysis.hunspell` is a pure-Java
reimplementation, tested against Hunspell's own test files. It has spelling,
suggestions and a `TimeoutPolicy` for slow suggestions, and is a JPMS module
under Apache 2.0. The C++ library is under MPL 1.1, GPL 2 and LGPL 2.1, the
user's choice, and would need a superbuild and CI on four targets.

## Decision

The owner answered D1–D9 on 2026-11-22:

1. **D1 (a): Lucene's Hunspell reads the dictionaries.** No native code, no
   new ABI, the toolkit's own licence. It costs 6.6 MB of jars
   (`lucene-core` and `lucene-analysis-common`), carried only by an
   application that adds the module.
2. **D2 (a): the SPI is in `:core` and the engine is in `:spell`.** The SPI
   lives in `dev.goldberry.text.spell` (`SpellChecker`, the registry and the
   checking service), so the fields in `:widgets` can use it. The engine is in
   `goldberry-spell` (`dev.goldberry.spell`), provided as a service and
   optional in the umbrella.
3. **D3 (a): the squiggle is `::spelling-error`.** CSS Pseudo-Elements 4's
   pseudo-element, a part like `::placeholder`, taking colour, background and
   the `text-decoration` properties. The theme gives it a wavy underline in
   the danger colour.
4. **D4 (a): all of CSS text decoration comes first:** style, colour,
   thickness and `text-underline-offset` (ADR-0734).
5. **D5 (b): no field checks by default.** A field checks when it says
   `spellcheck=true`, whatever its kind. Password and code fields never
   check.
6. **D6 (a): a checker per language, per application.** A field uses the one
   for its `lang=`, then the toolkit's labels' locale, then the default
   locale. A missing language checks nothing and is logged once.
7. **D7 (a): the application keeps the personal dictionary.** *Add to
   dictionary* goes into an in-memory dictionary per language, which the
   application reads and saves. The toolkit owns no settings directory.
   *Ignore* lasts as long as the field.
8. **D8 (a): a word is checked once the caret leaves it.** Lines are checked
   off the UI thread, those in view first. Results are cached per hard line by
   its text and the checker's revision.
9. **D9 (a): suggestions go at the top of the field's menu.** Up to five, each
   one undo step. Then *Add to dictionary* and *Ignore*, a separator, and the
   stock rows.

## Consequences

- No dictionary ships in any published jar, because their licences differ by
  language. The showcase fetches `en_US` for itself.
- The checking service and the fields can be built and tested against a word
  list before the engine module exists.
- Should the reimplementation ever disagree with Hunspell where it matters,
  the SPI leaves room for a second provider, the C++ library or a platform's
  checker, without touching the fields.
