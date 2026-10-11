package dev.goldberry.motion.easing;

import java.math.BigDecimal;

/// How a curve writes its numbers back as CSS: as short as they can be, with no
/// trailing zeros, so `cubic-bezier(0.2, 0, 0, 1)` reads as it was written.
final class Numbers {

    private Numbers() {}

    static String css(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            return Long.toString((long) value);
        }
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    static String percent(double fraction) {
        return css(fraction * 100) + "%";
    }
}
