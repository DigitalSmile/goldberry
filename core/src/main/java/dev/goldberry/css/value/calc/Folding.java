package dev.goldberry.css.value.calc;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.value.calc.CalcNode.Abs;
import dev.goldberry.css.value.calc.CalcNode.Clamp;
import dev.goldberry.css.value.calc.CalcNode.Dimension;
import dev.goldberry.css.value.calc.CalcNode.Invert;
import dev.goldberry.css.value.calc.CalcNode.Max;
import dev.goldberry.css.value.calc.CalcNode.Min;
import dev.goldberry.css.value.calc.CalcNode.Mod;
import dev.goldberry.css.value.calc.CalcNode.Negate;
import dev.goldberry.css.value.calc.CalcNode.Percentage;
import dev.goldberry.css.value.calc.CalcNode.Plain;
import dev.goldberry.css.value.calc.CalcNode.Product;
import dev.goldberry.css.value.calc.CalcNode.Rem;
import dev.goldberry.css.value.calc.CalcNode.Round;
import dev.goldberry.css.value.calc.CalcNode.Sign;
import dev.goldberry.css.value.calc.CalcNode.Sum;

/// Folds a [CalcNode] tree to one [Quantity], when a style is computed.
///
/// ```java
/// Quantity q = Folding.fold(tree, (value, unit) -> lengths.of(value, unit));
/// ```
///
/// Everything is known by then except what a percentage is of, so a tree folds
/// to a number of pixels (or degrees, or milliseconds) plus a percentage. That
/// is exact for sums and products. `min()`, `max()`, `clamp()` and the rest
/// compare their arguments, and a pixel count cannot be compared with a
/// percentage of a box that has not been laid out: those fold when every
/// argument is in pixels, or every one a percentage, and otherwise do not.
///
/// A tree does not fold, and the declaration is dropped, when:
///
/// - two terms of a sum are of different kinds (`1px + 2`, `1px + 1s`);
/// - a product multiplies two quantities that are not numbers (`2px * 3px`);
/// - something is divided by zero, or by anything but a number;
/// - a `mod()`, `rem()` or `round()` step is zero;
/// - the result is not finite.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#box-and-layout).
public final class Folding {

    private Folding() {}

    /// What a dimension is worth: the length reader's answer for `em`, `vw` or
    /// `ch`, or null for a unit it does not know.
    @FunctionalInterface
    public interface Units {

        /// `value` in `unit`, or null when `unit` is not one.
        ///
        /// @param unit lowercased
        @Nullable
        Quantity of(double value, String unit);
    }

    /// `node` folded, or null when it does not fold.
    public static @Nullable Quantity fold(CalcNode node, Units units) {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(units, "units");
        var folded = folded(node, units);
        return folded != null && folded.isFinite() ? folded : null;
    }

    private static @Nullable Quantity folded(CalcNode node, Units units) {
        return switch (node) {
            case Plain(var value) -> Quantity.number(value);
            case Percentage(var value) -> Quantity.percentage(value);
            case Dimension(var value, var unit) -> units.of(value, unit);
            case Sum(var terms) -> sum(terms, units);
            case Negate(var operand) -> {
                var folded = folded(operand, units);
                yield folded == null ? null : folded.times(-1);
            }
            case Product(var factors) -> product(factors, units);
            case Invert(var operand) -> {
                var folded = folded(operand, units);
                yield folded == null || folded.kind() != Quantity.Kind.NUMBER || folded.value() == 0
                        ? null
                        : Quantity.number(1 / folded.value());
            }
            case Min(var arguments) -> extreme(arguments, units, -1);
            case Max(var arguments) -> extreme(arguments, units, 1);
            case Clamp(var min, var value, var max) -> clamp(min, value, max, units);
            case Round(var strategy, var value, var step) -> round(strategy, value, step, units);
            case Mod(var dividend, var divisor) -> remainder(dividend, divisor, units, true);
            case Rem(var dividend, var divisor) -> remainder(dividend, divisor, units, false);
            case Abs(var operand) -> {
                var scalar = Scalars.of(List.of(operand), units);
                yield scalar == null ? null : scalar.rebuild(Math.abs(scalar.values()[0]));
            }
            case Sign(var operand) -> {
                var scalar = Scalars.of(List.of(operand), units);
                yield scalar == null ? null : Quantity.number(Math.signum(scalar.values()[0]));
            }
        };
    }

    private static @Nullable Quantity sum(List<CalcNode> terms, Units units) {
        if (terms.isEmpty()) {
            return null;
        }
        Quantity total = null;
        for (var term : terms) {
            var folded = folded(term, units);
            if (folded == null || (total != null && folded.kind() != total.kind())) {
                return null;
            }
            total = total == null ? folded : total.plus(folded);
        }
        return total;
    }

    /// The factors multiplied: numbers scale, and at most one may be anything
    /// else.
    private static @Nullable Quantity product(List<CalcNode> factors, Units units) {
        var scale = 1.0;
        Quantity dimensioned = null;
        for (var factor : factors) {
            var folded = folded(factor, units);
            if (folded == null) {
                return null;
            }
            if (folded.kind() == Quantity.Kind.NUMBER) {
                scale *= folded.value();
            } else if (dimensioned == null) {
                dimensioned = folded;
            } else {
                return null;
            }
        }
        return dimensioned == null ? Quantity.number(scale) : dimensioned.times(scale);
    }

    /// `min()` (sign -1) or `max()` (sign 1).
    private static @Nullable Quantity extreme(List<CalcNode> arguments, Units units, int sign) {
        var scalars = Scalars.of(arguments, units);
        if (scalars == null) {
            return null;
        }
        var best = scalars.values()[0];
        for (var value : scalars.values()) {
            if (Double.compare(value, best) * sign > 0) {
                best = value;
            }
        }
        return scalars.rebuild(best);
    }

    /// `max(min, min(value, max))`, so that a minimum above the maximum wins.
    private static @Nullable Quantity clamp(
            @Nullable CalcNode min, CalcNode value, @Nullable CalcNode max, Units units) {
        var nodes = new ArrayList<CalcNode>(3);
        nodes.add(value);
        if (min != null) {
            nodes.add(min);
        }
        if (max != null) {
            nodes.add(max);
        }
        var scalars = Scalars.of(nodes, units);
        if (scalars == null) {
            return null;
        }
        var values = scalars.values();
        var result = values[0];
        var next = 1;
        var low = min == null ? Double.NEGATIVE_INFINITY : values[next++];
        var high = max == null ? Double.POSITIVE_INFINITY : values[next];
        result = Math.max(low, Math.min(result, high));
        return scalars.rebuild(result);
    }

    private static @Nullable Quantity round(
            CalcNode.Rounding strategy, CalcNode value, @Nullable CalcNode step, Units units) {
        var nodes = step == null ? List.of(value) : List.of(value, step);
        var scalars = Scalars.of(nodes, units);
        if (scalars == null) {
            return null;
        }
        // A step may be left out only where 1 means something: for a number.
        if (step == null && scalars.kind() != Quantity.Kind.NUMBER) {
            return null;
        }
        var a = scalars.values()[0];
        var b = step == null ? 1 : Math.abs(scalars.values()[1]);
        if (b == 0) {
            return null;
        }
        var ratio = a / b;
        var rounded = switch (strategy) {
            case NEAREST -> Math.floor(ratio + 0.5);
            case UP -> Math.ceil(ratio);
            case DOWN -> Math.floor(ratio);
            case TO_ZERO -> ratio < 0 ? Math.ceil(ratio) : Math.floor(ratio);
        };
        return scalars.rebuild(rounded * b);
    }

    /// `mod()` when `floored`, its remainder taking the divisor's sign, and
    /// `rem()` otherwise, taking the dividend's.
    private static @Nullable Quantity remainder(CalcNode dividend, CalcNode divisor, Units units, boolean floored) {
        var scalars = Scalars.of(List.of(dividend, divisor), units);
        if (scalars == null) {
            return null;
        }
        var a = scalars.values()[0];
        var b = scalars.values()[1];
        if (b == 0) {
            return null;
        }
        var quotient = floored ? Math.floor(a / b) : (a / b < 0 ? Math.ceil(a / b) : Math.floor(a / b));
        return scalars.rebuild(a - b * quotient);
    }

    /// Arguments of one kind reduced to comparable numbers: their pixels, or
    /// their percentages when every one is a percentage alone.
    ///
    /// A class rather than a record because it holds an array, which a
    /// record would compare by identity.
    private static final class Scalars {

        private final Quantity.Kind kind;
        private final double[] values;

        /// Whether [#values] are percentages.
        private final boolean byPercent;

        private Scalars(Quantity.Kind kind, double[] values, boolean byPercent) {
            this.kind = kind;
            this.values = values;
            this.byPercent = byPercent;
        }

        Quantity.Kind kind() {
            return kind;
        }

        double[] values() {
            return values;
        }

        /// The arguments folded and made comparable, or null when they are of
        /// different kinds, or mix pixels with percentages.
        static @Nullable Scalars of(List<CalcNode> nodes, Units units) {
            if (nodes.isEmpty()) {
                return null;
            }
            var folded = new ArrayList<Quantity>(nodes.size());
            for (var node : nodes) {
                var quantity = folded(node, units);
                if (quantity == null
                        || (!folded.isEmpty()
                                && quantity.kind() != folded.getFirst().kind())) {
                    return null;
                }
                folded.add(quantity);
            }
            var anyPercent = folded.stream().anyMatch(Quantity::hasPercent);
            var anyPixels = folded.stream().anyMatch(q -> q.value() != 0);
            // A percentage and some pixels cannot be compared until the box
            // that the percentage is of has a size.
            if (anyPercent && anyPixels) {
                return null;
            }
            var values = new double[folded.size()];
            for (var i = 0; i < values.length; i++) {
                values[i] = Quantity.comparable(folded.get(i), anyPercent);
            }
            return new Scalars(folded.getFirst().kind(), values, anyPercent);
        }

        /// `value` as a quantity of this kind, in the same terms the arguments
        /// were compared in.
        Quantity rebuild(double value) {
            return byPercent ? Quantity.percentage(value) : new Quantity(kind, value, 0);
        }
    }
}
