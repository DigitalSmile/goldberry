package dev.goldberry.gpu.raytrace.vulkan;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import org.jspecify.annotations.Nullable;

import dev.goldberry.natives.vulkan.Structs;
import dev.goldberry.natives.vulkan.VkConstants;
import dev.goldberry.natives.vulkan.VkStructs;
import dev.goldberry.natives.vulkan.VulkanException;
import dev.goldberry.natives.vulkan.VulkanMemory;

/// A `VkBuffer` with memory of its own: device-local for what only the GPU
/// reads and writes (acceleration structures, scratch), host-visible and
/// mapped for the rest (geometry going in, hits coming out, the shader
/// binding table).
///
/// A buffer made with `VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT` has an
/// [#address]: what an acceleration structure build and a ray-tracing dispatch
/// are handed instead of a binding.
final class DeviceBuffer implements AutoCloseable {

    /// Where its memory lives.
    enum Memory {
        /// The GPU's own, preferred where there is a choice.
        DEVICE_LOCAL,
        /// Mapped into this process, coherent, so no flush is needed: for what
        /// the host writes and the GPU reads.
        HOST_VISIBLE,
        /// Mapped and coherent, and cached where the device has such memory:
        /// for what the GPU writes and the host reads back. Read through the
        /// CPU's cache, rather than uncached across the bus, a large readback
        /// costs a copy rather than a crawl.
        HOST_READBACK
    }

    /// What every mapped buffer's memory has.
    static final int HOST =
            VkConstants.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VkConstants.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;

    private final RayDevice device;
    private final long buffer;
    private final long memory;
    private final long size;
    private final long address;
    private final @Nullable MemorySegment mapped;
    private boolean closed;

    private DeviceBuffer(
            RayDevice device, long buffer, long memory, long size, long address, @Nullable MemorySegment mapped) {
        this.device = device;
        this.buffer = buffer;
        this.memory = memory;
        this.size = size;
        this.address = address;
        this.mapped = mapped;
    }

    /// A buffer of `size` bytes for `usage`, a `VkBufferUsageFlags`, in
    /// `memory`.
    ///
    /// @throws VulkanException when the driver refuses
    static DeviceBuffer create(RayDevice device, long size, int usage, Memory memory) {
        if (size <= 0) {
            throw new IllegalArgumentException("a buffer of " + size + " bytes");
        }
        var f = device.functions();
        var handle = device.handle();
        try (var arena = Arena.ofConfined()) {
            var info = Structs.allocate(
                    arena, VkStructs.VkBufferCreateInfo.LAYOUT, VkConstants.VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO);
            info.set(JAVA_LONG, VkStructs.VkBufferCreateInfo.size, size);
            info.set(JAVA_INT, VkStructs.VkBufferCreateInfo.usage, usage);
            info.set(JAVA_INT, VkStructs.VkBufferCreateInfo.sharingMode, VkConstants.VK_SHARING_MODE_EXCLUSIVE);
            var out = arena.allocate(JAVA_LONG);
            VulkanException.check(f.createBuffer().call(handle, info, MemorySegment.NULL, out), "vkCreateBuffer");
            var buffer = out.get(JAVA_LONG, 0);

            var requirements = Structs.allocate(arena, VkStructs.VkMemoryRequirements.LAYOUT);
            f.getBufferMemoryRequirements().call(handle, buffer, requirements);
            var bits = requirements.get(JAVA_INT, VkStructs.VkMemoryRequirements.memoryTypeBits);
            var type = switch (memory) {
                case DEVICE_LOCAL -> device.memoryType(bits, VkConstants.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, false);
                case HOST_VISIBLE -> device.memoryType(bits, HOST, true);
                case HOST_READBACK -> device.readbackMemoryType(bits);
            };
            var allocate = Structs.allocate(
                    arena, VkStructs.VkMemoryAllocateInfo.LAYOUT, VkConstants.VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO);
            allocate.set(
                    JAVA_LONG,
                    VkStructs.VkMemoryAllocateInfo.allocationSize,
                    requirements.get(JAVA_LONG, VkStructs.VkMemoryRequirements.size));
            allocate.set(JAVA_INT, VkStructs.VkMemoryAllocateInfo.memoryTypeIndex, type);
            var addressed = (usage & VkConstants.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT) != 0;
            if (addressed) {
                var flags = Structs.allocate(
                        arena,
                        VkStructs.VkMemoryAllocateFlagsInfo.LAYOUT,
                        VkConstants.VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_FLAGS_INFO);
                flags.set(
                        JAVA_INT,
                        VkStructs.VkMemoryAllocateFlagsInfo.flags,
                        VkConstants.VK_MEMORY_ALLOCATE_DEVICE_ADDRESS_BIT);
                Structs.chain(allocate, flags);
            }
            var check = f.allocateMemory().call(handle, allocate, MemorySegment.NULL, out);
            if (check < 0) {
                f.destroyBuffer().call(handle, buffer, MemorySegment.NULL);
                VulkanException.check(check, "vkAllocateMemory");
            }
            var deviceMemory = out.get(JAVA_LONG, 0);
            VulkanException.check(f.bindBufferMemory().call(handle, buffer, deviceMemory, 0), "vkBindBufferMemory");

            var address = 0L;
            if (addressed) {
                var addressInfo = Structs.allocate(
                        arena,
                        VkStructs.VkBufferDeviceAddressInfo.LAYOUT,
                        VkConstants.VK_STRUCTURE_TYPE_BUFFER_DEVICE_ADDRESS_INFO);
                addressInfo.set(JAVA_LONG, VkStructs.VkBufferDeviceAddressInfo.buffer, buffer);
                address = f.getBufferDeviceAddress().call(handle, addressInfo);
            }
            MemorySegment mapped = null;
            if (memory != Memory.DEVICE_LOCAL) {
                mapped = VulkanMemory.map(f, handle, deviceMemory, size);
            }
            return new DeviceBuffer(device, buffer, deviceMemory, size, address, mapped);
        }
    }

    /// The `VkBuffer`.
    long handle() {
        requireOpen();
        return buffer;
    }

    /// Its size, in bytes.
    long size() {
        return size;
    }

    /// Its device address, or 0 when it was not made to have one.
    long address() {
        requireOpen();
        return address;
    }

    /// Its memory, mapped.
    ///
    /// @throws IllegalStateException when it is not host-visible
    MemorySegment mapped() {
        requireOpen();
        if (mapped == null) {
            throw new IllegalStateException("a device-local buffer is not mapped");
        }
        return mapped;
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("this buffer is closed");
        }
    }

    /// Frees it. The GPU must be done with it: every use is waited for.
    /// Idempotent.
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        var f = device.functions();
        f.destroyBuffer().call(device.handle(), buffer, MemorySegment.NULL);
        f.freeMemory().call(device.handle(), memory, MemorySegment.NULL);
    }

    /// `value` rounded up to a multiple of `alignment`, a power of two or any
    /// positive number.
    static long align(long value, long alignment) {
        if (alignment <= 1) {
            return value;
        }
        return (value + alignment - 1) / alignment * alignment;
    }
}
