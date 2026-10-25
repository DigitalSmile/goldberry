package dev.goldberry.gpu.raytrace.vulkan;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.goldberry.gpu.raytrace.SceneGeometry;
import dev.goldberry.gpu.raytrace.TriangleMesh;
import dev.goldberry.natives.vulkan.Structs;
import dev.goldberry.natives.vulkan.VkConstants;
import dev.goldberry.natives.vulkan.VkStructs;
import dev.goldberry.natives.vulkan.VulkanException;

/// A [SceneGeometry] built for tracing: a bottom-level acceleration structure
/// for each distinct mesh, compacted, and one top-level structure over the
/// instances.
///
/// Built in two submissions. The first builds every bottom level at once,
/// each with scratch of its own, and asks how small each compacts to. The
/// second copies each into a structure of that size, then builds the top
/// level over the copies. The geometry, the scratch and the structures as
/// first built are freed when that is done; what stays is the compacted
/// structures, and what the top level is rebuilt from: its instance records,
/// its scratch and its storage, each grown when an [#update] needs more.
public final class AccelerationScene implements AutoCloseable {

    private static final int INPUT_USAGE =
            VkConstants.VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR
                    | VkConstants.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT;
    private static final int STORAGE_USAGE = VkConstants.VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_STORAGE_BIT_KHR
            | VkConstants.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT;
    private static final int SCRATCH_USAGE =
            VkConstants.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VkConstants.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT;

    /// How a mesh's structure is built: for tracing speed, and to be compacted.
    static final int BOTTOM_FLAGS = VkConstants.VK_BUILD_ACCELERATION_STRUCTURE_PREFER_FAST_TRACE_BIT_KHR
            | VkConstants.VK_BUILD_ACCELERATION_STRUCTURE_ALLOW_COMPACTION_BIT_KHR;

    /// How the structure over the instances is built: for tracing speed. It is
    /// rebuilt rather than updated, so it allows neither.
    static final int TOP_FLAGS = VkConstants.VK_BUILD_ACCELERATION_STRUCTURE_PREFER_FAST_TRACE_BIT_KHR;

    private final RayDevice device;
    private final List<TriangleMesh> meshes;
    private final List<Structure> bottom;
    private final long bottomBuiltBytes;
    private SceneGeometry geometry;
    private @Nullable Structure top;
    private @Nullable DeviceBuffer instances;
    private @Nullable DeviceBuffer topScratch;
    private boolean closed;

    private AccelerationScene(
            RayDevice device,
            SceneGeometry geometry,
            List<TriangleMesh> meshes,
            List<Structure> bottom,
            long bottomBuiltBytes) {
        this.device = device;
        this.geometry = geometry;
        this.meshes = meshes;
        this.bottom = bottom;
        this.bottomBuiltBytes = bottomBuiltBytes;
    }

    /// One acceleration structure, the buffer it lives in, and its size.
    private record Structure(long handle, DeviceBuffer storage, long address, long size) {}

    /// What one build is handed: its geometry, its build info, the primitive
    /// count and the structure's sizes.
    private record Build(MemorySegment geometry, MemorySegment info, int primitives, long size, long scratch) {}

    /// Builds `geometry` on `device`, recording into `commands`.
    ///
    /// @throws VulkanException when the driver refuses
    static AccelerationScene build(RayDevice device, Commands commands, SceneGeometry geometry) {
        var meshes = geometry.meshes();
        var inputs = new ArrayList<DeviceBuffer>();
        var built = new ArrayList<Structure>();
        var compacted = new ArrayList<Structure>();
        AccelerationScene scene = null;
        try (var arena = Arena.ofConfined()) {
            var builds = new ArrayList<Build>();
            for (var mesh : meshes) {
                builds.add(bottomLevel(device, arena, mesh, inputs));
            }
            for (var build : builds) {
                built.add(structure(device, build.size(), VkConstants.VK_ACCELERATION_STRUCTURE_TYPE_BOTTOM_LEVEL_KHR));
            }
            for (var i = 0; i < builds.size(); i++) {
                prepare(device, builds.get(i), built.get(i), scratch(device, builds.get(i), inputs));
            }

            long[] sizes;
            try (var query = CompactedSizes.open(device, builds.size())) {
                var handles = built.stream().map(Structure::handle).toList();
                commands.run(buffer -> {
                    query.reset(buffer);
                    buildAll(device, arena, buffer, builds);
                    Barriers.betweenBuilds(device, buffer);
                    query.write(arena, buffer, handles);
                });
                sizes = query.read();
            }
            for (var size : sizes) {
                compacted.add(structure(device, size, VkConstants.VK_ACCELERATION_STRUCTURE_TYPE_BOTTOM_LEVEL_KHR));
            }
            var builtBytes = built.stream().mapToLong(Structure::size).sum();
            scene = new AccelerationScene(device, geometry, meshes, List.copyOf(compacted), builtBytes);
            var topBuild = scene.placeInstances(arena, geometry);
            commands.run(buffer -> {
                for (var i = 0; i < built.size(); i++) {
                    compact(device, arena, buffer, built.get(i), compacted.get(i));
                }
                Barriers.betweenBuilds(device, buffer);
                buildAll(device, arena, buffer, List.of(topBuild));
                Barriers.afterBuilds(device, buffer);
            });
            return scene;
        } catch (RuntimeException e) {
            if (scene != null) {
                scene.close();
            } else {
                compacted.forEach(structure -> destroy(device, structure));
            }
            throw e;
        } finally {
            built.forEach(structure -> destroy(device, structure));
            inputs.forEach(DeviceBuffer::close);
        }
    }

    /// Rebuilds the top level over `geometry`'s instances, in place where its
    /// storage holds them; the meshes' structures stay. A refused rebuild
    /// closes the scene, whose top level is then not whole.
    ///
    /// @throws IllegalArgumentException when `geometry` places a mesh the
    ///                                  scene was not built with
    /// @throws VulkanException          when the driver refuses
    void update(Commands commands, SceneGeometry geometry) {
        requireOpen();
        try (var arena = Arena.ofConfined()) {
            var topBuild = placeInstances(arena, geometry);
            commands.run(buffer -> {
                buildAll(device, arena, buffer, List.of(topBuild));
                Barriers.afterBuilds(device, buffer);
            });
        } catch (VulkanException e) {
            close();
            throw e;
        }
        this.geometry = geometry;
    }

    /// The index in `meshes` of each of `geometry`'s instances' mesh, by
    /// identity.
    ///
    /// @throws IllegalArgumentException when one is not among them
    static int[] meshIndices(List<TriangleMesh> meshes, SceneGeometry geometry) {
        var byMesh = new IdentityHashMap<TriangleMesh, Integer>();
        for (var i = 0; i < meshes.size(); i++) {
            byMesh.put(meshes.get(i), i);
        }
        var instances = geometry.instances();
        var indices = new int[instances.size()];
        for (var i = 0; i < indices.length; i++) {
            var index = byMesh.get(instances.get(i).mesh());
            if (index == null) {
                throw new IllegalArgumentException(
                        "instance " + instances.get(i).id() + " places a mesh of "
                                + instances.get(i).mesh().triangleCount()
                                + " triangles the scene was not built with: build a new scene for it");
            }
            indices[i] = index;
        }
        return indices;
    }

    /// Writes `geometry`'s instance records, grows the top level's buffers to
    /// hold them, and returns its build, pointed at its storage and scratch.
    private Build placeInstances(Arena arena, SceneGeometry geometry) {
        var indices = meshIndices(meshes, geometry);
        var count = indices.length;
        var records = InstanceRecords.SIZE * count;
        if (instances == null || instances.size() < records) {
            var grown = Math.max(records, instances == null ? 0 : instances.size() * 2);
            if (instances != null) {
                instances.close();
                instances = null;
            }
            instances = DeviceBuffer.create(device, grown, INPUT_USAGE, DeviceBuffer.Memory.HOST_VISIBLE);
        }
        var placed = instances;
        for (var i = 0; i < count; i++) {
            InstanceRecords.write(
                    placed.mapped(),
                    i,
                    geometry.instances().get(i),
                    bottom.get(indices[i]).address());
        }
        var build = topLevel(device, arena, placed, count);
        if (top == null || top.size() < build.size()) {
            if (top != null) {
                destroy(device, top);
                top = null;
            }
            top = structure(device, build.size(), VkConstants.VK_ACCELERATION_STRUCTURE_TYPE_TOP_LEVEL_KHR);
        }
        var alignment = device.limits().scratchAlignment();
        if (topScratch == null || topScratch.size() < scratchBytes(build, alignment)) {
            if (topScratch != null) {
                topScratch.close();
                topScratch = null;
            }
            topScratch = DeviceBuffer.create(
                    device, scratchBytes(build, alignment), SCRATCH_USAGE, DeviceBuffer.Memory.DEVICE_LOCAL);
        }
        prepare(device, build, top, topScratch);
        return build;
    }

    /// The top-level structure, for the descriptor that binds it.
    long topLevel() {
        requireOpen();
        if (top == null) {
            throw new IllegalStateException("this scene has no top level");
        }
        return top.handle();
    }

    /// What it was built from, or last updated to.
    public SceneGeometry geometry() {
        return geometry;
    }

    /// How many bottom-level structures it built: one a distinct mesh.
    public int bottomLevelCount() {
        return bottom.size();
    }

    /// How many bytes the meshes' structures took as first built, before they
    /// were compacted.
    public long bottomBytesBuilt() {
        return bottomBuiltBytes;
    }

    /// How many bytes the meshes' compacted structures take.
    public long bottomBytes() {
        return bottom.stream().mapToLong(Structure::size).sum();
    }

    /// How many bytes every structure takes: the meshes' and the instances'.
    public long structureBytes() {
        return bottomBytes() + (top == null ? 0 : top.size());
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

    /// Frees every structure and the top level's buffers. No trace may still
    /// be running on it, and none is: every trace is waited for. Idempotent.
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (top != null) {
            destroy(device, top);
        }
        for (var buffer : new @Nullable DeviceBuffer[] {instances, topScratch}) {
            if (buffer != null) {
                buffer.close();
            }
        }
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
                type == VkConstants.VK_ACCELERATION_STRUCTURE_TYPE_BOTTOM_LEVEL_KHR ? BOTTOM_FLAGS : TOP_FLAGS);
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

    /// A structure of `size` bytes, with its storage.
    private static Structure structure(RayDevice device, long size, int type) {
        var storage = DeviceBuffer.create(device, size, STORAGE_USAGE, DeviceBuffer.Memory.DEVICE_LOCAL);
        try (var arena = Arena.ofConfined()) {
            var create = Structs.allocate(
                    arena,
                    VkStructs.VkAccelerationStructureCreateInfoKHR.LAYOUT,
                    VkConstants.VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_CREATE_INFO_KHR);
            create.set(JAVA_LONG, VkStructs.VkAccelerationStructureCreateInfoKHR.buffer, storage.handle());
            create.set(JAVA_LONG, VkStructs.VkAccelerationStructureCreateInfoKHR.size, size);
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
            return new Structure(handle, storage, address, size);
        }
    }

    /// How much scratch `build` takes, with room to start it on `alignment`.
    private static long scratchBytes(Build build, long alignment) {
        return Math.max(build.scratch(), 1) + alignment;
    }

    /// Scratch memory for `build` alone, which joins `inputs` to be freed
    /// after the build.
    private static DeviceBuffer scratch(RayDevice device, Build build, List<DeviceBuffer> inputs) {
        var scratch = DeviceBuffer.create(
                device,
                scratchBytes(build, device.limits().scratchAlignment()),
                SCRATCH_USAGE,
                DeviceBuffer.Memory.DEVICE_LOCAL);
        inputs.add(scratch);
        return scratch;
    }

    /// Points `build` at `structure` and at `scratch`.
    private static void prepare(RayDevice device, Build build, Structure structure, DeviceBuffer scratch) {
        build.info()
                .set(
                        JAVA_LONG,
                        VkStructs.VkAccelerationStructureBuildGeometryInfoKHR.dstAccelerationStructure,
                        structure.handle());
        build.info()
                .set(
                        JAVA_LONG,
                        VkStructs.VkAccelerationStructureBuildGeometryInfoKHR.scratchData,
                        DeviceBuffer.align(scratch.address(), device.limits().scratchAlignment()));
    }

    /// Records the copy of `from` into `to`, compacted.
    private static void compact(RayDevice device, Arena arena, MemorySegment commands, Structure from, Structure to) {
        var copy = Structs.allocate(
                arena,
                VkStructs.VkCopyAccelerationStructureInfoKHR.LAYOUT,
                VkConstants.VK_STRUCTURE_TYPE_COPY_ACCELERATION_STRUCTURE_INFO_KHR);
        copy.set(JAVA_LONG, VkStructs.VkCopyAccelerationStructureInfoKHR.src, from.handle());
        copy.set(JAVA_LONG, VkStructs.VkCopyAccelerationStructureInfoKHR.dst, to.handle());
        copy.set(
                JAVA_INT,
                VkStructs.VkCopyAccelerationStructureInfoKHR.mode,
                VkConstants.VK_COPY_ACCELERATION_STRUCTURE_MODE_COMPACT_KHR);
        device.rayTracing().cmdCopyAccelerationStructureKHR().call(commands, copy);
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
