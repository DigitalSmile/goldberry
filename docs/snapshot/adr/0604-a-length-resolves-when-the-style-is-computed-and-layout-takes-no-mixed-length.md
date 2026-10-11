# ADR-0604: A length resolves when the style is computed, and layout takes no mixed length

- **Status:** Accepted
- **Date:** 2026-10-11
- **Relates to:** `docs/css-plan.md` (CSS-04 and CSS-20),
  `docs/css-support-2026-10-10.md` §1 and §3

## Context

A CSS length was resolved at parse time against a context of two font sizes,
which is why `em` and `rem` worked and nothing else relative did. `calc()`,
the viewport units and `ch`/`ex` were all dropped, and the support analysis
counted `calc()` as the most common thing a sheet written elsewhere uses that
the subset refused. Two `TODO.md` entries name it too.

Two facts shape what can be built:

- Yoga 3.2 takes a length or a percentage for a dimension, never an
  expression and never the two together. `calc(100% - 8px)` on `width` has
  no spelling at the layout boundary.
- Everything else in an expression is known when the style is computed: the
  font size, the root's, the face, and the window. Only a percentage waits,
  for the box it is of.

## Decision

**A math expression is parsed into a tree and folded when the style is
computed, to pixels plus a percentage. Layout takes either half alone and
drops the two together; painted properties take both.**

- `CalcParser` reads `calc()`, `min()`, `max()`, `clamp()`, `round()`,
  `mod()`, `rem()`, `abs()` and `sign()`, nested, with `e` and `pi`, into a
  sealed `CalcNode` tree. `Folding` folds it, asking the length reader what
  each unit is worth, to a `Quantity`: a number, a length, an angle or a
  time, and for a length a percentage beside its pixels. A sum of different
  kinds, a product of two dimensions, a division by zero, a zero step and a
  non-finite result do not fold, and the declaration is dropped.
  `clamp(min, v, max)` is `max(min, min(v, max))`, so a minimum above the
  maximum wins, as CSS says.
- `min()`, `max()`, `clamp()` and the other comparisons fold only when every
  argument is in pixels or every one is a percentage. A pixel count cannot be
  compared with a percentage of a box that has not been laid out.
- A folded length with both parts is a new `Length.Mixed(points, percent)`.
  `Length.resolve` and the paint-side readers resolve it against their box.
  The layout properties drop it with a warning that says why, and `Yoga`
  refuses one handed to it directly rather than guessing.
- `CssLength.Context` gains the window's logical size (`Viewport`) and the
  face's `ch` and `ex` per em (`FontUnits`). The renderer sets the viewport
  from its media context every frame and measures faces through
  `FontMeasures`, over the book of fonts it draws with. The cascade moves the
  measures from face to face: `font-family` and `font-weight` are read before
  the rest of an element's declarations, and `ch` in `font-size` is the
  parent's face, as `em` there is the parent's size.
- `svh`, `lvh`, `dvh` and their `w`, `min` and `max` forms are the plain
  viewport units: a desktop window has one size. A viewport unit before any
  window has said how big it is does not resolve, and is dropped until one
  has.
- `StyleResolver` records whether any declaration or keyframe, custom
  properties included, writes a viewport unit. If one does, a change of the
  window's size gives a new resolver, the same invalidation an `@media`
  breakpoint takes. A sheet with none keeps its resolver, and every cached
  style, through a resize.

## Consequences

- `width: calc(100% - 8px)`, the most common `calc()` there is, is still
  dropped. The guide says what to write instead: padding on the parent, or a
  growing child. A Java widget that needs it computes its own size in
  `render`, where the box's size is known.
- Every reader takes a math function: lengths through `CssLength.parse` (every
  box, background, shadow, border and radius length), and, with CSS-20, the
  transform functions' lengths, angles and numbers, times, plain numbers
  (`opacity`, `flex-grow`, `font-weight`, the `flex` shorthand) and gradient
  angles through `Maths.fold`. A transform's translation carries a percentage
  plus pixels (`Transform.Length.percentPlus`), since it is resolved against its
  own box. A colour's components are the one place a math function is not
  read; that is a conformance row marked unplanned.
- The slider's thumb radius and its tick marks' inset, which `TODO.md` named as
  hand-kept halves of `--gb-slider-thumb-size` for want of `calc()`, are
  `border-radius: 50%` and `padding: 0 calc(var(--gb-slider-thumb-size) / 2)`
  in `controls.css` now.
- A resize restyles every element of an application whose sheets write a
  viewport unit, even when only one rule does. The cascade has no cheaper
  route: a cached style does not record which of its values came from which
  unit.

## Status

Done 2026-10-11: `dev.goldberry.css.value.calc` (`CalcNode`, `CalcParser`,
`Folding`, `Quantity`, `ContextUnits`), `Viewport` and `FontUnits` in
`css.value`, `Length.Mixed`, `FontMeasures` and `Font.xHeight`, the resolver's
viewport flag, and tests (`CalcParserTest`, `FoldingTest`, `LateLengthsTest`,
`FontMeasuresTest`, `ViewportUnitsTest`, the conformance table). CSS-20, the
same day: `Maths`, the transform, time, number, weight and gradient-angle
readers, `Transform.Length`'s offset, the slider's two halves, and
`MathEverywhereTest`.
