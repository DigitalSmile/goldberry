# ADR-0688: Text is measured with the library that lays it out

- **Status:** Accepted
- **Date:** 2026-11-09
- **Relates to:** ADR-0291 (a refusal recorded as a decision)

## Context

A game's content linter counts lines and measures widths to check that dialog
text fits, with no window and no GPU (Lantern's G-12, here L-12).
`Paragraph.layout` needs `libgoldberry`, because shaping is HarfBuzz and the
face's metrics come through Blend2D, so the tool has to ship the platform's
native library and `--enable-native-access`. The entry asked for measurement
that works without it.

Bidi, itemizing and line breaking are pure Java already; shaping and metrics
are not. A Java fallback over the `sfnt` readers could read advances and
`kern`, but not GSUB and GPOS: ligatures, contextual forms, mark placement and
GPOS kerning would be missing.

## Decision

**Declined.** Goldberry measures text only with the shaper it draws with.

A measurement that differs from the game's layout is worse than none for
this use: a `text-fits` rule would pass a line that overflows in the game, in
exactly the scripts and fonts where shaping matters most. The answer is the
workaround the game already has: the tool depends on `goldberry-natives` for
its machine's classifier, and the headless backend needs no display.

## Consequences

- `Font` and `Paragraph` keep requiring the native library; nothing changes
  in the code.
- If a pure-Java shaper with GSUB/GPOS ever exists, this can be revisited on
  that ground, not on the size of the native jar.
