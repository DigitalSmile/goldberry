package dev.goldberry.text.flow;

import java.util.Objects;
import java.util.OptionalInt;

/// How a paragraph's decoration lines are drawn. These are CSS's
/// `text-decoration-style`, `text-decoration-color`,
/// `text-decoration-thickness` and `text-underline-offset`.
///
/// ```java
/// var squiggle = DecorationPaint.SOLID.style(DecorationStyle.WAVY).colour(0xFFE5484D);
/// ```
///
/// Which lines are drawn is [TextDecoration]'s question. This one is how they
/// look. The two travel together in [TextDecorations], because CSS propagates
/// a decoration as a whole: the box that wrote `underline wavy red` decides how
/// the line under its children's text looks.
///
/// @param style           the line's pattern
/// @param colour          its colour, `0xAARRGGBB` and not premultiplied, or
///                        empty for the text's own colour, which is CSS's
///                        initial `currentcolor`
/// @param thickness       how thick it is in logical units, or `NaN` for the
///                        face's own thickness (`auto` and `from-font`). Zero
///                        draws no line
/// @param underlineOffset how far below the baseline an underline's top edge
///                        is, in logical units, or `NaN` for the face's own
///                        position (`auto`). It moves no line-through
public record DecorationPaint(DecorationStyle style, OptionalInt colour, double thickness, double underlineOffset) {

    /// A solid line in the text's colour, at the face's own thickness and
    /// position: CSS's initial values.
    public static final DecorationPaint SOLID =
            new DecorationPaint(DecorationStyle.SOLID, OptionalInt.empty(), Double.NaN, Double.NaN);

    public DecorationPaint {
        Objects.requireNonNull(style, "style");
        Objects.requireNonNull(colour, "colour");
        if (thickness < 0 || Double.isInfinite(thickness)) {
            throw new IllegalArgumentException(
                    "a decoration is a finite, non-negative thickness or NaN for the face's own, not " + thickness);
        }
        if (Double.isInfinite(underlineOffset)) {
            throw new IllegalArgumentException("an underline offset is finite or NaN for the face's own");
        }
    }

    /// This, in another pattern.
    public DecorationPaint style(DecorationStyle value) {
        return new DecorationPaint(value, colour, thickness, underlineOffset);
    }

    /// This, in `argb` rather than the text's colour.
    public DecorationPaint colour(int argb) {
        return new DecorationPaint(style, OptionalInt.of(argb), thickness, underlineOffset);
    }

    /// This, in the text's own colour.
    public DecorationPaint textColour() {
        return new DecorationPaint(style, OptionalInt.empty(), thickness, underlineOffset);
    }

    /// This, `value` thick, or `NaN` for the face's own thickness.
    public DecorationPaint thickness(double value) {
        return new DecorationPaint(style, colour, value, underlineOffset);
    }

    /// This, with an underline `value` below the baseline, or `NaN` for the
    /// face's own position.
    public DecorationPaint underlineOffset(double value) {
        return new DecorationPaint(style, colour, thickness, value);
    }

    /// The colour a line is drawn in over text drawn in `textArgb`.
    public int argbOver(int textArgb) {
        return colour.orElse(textArgb);
    }

    /// Whether this is [#SOLID]: nothing differs from how a line was drawn
    /// before a line could be styled. A record compares its `double`
    /// components as `Double.compare` does, so `NaN` equals `NaN` here.
    public boolean isPlain() {
        return equals(SOLID);
    }

    @Override
    public String toString() {
        if (isPlain()) {
            return "solid";
        }
        var text = new StringBuilder(style.cssName());
        colour.ifPresent(argb -> text.append(" #").append(String.format("%08x", argb)));
        if (!Double.isNaN(thickness)) {
            text.append(' ').append(thickness).append("px");
        }
        if (!Double.isNaN(underlineOffset)) {
            text.append(", offset ").append(underlineOffset).append("px");
        }
        return text.toString();
    }
}
