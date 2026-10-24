package dev.goldberry.media.container.mp4;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dev.goldberry.media.Chapter;

/// The two ways an MP4 states chapters.
///
/// - **A chapter track** (QuickTime's, which Apple's players and FFmpeg's
///   muxer write): a `text` track that another track's `tref/chap` names, one
///   sample a chapter, timed by the sample table. A sample is a 16-bit length
///   and the title, UTF-8 or UTF-16 with a byte-order mark, and may be followed
///   by boxes that style it.
/// - **Nero's `chpl`** in `moov/udta`: a count and a list of starts in units of
///   100 ns, each with a title of up to 255 bytes. No ends.
///
/// The chapter track wins where a file has both, since it states the ends.
final class Mp4Chapters {

    /// Nero's unit: a start is a count of 100 ns.
    private static final long NANOS_PER_UNIT = 100;

    private Mp4Chapters() {}

    /// The chapters of a `chpl` box's payload, from its version byte on. As many
    /// as were whole.
    static List<Chapter> chpl(byte[] payload) {
        if (payload.length < 5) {
            return List.of();
        }
        var version = payload[0] & 0xFF;
        // Version 1 has four more reserved bytes before the count.
        var at = version == 0 ? 4 : 8;
        if (at >= payload.length) {
            return List.of();
        }
        var count = payload[at++] & 0xFF;
        var chapters = new ArrayList<Chapter>(count);
        for (var i = 0; i < count && at + 9 <= payload.length; i++) {
            var units = 0L;
            for (var b = 0; b < 8; b++) {
                units = units << 8 | (payload[at + b] & 0xFFL);
            }
            at += 8;
            var length = payload[at++] & 0xFF;
            if (at + length > payload.length || units < 0 || units > Long.MAX_VALUE / NANOS_PER_UNIT) {
                break;
            }
            var title = new String(payload, at, length, StandardCharsets.UTF_8);
            at += length;
            chapters.add(new Chapter(Duration.ofNanos(units * NANOS_PER_UNIT), Optional.empty(), Optional.of(title)));
        }
        return List.copyOf(chapters);
    }

    /// The title a chapter track's sample carries, or empty for a sample too
    /// short to hold its own length.
    static Optional<String> text(byte[] sample) {
        if (sample.length < 2) {
            return Optional.empty();
        }
        var length = Math.min((sample[0] & 0xFF) << 8 | (sample[1] & 0xFF), sample.length - 2);
        if (length >= 2 && (sample[2] & 0xFF) == 0xFE && (sample[3] & 0xFF) == 0xFF) {
            return Optional.of(new String(sample, 4, length - 2, StandardCharsets.UTF_16BE));
        }
        if (length >= 2 && (sample[2] & 0xFF) == 0xFF && (sample[3] & 0xFF) == 0xFE) {
            return Optional.of(new String(sample, 4, length - 2, StandardCharsets.UTF_16LE));
        }
        return Optional.of(new String(sample, 2, length, StandardCharsets.UTF_8));
    }
}
