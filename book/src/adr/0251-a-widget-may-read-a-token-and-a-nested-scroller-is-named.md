# 251. A widget may read a token, and a nested scroller is named

Date: 2026-09-05

## Status

Accepted. Closes two `TODO.md` entries opened by
[ADR-0116](0116-a-scroll-view-is-a-clip-an-offset-and-two-extents.md).

## Context

Both entries are about `scroll` and about the same shortfall — something the
design system says that nothing in the code could hear.

> A widget cannot read a resolved custom property, so `scroll`'s line height is a
> constant. […] `ScrollViewport.LINE` is 20 logical pixels and a
> `--gb-scroll-line` was deliberately *not* shipped, because a token no widget
> can read is a number an author sets and nothing honours.

> Nested same-axis scrollers are banned in the canon and nothing enforces it.
> §2.4 says so outright. Chaining means a nested pair behaves reasonably rather
> than badly, so the ban costs nothing today; what is missing is the diagnostic
> that would tell an author they wrote something the design system rules out.

**The first entry was half stale.** `Paints.Context.color` has read a resolved
custom property since [ADR-0195](0195-a-painter-reads-the-theme-through-a-custom-property.md)
— that is how a chart gets `--gb-chart-1…8`. What was missing was the same door
for a *number*.

## Decision

### `Paints.Context.length`, and the widget banks it

`color`'s companion, resolved through the same cascade and answering logical
pixels. Deliberately still narrow — lengths and colours and nothing else — on
`color`'s own terms: both are values the cascade already parses, and a general
token-returning accessor would invite a widget to reimplement the parser.

A percentage answers the fallback, because a percentage is *of* something and a
widget asking for a token has no containing block in hand to be a percentage of.

**The wheel arrives where there is no context to ask**, so `ScrollViewport` reads
the token in `render` and **banks** it through a callback into `ScrollState` —
the shape `onMeasured` already had. That makes the value a frame late, which is
[ADR-0117](0117-a-widget-may-be-told-what-it-measured.md)'s bargain unchanged: a
paint always precedes an input, so a real window has spent that frame before
anybody can turn a wheel. The banking is guarded on the value having *changed*,
because the callback sets state and a `setState` every frame is a rebuild every
frame.

`--gb-scroll-line` ships at 20px, and `ARROW` follows it — §2.4 gives the arrow
keys one line, and the two had always been the same number.

### A nested same-axis scroller says so, once

`BuildContext.findAncestorState` is the whole implementation. It exists for
`scrollIntoView` and answers this question with nothing added, which is the
argument for asking it in `ScrollState.build` rather than teaching the renderer
about scroll views.

It stays **a diagnostic and not a refusal**: the arrangement still works, because
turning a design rule into a crash is worse than the rule going unheard. And it
is deduplicated by axis, statically, for
[ADR-0243](0243-a-missing-token-is-a-message-not-a-stream.md)'s reason — `build`
runs per element per invalidation, and a document that nests scrollers in four
places has one mistake rather than four.

## Alternatives considered

- **Put the resolved custom properties on `ComputedStyle`.** Then anything with a
  style could read a token, including `restyle`. It adds a map to a record that
  is compared and cached per node per frame, for a door two widgets want.
- **A general `token(String)` returning tokens.** It is the accessor `color`'s
  comment already argued against: a widget would parse them, and there would be
  two parsers.
- **Read the line height in `onPointer` from the event.** The event would have to
  carry the element's tokens, which is a cascade lookup per wheel event rather
  than per frame.
- **Refuse to build a nested same-axis scroller**, or drop the inner one's wheel
  handling. Both turn a *canon* rule into a behaviour change, and chaining
  already makes the arrangement work; the author's problem is that nobody told
  them, not that it broke.
- **Warn from the renderer**, which sees the whole tree. It puts knowledge of a
  particular widget in `:core`, where `Scroll` is not even visible.

## Consequences

- **`Paints.Context` has a second implementor to update**, and the test one in
  `:widgets` answers the fallback — which is right: a widget rendered by hand has
  no cascade behind it.
- **Six tests.** Three for the token — the default is 20, an override is obeyed,
  and the arrow keys follow it — and three for the nesting: same axis is reported
  *once*, crossed axes are not (a wide table in a page is exactly that), and one
  scroll on its own is not.
- **The override test paints twice, and says why.** The first paint banks the
  token; the rebuild after it is what puts the value on the widget the router
  hands the wheel to. Writing it with one frame is what found that, and the
  comment is there so the next reader does not "fix" it.
- **`ScrollState` gained a static report set and two accessors for the test**,
  because only `slf4j-api` is on the classpath and there is no appender to read
  the log back from — `StyleResolverTest`'s arrangement exactly.
- **`list` is unchanged, and its entry stays open.** `ListView.virtualized(h)`
  takes the row height as an argument, which is an *API* choice rather than a
  missing reader: the number decides which rows to build in `children()`, and a
  value banked from `render` would be a frame late in the one place a frame late
  means building the wrong rows. The door this opens is the one `scroll` needed;
  `list` needs a different one.
- **A comment in `controls.css` is written without a selector**, because
  `ButtonTest` asserts the base sheet contains no `#` as a proxy for "names no
  colour of its own" — a crude check doing a useful job, and worth working around
  rather than weakening.
