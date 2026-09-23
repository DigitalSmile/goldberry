package io.github.digitalsmile.goldberry.media.codec;

/// What kind of stream a track is.
public enum MediaType {
    /// Pictures: a film, or a cover image embedded in an audio file.
    VIDEO,
    /// Sound.
    AUDIO,
    /// Timed text: SubRip, WebVTT, ASS, MP4's `tx3g`.
    SUBTITLE,
    /// A file carried inside the container, such as a font a Matroska subtitle track
    /// wants. Not played.
    ATTACHMENT,
    /// Anything else a container can hold: timecodes, chapters, or streams nothing
    /// here understands. Not played.
    DATA,
}
