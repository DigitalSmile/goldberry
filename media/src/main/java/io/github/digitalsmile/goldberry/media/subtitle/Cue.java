package io.github.digitalsmile.goldberry.media.subtitle;

import java.time.Duration;
import java.util.Objects;

/// One subtitle: text shown from `start` until `end`.
///
/// The text is plain, and that is a decision (`docs/goldberry-media.md` §6):
/// italics, colours, positions and karaoke timing are taken out, and what is
/// left is drawn by Goldberry's own text stack, in the theme's type. Lines are
/// separated by `\n`.
///
/// @param start when it appears, in presentation time
/// @param end   when it goes, after `start`
/// @param text  what it says, one or more lines, never blank
public record Cue(Duration start, Duration end, String text) {

    public Cue {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        Objects.requireNonNull(text, "text");
        if (end.compareTo(start) <= 0) {
            throw new IllegalArgumentException("a cue ends after it starts: " + start + " to " + end);
        }
        if (text.isBlank()) {
            throw new IllegalArgumentException("a cue says something");
        }
    }

    /// Whether the cue is showing at `position`: from its start, up to but not
    /// including its end.
    public boolean showsAt(Duration position) {
        return position.compareTo(start) >= 0 && position.compareTo(end) < 0;
    }
}
