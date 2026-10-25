/// Vulkan Video's decoders: H.264, HEVC, VP9 and AV1 on the GPU's video
/// engine, through the system's Vulkan, behind the Decoder SPI like the other
/// system decoders.
///
/// The application side of Vulkan Video is this package's: the parameter sets
/// and headers read in Java (`…media.bitstream`), each picture's order and
/// references worked out, the decoded picture buffer kept, and every picture
/// copied back from the GPU, as every system decoder here copies back. Not
/// exported.
///
/// Read more: [Bringing a codec](https://goldberry.dev/docs/components/media.html#bringing-a-codec).
@NullMarked
package dev.goldberry.media.platform.vulkan;

import org.jspecify.annotations.NullMarked;
