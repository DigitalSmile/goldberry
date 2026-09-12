# 279. Flexbox is the toolkit's vocabulary, not Yoga's

Date: 2026-09-12

## Status

Accepted. Closes `docs/gaps.md` G13, and is what
[ADR-0280](0280-natives-exports-to-core-and-to-nobody-else.md) needed before the
module could be sealed.

## Context

[ADR-0277](0277-a-path-is-a-value-and-the-rasterizers-is-package-private.md)
closed the drawing half of a rule nobody had written down — **no `:natives` type
appears in an application-facing signature** — and named the other half without
fixing it. This is that half.

`paint.Box` and `css.ComputedStyle` each carried thirteen Yoga-typed record
components: `StyleLength`, `Insets`, `Limits`, `FlexDirection`, `Justify`,
`Align`, `Wrap`, `PositionType`, `Overflow`. `BoxPainter.Placed` carried a
`ComputedLayout`. `paint.tree.ContainingBlock` and `widget.style.Corner` took and
returned `Insets`. And `css.value.CssLength.parse` *returned* `StyleLength`, so
the CSS engine's own output type was a binding's.

A `Box` is what every custom widget returns from `render()`. So the sanctioned
way to write a widget — the one `docs/core-widgets.md` documents and the showcase
demonstrates — meant reading `:natives`. Nineteen files in `:widgets` named
`StyleLength` alone; `:example` named `FlexDirection`.

`docs/gaps.md` did not record this, because it measured the leak by what *brd*
imports and brd draws rather than lays out. Its §0 said "exactly one leak, in one
file". That was true of brd and false of the toolkit.

## Decision

A `io.github.digitalsmile.goldberry.layout` package in `:core`, holding the
flexbox vocabulary as plain values: `Length` (sealed — `Points`, `Percent`,
`Keyword.AUTO`, `Keyword.UNDEFINED`), `Insets`, `Limits`, `FlexDirection`,
`Justify`, `Align`, `Wrap`, `Position`, `Overflow`, plus `Measure`,
`MeasureMode` and `MeasuredSize` for the callback a paragraph answers.

Nothing in it touches foreign memory, and nothing in it carries a wire format.
The C enumerators stay in `:natives`, checked against the compiled library by the
layout probe, where they belong.

**The translation is one package-private file.** `paint/tree/Yoga.java`, beside
`RenderObject` — whose Yoga-touching members (`apply`, `update`,
`reconcileChildren`, `node()`) were all package-private already. Nothing else in
the toolkit needs to know Yoga exists.

## `ComputedLayout` is deleted rather than mirrored

`render.model.LogicalRect` was already the toolkit's rectangle — `input.hit.HitTest.Region`
has returned one since hit testing was written — so a `ComputedLayout` mirror
would have been a second four-float rectangle kept alike by hand. `BoxPainter.Placed`,
`forEachBox` and `forEachPlacedBox` take `LogicalRect`, and `RenderObject.layout()`
is where the conversion happens.

That is one fewer type than the plan called for, and it is the only member of the
family that had a toolkit-owned counterpart already.

## Names travel; numbers do not

The two vocabularies agree on constant *names* and disagree on *numbers*.
`Align.CENTER` is 2 and `Justify.CENTER` is 1 in Yoga's headers; the stroke enums
next door number round as 2 for a cap and 4 for a join. So the translation is an
exhaustive `switch` rather than an ordinal cast — a coincidence relied upon
against a pinned C header that a bump could reorder, and Yoga has inserted a
constant into the middle of an enum before.

The compiler guarantees those `switch`es are exhaustive. It cannot guarantee each
arm names the right counterpart: `case CENTER -> Align.FLEX_END` compiles and
moves every centred row in the toolkit to one end. So `YogaTest` checks every
constant by name, **generically, from `values()`** — a constant added to either
side is checked the day it appears — and asserts the mapping is injective, since
two arms pointing at one constant would otherwise pass. Transposing one arm was
tried; the test fails on it.

## Consequences

- **`:natives`' Yoga packages are sealed**, which is the point: `exports … to
  io.github.digitalsmile.goldberry.core`, and a scratch module that tries to
  import `StyleLength` is refused by javac with "does not export it".
- **`yoga.measure` had to go with `yoga.style`.** Its `MeasureMode` implements
  `YogaEnum`, which lives in `yoga.style`, so qualifying one broke the other's
  public surface — found by `-Xlint:exports`, not by reading. That is why
  `Paragraph.measureFunction()` returns a toolkit `Measure` now.
- **`-Xlint:exports` under `-Werror` is the check this needed.** The plan called
  for a `PublicSurfaceTest` that read `:core`'s module descriptor and failed on
  any exported signature naming a `:natives` type. The compiler already does
  exactly that, better, and it named the three remaining sites in the text stack
  the moment `requires transitive` was removed. The test was not written, because
  it would have been a worse copy of something already running.
- **`Position`, not `PositionType`.** CSS calls the property `position`; the
  binding's name is the sort a binding carries. The constants are unchanged, so
  the name-based test still lines them up.
- **`Insets.NONE` is new**, and is not `Insets.ZERO`. An inset of zero pins a
  node to that edge; an undefined one leaves it where flow put it (ADR-0272).
  `Box.of()` had spelled that out inline; now the vocabulary carries it.
- **~1000 references moved**, across `:core`, `:widgets`, `:example` and the test
  source sets — 84 files. Almost all of it is an import line and a simple name.
- **No golden image moved.** The values translate to the same Yoga calls in the
  same order, and 192 `:widgets` goldens, 13 `:example` and 8 `:core` say so.
- **One cost, paid per node per layout**: a `switch` and, for insets, four of
  them. Against a foreign call each, which is what follows it.

## Alternatives considered

- **Re-export the Yoga types from `:core` under a `layout` alias.** Java has no
  type alias, and a subclass of an enum is not a thing.
- **Make `layout` depend on `:natives` and have the values carry their own wire
  numbers.** That is the binding's job, it would put a `nativeValue()` on a type
  an application reads, and it would make the vocabulary unusable by any future
  layout engine — which is the thing ADR-0010 says is possible and this makes
  cheap.
- **Mirror `ComputedLayout` too, for symmetry.** Two rectangles that must agree,
  when one of them was already the toolkit's.
- **Translate by ordinal.** Priced above: a coincidence, against a pinned header,
  with no failure mode short of a wrong picture.
- **Leave it and document the convention.** It had been documented, in
  `ARCHITECTURE.md` §3.1, for as long as the module graph existed — and thirteen
  components of `Box` broke it anyway. A convention a compiler does not check is
  a convention that has already been broken somewhere nobody has looked.
