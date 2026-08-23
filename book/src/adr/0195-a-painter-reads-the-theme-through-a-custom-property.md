# 195. A painter reads the theme through a custom property

Date: 2026-08-23

## Status

Accepted. How a chart gets its eight series colours, and the first thing on
`Paints.Context` whose answer is per node rather than per frame.

## Context

[ADR-0194](0194-a-series-colour-is-derived-from-nord-not-taken-from-it.md) fixed
*what* the eight series colours are. This is about where they live, and the
obvious answer — a `static final int[]` in the widgets module — is wrong for a
reason that only shows up later.

**A chart cannot express its colours as CSS properties.** Every other widget in
the catalog is coloured by the cascade: a node has a `color` and a `background`,
and a stylesheet reaches them by selector. A chart needs *eight* colours on one
node, and there is no way to say "the fourth series" in a rule. Nor can the parts
mechanism ([ADR-0065](0065-a-part-is-styleable-and-not-constructible.md)) help:
a part is a child node with a CSS type, and a `canvas` has no child nodes at all
— its content is a painter, not a tree.

So a chart either reads its palette from Java, or the toolkit grows a way for a
widget to ask the cascade a question that is not a property.

The cost of the Java table is not that it is ugly. It is that **the palette stops
being the theme's**. `design-system.md`'s whole claim is that a theme owns colour;
a table in `:widgets` means a Nord-light chart and a Nord-dark chart are the same
eight colours, an application cannot recolour one chart's first series, and a
third theme would have to be a code change.

## Decision

### `--gb-chart-1…8` in the theme files, read through `Paints.Context#color`

The eight values live in `nord-light.css` and `nord-dark.css` beside the semantic
hues, and `Context.color(name, fallback)` resolves one **against the node being
rendered** — through `StyleResolver.customPropertiesFor`, which is the same
mechanism `var()` already uses and which is cached by element identity
([ADR-0152](0152-the-cascade-looks-at-rules-that-could-match.md)).

That inheritance is the whole point: `#revenue { --gb-chart-1: #b48ead }`
recolours one chart's first series and nothing else, because custom properties
cascade and inherit like any other. A table cannot do that at any price.

### It answers colours, not tokens

`color(String, int)` and not `custom(String) → List<Token>`. Every other custom
property in the toolkit is consumed by a declaration the cascade already resolves;
handing a widget raw tokens would invite it to reimplement the value parsers, and
the second implementation of a colour parser is where `rgb(0 0 0 / 50%)` starts
meaning two things.

### The element is a field, like the frame's clock

`Paints.Context` is **one object per renderer**, shared by every node — which is
deliberate, and is why `nowMillis` is a field read once per frame rather than a
call to the clock (two spinners in one window would otherwise be on their own
ticks). This is the first question on it whose answer is per *node*, so the
renderer sets `currentElement` immediately before `render` and clears it in a
`finally` afterwards.

The clearing is not tidiness. A `canvas` painter **closes over the context** and
runs later, during the paint — so a context that still held an element would let a
painter read a stale node's tokens, silently, in a frame where the tree had
changed underneath it. Cleared, that read returns the fallback, which is a wrong
colour rather than a wrong colour that used to be right.

### A missing token is a colour, not an exception

`SeriesPalette` falls back to the derived dark steps. A chart rendered against no
theme at all — a test, a bare tree, an application that forgot the stylesheet —
draws eight distinguishable series rather than eight black lines or a stack trace.

### A ninth series repeats the eighth, visibly

`SeriesPalette.of` clamps rather than cycling. Cycling would make series 9 and
series 1 the same colour *and* look intentional; clamping makes 8 and 9 the same
colour, which is a chart that needs folding into "Other" or faceting into small
multiples, and it should look like one. Generating a ninth hue is the option that
is actually forbidden: under CVD it is indistinguishable from one of the eight,
and it would break the property the 40 320-permutation search established.

## Consequences

- **A theme owns the series palette.** A third theme is a stylesheet, not a code
  change, and `charts.md` §2's table is documentation of the default rather than
  the definition of it.
- **`Paints.Context` has grown a per-node method**, which its own doc said it was
  an interface in order to allow. The pattern is now established for the next
  such question, and the `currentElement`/`finally` pair is the part to copy.
- **A test fixture that implements `Context` by hand answers the fallback.**
  `TestFont.context()` has no element and no cascade behind it — a test calling
  `render` directly is not styling a tree — so a test that wants the theme's
  values drives a `WidgetRenderer`, which is the honest distinction and is
  documented on the fixture.
- **The lookup is a map read per slot per frame**, on a cascade the resolver
  already caches by element identity. Eight slots is eight map reads; if a chart
  with many series ever measures, the answer is to read the palette once per
  render rather than to cache it here.
- **Nothing else uses it yet.** `sparkline` is one series and takes `color`; this
  is built for `line-chart`, `bar-chart`, `area-chart` and `donut-chart`, which
  are the widgets that have more than one of anything.
