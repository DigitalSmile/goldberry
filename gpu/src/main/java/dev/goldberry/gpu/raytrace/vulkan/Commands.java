package dev.goldberry.gpu.raytrace.vulkan;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.function.Consumer;

import dev.goldberry.natives.vulkan.Structs;
import dev.goldberry.natives.vulkan.VkConstants;
import dev.goldberry.natives.vulkan.VkStructs;
import dev.goldberry.natives.vulkan.VulkanException;

/// One command buffer and its fence, recorded, submitted and waited for each
/// time: a build or a trace is one submission whose results are read the
/// moment it returns.
final class Commands implements AutoCloseable {

    /// Forever, for `vkWaitForFences`.
    private static final long FOREVER = -1L;

    private final RayDevice device;
    private final long pool;
    private final MemorySegment buffer;
    private final long fence;
    private boolean closed;

    Commands(RayDevice device) {
        this.device = device;
        var f = device.functions();
        var handle = device.handle();
        try (var arena = Arena.ofConfined()) {
            var poolInfo = Structs.allocate(
                    arena,
                    VkStructs.VkCommandPoolCreateInfo.LAYOUT,
                    VkConstants.VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO);
            poolInfo.set(
                    JAVA_INT,
                    VkStructs.VkCommandPoolCreateInfo.flags,
                    VkConstants.VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT);
            poolInfo.set(JAVA_INT, VkStructs.VkCommandPoolCreateInfo.queueFamilyIndex, device.queueFamily());
            var out = arena.allocate(JAVA_LONG);
            VulkanException.check(
                    f.createCommandPool().call(handle, poolInfo, MemorySegment.NULL, out), "vkCreateCommandPool");
            this.pool = out.get(JAVA_LONG, 0);

            var allocate = Structs.allocate(
                    arena,
                    VkStructs.VkCommandBufferAllocateInfo.LAYOUT,
                    VkConstants.VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO);
            allocate.set(JAVA_LONG, VkStructs.VkCommandBufferAllocateInfo.commandPool, pool);
            allocate.set(
                    JAVA_INT, VkStructs.VkCommandBufferAllocateInfo.level, VkConstants.VK_COMMAND_BUFFER_LEVEL_PRIMARY);
            allocate.set(JAVA_INT, VkStructs.VkCommandBufferAllocateInfo.commandBufferCount, 1);
            var buffers = arena.allocate(ADDRESS);
            VulkanException.check(
                    f.allocateCommandBuffers().call(handle, allocate, buffers), "vkAllocateCommandBuffers");
            this.buffer = buffers.get(ADDRESS, 0);

            var fenceInfo = Structs.allocate(
                    arena, VkStructs.VkFenceCreateInfo.LAYOUT, VkConstants.VK_STRUCTURE_TYPE_FENCE_CREATE_INFO);
            VulkanException.check(f.createFence().call(handle, fenceInfo, MemorySegment.NULL, out), "vkCreateFence");
            this.fence = out.get(JAVA_LONG, 0);
        }
    }

    /// Records `recording` into the command buffer, submits it, and returns
    /// when the GPU has run it.
    ///
    /// @throws VulkanException when a call fails, the device lost among them
    void run(Consumer<MemorySegment> recording) {
        if (closed) {
            throw new IllegalStateException("these commands are closed");
        }
        var f = device.functions();
        try (var arena = Arena.ofConfined()) {
            var begin = Structs.allocate(
                    arena,
                    VkStructs.VkCommandBufferBeginInfo.LAYOUT,
                    VkConstants.VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);
            begin.set(
                    JAVA_INT,
                    VkStructs.VkCommandBufferBeginInfo.flags,
                    VkConstants.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
            VulkanException.check(f.beginCommandBuffer().call(buffer, begin), "vkBeginCommandBuffer");
            try {
                recording.accept(buffer);
            } finally {
                VulkanException.check(f.endCommandBuffer().call(buffer), "vkEndCommandBuffer");
            }
            var buffers = arena.allocate(ADDRESS);
            buffers.set(ADDRESS, 0, buffer);
            var submit =
                    Structs.allocate(arena, VkStructs.VkSubmitInfo.LAYOUT, VkConstants.VK_STRUCTURE_TYPE_SUBMIT_INFO);
            submit.set(JAVA_INT, VkStructs.VkSubmitInfo.commandBufferCount, 1);
            submit.set(ADDRESS, VkStructs.VkSubmitInfo.pCommandBuffers, buffers);
            device.submit(submit, fence);
            var fences = arena.allocate(JAVA_LONG);
            fences.set(JAVA_LONG, 0, fence);
            VulkanException.check(f.waitForFences().call(device.handle(), 1, fences, 1, FOREVER), "vkWaitForFences");
            VulkanException.check(f.resetFences().call(device.handle(), 1, fences), "vkResetFences");
        } finally {
            f.resetCommandBuffer().call(buffer, 0);
        }
    }

    /// Frees the pool, its buffer and the fence. Idempotent.
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        var f = device.functions();
        f.destroyFence().call(device.handle(), fence, MemorySegment.NULL);
        f.destroyCommandPool().call(device.handle(), pool, MemorySegment.NULL);
    }
}
