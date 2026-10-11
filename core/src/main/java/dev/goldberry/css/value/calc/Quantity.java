package dev.goldberry.css.value.calc;

import java.util.Objects;

/// What a math expression folds to: a number of one kind, and a percentage
/// that only the box it lands on can turn into pixels.
///
/// `calc(100% - 8px)` folds to a length of `-8` with a percentage of `100`.
/// Only a length carries a percentage; a number, an angle and a time have
/// none.
///
/// @param kind    what the value counts
/// @param value   pixels for a length, degrees for an angle, milliseconds for a
///                time, and the number itself otherwise
/// @param percent the percentage part of a length, `50` meaning 50%
public record Quantity(Kind kind, double value, double percent) {

    /// The four things a math expression can be worth.
    public enum Kind {
        NUMBER,
        LENGTH,
        ANGLE,
        TIME
    }

    public Quantity {
        Objects.requireNonNull(kind, "kind");
        if (kind != Kind.LENGTH && percent != 0) {
            throw new IllegalArgumentException("only a length carries a percentage, and a " + kind + " was given one");
        }
    }

    /// A plain number.
    public static Quantity number(double value) {
        return new Quantity(Kind.NUMBER, value, 0);
    }

    /// A length in logical pixels.
    public static Quantity pixels(double value) {
        return new Quantity(Kind.LENGTH, value, 0);
    }

    /// A percentage of whatever a length is a percentage of.
    public static Quantity percentage(double value) {
        return new Quantity(Kind.LENGTH, 0, value);
    }

    /// An angle in degrees.
    public static Quantity degrees(double value) {
        return new Quantity(Kind.ANGLE, value, 0);
    }

    /// A time in milliseconds.
    public static Quantity millis(double value) {
        return new Quantity(Kind.TIME, value, 0);
    }

    /// Whether this is a length with no pixel part: a percentage alone.
    public boolean isPercentOnly() {
        return kind == Kind.LENGTH && value == 0 && percent != 0;
    }

    /// Whether this has a percentage part at all.
    public boolean hasPercent() {
        return percent != 0;
    }

    /// Whether both parts are finite. An expression that overflowed, or divided
    /// by something close enough to zero, is not a value.
    boolean isFinite() {
        return Double.isFinite(value) && Double.isFinite(percent);
    }

    Quantity plus(Quantity other) {
        return new Quantity(kind, value + other.value, percent + other.percent);
    }

    Quantity times(double factor) {
        return new Quantity(kind, value * factor, percent * factor);
    }

    /// The one number two quantities of the same kind can be compared by, or
    /// NaN when they cannot be compared without a basis: one has pixels and the
    /// other a percentage.
    static double comparable(Quantity quantity, boolean byPercent) {
        return byPercent ? quantity.percent : quantity.value;
    }
}
