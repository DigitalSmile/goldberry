package dev.goldberry.natives.vulkan;

import java.lang.foreign.MemorySegment;

/// What the generated holders in `…vulkan.calls` share beyond their handle:
/// each is called through the address the loader resolved for an instance or
/// a device, which is never null, and a failed crossing becomes a
/// [VulkanException]. The handles themselves are linked by `Downcalls`, as
/// every holder of this module's is.
public final class VulkanLinker {

    private VulkanLinker() {}

    /// `address`, which must be a function's.
    ///
    /// @throws VulkanException when the loader had none for `name`: a function
    ///                         of an extension the device did not enable, or a
    ///                         driver older than the function
    public static MemorySegment require(MemorySegment address, String name) {
        if (MemorySegment.NULL.equals(address)) {
            throw new VulkanException(name + " is not available from this Vulkan loader or driver");
        }
        return address;
    }

    /// What a failed call becomes.
    public static RuntimeException failure(String name, Throwable cause) {
        return cause instanceof RuntimeException runtime ? runtime : new VulkanException(name + " failed", cause);
    }
}
