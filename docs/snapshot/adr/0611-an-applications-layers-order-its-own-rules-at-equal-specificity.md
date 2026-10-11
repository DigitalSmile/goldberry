# ADR-0611: An application's `@layer`s order its own rules, at equal specificity

- **Status:** Accepted
- **Date:** 2026-10-11
- **Relates to:** `docs/css-plan.md` (CSS-31, CSS-33),
  `docs/css-support-2026-10-10.md` §1, ADR-0602

## Context

The cascade has four fixed layers, `TOOLKIT_BASE`, `THEME`, `APPLICATION` and
`INLINE`, and the guide argues for keeping them fixed: no sheet that loads late
can argue with the order. The four rank *after* specificity: at equal
specificity a later layer wins, so a layer is an extension of source order, and
a more specific toolkit rule still beats a vaguer application one.

CSS's `@layer` was refused. A sheet written elsewhere uses it to put a vendor
framework or a reset beneath its own rules (`@import url(vendor.css)
layer(vendor)`), and in CSS a layer ranks above specificity: an unlayered
`.btn` beats a layered `.btn.primary`.

Inside the application layer alone the two models cannot both hold. Take an
application rule in an early layer with high specificity, one in a late layer
with low specificity, and a theme rule between the two in specificity. Ranked
CSS's way inside the application layer and the toolkit's way across layers,
the first loses to the second, the second to the theme rule, and the theme
rule to the first: no order, and a sort that throws.

## Decision

**`@layer` is read in every sheet and ranks only in the application's, and
there it orders rules the way the four layers do: after specificity, before
source order.**

- `@layer a, b;` names layers in the order they rank; `@layer a { … }` puts
  rules in one; an unnamed block is a layer of its own; a layer inside another
  is `outer.inner`, and a layer's own rules rank above its sublayers, as in
  CSS. `@import … layer(name)` and `layer` put an imported sheet's rules in a
  layer, its own layers nested under it.
- The rank comes from the order the application's sheets, in the resolver's
  order, first name each layer. A rule in no layer ranks above every layer.
- The cascade compares `!important`, then specificity, then the four layers,
  then the application's layer, then the sheet and the rule's place in it.
  `!important` turns the layer order round, as CSS does.
- `revert-layer` rolls back below the declaration's application layer as well
  as below its fixed layer.
- A `@layer` in a toolkit or theme sheet is read and ranks nothing: those
  layers belong to the toolkit, and an application orders only its own sheets.

## Consequences

- The common use works: a vendor sheet imported into a layer loses every tie to
  the application's own rules, whatever order the sheets load in.
- A layered rule that is more specific still wins over an unlayered one. CSS
  would let the unlayered one win. The guide says so, beside the four layers'
  same rule.
- The four fixed layers are unchanged, and the guide's argument for them
  stands.

## Status

Done 2026-10-11: `StyleRule.layer`, the parser's `layer`, `declareLayer` and
`layer()` imports, `Stylesheet.layers`, `StyleResolver.ranks`, the cascade's
sub-layer key, `revert-layer` below a sub-layer, and tests (`LayerTest`,
`ImportTest`, the conformance table).
