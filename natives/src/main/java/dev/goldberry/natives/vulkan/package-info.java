/// The Vulkan bindings: the loader, an instance, and the structs, constants
/// and functions generated from the Vulkan headers by
/// `tools/natives/generate_vulkan.py`. The holders are in `…vulkan.calls`.
///
/// Vulkan is the operating system's: the loader (`libvulkan.so.1`,
/// `vulkan-1.dll`) is found at run time, and nothing is linked or shipped.
/// Two modules make a device of their own with it: `:media`, which decodes
/// video on one, and `:gpu`, which traces rays on one. Exported to those two
/// alone.
///
/// Read more: [Bringing a codec](https://goldberry.dev/docs/components/media.html#bringing-a-codec).
@NullMarked
package dev.goldberry.natives.vulkan;

import org.jspecify.annotations.NullMarked;
