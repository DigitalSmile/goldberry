# ADR-0615: A justified line is respaced at paint, and a variable face is pinned into a static one

- **Status:** Accepted
- **Date:** 2026-10-11
- **Relates to:** `docs/css-plan.md` (CSS-49), `docs/css-support-2026-10-10.md`
  §6; amends "a weight is a face" (the bundled-faces note on
  `assets.Asset`'s Inter entry and the guide's *Text*); native ABI 22 → 23

## Context

Both were refused with a reason. `text-align: justify` was dropped because "a
line is placed, never respaced": a paragraph is shaped once and sliced into
lines, and nothing gave a line extra advance. `font-variation-settings` and
`font-stretch` were unknown, and a weight between the faces a family ships was
drawn as the nearest face: `font-weight: 500` over Inter's 400 and 600 drew
the 400.

Inter's upright face ships as its variable file (`InterVariable.ttf`, `wght`
100 to 900, `opsz`), so the design space is already in the toolkit. The note
on the asset manifest weighed instancing at run time and set it aside: it
needs symbols in two libraries and an ABI change, and the design system names
two weights.

The user chose on 2026-10-11 to build both. Building variable fonts surfaced
the obstacle behind the asset note. The Blend2D we link has a variation-settings
API (`bl_font_set_variation_settings`) but reads no `gvar`, so it would draw a
variable face's default instance whatever it was told, while HarfBuzz shaped
the varied one: wider advances around unchanged glyphs. The user then chose to
pin a variable face into a static instance with HarfBuzz's subsetter, over
drawing outlines from HarfBuzz or deferring.

## Decision

**`justify` respaces a line's word gaps when the paragraph is painted.
`font-weight`, `font-stretch` and `font-variation-settings` set a variable
face's axes, and each distinct setting is pinned once into a static face that
both HarfBuzz and Blend2D read.**

Justification:

- `TextAlign.JUSTIFY` is a constant of its own. `Paragraph` draws a justified
  line word by word, each word moved on by the slack divided among the line's
  gaps (spaces and no-break spaces after its first word). The last line of the
  text, a line a newline ends, a line without a gap and a line without slack
  are placed at the start instead. Both paint paths do it, the plain one and
  the styled one with spans and joined fonts. A decoration runs the full width
  of a justified line.
- Measurement is unchanged. A justified paragraph measures at its natural
  width, as CSS's intrinsic sizes do.
- `text-justify` is inherited and lives in `Lettering`: `auto` and
  `inter-word` respace the gaps, `none` turns justification off, and
  `inter-character` is dropped. `ComputedStyle.lineAlign()` answers `start`
  for `justify` under `text-justify: none`.
- An editor never respaces. `TextAreaBox` paints a `justify` flow at the start,
  because its carets, selection and hit tests are placed by natural advances.
  A `text-input` is one line, which is never respaced anyway.

Variable faces:

- `libgoldberry` builds HarfBuzz's subsetter and exports
  `goldberry_hb_instance(blob, tags, values, count)`. It pins every axis to its
  default and then to the values given, with every glyph, table and name kept,
  and serialises the result. `goldberry_hb_blob_get_data` reads the bytes
  back. ABI 23. In Java, `natives.harfbuzz.Instancer.instance(bytes, axes)`
  answers the static font's bytes, or empty when the instancer refuses.
- `text.font.sfnt.VariationAxes` reads `fvar` in Java, the way `cmap` is read,
  and `FontFace.axes()` holds a face's axes from when it opened.
- `Typography` gains a `FontVariation` (`font-stretch` as a percentage, and
  the settings as tag and number pairs). Both inherit, and the `font`
  shorthand resets both, as CSS's does. `font-width` is an alias of
  `font-stretch`.
- `Fonts.of(Typography)` matches a face as before. When that face is not at
  the weight asked for, a variable face of the same family and style whose
  `wght` reaches it is used instead. The face is then pinned: `wght` at the
  weight, `wdth` at the stretch, and any axis a setting names, each clamped to
  the face's range, last setting winning, and only those that differ from the
  face's default. Each face and setting is opened once and kept in the book
  beside the faces, and closed with them. A face the instancer refuses is
  drawn as it is, and said so once.
- The faces that exist for a weight stay those faces: 400 is Inter's variable
  file at its default, 600 is the static SemiBold, and nothing is pinned for
  either.

## Consequences

- `font-weight: 500` and `700` over upright Inter are drawn at 500 and 700. The
  italics are static files and still draw the nearest of their two weights.
  The Text chapter's example says so now.
- A face an application ships that is variable along `wght` draws every weight
  asked of it, clamped to its axis, rather than the one weight it was declared
  at.
- Pinning a face costs one subsetting pass, tens of milliseconds for Inter,
  the first time a setting is asked for; `font-weight` is not animatable, so a
  settings count grows with what the sheets write, not with frames.
- `libgoldberry` grows by the subsetter, and the macOS and Windows libraries
  have to be rebuilt by CI before ABI 23 runs there.
- `font-optical-sizing` is not read: `opsz` stays at the face's default unless
  a setting names it.

## Status

Done 2026-10-11: `TextAlign.JUSTIFY`, `TextJustify`, `Lettering.justify`,
`Paragraph.gapRoom`/`eachWord`, `TextAreaBox`; `goldberry_hb_instance`,
`goldberry_hb_blob_get_data`, `HB_BUILD_SUBSET`, ABI 23, `InstanceCalls`,
`Instancer`; `VariationAxes`, `FontFace.axes`, `FontVariation`,
`Typography.variation`, `css.FontValues`, the `font-stretch` and
`font-variation-settings` arms, `Fonts.variableFor`/`axesFor`; `JustifyTest`,
`InstancerTest`, `VariableFontTest`, `FontVariationValuesTest`,
`ShippedFontsTest`, the conformance rows.
