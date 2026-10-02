package dev.goldberry.media.codec;

/// One decoded unit: a run of audio samples, or one picture.
///
/// **Borrowed.** A frame's memory belongs to the decoder that produced it, and it
/// is valid until that decoder's next [Decoder#receive], [Decoder#flush] or
/// [Decoder#close]. The Engine converts or uploads a frame before it asks for the
/// next one. A caller that wants to keep a frame copies it.
///
/// Sealed, so a consumer switches over the two kinds and the compiler checks it.
///
/// Read more: [Bringing a codec](https://goldberry.dev/docs/components/media.html#bringing-a-codec).
public sealed interface Frame permits AudioFrame, VideoFrame {

    /// Marks a frame with no presentation time.
    long NO_PTS = Long.MIN_VALUE;

    /// When this frame is presented, in nanoseconds of stream time, or [#NO_PTS].
    long ptsNanos();
}
