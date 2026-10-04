/// The H.264 and HEVC bitstream the platform decoders read and rewrite: the
/// decoder configuration records (`avcC`, `hvcC`) and the packets they
/// describe, shared by every system's provider, and read by the probe for the
/// pixel format of a track the published natives do not decode.
///
/// Pure Java, so every rule here is tested on any system. Not exported.
/// Null-marked.
///
/// Read more: [Bringing a codec](https://goldberry.dev/docs/components/media.html#bringing-a-codec).
@NullMarked
package dev.goldberry.media.bitstream;

import org.jspecify.annotations.NullMarked;
