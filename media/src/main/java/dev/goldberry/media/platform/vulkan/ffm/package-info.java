/// The Vulkan Video bindings: the loader, and the structs, constants and
/// functions generated from the Vulkan headers by
/// `tools/media/generate_vulkan.py`.
///
/// Vulkan is the operating system's, as GStreamer and Media Foundation are:
/// the loader (`libvulkan.so.1`, `vulkan-1.dll`) is found at run time, and
/// nothing is linked or shipped. Not exported.
///
/// Read more: [Bringing a codec](https://goldberry.dev/docs/components/media.html#bringing-a-codec).
@NullMarked
package dev.goldberry.media.platform.vulkan.ffm;

import org.jspecify.annotations.NullMarked;
