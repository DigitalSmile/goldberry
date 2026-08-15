package io.github.digitalsmile.goldberry.text;

import java.util.List;

/// A paragraph broken into lines at one particular width.
///
/// This is what a Yoga measure function reports: `width` and `height` are the
/// two numbers that go back into `YGSize`, and the lines are what a paint pass
/// then draws without re-deciding anything.
///
/// @param lines  the lines, in order
/// @param width  the widest line, in logical units — **not** the width it was
///               asked to fit in. A paragraph that wraps well before the
///               available width should not claim the space it did not use, or
///               a centred parent would centre the gap
/// @param height the total height in logical units: one line height per line,
///               including blank ones
public record TextLayout(List<TextLine> lines, double width, double height) {

    public TextLayout {
        lines = List.copyOf(lines);
        if (!Double.isFinite(width) || width < 0) {
            throw new IllegalArgumentException("a layout's width must be finite and non-negative: " + width);
        }
        if (!Double.isFinite(height) || height < 0) {
            throw new IllegalArgumentException("a layout's height must be finite and non-negative: " + height);
        }
    }

    /// How many lines the text wrapped into.
    public int lineCount() {
        return lines.size();
    }

    /// Whether there is nothing to draw.
    public boolean isEmpty() {
        return lines.isEmpty();
    }
}
