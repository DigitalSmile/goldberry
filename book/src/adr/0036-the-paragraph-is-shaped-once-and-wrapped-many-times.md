# ADR-0036: The paragraph is shaped once and wrapped many times

- **Status:** Accepted
- **Date:** 2026-08-16
- **Relates to:** `docs/ARCHITECTURE.md` §6, [ADR-0017](0017-proving-the-struct-by-value-upcall.md), [ADR-0029](0029-yogas-node-api-and-who-owns-a-node.md), [ADR-0032](0032-shaping-is-utf16-in-glyphs-out.md), [ADR-0034](0034-one-size-and-the-design-unit-crossing.md)

## Context

Everything M1 needed was bound and one thing was missing: nothing told Yoga how
tall a piece of text was. Layout could place rectangles, text could be drawn on
top of them, and the two had no relationship — the showcase positioned its lines
against hand-written constants because the layout tree had nowhere to put text.

Yoga cannot see inside a leaf. Its whole contract for content it does not
understand is a measure function: *here is a width, how big are you?* The answer
is returned as a `YGSize` **by value, from Java, called from C**, several times
per layout pass ([ADR-0017](0017-proving-the-struct-by-value-upcall.md)). So the
question is not only "how do we wrap text" but "how do we wrap text cheaply
enough to answer that from inside a layout pass".

The obvious implementation re-shapes: take the width, run the line breaker,
shape each candidate line to measure it, count the lines. That is a shaping pass
per measure call, and Yoga makes several per node per layout, and a layout runs
per frame during a resize. It is the wrong order of magnitude, and it is the one
thing `docs/ARCHITECTURE.md` §6 already flags by calling the paragraph cache
"the hot path".

## Decision

**Shape once, at construction. Wrap with arithmetic.**

`Paragraph.of(font, text)` shapes the whole text into a single `GlyphRun` and
never shapes again. Wrapping walks prefix sums over that one run, so a measure
call costs a scan and no native work at all.

Two `int[]`s make it possible: `advanceBefore[o]` is the advance of every glyph
whose cluster is before text offset `o`, and `glyphBefore[o]` is how many glyphs
come before it. The width of any range is one subtraction; the glyph range for
any line is two lookups.

This only works because shaping is in **font design units**
([ADR-0034](0034-one-size-and-the-design-unit-crossing.md)). A run that had a
size baked into it would be specific to that size; one in design units is not,
which is what makes a single shaping answer every width *and* every size.

**A line is a slice, not a string.** `TextLine` carries a text range and a glyph
range into the paragraph's own run. Re-wrapping produces new ranges over the same
glyphs, and painting draws `run[glyphStart, glyphEnd)` with the pen reset — which
is why `Font.draw` grew a range overload.

**Trailing whitespace is in the text range and not in the width.** A trailing
space advances the pen and draws nothing, so counting it would push visible text
left by a space when a line is centred or right-aligned. Selection and caret
positioning want the space, so the text range keeps it.

**The measure function reports the widest line, not the width it was offered** —
except under `EXACTLY`, where the parent has already decided. A paragraph that
wrapped well short of its column should not claim the space it did not use, or a
centred parent centres the gap.

**A one-entry memo, not a map.** Yoga asks for the same width repeatedly within a
pass and the paint that follows asks once more, so the access pattern is a run of
identical widths. One entry serves it; a map would hash a `double` for the same
hit. The global cache §6 describes — keyed by (text, resolved style, width
bucket) — needs a style system to key on and belongs with M2.

**`Box` gained text, and a box with text may not have children.** Yoga asks a
measured node for its size and never lays its children out, so a box that was
both would silently lose them. Refused where the box is built rather than where
the layout goes quiet.

**Right-to-left text is refused at construction.** HarfBuzz returns those glyphs
in *visual* order, so prefix sums accumulated in logical order would measure the
wrong glyphs and the paragraph would wrap confidently in the wrong places.
`java.text.Bidi.requiresBidi` detects it — the same class that will eventually do
the run splitting — and `Paragraph.of` throws rather than mis-wrapping.

## Alternatives considered

**Re-shape each candidate line.** Correct at every boundary, and the cost is a
shaping pass inside a measure callback. It is the implementation to reach for if
the approximation below ever shows.

**Shape the whole paragraph, then re-shape only the final lines for painting.**
Half the cost of the above and most of the correctness. Worth revisiting; it buys
accurate line-boundary kerning without touching the measure path, which is the
part that has to be fast.

**Cache globally from the start**, keyed by text and width. It is what §6
describes and it needs a resolved text style to key on. Building it now would mean
inventing that key before the style system exists.

**Let the widget layer wrap and hand Yoga a height.** Removes the upcall from the
hot path entirely and moves flexbox's job out of flexbox: the width a paragraph
should wrap to is the width flexbox is in the middle of deciding.

**Break inside over-long words.** Every browser does it eventually, and doing it
without a hyphenation dictionary means breaking mid-syllable. Overflowing is
visibly wrong in a way that gets fixed; a bad hyphen is wrong in a way that ships.

## Consequences

**M1's vertical slice is joined.** Yoga proposes a width from C, the paragraph
wraps and answers with a height through the `YGSize` upcall, flexbox sizes the box
around that answer, and Blend2D draws the lines that were measured. The showcase's
body text re-wraps as the window is dragged, and its layout has two numbers
written down — the bar's height and the padding — with everything else coming from
content.

**Breaks are not re-shaped, so a line boundary keeps a kern it should drop.** Each
line is a slice of the whole paragraph's shaping, so the kern between the last
character of one line and the first of the next is included. The error is a
fraction of a pixel at the end of a line, and it buys wrapping that costs no
shaping.

**Right-to-left text throws.** Loud, and a real limitation: Arabic and Hebrew do
not render at all rather than rendering wrongly. Bidi run splitting is the fix and
is the next thing the text stack needs.

**Still one font per paragraph.** No fallback to the emoji slot mid-run, no style
runs, no mixed sizes. Each of those makes a paragraph several runs, which is a
change to how the prefix sums are built and not to the idea behind them.

**M1's exit criteria are not all met.** The wrapped paragraph exists and resizes;
the 60 fps measurement on three machines has not been taken, the upcall benchmark
does not exist, and the paragraph cache is a one-entry memo rather than the keyed
cache §6 describes. Those are what remain.
