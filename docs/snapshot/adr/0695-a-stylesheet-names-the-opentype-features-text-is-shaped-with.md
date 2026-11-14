# ADR-0695: A stylesheet names the OpenType features text is shaped with

- **Status:** Accepted
- **Date:** 2026-11-14
- **Relates to:** the variable faces of `font-variation-settings`

## Context

Tessera needs tabular figures for timestamps and counters that must not
jitter as they tick, and wants `"case"`, `"calt"` and stylistic sets for its
type (Tessera's G-13, here T-13). The parser dropped `font-feature-settings`
as an unknown property, and `HarfBuzz.shape` passed no features at all; the
shim already took an `hb_feature_t*` and a count.

## Decision

`font-feature-settings` and `font-variant-numeric` are typography, read by
the cascade and inherited as CSS says.

- **Values.** `font-feature-settings` is `normal` or a comma list of
  `"tag"` with an optional non-negative integer, `on` or `off`; a tag alone
  is on, and a tag written twice keeps its last value. `font-variant-numeric`
  is `normal` or at most one keyword of each kind: `lining-nums` or
  `oldstyle-nums`, `proportional-nums` or `tabular-nums`,
  `diagonal-fractions` or `stacked-fractions`, `ordinal`, `slashed-zero`,
  each turning on its feature (`lnum`, `onum`, `pnum`, `tnum`, `frac`,
  `afrc`, `ordn`, `zero`). Anything else is dropped with the usual warning.
- **One record.** `FontFeatures(numeric, settings)` is a `Typography`
  component, beside `FontVariation`. `resolved()` applies the settings over
  the numeric variant, so `"tnum" 0` undoes `tabular-nums`, as CSS orders
  them. The `font` shorthand resets both, as it resets the variation.
- **A font of its own.** The font book keys a font by face, axes, size and
  resolved features, so each distinct set is a `Font` over the same face and
  every paragraph cache keyed on a font's identity stays correct.
  `Font.features(List)` sets them on a font made by hand, before anything is
  shaped, as `emoji` and `fallbacks` are set.
- **To HarfBuzz.** `ShapingBuffer.shape(font, features)` packs an
  `hb_feature_t` array over the whole run (`start` 0, `end`
  `HB_FEATURE_GLOBAL_END`) in a confined arena for the call; no features is
  still `NULL, 0`. `hb_feature_t` is a row of the shim's layout table, checked
  by `LayoutVerificationTest` like every struct Java writes. No function was
  added, so the native ABI stays 26.

Features apply to the font's own face. An emoji or fallback face shapes a
cluster without them.

## Consequences

- `Typography` has an eighth component, `features`; the seven-argument
  constructor stays and means none. A record pattern over it names eight.
- Tested by `FontFeatureValuesTest` (the grammar, last-wins, inheritance,
  the settings over the variant, the shorthand's reset),
  `FontFeaturesTest` (Inter's proportional figures become one width under
  `tnum`, `"tnum" 0` undoes it, `zero` changes the glyph, one font per set,
  an unknown tag changes nothing), `ShapingTest` (a feature array crosses and
  tags pack as `HB_TAG` packs them), `PropertiesTest` and the conformance
  rows.
