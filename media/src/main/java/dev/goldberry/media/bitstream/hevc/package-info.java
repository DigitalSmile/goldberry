/// HEVC's parameter sets and slice segment headers, read in Java (ITU-T H.265):
/// what a decoder that is handed the slices, and not the stream, needs to be
/// told about every picture.
///
/// Vulkan Video's HEVC decode is such a decoder: the application parses the
/// video, sequence and picture parameter sets into the `StdVideoH265*` structs,
/// works out each picture's order count and reference picture set from its
/// slice header, and keeps the decoded picture buffer. Everything here is read
/// as the specification writes it, field by field, and held to FFmpeg's
/// `trace_headers` of the same streams.
///
/// Not exported.
///
/// Read more: [Bringing a codec](https://goldberry.dev/docs/components/media.html#bringing-a-codec).
@NullMarked
package dev.goldberry.media.bitstream.hevc;

import org.jspecify.annotations.NullMarked;
