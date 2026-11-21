package dev.goldberry.i18n;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/// A number as CLDR's plural rules see it: not a value but a written number,
/// because `1` and `1.0` are in different categories in many languages.
///
/// @param n the absolute value
/// @param i the integer digits of `n`
/// @param v how many visible fraction digits, trailing zeros included
/// @param w how many visible fraction digits, trailing zeros excluded
/// @param f the visible fraction digits as a number, trailing zeros included
/// @param t the visible fraction digits as a number, trailing zeros excluded
/// @param e the exponent of a compact number (`1.2c6` is 1 200 000, `e` 6), or 0
public record PluralOperands(double n, long i, int v, int w, long f, long t, int e) {

    /// A whole number.
    public static PluralOperands of(long value) {
        var absolute = Math.abs(value);
        return new PluralOperands(absolute, absolute, 0, 0, 0, 0, 0);
    }

    /// A decimal as written: its scale is its visible fraction digits, so
    /// `new BigDecimal("1.50")` has `v` 2.
    public static PluralOperands of(BigDecimal value) {
        Objects.requireNonNull(value, "value");
        return decimal(value.abs(), 0);
    }

    /// A number as CLDR writes its samples: `1`, `1.50`, or a compact `1.2c6`
    /// (also `1.2e6`), whose exponent is an operand of its own.
    ///
    /// @throws NumberFormatException if `text` is not one
    public static PluralOperands parse(String text) {
        Objects.requireNonNull(text, "text");
        var written = text.strip();
        var compact = Math.max(written.indexOf('c'), written.indexOf('e'));
        if (compact < 0) {
            return decimal(new BigDecimal(written).abs(), 0);
        }
        var exponent = Integer.parseInt(written.substring(compact + 1));
        var mantissa = new BigDecimal(written.substring(0, compact)).abs();
        var value = mantissa.movePointRight(exponent);
        return decimal(value.scale() < 0 ? value.setScale(0, RoundingMode.UNNECESSARY) : value, exponent);
    }

    private static PluralOperands decimal(BigDecimal value, int exponent) {
        var integer = value.toBigInteger().longValueExact();
        var visible = Math.max(0, value.scale());
        var fraction = value.subtract(new BigDecimal(integer)).movePointRight(visible);
        var stripped = value.stripTrailingZeros();
        var trimmed = Math.max(0, stripped.scale());
        var fractionTrimmed = stripped.subtract(new BigDecimal(integer)).movePointRight(trimmed);
        return new PluralOperands(
                value.doubleValue(),
                integer,
                visible,
                trimmed,
                fraction.longValueExact(),
                fractionTrimmed.longValueExact(),
                exponent);
    }

    /// The operand CLDR names `letter`: `n`, `i`, `v`, `w`, `f`, `t`, and `e`
    /// or `c` for the exponent.
    double operand(char letter) {
        return switch (letter) {
            case 'n' -> n;
            case 'i' -> i;
            case 'v' -> v;
            case 'w' -> w;
            case 'f' -> f;
            case 't' -> t;
            case 'e', 'c' -> e;
            default -> throw new IllegalArgumentException("no plural operand '" + letter + "'");
        };
    }
}
