package dev.goldberry.media;

/// How exactly a [MediaPlayer#seek(java.time.Duration, SeekMode)] lands
/// (`docs/goldberry-media.md` §3, "Seeking").
public enum SeekMode {

    /// The first sample heard and the first picture shown are the ones at the
    /// target: decoding starts at the keyframe before it, and everything decoded
    /// before the target is discarded. What a click on the seek bar, a key, and
    /// the release of a drag ask for.
    ACCURATE,

    /// The picture shown is the keyframe the demuxer landed on, which is at or
    /// before the target. Nothing is decoded past it while paused, so a scrub is
    /// as fast as the keyframes are near. What a seek bar asks for while it is
    /// being dragged.
    KEYFRAME
}
