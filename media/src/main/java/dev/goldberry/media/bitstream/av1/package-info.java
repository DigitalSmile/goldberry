/// AV1's OBUs, sequence header and frame headers, read in Java (the AV1
/// Bitstream and Decoding Process Specification): what a decoder that is
/// handed the tiles, and not the stream, needs to be told about every frame.
///
/// Vulkan Video's AV1 decode is such a decoder: the application parses the
/// sequence header into `StdVideoAV1SequenceHeader`, each frame header into
/// `StdVideoDecodeAV1PictureInfo` and the structs it points to, finds every
/// tile's bytes, and keeps the reference slots, whose saved state the next
/// frame header is read against. Everything here is read as the
/// specification writes it, field by field, and held to FFmpeg's
/// `trace_headers` of the same streams. [Av1Parser] drives it OBU by OBU;
/// [References] is the state between frames.
///
/// Pure Java, so every rule here is tested on any system. Not exported.
/// Null-marked.
///
/// Read more: [Bringing a codec](https://goldberry.dev/docs/components/media.html#bringing-a-codec).
@NullMarked
package dev.goldberry.media.bitstream.av1;

import org.jspecify.annotations.NullMarked;
