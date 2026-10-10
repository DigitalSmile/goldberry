# ADR-0587: A property nothing reads is refused, as an unknown node is

- **Status:** Accepted
- **Date:** 2026-10-10
- **Relates to:** ADR-0511, where the gap was recorded

## Context

`KdlInflater` refused a node name nobody registered, and the registries a model
publishes refused an action, a binding or an icon nobody bound. A **property**
was different. `Attributes.of` read `id`, `class`, `tooltip`, `context-menu` and
`name`, each factory read what it understood, and a property no factory asked
for was dropped without a word. `row gap=8` drew a row with no gap. `Row`'s own
javadoc sample and `docs/core-widgets.md` §1 both wrote exactly that, and the
guide carried a warning box saying so. `BookMarkupTest` inflates every `kdl`
sample in the guide and could not catch a misspelt attribute, because nothing
anywhere could.

There is no schema to check against. A factory is a static method that asks its
node for properties as it goes, and what it asks can depend on what else the
node says. The list of properties a widget understands exists only as the calls
its factory makes.

## Decision

**The inflater notes every property asked for while a document is built, and
refuses the document if one it wrote was never asked for.**

- `KdlNode`'s accessors (`property`, `stringProperty`, `booleanProperty`,
  `flagProperty`, `numberProperty`) note the key they are asked for. A lookup in
  `properties()` notes that key, and walking the map notes every key.
- The notes go to a `PropertyReads` bound in a `ScopedValue` for the length of
  one outermost `inflate` or `inflateAll`. Outside an inflation nothing is
  bound and the accessors note nothing, and `properties()` hands out the node's
  own map.
- Nodes are keyed by **identity**. Two `button press="save"` nodes are equal
  records and are still two places an author wrote.
- The check runs once the **whole tree** is built, not after each factory. A
  factory is not the only reader of its node: `line-chart` reads its `point`
  children's `x` and `y` after their own factories have run.
- `UnreadPolicy` says what happens next. `REFUSE` is the default and throws a
  `KdlSyntaxException` at the first unread property's node, listing every other
  one. `WARN` logs one line per property and builds. `IGNORE` notes nothing.
- A finding names the node, its position, the property and its value, and the
  properties that node **did** read, in the order it read them:
  `row at 2:3 ignores gap=8; it reads id, class, tooltip, context-menu, name`.
  The read list is the likeliest place to find what a typo meant.

Refusing is the default for the reason an unknown node is refused. A property
nothing reads is a widget that ignores what its author wrote, and the reload
path already keeps the last good document and reports a `KdlSyntaxException`.

## What the sweep found

With `REFUSE` on, the `:core`, `:widgets`, `:html`, `:media` and `:example`
suites ran 3,092 tests, and six failed. Every one was a real finding:

- `radio selected=`, `option selected=` (segmented) and `step current=`. Each
  test asserted that markup *cannot* set the state and that the attribute was
  silently dropped. They now assert the refusal, which is a stronger form of
  the same invariant.
- `checkbox checked=#true indeterminate=#true`. `indeterminate` wins, as
  documented, but the factory never read `checked` once `indeterminate` was
  true, so the contradiction looked like an ignored property. The factory now
  reads both before deciding. The precedence is unchanged.
- `series` and `point` with `id=` and `class=`. `WidgetParityTest` recorded
  these as "the one place in the catalog where markup accepts an attribute and
  throws it away". It is now refused instead, and the parity test asserts that.

The showcase's `.kdl` documents (`ShowcaseDocumentsTest`) and the guide's 109
samples (`BookMarkupTest`) inflate clean. A one-off sweep of the 96 `kdl`
samples in the main sources' doc comments found one more, `Masonry`'s own
`gap=12`. That sample and `docs/core-widgets.md`'s `row gap=8` and
`masonry … gap=16` were fixed by hand: masonry reads `columns` and
`min-column-width` and takes its gap from the stylesheet.

## Consequences

- An application whose markup carries a dead property fails at inflation where
  it used to build. That is the intent, and the message says which property and
  where. `inflater.unread(UnreadPolicy.WARN)` is the way back for a document
  that cannot be fixed at once.
- A factory that reads a property only on some paths must read it on all of
  them, or a document that writes it on another path is refused. The checkbox
  was the one such factory in the catalog.
- A factory that inflates a subtree itself, through the public `inflate`, opens
  a fresh scope, so its own node's reads are not shared with the outer
  inflation. No factory does this; `Inflatable`'s contract hands children over
  already built.
- Arguments are not checked. A positional value is a node's content and every
  factory that has content reads its first argument; a second one going unread
  is a different question.
- The guide's warning box in *Row and column* and the TODO entry under *The
  catalog* are answered. Their replacements wait in `docs/snapshot/` for the
  release.
