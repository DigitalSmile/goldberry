/// VP9's frame headers, read in Java (the VP9 Bitstream & Decoding Process
/// Specification): what a decoder that is handed the compressed header and the
/// tiles, and not the stream, needs to be told about every frame.
///
/// Vulkan Video's VP9 decode is such a decoder: the application splits each
/// packet into its frames ([Superframe], Annex B), reads each frame's
/// uncompressed header into the `StdVideoDecodeVP9PictureInfo` structs, keeps
/// the eight reference slots, and points the implementation at the compressed
/// header and the tiles behind it. Much of a VP9 header is a change to what an
/// earlier frame set: the slots' sizes, the colour, the loop filter's deltas,
/// the segment features. [StreamState] carries that from one frame to the
/// next, and [UncompressedHeader] gives each frame's values as they stand for
/// it. Everything is read as the specification writes it, field by field, and
/// held to FFmpeg's `trace_headers` of the same streams.
///
/// Not exported.
///
/// Read more: [Bringing a codec](https://goldberry.dev/docs/components/media.html#bringing-a-codec).
@NullMarked
package dev.goldberry.media.bitstream.vp9;

import org.jspecify.annotations.NullMarked;
