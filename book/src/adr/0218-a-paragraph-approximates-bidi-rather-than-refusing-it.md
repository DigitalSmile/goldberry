# 218. A paragraph approximates bidi rather than refusing it

Date: 2026-08-30

## Status

Accepted. Amends [ADR-0036](0036-the-paragraph-is-shaped-once-and-wrapped-many-times.md)'s
last consequence; the run splitting it names is still ahead.

## Context

`Paragraph.of` threw `UnsupportedOperationException` on any text
`java.text.Bidi.requiresBidi` was true for. The reasoning (ADR-0036) was sound:
every measurement in a paragraph is a prefix sum accumulated in **logical** order,
HarfBuzz returns a right-to-left run in **visual** order, so a paragraph that
accepted such text would measure the wrong glyphs and wrap confidently in the
wrong places. Loud beat silent.

It was loud in the wrong place. A `text-input` does not choose its text — a user
pastes it — and nothing between the clipboard and the paint catches that
exception. So a user who pasted Arabic into a field lost the **window**: the
paste succeeded, the field held the text, and the frame that tried to describe it
threw. The one crash left on `TODO.md`, and the entry said what was missing:
"refusing the paste is not acceptable and neither is crashing, so the interim
behaviour has to be chosen."

## Decision

**A paragraph never refuses text.** Text that needs bidi is shaped with the
direction **forced to `LTR`**, which is what makes the glyphs come back in the
order every measurement here assumes.

**The glyphs are right and the order is wrong.** Joining and ligature forms come
from the script, which is still guessed, so Arabic is shaped as Arabic; what the
forced direction changes is the sequence. The text is therefore drawn *mirrored*
— first character at the left — rather than reordered into nonsense.

**Everything else stays self-consistent.** Widths, wrapping, caret positions,
hit testing and selection rectangles are all derived from the same prefix sums,
so a click lands where the caret is drawn and a highlight covers the glyphs it
appears to cover. The text is wrong in exactly one way, and it is the way that
needs run splitting to fix.

**It says so, twice.** `Paragraph.isBidiApproximate()` answers for any caller
that wants to know, and `Paragraph.of` logs one warning per distinct string —
once, because a paragraph is shaped once and held by `ParagraphCache`, and
nothing on this path runs per frame.

**`Font.shape(text, direction)` is the new seam.** One overload, one caller, and
it is the same call the eventual run splitter needs: bidi run splitting *is*
"shape each run in its own direction", so the parameter that makes this
approximation possible is the parameter that will make the real thing possible.

## Alternatives considered

- **Catching it in the field.** It puts a `try`/`catch` around a paint and leaves
  the question of what to draw unanswered — a field that swallowed the exception
  would draw nothing and hold text nobody can see. And every *other* caller of
  `Paragraph` would still crash: a `label` bound to a model, a menu item, a
  tooltip.
- **Refusing the paste.** The field would keep working and the user's text would
  vanish with no explanation. `TODO.md` ruled it out before this was written, and
  it is worse than mirrored text: data loss beats a drawing fault.
- **Substituting a placeholder — `?` per RTL character.** Also data loss, with
  the added property that the field's *contents* would no longer be the text the
  model holds.
- **Building bidi run splitting now.** The real fix, and not an interim: it is
  several runs per line, visual reordering within a line, per-run measurement,
  and a caret that has to know which run it is in and which side of it. Every one
  of `Paragraph`'s public measurements changes shape, and so does every caller
  that positions a caret from them. It is M5 work with a specification of its
  own, and it should not be smuggled in under a crash fix.
- **Handling a uniformly right-to-left paragraph correctly and approximating only
  mixed text.** Tempting, and genuinely tractable — the prefix sums can be built
  by walking a visually ordered run backwards, and a line's glyph range stays
  contiguous. It stops being tractable at the caret: `widthBetween` measures from
  the line's *left* edge, and in a right-to-left line the caret before offset `o`
  is at `lineWidth - widthBetween(start, o)`. That is a change to what every
  caller of these two methods means, which is the same change full bidi needs —
  so it is half the work of the real fix for a fraction of the correctness.

## Consequences

- **The last crash on `TODO.md` is gone.** `TextInputTest` pastes Arabic and
  asserts the field keeps it and the next frame is described; it fails against the
  old `Paragraph`, which is what says it is testing the crash and not the fix.
- **Right-to-left text is now visibly wrong instead of fatal**, and that is a
  deliberate trade recorded in three places a reader will hit: the class doc, the
  warning, and `isBidiApproximate()`.
- **`ParagraphCache` has no refusal path left.** Its test for "text the shaper
  refuses is not cached" became "text that needs bidi is held like any other",
  which is the same assertion about the same string with the exception removed.
- **`Font.shape` has an overload** that takes a direction. The single-argument
  form delegates to it with a null direction — "guess", the behaviour every
  existing caller had.
- **Nothing else changed.** Text that never needed bidi takes exactly the path it
  always did, including the guessed direction, so every golden image is
  byte-identical.
