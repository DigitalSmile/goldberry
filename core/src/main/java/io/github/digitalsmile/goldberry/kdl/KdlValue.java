package io.github.digitalsmile.goldberry.kdl;

import java.util.Objects;

/// One KDL value: an argument, or the right-hand side of a property.
///
/// Sealed, so the switch that turns markup into a widget stops compiling when a
/// kind is added rather than silently taking a default branch.
public sealed interface KdlValue {

    /// A quoted or raw string.
    record Str(String value) implements KdlValue {
        public Str {
            Objects.requireNonNull(value, "value");
        }

        @Override
        public String toString() {
            return "\"" + value + "\"";
        }
    }

    /// A number.
    ///
    /// Held as a `double` because KDL does not distinguish integers from
    /// decimals and a widget attribute is as likely to be `1.5` as `720`.
    /// [#asInt()] is where a caller that needs a whole number says so.
    record Num(double value) implements KdlValue {

        /// This number as an `int`.
        ///
        /// @throws KdlSyntaxException if it is not a whole number in range —
        ///         `width=7.5` is an author error, and silently truncating it is
        ///         how a window ends up one pixel narrow with no explanation
        public int asInt() {
            if (value != Math.rint(value) || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
                throw new KdlSyntaxException(value + " is not a whole number", 0, 0);
            }
            return (int) value;
        }

        @Override
        public String toString() {
            return value == Math.rint(value) && Math.abs(value) < 1e15
                    ? String.valueOf((long) value)
                    : String.valueOf(value);
        }
    }

    /// `#true` or `#false`.
    record Bool(boolean value) implements KdlValue {
        @Override
        public String toString() {
            return value ? "#true" : "#false";
        }
    }

    /// `#null`.
    ///
    /// An enum rather than a record so there is exactly one of it and it
    /// compares by identity.
    enum Null implements KdlValue {
        NULL;

        @Override
        public String toString() {
            return "#null";
        }
    }

    static KdlValue of(String value) {
        return new Str(value);
    }

    static KdlValue of(double value) {
        return new Num(value);
    }

    static KdlValue of(boolean value) {
        return new Bool(value);
    }

    /// This value as a string, whatever kind it is — what an attribute like
    /// `class="a b"` wants, and what a diagnostic wants.
    default String asString() {
        return this instanceof Str str ? str.value() : toString();
    }
}
