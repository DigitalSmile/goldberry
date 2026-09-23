package io.github.digitalsmile.goldberry.media.view;

import java.time.Duration;

/// How a player writes a time: `0:07`, `3:05`, `1:02:09`.
///
/// Minutes are not padded and hours appear only when needed, which is what every
/// desktop player does. A time is truncated to the second rather than rounded, so
/// the elapsed label never shows a second that has not finished yet.
public final class MediaTime {

    private MediaTime() {}

    /// `duration` as `m:ss` or `h:mm:ss`. Negative durations are written as zero.
    public static String format(Duration duration) {
        var seconds = Math.max(duration.toSeconds(), 0);
        var hours = seconds / 3600;
        var minutes = (seconds % 3600) / 60;
        var rest = seconds % 60;
        return hours > 0 ? "%d:%02d:%02d".formatted(hours, minutes, rest) : "%d:%02d".formatted(minutes, rest);
    }

    /// The time left, as `-m:ss`: the label at the end of a seek bar.
    public static String remaining(Duration position, Duration duration) {
        var left = duration.minus(position);
        return "-" + format(left.isNegative() ? Duration.ZERO : left);
    }
}
