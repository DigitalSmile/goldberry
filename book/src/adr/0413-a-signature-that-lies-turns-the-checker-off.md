# 413. A signature that lies turns the checker off

Date: 2026-09-19

## Status

Accepted. Closes the `TODO.md` entry left open by
[ADR-0257](0257-a-diagnostic-is-asked-for-not-logged.md), which is the record
that created it.

## Context

`StyleElement` has documented three of its five members as nullable since it was
written:

> The element type — `button`, `row`. … **May be null**, and a node with no type
> is the normal case for anything that exists only to compose.

> The `id`, or null. At most one per element.

> The element this one sits inside, or null if it is the root.

And declared all three non-null, in a `css` package that is `@NullMarked`. Under
NullAway's `OnlyNullMarked` mode that is not a gap in coverage — it is a checker
that has been told the wrong thing and is enforcing it. Every caller that handled
the null was, as far as the build was concerned, being paranoid about a value
that could not occur.

Nothing had noticed because nothing was in a position to. Every implementation
lived in an unmarked package: `Element` in `widget`, `TestElement` in a test
tree, `ThemeAudit.Root` in `css.contrast`. An override in an unmarked package can
say whatever it likes and NullAway never reads it. Then ADR-0257 promoted
`SupportedPropertyTest`'s machinery into `css.lint`, whose `Probe` is the first
`StyleElement` implementation ever written inside a marked package, and it could
not be written honestly.

What happened next is the part worth recording. The package was **unmarked** —
written marked, then taken back out — and a paragraph was added to its
`package-info` explaining why. A checker was switched off to accommodate a
signature that disagreed with its own javadoc, and the reason was written down so
carefully that it read like a decision rather than a debt.

## Decision

**`type()`, `id()` and `parent()` are `@Nullable`, and `css.lint` is
`@NullMarked`.**

Nothing is unmarked to make this work. That is the whole point: the entry that
opened this one put it exactly right — closing it properly "moves every
implementation and every caller", and that is the argument for doing it rather
than against.

### `Selector.Compound` was the same lie one package over

`css.lint`'s own `package-info` named both halves, and only one of them is in the
entry:

> `StyleElement` documents **three** members as "or null" … Unannotated, matching
> `StyleElement` and `Selector.Compound`, both of which document a null `type`
> and `id` and declare neither `@Nullable`.

`Selector.Compound` is a record in `css.select`, which is also marked. Its `type`
is null for `*` and for every compound that names none — `isUniversal()` is
written in terms of it — and its `id` is null for the overwhelming majority of
compounds ever parsed. It was invisible for a different reason than
`StyleElement`'s: the nulls are *written* by `CssParser`, in `css.parse`, which is
not marked, so neither side of the boundary could see the disagreement.

Annotating `StyleElement` alone would have been enough to mark `css.lint` —
`Probe.type()` returns `compound.type()`, and a non-null value is a legal
`@Nullable` return. It would also have left the second lie in place, one import
away, with a comment in `Probe` pointing at it. Both are annotated.

### What the checker actually found

Six diagnostics, and it is worth being exact about what they were, because the
entry predicted something else:

> …and would probably find real nullness bugs on the way, which is the argument
> for doing it rather than against.

**There were no latent `NullPointerException`s.** Every call site in the
repository that reads one of the three already handles null, and several handle
it with a comment explaining why. `SelectorMatcher.matchesCompound` guards both;
`OverflowWatch.name` falls back to "a box"; `PointerRouter` prints
`<composition>`; `ThemeAudit.Root` was already written with all four
annotations, in an unmarked package where nothing obliged it to be. The codebase
had internalised the javadoc and ignored the declaration.

What the six were:

- **`StyleResolver.candidatesFor(String type)`**, called from three sites with
  `element.type()`. Its body opens with `if (type == null) return untyped` — the
  null case is not a defensive branch, it is the reason the method exists, since
  a composition node has no CSS type and the rules that can match it are exactly
  the untyped ones. The parameter is `@Nullable` now. This is the pure form of
  the defect: correct code that no checker could confirm, in a method whose whole
  first line is about the value its signature forbade.

- **`StyleLint.Probe`'s `parent` component**, declared non-null and null for
  every single-compound selector — which is most rules in any sheet. It has to
  be: a null parent is how `probeFor` makes the leftmost probe the root, and
  being the root is how `:root`'s custom properties reach the rest of the chain.
  The one thing the package could not say was the thing it depended on.

- **`Probe.type()` and `Probe.id()`**, the two overrides that carried the comment
  saying the disagreement "is older than this class and wider than it".

So the entry was wrong about what it would find, and right about what it was
worth. The finding is not a crash; it is that a `@NullMarked` package had been
enforcing a contract nobody believed, for long enough that the first code
physically unable to lie about it was made to leave the room instead.

## Alternatives considered

- **Mark `css.lint` and write the three overrides non-null.** What the
  `package-info` called "lying in three overrides". It compiles, the lie is now
  in two places instead of one, and the next implementation written in a marked
  package has a precedent to copy.
- **Leave `css.lint` unmarked and move on.** The state this closes. Unmarking is
  cheap once and compounds: `css.contrast` is unmarked too, and `ThemeAudit.Root`
  is annotated anyway, so the only thing the unmarked package buys is that nobody
  checks whether it kept doing that.
- **Annotate `StyleElement` and not `Selector.Compound`.** Enough to close the
  entry as written and not enough to close the finding the entry's own source
  named. A sweep that fixes the half that was written down is how the other half
  becomes a surprise later.
- **Mark `widget` while the sweep was open.** `Element` is the largest
  `StyleElement` implementation and its `parent` field is unannotated, so marking
  `widget` means auditing a 800-line class with state, subscriptions and three
  caches in it. It is the right next package and it is not this entry.

## Consequences

- **`css.lint` is `@NullMarked`**, and its `package-info` no longer carries a
  section explaining which checker it has switched off and why. That paragraph
  was the visible cost of the defect, and deleting it is the visible fix.
- **`candidatesFor` takes a `@Nullable String`**, which is a private method and
  therefore not a compatibility question. It is the only signature that changed
  where the null was already handled; the rest of the change is annotations.
- **`Selector.Compound.type()` and `id()` are `@Nullable` in a published API.**
  A consumer compiling against the new jar with their own nullness checker on
  will be told about dereferences that were always possible. That is the change
  doing its job, and it is a source-compatible one: an annotation cannot break a
  caller that was already checking.
- **`StyleElementNullnessTest` asserts the annotations are present**, which
  nothing else in this repository does for an annotation. It is here because the
  defect was invisible to every behavioural test by construction: the code was
  right and the signature was wrong, so only a test that reads the signature can
  fail when somebody takes the annotation off to quieten a warning. It also
  asserts `classes()` is *not* annotated, so a later sweep does not annotate the
  fourth member by symmetry — an empty set and a null set would be two spellings
  of one state.
- **Four behavioural tests cover the null paths the sweep exposed** — a typeless
  node against the untyped rules, the same against the untyped `@starting-style`
  rules, a null parent being what makes an element match `:root`, and the lint
  probe's chain. None of them failed before. They are written because "this was
  already correct" is a claim, and a claim about a path that runs on every frame
  the showcase draws is worth an assertion.
