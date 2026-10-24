package dev.goldberry.media;

/// Whether a decoder may decode video on the GPU's video engine, for a
/// [dev.goldberry.media.codec.DecoderProvider] that reads it.
///
/// Set it with `MediaPlayer.setHardwareDecoding`; it applies from the next
/// source opened. The built-in decoders are software and do not read it: they
/// are bit-exact by specification, which is what deterministic tests want. The
/// system providers decode on hardware on their own terms. The setting is kept
/// for an application that set it, and for a provider that honours it.
///
/// Read more: [Bringing a codec](https://goldberry.dev/docs/components/media.html#bringing-a-codec).
public enum HardwareDecoding {

    /// Let a provider decode on the platform's video engine where it can, and
    /// fall back to software without the application seeing it.
    AUTO,

    /// Decode in software. What deterministic tests ask for: software decoding
    /// is bit-exact by specification, and a video engine need not be.
    OFF
}
