package dev.goldberry.media;

import java.time.Duration;
import java.util.Objects;

/// A stretch of the presentation, from `start` up to `end`: one of the
/// [PlayerStatus#bufferedRanges()] a seek bar shows as fetched.
///
/// @param start where the stretch begins
/// @param end   where it ends, not before `start`
public record TimeRange(Duration start, Duration end) {

    public TimeRange {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (start.isNegative() || end.compareTo(start) < 0) {
            throw new IllegalArgumentException("not a time range: [" + start + ", " + end + ")");
        }
    }

    /// How long the stretch lasts.
    public Duration length() {
        return end.minus(start);
    }

    /// Whether `position` falls in the stretch.
    public boolean contains(Duration position) {
        return position.compareTo(start) >= 0 && position.compareTo(end) < 0;
    }
}
