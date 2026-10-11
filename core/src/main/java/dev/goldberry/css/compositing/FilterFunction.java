package dev.goldberry.css.compositing;

import java.util.List;

/// One function of a `filter` list.
///
/// ```css
/// .disabled { filter: grayscale(1) opacity(0.6) }
/// .lifted   { filter: drop-shadow(0 2px 4px rgb(0 0 0 / 30%)) }
/// .frosted  { filter: blur(6px) saturate(140%) }
/// ```
///
/// Applied in the order written, to the box and everything under it drawn as
/// one picture. The colour functions work on the picture's sRGB values, as
/// browsers draw them; `blur()` and `drop-shadow()` reach past the box by about
/// three times their radius.
///
/// Sealed, so the filter pass reads it with a `switch` that has no default arm.
public sealed interface FilterFunction {

    /// How far this function draws past the picture it is given, on each
    /// side, in logical pixels: `left`, `top`, `right`, `bottom`.
    double[] reach();

    /// What three standard deviations of a blur `radius` reach: where a
    /// Gaussian has all but a few thousandths of its weight.
    private static double fade(double radius) {
        return 3 * radius;
    }

    /// `blur(radius)`: a Gaussian whose standard deviation is the radius.
    record Blur(double radius) implements FilterFunction {
        public Blur {
            requireNonNegative(radius, "a blur radius");
        }

        @Override
        public double[] reach() {
            var fade = fade(radius);
            return new double[] {fade, fade, fade, fade};
        }
    }

    /// `brightness(amount)`: every channel multiplied; 1 changes nothing.
    record Brightness(double amount) implements FilterFunction, ColourFunction {
        public Brightness {
            requireNonNegative(amount, "a brightness");
        }
    }

    /// `contrast(amount)`: every channel pushed away from the middle; 1
    /// changes nothing.
    record Contrast(double amount) implements FilterFunction, ColourFunction {
        public Contrast {
            requireNonNegative(amount, "a contrast");
        }
    }

    /// `grayscale(amount)`: 0 to 1 of the way to grey.
    record Grayscale(double amount) implements FilterFunction, ColourFunction {
        public Grayscale {
            amount = unit(amount, "a grayscale");
        }
    }

    /// `hue-rotate(degrees)`.
    record HueRotate(double degrees) implements FilterFunction, ColourFunction {}

    /// `invert(amount)`: 0 to 1 of the way to the opposite colour.
    record Invert(double amount) implements FilterFunction, ColourFunction {
        public Invert {
            amount = unit(amount, "an invert");
        }
    }

    /// `opacity(amount)`: 0 to 1, as the property, but inside the filter list.
    record Opacity(double amount) implements FilterFunction, ColourFunction {
        public Opacity {
            amount = unit(amount, "an opacity");
        }
    }

    /// `saturate(amount)`: 0 is grey, 1 changes nothing, more is more vivid.
    record Saturate(double amount) implements FilterFunction, ColourFunction {
        public Saturate {
            requireNonNegative(amount, "a saturation");
        }
    }

    /// `sepia(amount)`: 0 to 1 of the way to sepia.
    record Sepia(double amount) implements FilterFunction, ColourFunction {
        public Sepia {
            amount = unit(amount, "a sepia");
        }
    }

    /// `drop-shadow(dx dy blur colour)`: the picture's own shape, blurred,
    /// coloured and offset, drawn under it.
    ///
    /// @param dx     across, in logical pixels
    /// @param dy     down
    /// @param radius the blur's standard deviation
    /// @param argb   the shadow's colour, `currentcolor` when none was written
    record DropShadow(double dx, double dy, double radius, int argb) implements FilterFunction {
        public DropShadow {
            if (!Double.isFinite(dx) || !Double.isFinite(dy)) {
                throw new IllegalArgumentException("a shadow's offset is finite, not " + dx + ", " + dy);
            }
            requireNonNegative(radius, "a shadow's blur");
        }

        @Override
        public double[] reach() {
            var fade = fade(radius);
            return new double[] {
                Math.max(0, fade - dx), Math.max(0, fade - dy), Math.max(0, fade + dx), Math.max(0, fade + dy)
            };
        }
    }

    /// A function that changes each pixel's colour and nothing else, so draws
    /// nowhere past the picture.
    sealed interface ColourFunction extends FilterFunction {
        @Override
        default double[] reach() {
            return new double[4];
        }
    }

    /// How far `filters`, applied in order, draw past the picture on each side.
    static double[] reach(List<FilterFunction> filters) {
        var total = new double[4];
        for (var filter : filters) {
            var reach = filter.reach();
            for (var side = 0; side < 4; side++) {
                total[side] += reach[side];
            }
        }
        return total;
    }

    private static void requireNonNegative(double value, String what) {
        if (!(value >= 0) || !Double.isFinite(value)) {
            throw new IllegalArgumentException(what + " is finite and not negative, not " + value);
        }
    }

    /// `value` in 0 to 1: CSS clamps the amounts past 100%.
    private static double unit(double value, String what) {
        requireNonNegative(value, what);
        return Math.min(1, value);
    }
}
