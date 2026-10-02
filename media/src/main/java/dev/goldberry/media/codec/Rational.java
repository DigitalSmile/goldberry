package dev.goldberry.media.codec;

import java.math.BigInteger;
import java.time.Duration;

/// A time base: timestamps count `num/den`-second ticks.
///
/// Conversions are exact, with no floating point, and truncate to the
/// nanosecond. A golden test of presentation times must not see a frame land on
/// the other side of a boundary because a double rounded.
///
/// @param num the numerator, positive
/// @param den the denominator, positive
///
/// Read more: [Bringing a codec](https://goldberry.dev/docs/components/media.html#bringing-a-codec).
public record Rational(int num, int den) {

    private static final BigInteger NANOS_PER_SECOND = BigInteger.valueOf(1_000_000_000L);

    /// Microseconds: `AV_TIME_BASE`, the unit of a container's duration.
    public static final Rational MICROSECONDS = new Rational(1, 1_000_000);

    public Rational {
        if (num <= 0 || den <= 0) {
            throw new IllegalArgumentException("a time base is positive: " + num + "/" + den);
        }
    }

    /// `ticks` of this time base in nanoseconds, truncated. Saturates at the ends
    /// of a `long` rather than overflowing.
    public long toNanos(long ticks) {
        var nanos = BigInteger.valueOf(ticks)
                .multiply(BigInteger.valueOf(num))
                .multiply(NANOS_PER_SECOND)
                .divide(BigInteger.valueOf(den));
        if (nanos.bitLength() >= Long.SIZE) {
            return nanos.signum() < 0 ? Long.MIN_VALUE + 1 : Long.MAX_VALUE;
        }
        return nanos.longValueExact();
    }

    /// `ticks` of this time base as a [Duration].
    public Duration toDuration(long ticks) {
        return Duration.ofNanos(toNanos(ticks));
    }

    /// The number of whole ticks in `nanos` nanoseconds, truncated toward zero:
    /// the inverse of [#toNanos].
    public long fromNanos(long nanos) {
        return BigInteger.valueOf(nanos)
                .multiply(BigInteger.valueOf(den))
                .divide(BigInteger.valueOf(num).multiply(NANOS_PER_SECOND))
                .longValue();
    }
}
