package dev.goldberry.media;

/// Whether a decoder may decode video on the platform's video engine.
///
/// Set it with `MediaPlayer.setHardwareDecoding` or the builder; it applies from
/// the next source opened, and every
/// [dev.goldberry.media.codec.DecoderProvider] receives it in its request. The
/// system's decoders honour [#OFF]: GStreamer passes over its hardware
/// decoders (NVDEC, VA-API, V4L2) for its software ones, and VideoToolbox is
/// asked for its software decoder. Media Foundation's decoders are the
/// synchronous software ones either way. The built-in decoders are software,
/// bit-exact by specification, whatever this says.
///
/// [#OFF] is what a player's "disable hardware acceleration" is for: a video
/// driver that draws garbage or fails mid-stream, a machine where the video
/// engine is busy elsewhere, or a test that compares pictures byte for byte.
///
/// Read more: [Bringing a codec](https://goldberry.dev/docs/components/media.html#bringing-a-codec).
public enum HardwareDecoding {

    /// Let a provider decode on the platform's video engine where it can, and
    /// fall back to software without the application seeing it. The default.
    AUTO,

    /// Decode in software. A provider that has only a hardware decoder for a
    /// track says it does not support it, and the next provider is asked.
    OFF
}
