package dev.goldberry.media.platform.vulkan.ffm;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;

/// How the generated [VkCalls] link and call: one downcall handle a
/// descriptor, called through the address the loader resolved for an
/// instance or a device, which is never null.
public final class VulkanLinker {

    private VulkanLinker() {}

    /// The downcall handle for `descriptor`, called with an address first.
    @SuppressWarnings("restricted")
    public static MethodHandle link(FunctionDescriptor descriptor) {
        return Linker.nativeLinker().downcallHandle(descriptor);
    }

    /// `address`, which must be a function's.
    ///
    /// @throws VulkanException when the loader had none for `name`
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
