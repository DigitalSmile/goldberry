package dev.goldberry.gpu.raytrace.vulkan;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;

import dev.goldberry.gpu.raytrace.SceneGeometry;
import dev.goldberry.gpu.raytrace.TriangleMesh;
import dev.goldberry.natives.vulkan.Structs;
import dev.goldberry.natives.vulkan.VkConstants;
import dev.goldberry.natives.vulkan.VkStructs;
import dev.goldberry.natives.vulkan.VulkanException;

/// A [SceneGeometry] built for tracing: a bottom-level acceleration structure
/// for each distinct mesh, and one top-level structure over the instances.
///
/// Built in one submission: every bottom level at once, each with scratch of
/// its own, a barrier, then the top level. The geometry and scratch are freed
/// when the build is done; what stays is the structures' own storage.
public final class AccelerationScene implements AutoCloseable {

    private static final int INPUT_USAGE =
            VkConstants.VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR
                    | VkConstants.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT;
    private static final int STORAGE_USAGE = VkConstants.VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_STORAGE_BIT_KHR
            | VkConstants.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT;
    private static final int SCRATCH_USAGE =
            VkConstants.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VkConstants.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT;

    private final RayDevice device;
    private final SceneGeometry geometry;
    private final List<Structure> bottom;
    private final Structure top;
    private boolean closed;

    private AccelerationScene(RayDevice device, SceneGeometry geometry, List<Structure> bottom, Structure top) {
        this.device = device;
        this.geometry = geometry;
        this.bottom = bottom;
        this.top = top;
    }

    /// One acceleration structure and the buffer it lives in.
    private record Structure(long handle, DeviceBuffer storage, long address) {}

    /// What one build is handed: its geometry, its build info, the primitive
    /// count and the structure's sizes.
    private record Build(MemorySegment geometry, MemorySegment info, int primitives, long size, long scratch) {}

    /// Builds `geometry` on `device`, recording into `commands`.
    ///
    /// @throws VulkanException when the driver refuses
    static AccelerationScene build(RayDevice device, Commands commands, SceneGeometry geometry) {
        var meshes = geometry.meshes();
        var inputs = new ArrayList<DeviceBuffer>();
        var bottom = new ArrayList<Structure>();
        Structure top = null;
        try (var arena = Arena.ofConfined()) {
            var bottomBuilds = new ArrayList<Build>();
            for (var mesh : meshes) {
                bottomBuilds.add(bottomLevel(device, arena, mesh, inputs));
            }
            for (var build : bottomBuilds) {
                bottom.add(structure(device, build, VkConstants.VK_ACCELERATION_STRUCTURE_TYPE_BOTTOM_LEVEL_KHR));
            }
            for (var i = 0; i < bottomBuilds.size(); i++) {
                prepare(device, bottomBuilds.get(i), bottom.get(i), inputs);
            }

            var indices = geometry.meshIndices();
            var instances = DeviceBuffer.create(
                    device,
                    InstanceRecords.SIZE * geometry.instances().size(),
                    INPUT_USAGE,
                    DeviceBuffer.Memory.HOST_VISIBLE);
            inputs.add(instances);
            for (var i = 0; i < indices.length; i++) {
                InstanceRecords.write(
                        instances.mapped(),
                        i,
                        geometry.instances().get(i),
                        bottom.get(indices[i]).address());
            }
            var topBuild =
                    topLevel(device, arena, instances, geometry.instances().size());
            top = structure(device, topBuild, VkConstants.VK_ACCELERATION_STRUCTURE_TYPE_TOP_LEVEL_KHR);
            prepare(device, topBuild, top, inputs);
            var topBuilt = top;

            commands.run(buffer -> {
                buildAll(device, arena, buffer, bottomBuilds);
                Barriers.betweenBuilds(device, buffer);
                buildAll(device, arena, buffer, List.of(topBuild));
                Barriers.afterBuilds(device, buffer);
            });
            return new AccelerationScene(device, geometry, List.copyOf(bottom), topBuilt);
        } catch (RuntimeException e) {
            if (top != null) {
                destroy(device, top);
            }
            bottom.forEach(structure -> destroy(device, structure));
            throw e;
        } finally {
            inputs.forEach(DeviceBuffer::close);
        }
    }

    /// The top-level structure, for the descriptor that binds it.
    long topLevel() {
        requireOpen();
        return top.handle();
    }

    /// What it was built from.
    public SceneGeometry geometry() {
        return geometry;
    }

    /// How many bottom-level structures it built: one a distinct mesh.
    public int bottomLevelCount() {
        return bottom.size();
    }

    /// Whether it has been closed.
    public boolean isClosed() {
        return closed;
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("this scene is closed");
        }
    }

    /// Frees every structure. No trace may still be running on it, and none
    /// is: every trace is waited for. Idempotent.
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        destroy(device, top);
        bottom.forEach(structure -> destroy(device, structure));
    }

    private static Build bottomLevel(RayDevice device, Arena arena, TriangleMesh mesh, List<DeviceBuffer> inputs) {
        var vertices = DeviceBuffer.create(
                device, (long) mesh.vertexCount() * 3 * Float.BYTES, INPUT_USAGE, DeviceBuffer.Memory.HOST_VISIBLE);
        inputs.add(vertices);
        var positions = mesh.positions();
        for (var i = 0; positions.hasRemaining(); i++) {
            vertices.mapped().setAtIndex(JAVA_FLOAT, i, positions.get());
        }
        var indexBuffer = DeviceBuffer.create(
                device, (long) mesh.triangleCount() * 3 * Integer.BYTES, INPUT_USAGE, DeviceBuffer.Memory.HOST_VISIBLE);
        inputs.add(indexBuffer);
        var indices = mesh.indices();
        for (var i = 0; indices.hasRemaining(); i++) {
            indexBuffer.mapped().setAtIndex(JAVA_INT, i, indices.get());
        }

        var geometry = Structs.allocate(
                arena,
                VkStructs.VkAccelerationStructureGeometryKHR.LAYOUT,
                VkConstants.VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_GEOMETRY_KHR);
        geometry.set(
                JAVA_INT,
                VkStructs.VkAccelerationStructureGeometryKHR.geometryType,
                VkConstants.VK_GEOMETRY_TYPE_TRIANGLES_KHR);
        geometry.set(
                JAVA_INT, VkStructs.VkAccelerationStructureGeometryKHR.flags, VkConstants.VK_GEOMETRY_OPAQUE_BIT_KHR);
        var triangles = geometry.asSlice(
                VkStructs.VkAccelerationStructureGeometryKHR.geometry,
                VkStructs.VkAccelerationStructureGeometryTrianglesDataKHR.LAYOUT.byteSize());
        triangles.set(
                JAVA_INT,
                VkStructs.VkAccelerationStructureGeometryTrianglesDataKHR.sType,
                VkConstants.VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_GEOMETRY_TRIANGLES_DATA_KHR);
        triangles.set(
                JAVA_INT,
                VkStructs.VkAccelerationStructureGeometryTrianglesDataKHR.vertexFormat,
                VkConstants.VK_FORMAT_R32G32B32_SFLOAT);
        triangles.set(
                JAVA_LONG, VkStructs.VkAccelerationStructureGeometryTrianglesDataKHR.vertexData, vertices.address());
        triangles.set(
                JAVA_LONG, VkStructs.VkAccelerationStructureGeometryTrianglesDataKHR.vertexStride, 3L * Float.BYTES);
        triangles.set(
                JAVA_INT, VkStructs.VkAccelerationStructureGeometryTrianglesDataKHR.maxVertex, mesh.vertexCount() - 1);
        triangles.set(
                JAVA_INT,
                VkStructs.VkAccelerationStructureGeometryTrianglesDataKHR.indexType,
                VkConstants.VK_INDEX_TYPE_UINT32);
        triangles.set(
                JAVA_LONG, VkStructs.VkAccelerationStructureGeometryTrianglesDataKHR.indexData, indexBuffer.address());
        return sized(
                device,
                arena,
                geometry,
                VkConstants.VK_ACCELERATION_STRUCTURE_TYPE_BOTTOM_LEVEL_KHR,
                mesh.triangleCount());
    }

    private static Build topLevel(RayDevice device, Arena arena, DeviceBuffer instances, int count) {
        var geometry = Structs.allocate(
                arena,
                VkStructs.VkAccelerationStructureGeometryKHR.LAYOUT,
                VkConstants.VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_GEOMETRY_KHR);
        geometry.set(
                JAVA_INT,
                VkStructs.VkAccelerationStructureGeometryKHR.geometryType,
                VkConstants.VK_GEOMETRY_TYPE_INSTANCES_KHR);
        geometry.set(
                JAVA_INT, VkStructs.VkAccelerationStructureGeometryKHR.flags, VkConstants.VK_GEOMETRY_OPAQUE_BIT_KHR);
        var data = geometry.asSlice(
                VkStructs.VkAccelerationStructureGeometryKHR.geometry,
                VkStructs.VkAccelerationStructureGeometryInstancesDataKHR.LAYOUT.byteSize());
        data.set(
                JAVA_INT,
                VkStructs.VkAccelerationStructureGeometryInstancesDataKHR.sType,
                VkConstants.VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_GEOMETRY_INSTANCES_DATA_KHR);
        data.set(JAVA_INT, VkStructs.VkAccelerationStructureGeometryInstancesDataKHR.arrayOfPointers, 0);
        data.set(JAVA_LONG, VkStructs.VkAccelerationStructureGeometryInstancesDataKHR.data, instances.address());
        return sized(device, arena, geometry, VkConstants.VK_ACCELERATION_STRUCTURE_TYPE_TOP_LEVEL_KHR, count);
    }

    /// The build info for `geometry`, asked for its sizes.
    private static Build sized(RayDevice device, Arena arena, MemorySegment geometry, int type, int primitives) {
        var info = Structs.allocate(
                arena,
                VkStructs.VkAccelerationStructureBuildGeometryInfoKHR.LAYOUT,
                VkConstants.VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_BUILD_GEOMETRY_INFO_KHR);
        info.set(JAVA_INT, VkStructs.VkAccelerationStructureBuildGeometryInfoKHR.type, type);
        info.set(
                JAVA_INT,
                VkStructs.VkAccelerationStructureBuildGeometryInfoKHR.flags,
                VkConstants.VK_BUILD_ACCELERATION_STRUCTURE_PREFER_FAST_TRACE_BIT_KHR);
        info.set(
                JAVA_INT,
                VkStructs.VkAccelerationStructureBuildGeometryInfoKHR.mode,
                VkConstants.VK_BUILD_ACCELERATION_STRUCTURE_MODE_BUILD_KHR);
        info.set(JAVA_INT, VkStructs.VkAccelerationStructureBuildGeometryInfoKHR.geometryCount, 1);
        info.set(ADDRESS, VkStructs.VkAccelerationStructureBuildGeometryInfoKHR.pGeometries, geometry);

        var sizes = Structs.allocate(
                arena,
                VkStructs.VkAccelerationStructureBuildSizesInfoKHR.LAYOUT,
                VkConstants.VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_BUILD_SIZES_INFO_KHR);
        device.rayTracing()
                .getAccelerationStructureBuildSizesKHR()
                .call(
                        device.handle(),
                        VkConstants.VK_ACCELERATION_STRUCTURE_BUILD_TYPE_DEVICE_KHR,
                        info,
                        arena.allocateFrom(JAVA_INT, primitives),
                        sizes);
        return new Build(
                geometry,
                info,
                primitives,
                sizes.get(JAVA_LONG, VkStructs.VkAccelerationStructureBuildSizesInfoKHR.accelerationStructureSize),
                sizes.get(JAVA_LONG, VkStructs.VkAccelerationStructureBuildSizesInfoKHR.buildScratchSize));
    }

    /// The structure `build` builds, with its storage.
    private static Structure structure(RayDevice device, Build build, int type) {
        var storage = DeviceBuffer.create(device, build.size(), STORAGE_USAGE, DeviceBuffer.Memory.DEVICE_LOCAL);
        try (var arena = Arena.ofConfined()) {
            var create = Structs.allocate(
                    arena,
                    VkStructs.VkAccelerationStructureCreateInfoKHR.LAYOUT,
                    VkConstants.VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_CREATE_INFO_KHR);
            create.set(JAVA_LONG, VkStructs.VkAccelerationStructureCreateInfoKHR.buffer, storage.handle());
            create.set(JAVA_LONG, VkStructs.VkAccelerationStructureCreateInfoKHR.size, build.size());
            create.set(JAVA_INT, VkStructs.VkAccelerationStructureCreateInfoKHR.type, type);
            var out = arena.allocate(JAVA_LONG);
            var result = device.rayTracing()
                    .createAccelerationStructureKHR()
                    .call(device.handle(), create, MemorySegment.NULL, out);
            if (result < 0) {
                storage.close();
                VulkanException.check(result, "vkCreateAccelerationStructureKHR");
            }
            var handle = out.get(JAVA_LONG, 0);
            var addressInfo = Structs.allocate(
                    arena,
                    VkStructs.VkAccelerationStructureDeviceAddressInfoKHR.LAYOUT,
                    VkConstants.VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_DEVICE_ADDRESS_INFO_KHR);
            addressInfo.set(
                    JAVA_LONG, VkStructs.VkAccelerationStructureDeviceAddressInfoKHR.accelerationStructure, handle);
            var address = device.rayTracing()
                    .getAccelerationStructureDeviceAddressKHR()
                    .call(device.handle(), addressInfo);
            return new Structure(handle, storage, address);
        }
    }

    /// Points `build` at `structure` and at scratch memory of its own, which
    /// joins `inputs` to be freed after the build.
    private static void prepare(RayDevice device, Build build, Structure structure, List<DeviceBuffer> inputs) {
        var alignment = device.limits().scratchAlignment();
        var scratch = DeviceBuffer.create(
                device, Math.max(build.scratch(), 1) + alignment, SCRATCH_USAGE, DeviceBuffer.Memory.DEVICE_LOCAL);
        inputs.add(scratch);
        build.info()
                .set(
                        JAVA_LONG,
                        VkStructs.VkAccelerationStructureBuildGeometryInfoKHR.dstAccelerationStructure,
                        structure.handle());
        build.info()
                .set(
                        JAVA_LONG,
                        VkStructs.VkAccelerationStructureBuildGeometryInfoKHR.scratchData,
                        DeviceBuffer.align(scratch.address(), alignment));
    }

    /// Records one `vkCmdBuildAccelerationStructuresKHR` of every build.
    private static void buildAll(RayDevice device, Arena arena, MemorySegment commands, List<Build> builds) {
        var infoLayout = VkStructs.VkAccelerationStructureBuildGeometryInfoKHR.LAYOUT;
        var rangeLayout = VkStructs.VkAccelerationStructureBuildRangeInfoKHR.LAYOUT;
        var infos = arena.allocate(infoLayout, builds.size());
        var ranges = arena.allocate(rangeLayout, builds.size());
        var rangePointers = arena.allocate(ADDRESS, builds.size());
        for (var i = 0; i < builds.size(); i++) {
            var build = builds.get(i);
            Structs.at(infos, infoLayout, i).copyFrom(build.info());
            var range = Structs.at(ranges, rangeLayout, i);
            range.set(JAVA_INT, VkStructs.VkAccelerationStructureBuildRangeInfoKHR.primitiveCount, build.primitives());
            rangePointers.setAtIndex(ADDRESS, i, range);
        }
        device.rayTracing().cmdBuildAccelerationStructuresKHR().call(commands, builds.size(), infos, rangePointers);
    }

    private static void destroy(RayDevice device, Structure structure) {
        device.rayTracing()
                .destroyAccelerationStructureKHR()
                .call(device.handle(), structure.handle(), MemorySegment.NULL);
        structure.storage().close();
    }

    @Override
    public String toString() {
        return "AccelerationScene[" + geometry.instances().size() + " instances of " + bottom.size() + " meshes"
                + (closed ? ", closed]" : "]");
    }
}
