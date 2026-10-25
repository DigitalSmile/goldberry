package dev.goldberry.gpu.raytrace.vulkan;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import dev.goldberry.natives.vulkan.Structs;
import dev.goldberry.natives.vulkan.VkConstants;
import dev.goldberry.natives.vulkan.VkStructs;
import dev.goldberry.natives.vulkan.VulkanException;

/// The ray-tracing pipeline of `trace.lib.hlsl`, with what binds it: its
/// descriptor set (the scene at binding 0, the hits at binding 1, the meshes'
/// normals at 2 and where each instance's start at 3), its push constants,
/// and its shader binding table.
///
/// Four groups: ray generation; the primary and the shadow miss; one
/// triangle hit group with a closest-hit shader. No ray is traced from a hit
/// shader, so the recursion depth is 1.
final class RayPipeline implements AutoCloseable {

    /// How many miss shaders: the primary ray's, then the shadow ray's, which
    /// `TraceRay`'s miss index selects.
    static final int MISS_COUNT = 2;

    /// How many hit groups.
    static final int HIT_COUNT = 1;

    private static final int GROUP_COUNT = 1 + MISS_COUNT + HIT_COUNT;

    private final RayDevice device;
    private final long setLayout;
    private final long layout;
    private final long pipeline;
    private final long descriptorPool;
    private final long descriptorSet;
    private final ShaderBindingTable table;
    private final DeviceBuffer tableBuffer;
    private final long tableAddress;
    private boolean closed;

    private RayPipeline(
            RayDevice device,
            long setLayout,
            long layout,
            long pipeline,
            long descriptorPool,
            long descriptorSet,
            ShaderBindingTable table,
            DeviceBuffer tableBuffer,
            long tableAddress) {
        this.device = device;
        this.setLayout = setLayout;
        this.layout = layout;
        this.pipeline = pipeline;
        this.descriptorPool = descriptorPool;
        this.descriptorSet = descriptorSet;
        this.table = table;
        this.tableBuffer = tableBuffer;
        this.tableAddress = tableAddress;
    }

    /// The pipeline on `device`.
    ///
    /// @throws VulkanException when the driver refuses it
    static RayPipeline create(RayDevice device) {
        var f = device.functions();
        var handle = device.handle();
        long module = 0;
        long setLayout = 0;
        long layout = 0;
        long pipeline = 0;
        long pool = 0;
        DeviceBuffer tableBuffer = null;
        try (var arena = Arena.ofConfined()) {
            var out = arena.allocate(JAVA_LONG);
            module = shaderModule(device, arena, out);
            setLayout = setLayout(device, arena, out);

            var range = Structs.allocate(arena, VkStructs.VkPushConstantRange.LAYOUT);
            range.set(JAVA_INT, VkStructs.VkPushConstantRange.stageFlags, VkConstants.VK_SHADER_STAGE_RAYGEN_BIT_KHR);
            range.set(JAVA_INT, VkStructs.VkPushConstantRange.size, RayConstants.SIZE);
            var layouts = arena.allocateFrom(JAVA_LONG, setLayout);
            var layoutInfo = Structs.allocate(
                    arena,
                    VkStructs.VkPipelineLayoutCreateInfo.LAYOUT,
                    VkConstants.VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO);
            layoutInfo.set(JAVA_INT, VkStructs.VkPipelineLayoutCreateInfo.setLayoutCount, 1);
            layoutInfo.set(ADDRESS, VkStructs.VkPipelineLayoutCreateInfo.pSetLayouts, layouts);
            layoutInfo.set(JAVA_INT, VkStructs.VkPipelineLayoutCreateInfo.pushConstantRangeCount, 1);
            layoutInfo.set(ADDRESS, VkStructs.VkPipelineLayoutCreateInfo.pPushConstantRanges, range);
            VulkanException.check(
                    f.createPipelineLayout().call(handle, layoutInfo, MemorySegment.NULL, out),
                    "vkCreatePipelineLayout");
            layout = out.get(JAVA_LONG, 0);

            pipeline = pipeline(device, arena, out, module, layout);
            pool = descriptorPool(device, arena, out);
            var set = descriptorSet(device, arena, pool, setLayout);

            var limits = device.limits();
            var table = ShaderBindingTable.of(
                    limits.handleSize(), limits.handleAlignment(), limits.baseAlignment(), MISS_COUNT, HIT_COUNT);
            tableBuffer = DeviceBuffer.create(
                    device,
                    table.size() + limits.baseAlignment(),
                    VkConstants.VK_BUFFER_USAGE_SHADER_BINDING_TABLE_BIT_KHR
                            | VkConstants.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT,
                    DeviceBuffer.Memory.HOST_VISIBLE);
            var tableAddress = DeviceBuffer.align(tableBuffer.address(), limits.baseAlignment());
            var start = tableAddress - tableBuffer.address();
            var handles = arena.allocate((long) GROUP_COUNT * limits.handleSize());
            VulkanException.check(
                    device.rayTracing()
                            .getRayTracingShaderGroupHandlesKHR()
                            .call(handle, pipeline, 0, GROUP_COUNT, handles.byteSize(), handles),
                    "vkGetRayTracingShaderGroupHandlesKHR");
            var offsets = table.groupOffsets();
            for (var group = 0; group < GROUP_COUNT; group++) {
                MemorySegment.copy(
                        handles,
                        (long) group * limits.handleSize(),
                        tableBuffer.mapped(),
                        start + offsets[group],
                        limits.handleSize());
            }
            var made =
                    new RayPipeline(device, setLayout, layout, pipeline, pool, set, table, tableBuffer, tableAddress);
            module = destroyModule(device, module);
            return made;
        } catch (RuntimeException e) {
            destroyModule(device, module);
            if (tableBuffer != null) {
                tableBuffer.close();
            }
            if (pool != 0) {
                f.destroyDescriptorPool().call(handle, pool, MemorySegment.NULL);
            }
            if (pipeline != 0) {
                f.destroyPipeline().call(handle, pipeline, MemorySegment.NULL);
            }
            if (layout != 0) {
                f.destroyPipelineLayout().call(handle, layout, MemorySegment.NULL);
            }
            if (setLayout != 0) {
                f.destroyDescriptorSetLayout().call(handle, setLayout, MemorySegment.NULL);
            }
            throw e;
        }
    }

    /// Binds `scene`'s top level and normals, and `hits`, to the descriptor
    /// set. No trace may be running: every trace is waited for.
    void bind(AccelerationScene scene, DeviceBuffer hits) {
        var topLevel = scene.topLevel();
        try (var arena = Arena.ofConfined()) {
            var writeLayout = VkStructs.VkWriteDescriptorSet.LAYOUT;
            var writes = Structs.array(arena, writeLayout, 4, VkConstants.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET);

            var structures = Structs.allocate(
                    arena,
                    VkStructs.VkWriteDescriptorSetAccelerationStructureKHR.LAYOUT,
                    VkConstants.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET_ACCELERATION_STRUCTURE_KHR);
            structures.set(
                    JAVA_INT, VkStructs.VkWriteDescriptorSetAccelerationStructureKHR.accelerationStructureCount, 1);
            structures.set(
                    ADDRESS,
                    VkStructs.VkWriteDescriptorSetAccelerationStructureKHR.pAccelerationStructures,
                    arena.allocateFrom(JAVA_LONG, topLevel));
            var structure = Structs.at(writes, writeLayout, 0);
            Structs.chain(structure, structures);
            structure.set(JAVA_LONG, VkStructs.VkWriteDescriptorSet.dstSet, descriptorSet);
            structure.set(JAVA_INT, VkStructs.VkWriteDescriptorSet.dstBinding, 0);
            structure.set(JAVA_INT, VkStructs.VkWriteDescriptorSet.descriptorCount, 1);
            structure.set(
                    JAVA_INT,
                    VkStructs.VkWriteDescriptorSet.descriptorType,
                    VkConstants.VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR);

            storage(arena, Structs.at(writes, writeLayout, 1), 1, hits);
            storage(arena, Structs.at(writes, writeLayout, 2), 2, scene.normals());
            storage(arena, Structs.at(writes, writeLayout, 3), 3, scene.instanceNormals());

            device.functions().updateDescriptorSets().call(device.handle(), 4, writes, 0, MemorySegment.NULL);
        }
    }

    /// Fills `write` with the whole of `buffer` at `binding`, a storage buffer.
    private void storage(Arena arena, MemorySegment write, int binding, DeviceBuffer buffer) {
        var bufferInfo = Structs.allocate(arena, VkStructs.VkDescriptorBufferInfo.LAYOUT);
        bufferInfo.set(JAVA_LONG, VkStructs.VkDescriptorBufferInfo.buffer, buffer.handle());
        bufferInfo.set(JAVA_LONG, VkStructs.VkDescriptorBufferInfo.range, buffer.size());
        write.set(JAVA_LONG, VkStructs.VkWriteDescriptorSet.dstSet, descriptorSet);
        write.set(JAVA_INT, VkStructs.VkWriteDescriptorSet.dstBinding, binding);
        write.set(JAVA_INT, VkStructs.VkWriteDescriptorSet.descriptorCount, 1);
        write.set(
                JAVA_INT, VkStructs.VkWriteDescriptorSet.descriptorType, VkConstants.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER);
        write.set(ADDRESS, VkStructs.VkWriteDescriptorSet.pBufferInfo, bufferInfo);
    }

    /// Records the dispatch of one ray a pixel of `width` by `height`, with
    /// `constants` pushed first.
    void record(MemorySegment commands, MemorySegment constants, int width, int height) {
        var f = device.functions();
        try (var arena = Arena.ofConfined()) {
            f.cmdBindPipeline().call(commands, VkConstants.VK_PIPELINE_BIND_POINT_RAY_TRACING_KHR, pipeline);
            f.cmdBindDescriptorSets()
                    .call(
                            commands,
                            VkConstants.VK_PIPELINE_BIND_POINT_RAY_TRACING_KHR,
                            layout,
                            0,
                            1,
                            arena.allocateFrom(JAVA_LONG, descriptorSet),
                            0,
                            MemorySegment.NULL);
            f.cmdPushConstants()
                    .call(
                            commands,
                            layout,
                            VkConstants.VK_SHADER_STAGE_RAYGEN_BIT_KHR,
                            0,
                            RayConstants.SIZE,
                            constants);
            device.rayTracing()
                    .cmdTraceRaysKHR()
                    .call(
                            commands,
                            region(arena, table.raygen()),
                            region(arena, table.miss()),
                            region(arena, table.hit()),
                            Structs.allocate(arena, VkStructs.VkStridedDeviceAddressRegionKHR.LAYOUT),
                            width,
                            height,
                            1);
        }
    }

    private MemorySegment region(Arena arena, ShaderBindingTable.Region region) {
        var segment = Structs.allocate(arena, VkStructs.VkStridedDeviceAddressRegionKHR.LAYOUT);
        segment.set(JAVA_LONG, VkStructs.VkStridedDeviceAddressRegionKHR.deviceAddress, tableAddress + region.offset());
        segment.set(JAVA_LONG, VkStructs.VkStridedDeviceAddressRegionKHR.stride, region.stride());
        segment.set(JAVA_LONG, VkStructs.VkStridedDeviceAddressRegionKHR.size, region.size());
        return segment;
    }

    /// Frees it all. Idempotent.
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        var f = device.functions();
        var handle = device.handle();
        tableBuffer.close();
        f.destroyDescriptorPool().call(handle, descriptorPool, MemorySegment.NULL);
        f.destroyPipeline().call(handle, pipeline, MemorySegment.NULL);
        f.destroyPipelineLayout().call(handle, layout, MemorySegment.NULL);
        f.destroyDescriptorSetLayout().call(handle, setLayout, MemorySegment.NULL);
    }

    private static long shaderModule(RayDevice device, Arena arena, MemorySegment out) {
        var code = RayShaders.trace();
        var info = Structs.allocate(
                arena,
                VkStructs.VkShaderModuleCreateInfo.LAYOUT,
                VkConstants.VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO);
        var words = arena.allocate(code.length, Integer.BYTES);
        MemorySegment.copy(code, 0, words, JAVA_BYTE, 0, code.length);
        info.set(JAVA_LONG, VkStructs.VkShaderModuleCreateInfo.codeSize, code.length);
        info.set(ADDRESS, VkStructs.VkShaderModuleCreateInfo.pCode, words);
        VulkanException.check(
                device.functions().createShaderModule().call(device.handle(), info, MemorySegment.NULL, out),
                "vkCreateShaderModule");
        return out.get(JAVA_LONG, 0);
    }

    private static long destroyModule(RayDevice device, long module) {
        if (module != 0) {
            device.functions().destroyShaderModule().call(device.handle(), module, MemorySegment.NULL);
        }
        return 0;
    }

    private static long setLayout(RayDevice device, Arena arena, MemorySegment out) {
        var bindingLayout = VkStructs.VkDescriptorSetLayoutBinding.LAYOUT;
        var bindings = arena.allocate(bindingLayout, 4);
        var scene = Structs.at(bindings, bindingLayout, 0);
        scene.set(JAVA_INT, VkStructs.VkDescriptorSetLayoutBinding.binding, 0);
        scene.set(
                JAVA_INT,
                VkStructs.VkDescriptorSetLayoutBinding.descriptorType,
                VkConstants.VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR);
        scene.set(JAVA_INT, VkStructs.VkDescriptorSetLayoutBinding.descriptorCount, 1);
        scene.set(
                JAVA_INT,
                VkStructs.VkDescriptorSetLayoutBinding.stageFlags,
                VkConstants.VK_SHADER_STAGE_RAYGEN_BIT_KHR);
        var hits = Structs.at(bindings, bindingLayout, 1);
        hits.set(JAVA_INT, VkStructs.VkDescriptorSetLayoutBinding.binding, 1);
        hits.set(
                JAVA_INT,
                VkStructs.VkDescriptorSetLayoutBinding.descriptorType,
                VkConstants.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER);
        hits.set(JAVA_INT, VkStructs.VkDescriptorSetLayoutBinding.descriptorCount, 1);
        hits.set(
                JAVA_INT,
                VkStructs.VkDescriptorSetLayoutBinding.stageFlags,
                VkConstants.VK_SHADER_STAGE_RAYGEN_BIT_KHR);
        // The normals, which the closest-hit shader reads.
        for (var binding = 2; binding <= 3; binding++) {
            var normals = Structs.at(bindings, bindingLayout, binding);
            normals.set(JAVA_INT, VkStructs.VkDescriptorSetLayoutBinding.binding, binding);
            normals.set(
                    JAVA_INT,
                    VkStructs.VkDescriptorSetLayoutBinding.descriptorType,
                    VkConstants.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER);
            normals.set(JAVA_INT, VkStructs.VkDescriptorSetLayoutBinding.descriptorCount, 1);
            normals.set(
                    JAVA_INT,
                    VkStructs.VkDescriptorSetLayoutBinding.stageFlags,
                    VkConstants.VK_SHADER_STAGE_CLOSEST_HIT_BIT_KHR);
        }
        var info = Structs.allocate(
                arena,
                VkStructs.VkDescriptorSetLayoutCreateInfo.LAYOUT,
                VkConstants.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO);
        info.set(JAVA_INT, VkStructs.VkDescriptorSetLayoutCreateInfo.bindingCount, 4);
        info.set(ADDRESS, VkStructs.VkDescriptorSetLayoutCreateInfo.pBindings, bindings);
        VulkanException.check(
                device.functions().createDescriptorSetLayout().call(device.handle(), info, MemorySegment.NULL, out),
                "vkCreateDescriptorSetLayout");
        return out.get(JAVA_LONG, 0);
    }

    private static long pipeline(RayDevice device, Arena arena, MemorySegment out, long module, long layout) {
        var stageLayout = VkStructs.VkPipelineShaderStageCreateInfo.LAYOUT;
        var entries = new String[] {RayShaders.RAYGEN, RayShaders.MISS, RayShaders.SHADOW_MISS, RayShaders.CLOSEST_HIT};
        var kinds = new int[] {
            VkConstants.VK_SHADER_STAGE_RAYGEN_BIT_KHR,
            VkConstants.VK_SHADER_STAGE_MISS_BIT_KHR,
            VkConstants.VK_SHADER_STAGE_MISS_BIT_KHR,
            VkConstants.VK_SHADER_STAGE_CLOSEST_HIT_BIT_KHR
        };
        var stages = Structs.array(
                arena, stageLayout, entries.length, VkConstants.VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO);
        for (var i = 0; i < entries.length; i++) {
            var stage = Structs.at(stages, stageLayout, i);
            stage.set(JAVA_INT, VkStructs.VkPipelineShaderStageCreateInfo.stage, kinds[i]);
            stage.set(JAVA_LONG, VkStructs.VkPipelineShaderStageCreateInfo.module, module);
            stage.set(ADDRESS, VkStructs.VkPipelineShaderStageCreateInfo.pName, arena.allocateFrom(entries[i]));
        }

        var groupLayout = VkStructs.VkRayTracingShaderGroupCreateInfoKHR.LAYOUT;
        var groups = Structs.array(
                arena,
                groupLayout,
                GROUP_COUNT,
                VkConstants.VK_STRUCTURE_TYPE_RAY_TRACING_SHADER_GROUP_CREATE_INFO_KHR);
        var unused = (int) VkConstants.VK_SHADER_UNUSED_KHR;
        for (var i = 0; i < GROUP_COUNT; i++) {
            var group = Structs.at(groups, groupLayout, i);
            var hit = i == GROUP_COUNT - 1;
            group.set(
                    JAVA_INT,
                    VkStructs.VkRayTracingShaderGroupCreateInfoKHR.type,
                    hit
                            ? VkConstants.VK_RAY_TRACING_SHADER_GROUP_TYPE_TRIANGLES_HIT_GROUP_KHR
                            : VkConstants.VK_RAY_TRACING_SHADER_GROUP_TYPE_GENERAL_KHR);
            group.set(JAVA_INT, VkStructs.VkRayTracingShaderGroupCreateInfoKHR.generalShader, hit ? unused : i);
            group.set(JAVA_INT, VkStructs.VkRayTracingShaderGroupCreateInfoKHR.closestHitShader, hit ? i : unused);
            group.set(JAVA_INT, VkStructs.VkRayTracingShaderGroupCreateInfoKHR.anyHitShader, unused);
            group.set(JAVA_INT, VkStructs.VkRayTracingShaderGroupCreateInfoKHR.intersectionShader, unused);
        }

        var info = Structs.allocate(
                arena,
                VkStructs.VkRayTracingPipelineCreateInfoKHR.LAYOUT,
                VkConstants.VK_STRUCTURE_TYPE_RAY_TRACING_PIPELINE_CREATE_INFO_KHR);
        info.set(JAVA_INT, VkStructs.VkRayTracingPipelineCreateInfoKHR.stageCount, entries.length);
        info.set(ADDRESS, VkStructs.VkRayTracingPipelineCreateInfoKHR.pStages, stages);
        info.set(JAVA_INT, VkStructs.VkRayTracingPipelineCreateInfoKHR.groupCount, GROUP_COUNT);
        info.set(ADDRESS, VkStructs.VkRayTracingPipelineCreateInfoKHR.pGroups, groups);
        info.set(JAVA_INT, VkStructs.VkRayTracingPipelineCreateInfoKHR.maxPipelineRayRecursionDepth, 1);
        info.set(JAVA_LONG, VkStructs.VkRayTracingPipelineCreateInfoKHR.layout, layout);
        VulkanException.check(
                device.rayTracing()
                        .createRayTracingPipelinesKHR()
                        .call(device.handle(), 0L, 0L, 1, info, MemorySegment.NULL, out),
                "vkCreateRayTracingPipelinesKHR");
        return out.get(JAVA_LONG, 0);
    }

    private static long descriptorPool(RayDevice device, Arena arena, MemorySegment out) {
        var sizeLayout = VkStructs.VkDescriptorPoolSize.LAYOUT;
        var sizes = arena.allocate(sizeLayout, 2);
        Structs.at(sizes, sizeLayout, 0)
                .set(
                        JAVA_INT,
                        VkStructs.VkDescriptorPoolSize.type,
                        VkConstants.VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR);
        Structs.at(sizes, sizeLayout, 0).set(JAVA_INT, VkStructs.VkDescriptorPoolSize.descriptorCount, 1);
        Structs.at(sizes, sizeLayout, 1)
                .set(JAVA_INT, VkStructs.VkDescriptorPoolSize.type, VkConstants.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER);
        Structs.at(sizes, sizeLayout, 1).set(JAVA_INT, VkStructs.VkDescriptorPoolSize.descriptorCount, 3);
        var info = Structs.allocate(
                arena,
                VkStructs.VkDescriptorPoolCreateInfo.LAYOUT,
                VkConstants.VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO);
        info.set(JAVA_INT, VkStructs.VkDescriptorPoolCreateInfo.maxSets, 1);
        info.set(JAVA_INT, VkStructs.VkDescriptorPoolCreateInfo.poolSizeCount, 2);
        info.set(ADDRESS, VkStructs.VkDescriptorPoolCreateInfo.pPoolSizes, sizes);
        VulkanException.check(
                device.functions().createDescriptorPool().call(device.handle(), info, MemorySegment.NULL, out),
                "vkCreateDescriptorPool");
        return out.get(JAVA_LONG, 0);
    }

    private static long descriptorSet(RayDevice device, Arena arena, long pool, long setLayout) {
        var info = Structs.allocate(
                arena,
                VkStructs.VkDescriptorSetAllocateInfo.LAYOUT,
                VkConstants.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO);
        info.set(JAVA_LONG, VkStructs.VkDescriptorSetAllocateInfo.descriptorPool, pool);
        info.set(JAVA_INT, VkStructs.VkDescriptorSetAllocateInfo.descriptorSetCount, 1);
        info.set(ADDRESS, VkStructs.VkDescriptorSetAllocateInfo.pSetLayouts, arena.allocateFrom(JAVA_LONG, setLayout));
        var out = arena.allocate(JAVA_LONG);
        VulkanException.check(
                device.functions().allocateDescriptorSets().call(device.handle(), info, out),
                "vkAllocateDescriptorSets");
        return out.get(JAVA_LONG, 0);
    }
}
