package dev.goldberry.text;

import java.util.Objects;
import java.util.Set;

import dev.goldberry.text.flow.TextDecoration;

/// How one stretch of a paragraph is drawn: its colour, and the rules along it.
///
/// ```java
/// var keyword = new SpanPaint(8, 16, 0xFFEBCB8B, TextDecoration.NONE);
/// paragraph.paint(frame, x, top, width, 0xFFECEFF4, flow, List.of(keyword));
/// ```
///
/// The drawn half of a styled paragraph. The shaped half is the paragraph
/// itself: [Paragraph#join] puts paragraphs shaped in different fonts end to end,
/// and the fonts are fixed once that is done. A colour and a rule change nothing
/// about where a glyph goes, so they ride beside the paragraph rather than in it,
/// and a run that changes colour on hover is drawn again without being shaped
/// again.
///
/// A range of the paragraph's text, not of its glyphs: `start` and `end` are
/// the offsets a caller already holds, and the painter turns them into glyph
/// ranges. Text outside every range is drawn in the colour and the rules the
/// paragraph was painted with.
///
/// Read more: [Text, fonts and icons](https://goldberry.dev/docs/guide/text.html#paragraphs).
///
/// @param start       the first character, as an offset into the paragraph's text
/// @param end         one past the last
/// @param argb        the colour, `0xAARRGGBB`, not premultiplied
/// @param decorations the rules drawn along the stretch in that colour, and
///                    usually none. They **replace** the paragraph's own for this
///                    stretch rather than adding to them
public record SpanPaint(int start, int end, int argb, Set<TextDecoration> decorations) {

    public SpanPaint {
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("a span runs forward from offset 0 or later: " + start + ".." + end);
        }
        // Copied, so a span is a value: a `Box.Text` is compared by equality to
        // decide whether a frame changed, and a set a caller can still mutate
        // would change the answer after the fact.
        decorations = Set.copyOf(Objects.requireNonNull(decorations, "decorations"));
    }

    /// Whether `offset` is inside this stretch.
    public boolean covers(int offset) {
        return offset >= start && offset < end;
    }

    /// This stretch in another colour, its range and its rules kept.
    public SpanPaint argb(int value) {
        return new SpanPaint(start, end, value, decorations);
    }
}
