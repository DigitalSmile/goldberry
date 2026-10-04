package dev.goldberry.media.codec;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Optional;

/// How many pictures a video track shows a second, as the fraction the container
/// or the codec states it: `60/1`, or NTSC's `30000/1001` (29.97 a second),
/// exactly.
///
/// ```java
/// probe.defaultTrack(MediaType.VIDEO)
///         .map(Track::params)
///         .flatMap(params -> params instanceof TrackParams.Video video ? video.frameRate() : Optional.empty())
///         .ifPresent(rate -> System.out.println(rate + " = " + rate.perSecond() + " fps"));
/// ```
///
/// @param num pictures, positive
/// @param den in this many seconds, positive
///
/// Read more: [A player](https://goldberry.dev/docs/components/media.html#a-player).
public record FrameRate(int num, int den) {

    private static final BigInteger NANOS_PER_SECOND = BigInteger.valueOf(1_000_000_000L);

    public FrameRate {
        if (num <= 0 || den <= 0) {
            throw new IllegalArgumentException("a frame rate is positive: " + num + "/" + den);
        }
    }

    /// The rate `num/den` when both are positive, as FFmpeg writes a known one;
    /// empty for its `0/0`, which is an unknown one.
    public static Optional<FrameRate> known(int num, int den) {
        return num > 0 && den > 0 ? Optional.of(new FrameRate(num, den)) : Optional.empty();
    }

    /// Pictures a second, as a `double`: `59.94005994005994` for `60000/1001`.
    public double perSecond() {
        return (double) num / den;
    }

    /// How long one picture shows, truncated to the nanosecond: 16,666,666 ns at
    /// 60 a second.
    public Duration frameDuration() {
        return Duration.ofNanos(BigInteger.valueOf(den)
                .multiply(NANOS_PER_SECOND)
                .divide(BigInteger.valueOf(num))
                .longValueExact());
    }

    /// How many pictures `duration` holds at this rate, to the nearest: 1,200 in
    /// 20 seconds at 60 a second. For a container that records a track's length
    /// and not its count of pictures, as WebM does.
    public long framesIn(Duration duration) {
        var nanos = BigInteger.valueOf(duration.getSeconds())
                .multiply(NANOS_PER_SECOND)
                .add(BigInteger.valueOf(duration.getNano()));
        var pictures = new BigDecimal(nanos.multiply(BigInteger.valueOf(num)));
        var unit = new BigDecimal(NANOS_PER_SECOND.multiply(BigInteger.valueOf(den)));
        return pictures.divide(unit, 0, RoundingMode.HALF_UP).longValueExact();
    }

    /// `num/den`, as FFmpeg writes a rate: `60/1`.
    @Override
    public String toString() {
        return num + "/" + den;
    }
}
