package dev.goldberry.gpu.raytrace.vulkan;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import dev.goldberry.natives.vulkan.Structs;
import dev.goldberry.natives.vulkan.VkConstants;
import dev.goldberry.natives.vulkan.VkStructs;

/// The memory dependencies a build and a trace need, as synchronization2's
/// global barriers: nothing here touches an image, so no layout changes.
final class Barriers {

    private Barriers() {}

    /// What every stage `sourceStages` wrote through `sourceAccess` is seen by
    /// `destinationStages` reading through `destinationAccess`.
    static void memory(
            RayDevice device,
            MemorySegment commands,
            long sourceStages,
            long sourceAccess,
            long destinationStages,
            long destinationAccess) {
        try (var arena = Arena.ofConfined()) {
            var barrier = Structs.allocate(
                    arena, VkStructs.VkMemoryBarrier2.LAYOUT, VkConstants.VK_STRUCTURE_TYPE_MEMORY_BARRIER_2);
            barrier.set(JAVA_LONG, VkStructs.VkMemoryBarrier2.srcStageMask, sourceStages);
            barrier.set(JAVA_LONG, VkStructs.VkMemoryBarrier2.srcAccessMask, sourceAccess);
            barrier.set(JAVA_LONG, VkStructs.VkMemoryBarrier2.dstStageMask, destinationStages);
            barrier.set(JAVA_LONG, VkStructs.VkMemoryBarrier2.dstAccessMask, destinationAccess);
            var dependency = Structs.allocate(
                    arena, VkStructs.VkDependencyInfo.LAYOUT, VkConstants.VK_STRUCTURE_TYPE_DEPENDENCY_INFO);
            dependency.set(JAVA_INT, VkStructs.VkDependencyInfo.memoryBarrierCount, 1);
            dependency.set(ADDRESS, VkStructs.VkDependencyInfo.pMemoryBarriers, barrier);
            device.functions().cmdPipelineBarrier2().call(commands, dependency);
        }
    }

    /// Structures built before are complete for the builds after.
    static void betweenBuilds(RayDevice device, MemorySegment commands) {
        memory(
                device,
                commands,
                VkConstants.VK_PIPELINE_STAGE_2_ACCELERATION_STRUCTURE_BUILD_BIT_KHR,
                VkConstants.VK_ACCESS_2_ACCELERATION_STRUCTURE_WRITE_BIT_KHR,
                VkConstants.VK_PIPELINE_STAGE_2_ACCELERATION_STRUCTURE_BUILD_BIT_KHR,
                VkConstants.VK_ACCESS_2_ACCELERATION_STRUCTURE_READ_BIT_KHR);
    }

    /// Structures built here are complete for every trace submitted later.
    static void afterBuilds(RayDevice device, MemorySegment commands) {
        memory(
                device,
                commands,
                VkConstants.VK_PIPELINE_STAGE_2_ACCELERATION_STRUCTURE_BUILD_BIT_KHR,
                VkConstants.VK_ACCESS_2_ACCELERATION_STRUCTURE_WRITE_BIT_KHR,
                VkConstants.VK_PIPELINE_STAGE_2_RAY_TRACING_SHADER_BIT_KHR,
                VkConstants.VK_ACCESS_2_ACCELERATION_STRUCTURE_READ_BIT_KHR);
    }

    /// What the ray-tracing shaders wrote is complete for the copy after them.
    static void tracedToCopy(RayDevice device, MemorySegment commands) {
        memory(
                device,
                commands,
                VkConstants.VK_PIPELINE_STAGE_2_RAY_TRACING_SHADER_BIT_KHR,
                VkConstants.VK_ACCESS_2_SHADER_WRITE_BIT,
                VkConstants.VK_PIPELINE_STAGE_2_TRANSFER_BIT,
                VkConstants.VK_ACCESS_2_TRANSFER_READ_BIT);
    }

    /// What the copy wrote is readable by the host once the submission's fence
    /// has signalled.
    static void copiedToHost(RayDevice device, MemorySegment commands) {
        memory(
                device,
                commands,
                VkConstants.VK_PIPELINE_STAGE_2_TRANSFER_BIT,
                VkConstants.VK_ACCESS_2_TRANSFER_WRITE_BIT,
                VkConstants.VK_PIPELINE_STAGE_2_HOST_BIT,
                VkConstants.VK_ACCESS_2_HOST_READ_BIT);
    }
}
