package dev.goldberry.vector;

import java.util.Optional;
import java.util.regex.Pattern;

/// A document's `viewBox`: the rectangle of its own coordinates that its
/// viewport shows.
///
/// @param x      the left edge, in the document's user units
/// @param y      the top edge
/// @param width  the width; above zero
/// @param height the height; above zero
public record ViewBox(float x, float y, float width, float height) {

    private static final Pattern SEPARATOR = Pattern.compile("[\\s,]+");

    public ViewBox {
        if (!Float.isFinite(x) || !Float.isFinite(y)) {
            throw new IllegalArgumentException("a view box starts at a finite point, not " + x + "," + y);
        }
        if (!(width > 0) || !(height > 0) || !Float.isFinite(width) || !Float.isFinite(height)) {
            throw new IllegalArgumentException(
                    "a view box has a width and a height above zero, not " + width + "x" + height);
        }
    }

    /// The view box `value` spells, as SVG writes one: four numbers apart by
    /// white space or commas. Empty when it is not one, which a document reader
    /// treats as no view box at all.
    public static Optional<ViewBox> parse(String value) {
        var parts = SEPARATOR.splitAsStream(value.strip()).toList();
        if (parts.size() != 4) {
            return Optional.empty();
        }
        try {
            var numbers = new float[4];
            for (var i = 0; i < 4; i++) {
                numbers[i] = Float.parseFloat(parts.get(i));
            }
            if (!(numbers[2] > 0) || !(numbers[3] > 0)) {
                return Optional.empty();
            }
            return Optional.of(new ViewBox(numbers[0], numbers[1], numbers[2], numbers[3]));
        } catch (IllegalArgumentException e) {
            // Not a number, or not a finite one: NumberFormatException is one.
            return Optional.empty();
        }
    }
}
