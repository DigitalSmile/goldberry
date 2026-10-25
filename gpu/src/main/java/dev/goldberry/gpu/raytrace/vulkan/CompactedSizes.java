package dev.goldberry.gpu.raytrace.vulkan;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.List;

import dev.goldberry.natives.vulkan.Structs;
import dev.goldberry.natives.vulkan.VkConstants;
import dev.goldberry.natives.vulkan.VkStructs;
import dev.goldberry.natives.vulkan.VulkanException;

/// A query pool that asks the device how small each of a set of built
/// acceleration structures compacts to: reset and written in one command
/// buffer, read once its submission is done.
final class CompactedSizes implements AutoCloseable {

    private final RayDevice device;
    private final long pool;
    private final int count;
    private boolean closed;

    private CompactedSizes(RayDevice device, long pool, int count) {
        this.device = device;
        this.pool = pool;
        this.count = count;
    }

    /// A pool for `count` structures.
    ///
    /// @throws VulkanException when the driver refuses
    static CompactedSizes open(RayDevice device, int count) {
        if (count < 1) {
            throw new IllegalArgumentException("a query of " + count + " structures");
        }
        try (var arena = Arena.ofConfined()) {
            var info = Structs.allocate(
                    arena,
                    VkStructs.VkQueryPoolCreateInfo.LAYOUT,
                    VkConstants.VK_STRUCTURE_TYPE_QUERY_POOL_CREATE_INFO);
            info.set(
                    JAVA_INT,
                    VkStructs.VkQueryPoolCreateInfo.queryType,
                    VkConstants.VK_QUERY_TYPE_ACCELERATION_STRUCTURE_COMPACTED_SIZE_KHR);
            info.set(JAVA_INT, VkStructs.VkQueryPoolCreateInfo.queryCount, count);
            var out = arena.allocate(JAVA_LONG);
            VulkanException.check(
                    device.functions().createQueryPool().call(device.handle(), info, MemorySegment.NULL, out),
                    "vkCreateQueryPool");
            return new CompactedSizes(device, out.get(JAVA_LONG, 0), count);
        }
    }

    /// Records the pool's reset, which comes before the builds it measures.
    void reset(MemorySegment commands) {
        device.functions().cmdResetQueryPool().call(commands, pool, 0, count);
    }

    /// Records the query of `structures`' compacted sizes, after a barrier
    /// that completes their builds.
    void write(Arena arena, MemorySegment commands, List<Long> structures) {
        if (structures.size() != count) {
            throw new IllegalArgumentException("a pool of " + count + " asked of " + structures.size());
        }
        var handles = arena.allocate(JAVA_LONG, count);
        for (var i = 0; i < count; i++) {
            handles.setAtIndex(JAVA_LONG, i, structures.get(i));
        }
        device.rayTracing()
                .cmdWriteAccelerationStructuresPropertiesKHR()
                .call(
                        commands,
                        count,
                        handles,
                        VkConstants.VK_QUERY_TYPE_ACCELERATION_STRUCTURE_COMPACTED_SIZE_KHR,
                        pool,
                        0);
    }

    /// The sizes, in the order they were written; waits for them should they
    /// not be ready.
    ///
    /// @throws VulkanException when the driver refuses
    long[] read() {
        try (var arena = Arena.ofConfined()) {
            var results = arena.allocate(JAVA_LONG, count);
            VulkanException.check(
                    device.functions()
                            .getQueryPoolResults()
                            .call(
                                    device.handle(),
                                    pool,
                                    0,
                                    count,
                                    results.byteSize(),
                                    results,
                                    Long.BYTES,
                                    VkConstants.VK_QUERY_RESULT_64_BIT | VkConstants.VK_QUERY_RESULT_WAIT_BIT),
                    "vkGetQueryPoolResults");
            return results.toArray(JAVA_LONG);
        }
    }

    /// Frees the pool. Idempotent.
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        device.functions().destroyQueryPool().call(device.handle(), pool, MemorySegment.NULL);
    }
}
