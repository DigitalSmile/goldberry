# 255. A label that does not fit is cut, not wrapped

Date: 2026-09-05

## Status

Accepted. Builds what
[ADR-0235](0235-a-cut-label-needs-nowrap-not-text-overflow.md) diagnosed and
declined to build, and closes the four `TODO.md` entries that were waiting on it:
the menu row, `option`, `select-value` and the segment label.

## Context

ADR-0235 spent a whole record establishing that the toolkit could not cut a
label, and named the property that was missing:

> **What is missing is `white-space: nowrap`**: a way to tell the text stack to
> measure a paragraph at its natural width whatever width it is offered. With
> that, a clip box works and an ellipsis becomes reachable. Without it, no
> arrangement of `overflow` and `flex-shrink` can cut a label, because the label
> is never too long for the box it is in.

It then declined to add it, for a reason that has now expired:

> **No `white-space` property is added here.** It is a text-stack change, it
> wants a consumer that is not a comment, and §8's subset has grown one property
> at a time against a named need — which is the rule that kept the subset small
> enough to believe in.

There are **four** consumers, and none of them is a comment. A menu row clamped
to the work area, an `option` in a `segmented` bar whose cells are exactly 1/n of
the track, a `select-value` in a field an application gave a width, and — since
ADR-0182 — a suggestion row in an autocomplete popup. Each one currently
overflows its box, and each one's stylesheet or `render` carries a paragraph
explaining that it cannot do otherwise.

The rule ADR-0235 invoked is satisfied rather than broken by building it now: the
need is named four times over, and it was named before the property was written
rather than after.

## Decision

**Add `white-space` and `text-overflow` to §8's subset**, with two values each,
and give the text stack the one thing it was missing.

### `white-space` is the whole mechanism, and it lives in the measure function

`Paragraph.measureFunction(TextFlow)` ignores the width Yoga offers under
`nowrap` and reports the width the text actually wants. That is the entirety of
the change ADR-0235 was asking for. Everything else here is consequences of it:
a box may now be laid out narrower than its own content, which is the state
`overflow: hidden` and `text-overflow` were always waiting for and which the
toolkit could not previously reach.

### `text-overflow` is a paint decision and never a layout one

An ellipsised line is **drawn short and measured long**. The measure function
does not read `text-overflow` at all, and `ParagraphFlowTest` asserts that the
two flows measure identically.

This is the load-bearing constraint of the whole design. A paragraph whose
measurement shrank because it had been truncated would be a box that shrank
because it was too narrow — which either settles at a width nobody asked for or
oscillates, and either way lets the ellipsis decide the width it is supposed to
be a consequence of. It is the same trap `Measured` carries a standing warning
about: read geometry to draw something that cannot affect layout, never to decide
a size.

### The cascade carries two properties; everything below it sees one value

`ComputedStyle` gained **two** components, not one. `TextFlow` — the record they
are handed out as — is built by `ComputedStyle.textFlow()` and is what
`Box.Text`, the measure function and the painter all read.

The split is not tidiness. CSS **inherits `white-space` and does not inherit
`text-overflow`**, and a bundle cannot be half-inherited. Both halves of that are
what an author means as well as what the specification says: `menu { white-space:
nowrap }` is a statement about the rows, and `text-overflow` on a container that
draws no text of its own would otherwise put a mark on every label underneath it.

So `whiteSpace` joins `color` and `typography` in `inheritingFrom` — and in
`inheritsSameAs`, which is the style cache's key and which
[ADR-0248](0248-only-the-inherited-half-is-handed-down.md) warns has to be edited
in the same breath or the cache goes stale rather than merely cold. A test
asserts the pair.

### `TextFlow.ellipsises()` refuses an ellipsis that has nothing to mark

`text-overflow: ellipsis` without `white-space: nowrap` marks nothing, which is
what every browser does with the same two rules and follows from the mechanism:
a line that is allowed to wrap is never too long. It is decided in the value
rather than in the painter so that the measure function and the paint cannot
reach different conclusions about one style.

### An anonymous label box inherits by hand

`Box.style(ComputedStyle)` carries the flow onto a `Box.Text` exactly as it
carries `color`, so a `text` element and a `select-value` need nothing. A menu
`item` and an `option` build their label as a **child** box that no style is
applied to, so their `render` passes `style.textFlow()` to a new
`Box.text(paragraph, argb, flow)` overload. That is the same inheritance one
level below the cascade, and it is written down in both widgets because it is the
thing a fifth consumer would otherwise get wrong.

### `flex-shrink: 0` comes off the menu label

ADR-0148 put it there, and said why: a box that never narrows is a paragraph that
never re-wraps. With `nowrap` that is no longer the only way to stop the wrap, so
the label shrinks again and is cut. **The accelerator keeps its `flex-shrink: 0`**
— a cramped row spends its missing pixels on the label, which has an ellipsis to
say so, and never on the shortcut, because half of `Ctrl+Shift+K` is not a
shortcut.

## What is deliberately not built

- **`pre`, `pre-wrap` and `pre-line`.** All three are statements about
  *collapsing* runs of spaces and newlines, and Goldberry never collapses
  anything: a `Paragraph` draws the string it was handed. So `pre-wrap` is what
  `normal` already does here and `pre` is what `nowrap` already does. Naming them
  would be four spellings of two behaviours.
- **CSS's newline collapsing under `nowrap`.** A hard `\n` still breaks a line
  under either value; only *soft* wrapping is turned off. The difference is
  invisible to every consumer in the catalog, all of which are single-line
  labels, and the alternative is a paragraph whose text is not the string it was
  given.
- **CSS's "last line only" ellipsis.** The mark is applied **per line**. The two
  agree for every single-line label, which is all four consumers, and per-line is
  the reading that stays true of a `nowrap` paragraph with hard newlines in it —
  where CSS would leave every line but the last running off the edge.
- **`text-overflow: <string>`.** CSS allows a custom marker. Nothing has asked,
  and it would put a string in `ComputedStyle` where an enum is.

## Alternatives considered

- **A clip box, again.** ADR-0235 records three arrangements of `overflow` and
  `flex-shrink` and why each failed. All three fail for the same reason and this
  removes it; a clip box is now *possible* and is not needed, because the
  ellipsis lands inside the width by construction.
- **Truncating in Java through `Measured`.** It works — `scroll` and `select`
  already read last frame's geometry — and it puts a text-layout decision in four
  widgets instead of one property in the cascade, one frame late in each.
- **One `TextFlow` component on `ComputedStyle`.** Simpler by one field and wrong
  about inheritance, which is the only thing that actually differs between the
  two properties.
- **Bundling `overflow: hidden` into `nowrap`.** They are separate questions:
  `nowrap` says how the text is measured and `overflow` says what an ancestor
  does about the result, and a label that overflows visibly is a legitimate
  drawing — it is what a menu row did before this and what `TextOverflow.CLIP`
  still means.
- **Memoising the ellipsis width on `Paragraph`.** It would be memoised once per
  distinct string for a number that never differs between them. It is a fact
  about the *font*, so `Font.ellipsisWidth()` is where it is cached — one shaping
  of one character per font, ever.

## Consequences

- **§8's subset grows by two properties**, both with a named consumer, which is
  the rule that has kept it small.
- **Four widgets stop overflowing**: a clamped menu, a narrow `segmented` cell, a
  `select` whose value is longer than its field, and an autocomplete suggestion.
  Four stylesheet comments and two `render` comments that explained why they
  could not are replaced with what they now do.
- **`RenderObject` rebinds its measure callback when the flow changes**, not only
  when the paragraph does. A restyle that turns `nowrap` on has to re-measure a
  node whose text did not change, and Yoga does not dirty a node when its measure
  function is replaced — the same trap ADR's note on `applyMeasure` already
  records for a changed paragraph. The flow is compared by **equality** where the
  paragraph is compared by identity, because the cascade hands out a fresh
  `TextFlow` on every resolution.
- **`inheritsSameAs` widened by one field**, so a subtree under a node whose
  `white-space` changed re-resolves. That is correct and is a real cost: it is one
  more way for a style cache to miss.
- **`Font` gained a memo**, its first. It is not `volatile` for the reason
  nothing else in that class is: a font holds native handles and is confined to
  the thread that made it.
- **An ellipsis draws even where a single letter would not fit.** A cell that went
  blank as it narrowed reads as a missing value rather than as a truncated one.
- **`ProgressFill`'s indeterminate sweep is still a there-and-back**, and is still
  a design decision rather than a missing mechanism. This changes nothing about
  it; ADR-0235 already moved it out of the "blocked" column.
- **`text-input` and `text-area` are untouched.** A field's text is drawn by its
  own machinery rather than through `Box.text`, it scrolls rather than truncating,
  and truncating an editable value would hide characters a caret can still reach.
