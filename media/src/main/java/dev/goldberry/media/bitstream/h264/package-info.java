/// H.264's parameter sets and slice headers, read in Java (ITU-T H.264): what
/// a decoder handed whole slices, as Vulkan Video's H.264 decode is, needs to
/// be told about every picture, held to FFmpeg's `trace_headers` field by
/// field.
///
/// Not exported.
///
/// Read more: [Bringing a codec](https://goldberry.dev/docs/components/media.html#bringing-a-codec).
@NullMarked
package dev.goldberry.media.bitstream.h264;

import org.jspecify.annotations.NullMarked;
