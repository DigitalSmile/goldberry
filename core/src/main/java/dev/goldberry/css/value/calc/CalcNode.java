package dev.goldberry.css.value.calc;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/// One node of a math expression, as [CalcParser] reads it.
///
/// ```java
/// CalcNode tree = CalcParser.parse(tokens);          // calc(100% - 2 * 4px)
/// Quantity folded = Folding.fold(tree, units);       // -8px + 100%
/// ```
///
/// Sealed, so [Folding] reads it with a `switch` that has no default arm: a
/// function added here is a compile error there until it is folded.
///
/// Subtraction is a [Sum] over a [Negate], and division a [Product] over an
/// [Invert], which is how CSS Values 4 builds the tree too.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#box-and-layout).
public sealed interface CalcNode {

    /// A plain number: `2`, `0.5`, or the constants `e` and `pi`.
    record Plain(double value) implements CalcNode {}

    /// A percentage, `50` meaning 50%.
    record Percentage(double value) implements CalcNode {}

    /// A number with a unit: `8px`, `1.5em`, `90deg`, `200ms`.
    ///
    /// @param unit lowercased, as the tokenizer leaves it
    record Dimension(double value, String unit) implements CalcNode {
        public Dimension {
            Objects.requireNonNull(unit, "unit");
        }
    }

    /// Terms added together.
    record Sum(List<CalcNode> terms) implements CalcNode {
        public Sum {
            terms = List.copyOf(terms);
        }
    }

    /// A term subtracted: `a - b` is a sum of `a` and `-b`.
    record Negate(CalcNode operand) implements CalcNode {
        public Negate {
            Objects.requireNonNull(operand, "operand");
        }
    }

    /// Factors multiplied together.
    record Product(List<CalcNode> factors) implements CalcNode {
        public Product {
            factors = List.copyOf(factors);
        }
    }

    /// A divisor: `a / b` is a product of `a` and `1 / b`. Only a number may be
    /// one.
    record Invert(CalcNode operand) implements CalcNode {
        public Invert {
            Objects.requireNonNull(operand, "operand");
        }
    }

    /// `min(…)`: the least of its arguments.
    record Min(List<CalcNode> arguments) implements CalcNode {
        public Min {
            arguments = List.copyOf(arguments);
        }
    }

    /// `max(…)`: the greatest of its arguments.
    record Max(List<CalcNode> arguments) implements CalcNode {
        public Max {
            arguments = List.copyOf(arguments);
        }
    }

    /// `clamp(min, value, max)`. A bound written `none` is null. When the
    /// minimum is above the maximum the minimum wins, as CSS says.
    record Clamp(
            @Nullable CalcNode min,
            CalcNode value,
            @Nullable CalcNode max) implements CalcNode {
        public Clamp {
            Objects.requireNonNull(value, "value");
        }
    }

    /// `round(strategy, value, step)`. The step is 1 when not written, which
    /// CSS allows for a number only.
    record Round(
            Rounding strategy, CalcNode value, @Nullable CalcNode step) implements CalcNode {
        public Round {
            Objects.requireNonNull(strategy, "strategy");
            Objects.requireNonNull(value, "value");
        }
    }

    /// `mod(a, b)`: the remainder with the sign of `b`.
    record Mod(CalcNode dividend, CalcNode divisor) implements CalcNode {
        public Mod {
            Objects.requireNonNull(dividend, "dividend");
            Objects.requireNonNull(divisor, "divisor");
        }
    }

    /// `rem(a, b)`: the remainder with the sign of `a`.
    record Rem(CalcNode dividend, CalcNode divisor) implements CalcNode {
        public Rem {
            Objects.requireNonNull(dividend, "dividend");
            Objects.requireNonNull(divisor, "divisor");
        }
    }

    /// `abs(a)`, of the same kind as `a`.
    record Abs(CalcNode operand) implements CalcNode {
        public Abs {
            Objects.requireNonNull(operand, "operand");
        }
    }

    /// `sign(a)`: `-1`, `0` or `1`, a number whatever `a` is.
    record Sign(CalcNode operand) implements CalcNode {
        public Sign {
            Objects.requireNonNull(operand, "operand");
        }
    }

    /// How [Round] rounds: CSS's four keywords.
    enum Rounding {
        /// To the nearest multiple, half-way up. The default.
        NEAREST,
        /// Towards positive infinity.
        UP,
        /// Towards negative infinity.
        DOWN,
        /// Towards zero.
        TO_ZERO
    }
}
