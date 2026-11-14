package dev.goldberry.i18n;

import java.util.List;

/// A plural rule's condition, parsed: `v = 0 and i % 10 = 2..4 or …`.
sealed interface PluralCondition {

    /// Whether `number` satisfies it.
    boolean matches(PluralOperands number);

    /// Always true: an `other` rule, which has no condition.
    record Always() implements PluralCondition {
        @Override
        public boolean matches(PluralOperands number) {
            return true;
        }
    }

    /// Any of its parts.
    record Or(List<PluralCondition> parts) implements PluralCondition {
        @Override
        public boolean matches(PluralOperands number) {
            for (var part : parts) {
                if (part.matches(number)) {
                    return true;
                }
            }
            return false;
        }
    }

    /// All of its parts.
    record And(List<PluralCondition> parts) implements PluralCondition {
        @Override
        public boolean matches(PluralOperands number) {
            for (var part : parts) {
                if (!part.matches(number)) {
                    return false;
                }
            }
            return true;
        }
    }

    /// `operand [% modulus] (= | !=) ranges`. A value matches a range only when
    /// it is a whole number inside it, as CLDR says: `n = 2..4` is false for 2.5.
    ///
    /// @param modulus 0 for none
    record Relation(char operand, long modulus, boolean equal, List<Range> ranges) implements PluralCondition {
        @Override
        public boolean matches(PluralOperands number) {
            var value = number.operand(operand);
            if (modulus != 0) {
                value = value % modulus;
            }
            var inside = false;
            for (var range : ranges) {
                if (range.contains(value)) {
                    inside = true;
                    break;
                }
            }
            return inside == equal;
        }
    }

    /// `low..high`, or one value where the two are equal.
    record Range(long low, long high) {
        boolean contains(double value) {
            return value == Math.rint(value) && value >= low && value <= high;
        }
    }
}
