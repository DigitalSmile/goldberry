package dev.goldberry.media.engine;

import java.time.Duration;
import java.util.OptionalLong;

import dev.goldberry.media.MediaInfo;

/// How far ahead of the demuxer a network source fetches, in bytes, chosen
/// from the stream's own bitrate rather than fixed.
///
/// A fixed byte count is a different stretch of time for every stream: the
/// eight megabytes `HttpIO` starts with are eight minutes of a podcast and
/// three seconds of a 20 Mbit/s film, which a jittery connection drains
/// before the next request answers. Once a source is open its mean bitrate is
/// known, its length over its duration, and the window becomes [#AHEAD] of
/// it. The source keeps it between its own floor, what it was opened with,
/// and what its cache leaves room for, so a slow stream fetches no less than
/// before and a fast one never more than the cache holds.
final class PrefetchWindow {

    /// The stretch of the stream to have fetched ahead of the demuxer.
    static final Duration AHEAD = Duration.ofSeconds(30);

    private PrefetchWindow() {}

    /// The bytes of [#AHEAD] at the mean bitrate of `info`'s source, `size`
    /// bytes long. Empty when either is unknown, or the source is live.
    static OptionalLong bytes(MediaInfo info, OptionalLong size) {
        var duration = info.duration();
        if (info.live()
                || size.isEmpty()
                || duration.isEmpty()
                || !duration.get().isPositive()) {
            return OptionalLong.empty();
        }
        var bytesPerSecond = (double) size.getAsLong() * 1e9 / duration.get().toNanos();
        var ahead = bytesPerSecond * AHEAD.toNanos() / 1e9;
        return ahead >= Long.MAX_VALUE ? OptionalLong.of(Long.MAX_VALUE) : OptionalLong.of((long) Math.ceil(ahead));
    }
}
