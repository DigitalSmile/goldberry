# ADR-0601: A stylesheet may write any colour CSS Color 4 has

- **Status:** Accepted
- **Date:** 2026-10-11
- **Relates to:** `docs/css-plan.md` (CSS-13, CSS-14), `docs/css-support-2026-10-10.md`
  §3

## Context

`CssColor` read hex, `rgb()`/`rgba()`, `transparent` and the sixteen CSS Level 1
names, and its documentation refused the rest on purpose, for two reasons.
The 148-name list would be "148 chances for `grey`/`gray` confusion to look
like a toolkit bug", and a palette that comes from Nord through custom
properties has no use for `papayawhip`. And `oklch()` was refused as input:
OKLCH is the space colours are mixed in, and "a space a value cannot be
expressed in is one the parser has no reason to carry".

The support analysis found what those refusals cost a sheet written
elsewhere. `color: hsl(…)`, `oklch(…)` and `orange` are each a dropped
declaration with one warning, and the element keeps the colour it inherited.
Design tools and modern CSS frameworks write `oklch()` and `hsl()` by default.
`currentcolor`, the one way CSS ties a border to the text's colour, was
dropped too.

## Decision

**Every colour CSS Color 4 can write is read, and clamped into sRGB.**

- The 148 named colours, `rebeccapurple` and both spellings of grey included,
  in a table of their own (`NamedColours`). `transparent` is a keyword beside
  them.
- `hsl()`/`hsla()` and `hwb()`; CIE `lab()` and `lch()` at D50; `oklab()` and
  `oklch()`; and `color()` in `srgb`, `srgb-linear`, `display-p3`, `xyz`,
  `xyz-d65` and `xyz-d50`. Each takes the modern space form with an optional
  `/ alpha`, `none` for a component, an angle in any of CSS's units for a hue,
  and a percentage where CSS gives one a meaning. `hsl()` also takes the
  legacy comma form. The conversions are CSS Color 4's own matrices
  (`ColourSpaces`).
- A colour outside sRGB is **clamped**, channel by channel, not gamut-mapped.
  That is what `Oklch.mix` already does, for the reason it gives: the colours
  here are drawn to an sRGB surface, and a gamut mapper is a solver nothing has
  asked for. Clamping can shift a hue at the extremes of `display-p3`.
- `currentcolor` is substituted before a value is read. `color` is applied
  first, the way `font-size` already is, and every other `currentcolor`
  becomes that colour as a hex token, so the border, shadow, gradient and
  outline parsers need no keyword of their own. In `color` itself it is the
  inherited colour, as CSS says.

## Consequences

- The toolkit's own sheets do not change. Their palette is still Nord through
  custom properties; the names and functions are for sheets written elsewhere.
- `currentcolor` is fixed when the style is computed. A `color` transition
  moves the text, while a border that said `currentcolor` keeps the colour of
  the style being transitioned to. CSS would carry the keyword to computed
  time and move both. That is written down, not built around.
- `color-mix()` and relative colours (`oklch(from …)`) are not part of this.
  They are CSS-37, and they reuse `ColourSpaces`.

## Status

Done 2026-10-11: `dev.goldberry.css.value.colour` (`NamedColours`,
`ColourSpaces`, `ColourFunctions`, `CurrentColour`), `CssColor.parse` reading
through it, and tests (`ColourFunctionsTest`, `CurrentColourTest`, the
conformance table).
