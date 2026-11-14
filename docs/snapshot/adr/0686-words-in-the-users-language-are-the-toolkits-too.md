# ADR-0686: Words in the user's language are the toolkit's too

- **Status:** Accepted
- **Date:** 2026-11-09
- **Relates to:** ADR-0291 (what is the application's and what the toolkit's)

## Context

A messenger built on Goldberry asked for plural rules and message formatting
(its G-26, here T-26): "1 файл, 3 файла, 5 файлов" cannot be built from
`String.format`, and every application in a language with more than two plural
forms writes the same tables by hand. The toolkit had none, and its own words,
the text menu's Cut/Copy/Paste and the accessible names of its close and
dismiss buttons, were English string literals no application could replace.

ICU4J answers both and is about 13 MB. The owner chose (D17, 2026-11-08) a
package of the toolkit's own over CLDR's data instead.

## Decision

**`dev.goldberry.i18n`, exported from `:core`, with three parts.**

- **`PluralRules`**: CLDR's cardinal and ordinal rules for every locale CLDR
  has (version 49), as `PluralRules.of(locale, type).select(number)`. The
  rules are extracted by `tools/i18n/extract_plurals.py` from cldr-json's
  `plurals.json` and `ordinals.json` (checked by SHA-256) into one resource,
  `plurals.txt`, and parsed once at first use: CLDR's rule language has five
  operators, and a parser of it is smaller than generated code for 200
  locales. Operands follow UTS #35 (`n i v w f t e`), so `1` and `1.0` differ
  where a language says they do. A locale falls back to its language, then to
  CLDR's `und` (`other` alone).
- **`IcuMessage`**: ICU's message syntax for the parts applications use:
  `{arg}`, `{arg, number}`, `plural` and `selectordinal` with `=n` cases,
  `offset:` and `#`, `select`, and ICU's apostrophe quoting. A plural or select
  without `other` is refused when parsed, not when shown. Dates, times and
  `choice` are not parsed; `java.time` and `NumberFormat` cover them.
- **`Labels` and `ToolkitLabel`**: every word the toolkit itself shows or
  speaks is a `ToolkitLabel` with a `goldberry.*` key and its English. An
  application hands `Labels.use(bundle)` a `ResourceBundle` (or a function),
  and a word the translation lacks stays English. The text menu, dialog and
  tab close buttons, the new-tab button, chip and message dismiss buttons, the
  breadcrumbs' overflow, a tour's Back/Next/Done and the media player's
  accessible names read from it.

`Wizard` keeps its own `Wizard.Labels` record: it was already the
application's to pass.

## Consequences

- One process-wide set of labels, as there is one language for an
  application's windows. Switching language at run time is `Labels.use` plus a
  restyle, so open menus are rebuilt.
- CLDR's data is under the Unicode License v3, already in `licenses/` for the
  emoji groups; `THIRD-PARTY-NOTICES.md` names it.
- Updating CLDR is: bump the tag and hashes in the script, run it, and let
  `PluralRulesTest.cldrSamples` check every sample number CLDR lists for every
  rule of every locale (over 10 000) against the parsed rules.
- Tested by `PluralRulesTest`, `PluralOperandsTest`, `IcuMessageTest`,
  `LabelsTest` and `TextMenuTest.translated`.
