# ADR-0622: A paragraph is split into directional runs and placed in visual order

- **Status:** Accepted
- **Date:** 2026-10-11
- **Relates to:** `docs/css-plan.md` (CSS-50), `docs/css-support-2026-10-10.md`
  §2; supersedes ADR-0218 (a paragraph approximates bidi rather than refusing
  it); amends ADR-0614 (direction mirrors layout, not glyphs)

## Context

ADR-0218 made a paragraph take right-to-left text instead of refusing it: the
text was shaped with the direction forced to `LTR`, so every prefix sum
measured the right glyphs, and the text was drawn in logical order, mirrored.
`isBidiApproximate()` and one warning per string said so. ADR-0614 then gave
boxes `direction: rtl`, which mirrors rows, logical properties and
`text-align: start` but left the glyphs as they were.

CSS-50 asked whether to build bidi run splitting, which reaches every
measurement a paragraph answers and both editors, and with what base direction
when none is set. The user decided on 2026-10-11:

- build it;
- the base direction is the box's `direction`, as CSS has it, `ltr` initial;
- `unicode-bidi: plaintext` opts a box into the first strong character, per
  paragraph;
- a paragraph made with no direction at all (`Paragraph.of(font, text)`) takes
  the first strong character;
- the arrow keys move the caret logically, mirrored in a right-to-left
  paragraph.

## Decision

**A paragraph resolves its embedding levels with `java.text.Bidi`, one hard
line at a time, shapes each level run in its own direction, keeps its
measurements in logical order, and places each line's runs in visual order.**

- `dev.goldberry.text.bidi` (exported) holds the model. `Directionality` is a
  direction and where the base comes from (`DIRECTION`, `FIRST_STRONG`,
  `OVERRIDE`); `Directionality.AUTO` is the first strong character with a
  left-to-right fallback. `UnicodeBidi` is CSS's six keywords; a box's text is
  always a paragraph of its own, so `embed` and `isolate` read as `normal` and
  `isolate-override` as `bidi-override`. `BidiLevels` resolves the levels per
  hard line, answers a paragraph's base direction, and orders a line's runs by
  rules L1 (trailing whitespace and tabs take the paragraph's level) and L2
  (reverse from the highest level down). `LevelRun` is a run.
- Text with no right-to-left character under a left-to-right base has no
  levels at all (`BidiLevels.isLeftToRight()`), and the paragraph keeps no
  `BidiLevels`. Such a paragraph is shaped, measured and drawn by exactly the
  code it was before, so no golden of left-to-right text moved.
- Shaping: each level run is shaped in its direction, split as before into
  the faces that draw it (emoji, fallbacks). A right-to-left segment keeps
  HarfBuzz's visual run for drawing; its glyphs are appended **reversed** to
  the concatenated run, so `advanceBefore` and `glyphBefore` stay prefix sums
  in logical order. Widths, wrapping and `widthBetween` are unchanged.
- Placing: every paint path (plain, styled, justified, truncated) walks a
  line's visual runs left to right. A logical glyph range inside a
  right-to-left segment is one stretch of its visual run, counted from its far
  end. Justified room goes beside each gap on the side the run reads towards.
  A truncated line in a right-to-left paragraph keeps its logical start at the
  right and draws the mark at its left.
- Geometry: `Paragraph.caretX(lineStart, lineEnd, offset)` is the caret's x
  from the line's left edge: the edge of the character after it that its run
  reads from, the last character's other edge at the end of the visible text,
  and on the reading side of the line in its trailing whitespace (negative in
  a right-to-left paragraph). `offsetAt` is the nearest `caretX`.
  `stretchesOf(lineStart, lineEnd, from, to)` answers a range as one
  `Stretch` per run it touches. `indentOf(line, align, available)` places a
  line by its own paragraph's direction, with `TextAlign.placing`, which reads
  `justify` as `start`. `TextDocument` forwards all four per hard line.
- `TextGeometry`, `TextField`, `TextInputState`, `TextAreaState` and
  `TextAreaBox` use them. A highlight over several runs of one line is one box
  over their hull holding a piece over each (`Highlight.over`), so the controls'
  child lists keep their shape. An untouched right-to-left field shows the
  right end of its value.
- Keys: `Motion.in(direction)` swaps `LEFT` and `RIGHT` in a right-to-left
  paragraph; the core `Editor`, `text-input` and `text-area` ask the base
  direction at the caret.
- CSS: `unicode-bidi` is read (not inherited) onto `Presence`;
  `ComputedStyle.directionality()` combines it with `direction`, and the
  renderer's `paragraph(style, text)` passes it, so the paragraph cache keys on
  it. Under `plaintext`, `lineAlign()` leaves `start` and `end` for each
  paragraph to place.
- `isBidiApproximate()` and its warning are gone; `isBidirectional()`,
  `directionAt(offset)` and `directionality()` replace them.

## Consequences

- Arabic and Hebrew read correctly in every label, field and area, mixed with
  Latin and digits. The `fallback-names` golden was retaken: the Arabic name in
  it is now drawn right to left.
- A joined paragraph is one text to the algorithm under its first span's
  directionality, while each span keeps the directions it was shaped in. A
  neutral at a span's edge may be shaped one way and placed the other, which
  only a mirrored bracket would show.
- At a run boundary the caret is drawn at one of the two places the offset
  touches, the side of the character after it. Two offsets can share a place
  on screen (a run's far edge and the next run's near one), so a click there
  lands on the first.
- The caret moves logically: `Left` at the edge of a right-to-left run inside
  a left-to-right line jumps to the run's other end. That is what the user
  chose, and it keeps `TextEdit` a function of offsets.
- `letter-spacing` is added after each cluster in array order, so inside a
  right-to-left run it falls on the glyph's right; the widths are unchanged.
- `TextAreaBox` still never respaces `justify`; that is unchanged from
  ADR-0615.

## Status

Done 2026-10-11: `dev.goldberry.text.bidi` (`Directionality`, `UnicodeBidi`,
`BidiLevels`, `LevelRun`); `Paragraph` (levels, per-run shaping, the
reversed concatenation, `eachFragment`, `caretX`, `stretchesOf`, `indentOf`,
`directionAt`), `Stretch`, `ParagraphCache`'s key, `TextDocument`,
`TextGeometry`, `TextAlign.placing`, `Motion.in`, `Editor`, `Presence`'s
`unicodeBidi`, `ComputedStyle` (`unicode-bidi`, `directionality()`,
`lineAlign()`), `WidgetRenderer`, `TextField`, `TextInputState`,
`TextAreaState`, `TextAreaBox`, `Highlight.over`; `BidiLevelsTest`,
`ParagraphBidiTest`, `BidiEditingTest`, `UnicodeBidiTest`,
`TextInputBidiTest`, `TextAreaBidiTest`, the conformance rows, the retaken
`fallback-names` golden.
