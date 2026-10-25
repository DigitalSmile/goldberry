/// H.264 on Vulkan Video: the picture order counts and reference marking of
/// ITU-T H.264 §8.2, kept in Java, and the `StdVideoH264*` structs the decode
/// is told each picture with. Frame pictures only: a field picture is refused,
/// and the player moves to the next decoder. Not exported.
///
/// Read more: [Bringing a codec](https://goldberry.dev/docs/components/media.html#bringing-a-codec).
@NullMarked
package dev.goldberry.media.platform.vulkan.h264;

import org.jspecify.annotations.NullMarked;
