package dev.goldberry.media;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/// A named stretch of a presentation: a film's scene, an audiobook's chapter, a
/// podcast's segment.
///
/// Read from what the container carries: Matroska's `Chapters`, MP4's chapter
/// track or Nero `chpl` list, an MP3's ID3v2 `CHAP` frames, and the
/// `CHAPTERnnn` comments of Ogg and FLAC. [MediaInfo#chapters()] lists them in
/// order of their starts.
///
/// @param start where it starts, from the presentation's beginning
/// @param end   where it ends: as the container states it, else where the next
///              one starts, else where the presentation ends. Empty only for the
///              last chapter of a source whose length is unknown
/// @param title what the container calls it, when it calls it anything
///
/// Read more: [A player](https://goldberry.dev/docs/components/media.html#a-player).
public record Chapter(Duration start, Optional<Duration> end, Optional<String> title) {

    public Chapter {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        Objects.requireNonNull(title, "title");
        if (start.isNegative()) {
            throw new IllegalArgumentException("a chapter starts at " + start);
        }
        if (end.isPresent() && end.get().compareTo(start) < 0) {
            throw new IllegalArgumentException("a chapter ends at " + end.get() + ", before its start " + start);
        }
        title = title.filter(text -> !text.isBlank());
    }

    /// A chapter from `start` whose end is not stated.
    public Chapter(Duration start, String title) {
        this(start, Optional.empty(), Optional.of(title));
    }

    /// Whether `position` is in this chapter: at or after its start, and before
    /// its end when it has one.
    public boolean contains(Duration position) {
        return position.compareTo(start) >= 0
                && end.map(stop -> position.compareTo(stop) < 0).orElse(true);
    }
}
