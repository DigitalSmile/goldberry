package io.github.digitalsmile.goldberry.widgets.data;

import java.util.ArrayList;
import java.util.List;

/// Where the labels go on a logarithmic axis.
///
/// [Ticks] is the wrong algorithm here for the same kind of reason [TimeTicks]
/// is. Wilkinson's scores a labelling on how *round* its numbers are and how
/// evenly they cover the range, and on a log axis those two pull apart
/// completely: `1, 10, 100, 1000` is the only labelling anybody wants and it is,
/// in the value space Wilkinson works in, wildly uneven — three quarters of the
/// axis carries one label.
///
/// So the rungs here are **decades**, and the only questions are how many decades
/// to skip when there are too many, and what to do when there are too few.
///
/// ## Too few decades
///
/// A range of `3…40` is one and a bit decades, and labelling it `1, 10, 100`
/// leaves a chart with two labels on it. So a short range is subdivided by the
/// **1-2-5** mantissas — `2, 5, 10, 20, 50` — which are the subdivisions a reader
/// of a log axis already expects, because they are the ones every log-ruled paper
/// has ever used. Not 1-3 and not every integer: `1, 2, 3, 4, 5, 6, 7, 8, 9, 10`
/// is what a log axis looks like when nobody chose, and the labels crowd into the
/// bottom of each decade where there is least room.
public final class LogTicks {

    private LogTicks() {
    }

    /// The mantissas a short range is subdivided by, in the order they are tried:
    /// decades alone, then halves of a decade, then the full 1-2-5.
    private static final double[][] MANTISSAS = {{1}, {1, 5}, {1, 2, 5}};

    /// The labelling of `min…max` at about `target` labels.
    ///
    /// @param min    the smallest value on the axis — must be positive
    /// @param max    the largest
    /// @param target how many labels to aim for; a preference, as [Ticks]'s is
    /// @throws IllegalArgumentException if either end is not positive
    public static Labelling of(double min, double max, int target) {
        if (!(min > 0) || !(max > 0)) {
            throw new IllegalArgumentException(
                    "a logarithmic axis needs a positive range, and " + min + "…" + max
                            + " is not");
        }
        var low = Math.min(min, max);
        var high = Math.max(min, max);
        var wanted = Math.max(2, target);

        var first = (int) Math.floor(Math.log10(low));
        var last = (int) Math.ceil(Math.log10(high));

        // Enough decades to stand on their own: label every *n*th one, for the
        // smallest n that fits. `1, 100, 10000` is a real log axis; `1, 10, 100,
        // …, 10^12` is a column of numbers nobody can read.
        var decades = last - first + 1;
        if (decades >= wanted) {
            var stride = (int) Math.ceil((double) decades / wanted);
            return new Labelling(powers(first, last, stride, low, high), true);
        }

        // Too few decades to stand alone. Subdivide by whichever set of mantissas
        // comes **nearest** the number of labels asked for, coarser winning a tie.
        //
        // Nearest rather than "the first that reaches the target", which was the
        // first rule here and overshoots: `1…1000` at five labels has four whole
        // decades, and taking the first set to reach five gives `1, 5, 10, 50,
        // 100, 500, 1000` — seven labels, and a decade axis turned into a
        // half-decade one to gain one label it did not need.
        var chosen = MANTISSAS[0];
        var best = Integer.MAX_VALUE;
        for (var mantissas : MANTISSAS) {
            var distance = Math.abs(within(first, last, mantissas, low, high).size() - wanted);
            if (distance < best) {
                best = distance;
                chosen = mantissas;
            }
        }
        var values = within(first, last, chosen, low, high);
        return new Labelling(values, chosen.length == 1);
    }

    /// Every `stride`th power of ten in `first…last` that is inside `low…high`.
    private static List<Double> powers(
            int first, int last, int stride, double low, double high) {

        var out = new ArrayList<Double>();
        for (var exponent = first; exponent <= last; exponent += stride) {
            var value = Math.pow(10, exponent);
            if (value >= low * 0.999999 && value <= high * 1.000001) {
                out.add(value);
            }
        }
        return List.copyOf(out);
    }

    /// Every `mantissa × 10^n` inside `low…high`.
    private static List<Double> within(
            int first, int last, double[] mantissas, double low, double high) {

        var out = new ArrayList<Double>();
        for (var exponent = first; exponent <= last; exponent++) {
            var decade = Math.pow(10, exponent);
            for (var mantissa : mantissas) {
                var value = mantissa * decade;
                if (value >= low * 0.999999 && value <= high * 1.000001) {
                    out.add(value);
                }
            }
        }
        return List.copyOf(out);
    }

    /// Where the labels go.
    ///
    /// @param values     the tick values, in order — possibly empty for a range
    ///                   that contains no round number at all
    /// @param wholeDecades whether every one of them is a power of ten, which is
    ///                   what a reader means by "a log axis"
    public record Labelling(List<Double> values, boolean wholeDecades) {

        public Labelling {
            values = List.copyOf(values);
        }

        /// `value` as a label.
        ///
        /// **Not by a step**, which is what a linear axis takes its decimals
        /// from: on a log axis every label is a different distance from the next
        /// one, so the decimals come from the value's own magnitude. `0.01` needs
        /// two and `1000` needs none, and an axis that gave them all the same
        /// number would read `0.01, 0.10, 1.00, 10.00`.
        public String label(double value) {
            if (value >= 1) {
                return String.format(java.util.Locale.ROOT, "%.0f", value);
            }
            var decimals = Math.min(6, (int) Math.ceil(-Math.log10(value)));
            return String.format(java.util.Locale.ROOT, "%." + decimals + "f", value);
        }
    }
}
