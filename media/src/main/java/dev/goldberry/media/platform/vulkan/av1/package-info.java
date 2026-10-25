/// AV1 on Vulkan Video: the sequence header as session parameters, each
/// frame header and its tiles as `StdVideoDecodeAV1PictureInfo`, the eight
/// reference slots mapped onto the session's picture buffer, and the decoder
/// that drives a session with them.
///
/// Read more: [Bringing a codec](https://goldberry.dev/docs/components/media.html#bringing-a-codec).
@NullMarked
package dev.goldberry.media.platform.vulkan.av1;

import org.jspecify.annotations.NullMarked;
