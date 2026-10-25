package dev.goldberry.natives.vulkan;

import static java.lang.foreign.ValueLayout.ADDRESS;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/// Device memory mapped into this process, for a module that drives Vulkan
/// through these bindings without native access of its own.
///
/// Mapping ends in `MemorySegment.reinterpret`, a restricted method: the
/// address `vkMapMemory` writes has no size until it is given one. Only this
/// module is granted native access, so the one restricted step is taken here,
/// and `:gpu` — which maps its ray tracer's buffers — needs no flag.
public final class VulkanMemory {

    private VulkanMemory() {}

    /// The first `size` bytes of `memory`, a `VkDeviceMemory` of `device`
    /// that is host-visible, mapped and readable and writable from Java until
    /// the memory is freed, which unmaps it.
    ///
    /// @throws IllegalArgumentException when `size` is not positive
    /// @throws VulkanException          when the driver refuses
    @SuppressWarnings("restricted")
    public static MemorySegment map(VkDeviceFunctions functions, MemorySegment device, long memory, long size) {
        if (size <= 0) {
            throw new IllegalArgumentException("a mapping of " + size + " bytes");
        }
        try (var arena = Arena.ofConfined()) {
            var pointer = arena.allocate(ADDRESS);
            VulkanException.check(
                    functions.mapMemory().call(device, memory, 0, VkConstants.VK_WHOLE_SIZE, 0, pointer),
                    "vkMapMemory");
            return pointer.get(ADDRESS, 0).reinterpret(size);
        }
    }
}
