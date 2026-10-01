package dev.goldberry.media;

import java.util.Objects;

import dev.goldberry.media.io.Source;

/// Where the subtitles showing come from ([PlayerStatus#subtitles()]): a
/// subtitle track of the source, or a file loaded beside it.
public sealed interface SubtitleSource {

    /// A subtitle track of the source.
    ///
    /// @param track the track
    record Embedded(Track track) implements SubtitleSource {
        public Embedded {
            Objects.requireNonNull(track, "track");
        }
    }

    /// A SubRip or WebVTT file loaded with [MediaPlayer#loadSubtitles].
    ///
    /// @param file where it was read from
    record External(Source file) implements SubtitleSource {
        public External {
            Objects.requireNonNull(file, "file");
        }
    }
}
