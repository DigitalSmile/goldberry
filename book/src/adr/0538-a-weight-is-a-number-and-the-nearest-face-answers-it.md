# ADR-0538: A weight is a number, and the nearest face answers it

- **Status:** Accepted. Amends [ADR-0066](0066-a-weight-is-a-face-and-color-inherits.md)
  ("a weight is a face, not an axis; two weights") and
  [ADR-0349](0349-a-face-an-application-ships-is-found-after-the-bundled-ones.md)
  ("the matrix stays closed").
- **Date:** 2026-10-02
- **Relates to:** [ADR-0323](0323-an-italic-is-a-face-and-the-matrix-closes.md),
  [ADR-0539](0539-an-application-reads-its-own-resources-and-a-missing-face-is-said-at-start.md),
  `docs/goldberry-gaps.md` #16

## Context

Deploy Orc's prototype sets type at 500, 600, 700 and 800. In Goldberry every
`font-weight: 500` in its stylesheet drew at 400, and Grenze Gotisch ExtraBold
had to be registered as "semi-bold" to be reachable at all.

The cause was one type. `BundledFont.Weight` was `REGULAR(400)` and
`SEMI_BOLD(600)`, `Weight.nearest` folded every CSS number onto one of the two
in the cascade (at or below 500 regular, above it semi-bold), and
`Typography.weight`, `Face.weight` and `FontSource` all carried the enum. So
the number was gone before the font book saw it, and an application's face
could only be one of two weights.

ADR-0066 chose that for the toolkit's own faces, and the reason still holds
there: the design system ships Inter at 400 and 600, and a third weight is a
change to the system. ADR-0349 then applied the same closed pair to the faces an
application ships, citing the same principle. That second step is where it went
wrong. An application's typography is the application's, and the toolkit has no
design system to defend in it.

## Decision

**The cascade carries the CSS number, and the font book chooses the face by
CSS's nearest-weight rule over the faces the family has.**

- `Typography.weight()` is an `int`, 1 to 1000. `font-weight` parses a number
  (rounded to a whole one), `normal` as 400 and `bold` as 700, and drops
  anything outside 1 to 1000 with the usual warning.
- `Face.weight()` is an `int`. `FontSource` takes any weight from 1 to 1000;
  `BundledFont`'s faces are 400 and 600 as before.
- `Face.match` is CSS Fonts' matching algorithm: the family's faces; of those,
  the ones with the style asked for if there are any; of those, the weight
  asked for, or else the nearest in CSS's order (400 to 500: heavier up to 500,
  then lighter, then heavier; below 400 lighter first; above 500 heavier
  first). Over Inter's 400 and 600 that answers exactly what `nearest` did, so
  the toolkit's own text draws the same.
- **Source compatibility, where it was cheap.** `BundledFont.Weight` stays as
  the names of 400 and 600, and every constructor and factory that took it
  still does: `FontSource.resource`, `FontSource.of`, the `FontSource`
  constructor, `BundledFont.of`, `Typography`'s constructors and
  `Typography.weight(Weight)`. What does not compile unchanged is reading a
  weight back: `typography.weight()`, `face.weight()` and `source.weight()`
  answer a number now.
- One rung of the old ladder changes: a family whose only face is, say, a
  semi-bold italic used to answer null for a regular upright and fall back to
  Inter. CSS draws a family in whatever it has, and so does this now.

### No variable axis, yet

Inter and JetBrains Mono are variable files, so `font-weight: 500` over Inter
could be a true 500 by instancing `wght`. That is not in this change. It would
take Blend2D and HarfBuzz variation bindings (`hb_font_set_variations`, the
Blend2D font's variation settings, both behind the shim and an ABI bump), a
font keyed on weight as well as face and size in the book, and a decision about
whether the design system wants an intermediate weight at all, which is
ADR-0066's question and not this one's. A shipped face covers the application
that needs a 500 today.

## Consequences

- An application that registers 500, 600, 700 and 800 gets each from the
  stylesheet that asks; Deploy Orc's ExtraBold is an 800.
- The toolkit's own text is unchanged: every weight lands on the face it
  landed on before, now by the general rule rather than a two-face special
  case.
- `Typography` carries a value a face may not honour exactly, which ADR-0066
  avoided. The painter never sees it: the book resolves it to a face before
  anything is shaped, and the number is what makes two families with
  different weights answer the same declaration differently.
- `Weight.nearest` stays, as the two-face form of the rule, for a caller that
  wants a `BundledFont.Weight` from a number.
