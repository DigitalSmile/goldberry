package io.github.digitalsmile.goldberry.media;

/// Whether the built-in decoder may decode video on the GPU's video engine
/// (`docs/goldberry-media.md` §3, "Fallback ladder"; ADR-0470).
///
/// Only the built-in FFmpeg decoder reads it. A
/// [io.github.digitalsmile.goldberry.media.codec.DecoderProvider] that decodes on
/// hardware does so on its own terms.
public enum HardwareDecoding {

    /// Decode on the platform's video engine where this build and the machine
    /// can: VideoToolbox on macOS, D3D11 on Windows, VAAPI on Linux, for VP9 and
    /// AV1. Every picture is copied back to system memory (NV12, or P010 for
    /// 10-bit), so it is presented exactly like a software one.
    ///
    /// Anything that goes wrong falls back to software without the application
    /// seeing it (S4). No device, or one that cannot decode the codec, means
    /// software from the start. A device that fails mid-stream means the software
    /// decoder takes over from the keyframe before the position.
    AUTO,

    /// Decode in software. What deterministic tests ask for: software decoding
    /// is bit-exact by specification, and a video engine need not be.
    OFF
}
