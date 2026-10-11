package dev.goldberry.example.ui.lookalike.library;

import java.time.Duration;
import java.util.Objects;

/// One song.
///
/// @param id     what a like and a queue remember it by
/// @param title  its name
/// @param artist who plays it
/// @param album  the record it is on
/// @param length how long it plays for
/// @param plays  how many times it has been played, for the popularity column
public record Track(String id, String title, String artist, String album, Duration length, long plays) {

    public Track {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(artist, "artist");
        Objects.requireNonNull(album, "album");
        if (length.isNegative() || length.isZero()) {
            throw new IllegalArgumentException("a track plays for some time, not " + length);
        }
        if (plays < 0) {
            throw new IllegalArgumentException("a play count is never negative, not " + plays);
        }
    }
}
