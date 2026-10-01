package dev.goldberry.media.ffi;

import java.math.BigInteger;
import java.time.Duration;
import java.util.Optional;

/// FFmpeg timestamps as [Duration]s.
///
/// A timestamp is a count of `num/den`-second ticks. The conversion is exact, with
/// the result truncated to the nanosecond, and uses no floating point. A golden
/// test that compares frame times must not see a timestamp land one nanosecond
/// either side of a boundary depending on how a double rounded.
public final class Timestamps {

    private static final BigInteger NANOS_PER_SECOND = BigInteger.valueOf(1_000_000_000L);

    private Timestamps() {}

    /// `ticks` of `num/den` seconds, or empty when `ticks` is `noPts` or the time
    /// base is unusable.
    public static Optional<Duration> toDuration(long ticks, int num, int den, long noPts) {
        if (ticks == noPts || num <= 0 || den <= 0 || ticks < 0) {
            return Optional.empty();
        }
        var nanos = BigInteger.valueOf(ticks)
                .multiply(BigInteger.valueOf(num))
                .multiply(NANOS_PER_SECOND)
                .divide(BigInteger.valueOf(den));
        if (nanos.bitLength() >= Long.SIZE) {
            return Optional.empty(); // centuries: a corrupt header rather than a film
        }
        return Optional.of(Duration.ofNanos(nanos.longValueExact()));
    }
}
