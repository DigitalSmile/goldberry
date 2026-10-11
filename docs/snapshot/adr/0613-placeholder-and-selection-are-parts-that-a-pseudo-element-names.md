# ADR-0613: `::placeholder` and `::selection` are parts that a pseudo-element names

- **Status:** Accepted
- **Date:** 2026-10-11
- **Relates to:** `docs/css-plan.md` (CSS-44), `docs/css-support-2026-10-10.md`
  §2; the guide's "a part type instead of a pseudo-element"

## Context

The guide's answer to pseudo-elements is the part: a control with two
surfaces a theme styles differently is two cascade nodes, and the inner one
has a type name (`check-indicator`, `text-value`, `text-selection`). It keeps
selector matching a walk over real nodes and lets a part take every property.

A sheet written for a browser styles a field's placeholder with
`input::placeholder` and a selection with `::selection`. Here both already
exist as parts: a field's `text-value` carries the class `placeholder` while
it stands in for a value, a `select`'s `select-value` does the same, a field
draws its selection as `text-selection`, and the HTML and Markdown views draw
theirs as `selection-layer`. Neither selector an author looks for reached
them, and both were refused as pseudo-elements.

The user decided on 2026-10-11 to keep the part model and read the two
pseudo-elements as names for those parts.

## Decision

**A part may say which pseudo-element it stands for, and a selector ending in
`::placeholder` or `::selection` matches that part below the element written
before the `::`.**

- `dev.goldberry.css.select.PseudoElement` is a closed enum, `PLACEHOLDER` and
  `SELECTION`. `StyleElement.pseudoElement()` and `Styled.pseudoElement()`
  default to null. `Value` and `SelectValue` answer `PLACEHOLDER` while they
  show the placeholder, `Highlight` (`text-selection`) and `SelectionLayer`
  answer `SELECTION`. The `placeholder` class stays.
- `Selector.Compound` gains a `pseudoElement` component, and
  `Selector.Combinator` gains `PART`. `text-input:focus::placeholder` is read
  as `text-input:focus`, then `PART`, then a compound that holds only the
  pseudo-element. `PART` matches as the descendant combinator does and prints
  as nothing, so the selector prints as it was written. `::placeholder` alone
  is the part anywhere.
- "Below" rather than "owned by" is a simplification: `form::placeholder`
  matches every placeholder in a form, where CSS would match none. A part sits
  inside its control, and nothing else needs the owner.
- A pseudo-element ends its selector. Anything after it (`::placeholder:hover`,
  `::placeholder b`) is refused as outside the subset. So is one inside a
  functional pseudo-class's arguments, which CSS does not allow either. Every
  other pseudo-element is refused, as before.
- Specificity counts a pseudo-element as a type, as CSS does.
- The resolver buckets a rule whose subject is a pseudo-element under a key no
  type can have (`::placeholder`). It is tried only on the parts that stand
  for that pseudo-element, and the lint does not report it as untyped.
- The toolkit's own placeholder rule is now `::placeholder`, one rule for
  fields and selects at the lowest specificity a rule naming the
  pseudo-element can have. Specificity outranks layer here, so the old
  `text-value.placeholder` (0,1,1) beat an application's
  `text-input::placeholder` (0,0,2). Written this way, any application rule
  that names the pseudo-element wins in the later layer.
- `::selection` takes `background`, which is how CSS colours a selection. A
  field's `text-selection` already draws its background. `SelectionLayer`,
  which fills the whole document and so took its wash from `color`, now takes
  it from a background when the cascade gives one, and draws no background of
  its own.

## Consequences

- `color` in a `::selection` rule does not recolour selected text: the
  highlight is drawn behind glyphs that keep their own colour, which the
  contrast rule needs. On the HTML view's layer, a `color` with no
  `background` is still the wash, as it was.
- `::before`, `::after`, `::marker` and the rest stay refused: there is no
  part for them to name, and generated content is a widget.

## Status

Done 2026-10-11: `PseudoElement`, `Compound.pseudoElement`,
`Combinator.PART`, the parser's `partFor` and `endOfPseudoElement`,
`StyleElement`/`Styled`/`Element.pseudoElement`, the resolver's `bucketOf`,
`Value`, `SelectValue`, `Highlight`, `SelectionLayer`, `controls.css`;
`PseudoElementTest`, `PseudoElementPartsTest`, the conformance rows.
