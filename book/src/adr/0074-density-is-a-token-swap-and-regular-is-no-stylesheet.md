# ADR-0074: Density is a token swap, and regular is no stylesheet at all

- **Status:** Accepted
- **Date:** 2026-08-17
- **Relates to:** `docs/design-system.md` §1.3, §3; `docs/ARCHITECTURE.md` §8, §10;
  uses the mechanism of [ADR-0049](0049-the-css-engine-stops-at-computedstyle.md);
  applies to every control [ADR-0059](0059-a-control-is-a-record-a-node-and-a-rule.md)
  ships

## Context

`docs/design-system.md` §1.3 specifies a density preference:

> **Density:** `--gb-density` `regular` (default) | `compact` — control heights
> 32 / 28, list rows 32 / 26. A user preference applied app-wide; token-conformant
> apps adapt with zero code.

Nothing implemented it. Every control's height was a literal `32px` in
`controls.css`, so there was nothing for a density to swap — the tokens the
promise depends on did not exist, and "adapt with zero code" was a sentence
about a mechanism that was not there.

This is deliberately being done at **four controls rather than at thirteen**. It
is per-control plumbing: every control written before the token exists is a
control that has to be revisited, so the change costs three edits now and ten
later. That is the whole reason it is scheduled ahead of the fifth control rather
than after the catalog.

## Decision

### The height is a token; nothing else is

`controls.css` declares §1.3's regular column at `:root` and every control sizes
itself from it:

```css
:root {
  --gb-density: regular;
  --gb-control-height: 32px;
  --gb-list-row-height: 32px;
}

button   { height: var(--gb-control-height) }
checkbox { height: var(--gb-control-height) }
radio    { height: var(--gb-control-height) }
```

**Padding, gap and radius stay literal.** The obvious next move is to tokenise
them too "for symmetry", and it is wrong: §1.3's density row names control
heights and list rows and nothing else, so a `--gb-control-padding` that a
density moved would be inventing a scale the design system does not define
(Principle 3, "token or extend" — and extending means editing the table first).
`DensityTest.onlyHeightMoves` asserts that padding, gap and radius are identical
at both densities, which is what keeps a later change honest.

### Compact is a theme-layer stylesheet, and there is no fifth cascade layer

`density-compact.css` is a `:root` block of three custom properties, parsed into
`CascadeLayer.THEME` — the same slot `nord-light` and `nord-dark` go into.

The alternative was a fifth layer between `TOOLKIT_BASE` and `THEME`, and it was
rejected because **the theme layer is defined by what it holds, not by what it is
called**: custom properties that the toolkit-base rules read, swapped as a user
preference, meaning nothing until a base rule reads them. That is a description
of a density as exactly as it is a description of a theme. A fifth layer would
differ from the fourth in its name and in nothing else, and `CascadeLayer` says
in its own documentation that four layers everyone knows beats an open-ended
mechanism.

The layer is also what makes the override *work*, and this is worth stating
because it looks like list order and is not. Both blocks are `:root`, so they
carry identical specificity; the cascade compares `important → specificity →
layer → order`, and `layer` is therefore the only term that separates them. A
compact sheet parsed into `TOOLKIT_BASE` by mistake would tie all the way down to
`order`, and the winner would be an accident of sort stability rather than a
decision. `DensityTest.compactIsAThemeLayer` asserts the layer for that reason,
rather than asserting the resolved height and calling it covered.

There is no conflict with the theme sharing the slot: no theme declares
`--gb-control-height`, and no density declares a colour. `compactDeclaresOnlyTokens`
holds the second half of that — a density that grew a rule would be styling
controls behind the theme's back, and switching one would restyle rather than
resize.

### `Density.REGULAR` ships no stylesheet

`Density.stylesheets()` returns a `List<Stylesheet>`, empty for `REGULAR` and one
sheet for `COMPACT`. There is no `density-regular.css`.

This asymmetry is the fact rather than an omission. §1.3 spells regular
"(default)", and **a default is the absence of an override** — regular is not
something an application applies, it is what the toolkit already is. Writing 32
in `controls.css` *and* in a `density-regular.css` would be one number in two
files, which is the arrangement this repository has already been bitten by twice:
§10.1 carried a typography table that disagreed with §1.4's, and the checkbox
carried a surface ramp beside the button's that disagreed with it
([ADR-0073](0073-a-composite-is-one-tab-stop.md)). One number, one place.

The return type is a list rather than a `Stylesheet` for the same reason. An
empty stylesheet returned to keep two shapes matching is a thing that parses,
sorts and cascades every frame in order to do nothing, and `Stylesheet.empty` was
available — it was not used, because the honest statement is "regular contributes
no stylesheets", not "regular contributes an empty one".

The consequence an application sees is the good one: an application that never
mentions density gets regular, because regular is the base and there is nothing
to remember to add.

### `--gb-density` is a marker, not the mechanism

§1.3 names the property, so it is declared. Nothing in the toolkit reads it.

A keyword custom property **cannot select a number** in §8's subset — there is no
`@container style()` here, no `@media`, and there is not going to be either, so
`--gb-density: compact` cannot by itself make anything 28 tall. The two length
tokens beside it are what switch; this one says which set is in force, and custom
properties inherit, so any element can be asked. It ships because an application
that needs to branch in Java — or a `list` that has to pick a row height — should
read the answer rather than be told it out of band.

### `Density` lives in `:widgets`, and `Theme` stays in `:core`

A density sizes *controls*, and `:core`'s primitives have no height for one to
move: `row`, `column`, `text`, `panel` and `spacer` are sized by their content
and their application's rules. A theme is in `:core` for the opposite reason —
`text` reads `--gb-text` and `panel` reads `--gb-surface`, so the colour tokens
have consumers on both sides of the module boundary and the height token has
consumers on one.

`Controls.stylesheets(theme, density)` assembles the three in order, for the
reason the rest of that class exists: the order matters, getting it wrong is
silent rather than loud, and an application should not have to know that a
density goes above a theme in a list.

### Compact is below §1.3's own hit-target floor, deliberately

§1.3 says two things that cannot both hold:

> Hit targets **≥ 32×32** logical px even when the visual is smaller.
> Density: … control heights 32 / **28**.

A compact control is 28 tall. The floor gives, and it gives because that is what
the preference *is*: a user who asks for compact is asking to trade the comfort
margin for more on screen, and a density that refused to go below 32 would be a
density that does nothing. The ≥ 32 rule is therefore the **regular** default
rather than an invariant, and this record is where that is written down.

Two things bound the trade:

- **The glyph does not shrink.** A checkbox's tick and a radio's dot stay 16px at
  either density; only the row around them closes. Compact costs 4px of margin
  around the target, not a smaller target — a density that scaled its contents
  would look plausible in a screenshot and be a zoom rather than a density.
  `theGlyphHoldsStill` asserts it.
- **Compact is never the default.** It is reached only by an application setting
  it, on a user's instruction. Nothing in the toolkit chooses it, and no OS
  setting is read to infer it.

## Consequences

- §1.3's density row is implemented and its "zero code" promise is real: the
  showcase switches density on `Ctrl+D` and **not one widget in that file
  mentions a height**. There is deliberately no button for it in the tree —
  a density is an application-wide preference, so it belongs in a menu or a
  settings screen, neither of which exists yet.
- **Every existing golden image is byte-identical.** The token swap changes
  nothing at regular density, which is the check that it was a refactor — the
  same check [ADR-0073](0073-a-composite-is-one-tab-stop.md) used when the mark
  became a node. Two new images, `controls-density-{regular,compact}.png`, are
  the same scene at both, so the pair is the assertion: three controls four
  pixels shorter and nothing else moved.
- The height assertions are written **over the catalog rather than per control**,
  because a density that moved `button` and not `checkbox` would pass three
  per-control tests and be exactly the divergence §3's shared metrics row exists
  to prevent. A control added with a literal height fails `DensityTest` on the
  day it is added, which is the point of scheduling this at four controls.
- `theTwoDiffer` exists because the two height tests cannot cover each other: if
  the token were dropped and both densities fell back to one literal, one of them
  would still pass in full.
- **Open: `--gb-list-row-height` has no consumer.** `list` is M3. It ships now
  because the density a `list` will have to honour is decided here rather than
  there, and an application building its own rows today has the token it would
  otherwise hard-code. That is the same argument
  [ADR-0037](0037-what-the-text-path-costs.md) made for `ParagraphCache`, which
  shipped a year of frames before anything called it.
- **Open: nothing detects the user's preference.** An application that knows sets
  it, exactly as with reduced motion
  ([ADR-0067](0067-motion-is-an-overlay-on-a-frame-clock.md)) — SDL exposes no
  query for either. The difference is that reduced motion is an accessibility
  setting the OS really does hold, while density is usually the application's own
  preference, so this one may never need detecting.
- **Open: the typography does not move with the density.** A 28px control still
  carries a 13/18 label, which fits (18 of 28, against 18 of 32) and is what
  §1.4 specifies unconditionally. Whether a compact density should also take a
  step down the type scale is a question §1.3 does not answer, and inventing an
  answer here would be the "improvise a third value" mistake
  [ADR-0066](0066-a-weight-is-a-face-and-color-inherits.md) declined to make.
