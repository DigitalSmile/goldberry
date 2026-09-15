# 321. A rule under text belongs to the face

Date: 2026-09-15

## Status

Accepted. Closes the second half of `docs/gaps.md` G27 — **text decorations**. The
first half, an italic face, is still open and is
[ADR-0066](0066-a-weight-is-a-face-and-color-inherits.md)'s question rather than this one's.

## Context

G27 asks for two of the three controls every board and document tool puts beside
**bold**, and says either would be enough on its own:

> - `BundledFont.UI_ITALIC` (and a `Style` beside `Weight`), or the variable-axis
>   answer ADR-0066 deferred.
> - `TextFlow.decoration(Decoration.UNDERLINE | Decoration.LINE_THROUGH)`, drawn by
>   `Paragraph.paint` from the face's own metrics.

Italic is a *face*, and a face is a file: ADR-0066 settled that a second weight is
a second file here, because instancing `wght` at runtime needs symbols bound in
both HarfBuzz and Blend2D. Italic is the same decision one step further on, and it
adds a megabyte of outlines, a licence line and a golden-image sweep to a change
that has nothing else in common with the one below. It stays open.

Underline and strikethrough are a **text-layer** feature, and the gap says exactly
why an application cannot have them:

> a decoration needs the line's extents and the font's `underlinePosition` and
> `underlineThickness`, which are in the face and not reachable from `Paragraph`.
> Drawing a rectangle under the text in `TextPainter` would get the thickness wrong
> at every size and the position wrong at every family.

The numbers turned out to be one layer nearer than expected. `BLFontMetrics` has
sixteen floats, the layout table has verified all sixteen against the compiled
library since ADR-0010, and `BlendFontMetrics` carried six of them — with a note
saying why the other ten were not surfaced:

> an accessor for a number nothing uses is a promise to keep it working.

Four of those ten are the decoration metrics. Something uses them now.

## Decision

**`text-decoration-line`, from the face's own numbers, drawn by the paint.**

Four layers, none of them new machinery:

1. `BlendFontMetrics` carries ten fields instead of six —
   `underlinePosition`/`Thickness` and `strikethroughPosition`/`Thickness`, read
   out of the struct the call was already filling. **No new native symbol and no
   relink**: `bl_font_get_metrics` was writing all sixteen and this end was reading
   six.
2. `Font.decorations()` answers a `Font.Decorations` record — the four together,
   because they are only ever read together.
3. `TextDecoration` (`UNDERLINE`, `LINE_THROUGH`) joins `text.flow`, and `TextFlow`
   carries a `Set` of them beside `white-space`, `text-overflow` and `text-align`.
4. `Paragraph.paint` draws a rectangle per line per decoration, in the **text's own
   colour**, at the face's position and thickness.

**The sign convention is Blend2D's and it is the useful one.** Both positions are
y-down offsets from the baseline to the *top* of the rule: an underline is positive
(below the text) and a strikethrough negative (through it). So the paint is
`baseline + position` with no arithmetic to get wrong — unlike `ascent` and
`descent`, which are compared against a box and are therefore both positive.

**A face that says nothing gets conventional numbers.** A `post` table too short to
carry the pair is legal, and a thickness of zero is a real answer;
`Decorations.orElse(size, ascent)` substitutes a rule a fourteenth of the em, an
underline a tenth of the em below the baseline and a strikethrough a third of the
ascent above it. "Do not draw the underline the stylesheet asked for" is the one
response that is certainly wrong.

**The rule follows the glyphs, not the box.** It is as long as the line, indented
with it under `text-align` (`TextAlign.indentOf`, one implementation —
[ADR-0318](0318-a-line-starts-where-the-paint-says-it-does.md)), and absent from a
blank line, which has no glyphs to mark. An **ellipsised** line is ruled across its
mark, because the mark is part of the line: a rule that stopped short of its own `…`
reads as two words, one of them underlined.

The face's metrics are read **once per paint**, not once per line: it is a downcall,
and it is the same answer for every line of one font. A paragraph with no
decorations reads them not at all.

### The cascade

`text-decoration` and `text-decoration-line` both resolve, into a
`Set<TextDecoration>` component of `ComputedStyle`, and **it inherits**.

CSS does not inherit it — it *propagates* to in-flow descendants, which is a
different mechanism with nearly the same effect — and inheritance is how that reads
here for `text-align`'s reason (ADR-0256): a control's text is very often an
anonymous child box, so `button.link { text-decoration: underline }` has to reach
the label inside the button or it decorates nothing at all. `inheritsSameAs` learned
about it in the same commit, because a property that starts inheriting and is not in
the style cache's key makes the cache go stale rather than merely cold.

CSS's shorthand also carries a colour and a style (`wavy`, `dotted`), and a
declaration naming either is **dropped whole** with the usual warning. Half-applying
it would be a property that lies: a rule that asked for a wavy red underline and got
a straight one in the text's colour is worse than a rule that did nothing.
`overline` is absent for the ordinary reason — the subset in
`docs/core-widgets.md` §8 says which properties exist, and nothing has asked.

## Consequences

An application setting a shape's text to struck-through writes one field, one codec
line and one button — the gap says so — and a stylesheet can underline a link
without the widget knowing. `:html`'s `<u>`, `<s>`, `<del>` and `<ins>` become
styleable the same way, since every element already contributes an `html-<tag>`
type; `html.css` does not use it yet and this ADR does not add it.

The two form controls that draw text through `Value` still pass the default flow,
so `text-input` and `text-area` ignore `text-decoration` exactly as they ignore
`text-align`. That is consistent rather than half-done: the day one of them passes
`style.textFlow()` down it gets both, and the caret geometry it would need is ready.

`BlendFontMetrics`'s note now covers six unsurfaced fields rather than ten, and the
promise it describes is one this ADR takes on for the four: something uses them, so
they are kept working.

## Alternatives considered

**Draw the rule in the widget layer**, from a `Box` field. It is where a border
lives, and a decoration is not a border: it belongs to the *line*, and a wrapped
paragraph has as many rules as it has lines, each as long as its own line. A box
knows none of that.

**A colour of its own.** CSS has `text-decoration-color`, and the honest use for it
is a spell-checker's squiggle, which also wants `wavy`. Both together are a feature;
either alone is a property that mostly disagrees with the text.

**An `int` bit mask**, which is how the gap sketched it. A `Set` of an enum is what
the rest of the toolkit uses for a small closed set, it prints legibly in a
`TextFlow`'s `toString`, and it cannot be `|`-ed with a value from another enum by
accident.
