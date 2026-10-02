package dev.goldberry.text;

/// One line of a wrapped paragraph: a range of the paragraph's text, the range
/// of its glyphs, and the width those glyphs take.
///
/// A slice, not a string. `start` and `end` index the paragraph's own text, and
/// `glyphStart` and `glyphEnd` index the single [ShapedRun] the paragraph was
/// shaped into once, so re-wrapping at a different width produces new lines over
/// the same glyphs and costs no shaping. [TextLayout#lines] holds them in order.
///
/// The text range keeps trailing whitespace; the glyph range and the width do
/// not. A trailing space advances the pen and draws nothing, so counting it in
/// the width would push a right-aligned or centred line left by a space's worth.
/// Selection and caret positioning want the space, which is why the text range
/// still has it.
///
/// Read more: [Text, fonts and icons](https://goldberry.dev/docs/guide/text.html#paragraphs).
///
/// @param start      first character of the line, in the paragraph's text
/// @param end        one past the last character, trailing whitespace included
/// @param glyphStart first glyph of the line, in the paragraph's run
/// @param glyphEnd   one past the last glyph, trailing whitespace excluded
/// @param width      the line's width in logical units, trailing whitespace
///                   excluded
public record TextLine(int start, int end, int glyphStart, int glyphEnd, double width) {

    public TextLine {
        if (start > end) {
            throw new IllegalArgumentException("a line cannot end before it starts: " + start + ".." + end);
        }
        if (glyphStart > glyphEnd) {
            throw new IllegalArgumentException(
                    "a line's glyphs cannot end before they start: " + glyphStart + ".." + glyphEnd);
        }
    }

    /// Whether the line has no glyphs to draw. True for a blank line between two
    /// paragraphs, which still occupies its full height.
    public boolean isEmpty() {
        return glyphStart == glyphEnd;
    }

    /// The line's text, taken back out of the paragraph it came from.
    public String textIn(String source) {
        return source.substring(start, end);
    }
}
