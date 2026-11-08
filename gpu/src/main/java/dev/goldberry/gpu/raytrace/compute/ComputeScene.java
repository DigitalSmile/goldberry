package dev.goldberry.gpu.raytrace.compute;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

import dev.goldberry.gpu.BufferUsage;
import dev.goldberry.gpu.GpuBuffer;
import dev.goldberry.gpu.GpuDevice;
import dev.goldberry.gpu.raytrace.SceneGeometry;
import dev.goldberry.gpu.raytrace.bvh.InstanceLevel;
import dev.goldberry.gpu.raytrace.bvh.MeshLevels;
import dev.goldberry.gpu.raytrace.tier.TracedScene;

/// A scene built for the software tier: its [MeshLevels] and [InstanceLevel],
/// built in Java and uploaded into five storage buffers on the window's
/// device, in the order `raytrace.hlsli` binds them.
///
/// An update builds the top level again and uploads it; the meshes' buffers
/// stay. A top-level buffer of another size is made anew, the old one released
/// once the GPU is done with it, and one of the same size is uploaded whole, so
/// no frame still reading it sees half of each.
final class ComputeScene implements TracedScene {

    private final GpuDevice device;
    private final MeshLevels meshes;
    private final GpuBuffer bottomNodes;
    private final GpuBuffer triangles;
    private final GpuBuffer normals;
    private GpuBuffer topNodes;
    private GpuBuffer instances;
    private InstanceLevel top;
    private boolean closed;

    private ComputeScene(
            GpuDevice device,
            MeshLevels meshes,
            InstanceLevel top,
            GpuBuffer bottomNodes,
            GpuBuffer triangles,
            GpuBuffer normals,
            GpuBuffer topNodes,
            GpuBuffer instances) {
        this.device = device;
        this.meshes = meshes;
        this.top = top;
        this.bottomNodes = bottomNodes;
        this.triangles = triangles;
        this.normals = normals;
        this.topNodes = topNodes;
        this.instances = instances;
    }

    /// `geometry`, built and uploaded to `device`.
    ///
    /// @throws IllegalArgumentException when the scene is larger than a GPU
    ///                                  buffer holds
    /// @throws dev.goldberry.gpu.GpuException when the driver refuses
    static ComputeScene build(GpuDevice device, SceneGeometry geometry) {
        Objects.requireNonNull(geometry, "geometry");
        var meshes = MeshLevels.of(geometry);
        var top = InstanceLevel.of(meshes, geometry);
        var made = new ArrayList<GpuBuffer>(5);
        try {
            var data = List.of(
                    meshes.nodeBytes(),
                    meshes.triangleBytes(),
                    meshes.normalBytes(),
                    top.nodeBytes(),
                    top.instanceBytes());
            for (var bytes : data) {
                made.add(storage(device, bytes));
            }
            upload(device, made, data);
            return new ComputeScene(
                    device, meshes, top, made.get(0), made.get(1), made.get(2), made.get(3), made.get(4));
        } catch (RuntimeException e) {
            made.forEach(GpuBuffer::close);
            throw e;
        }
    }

    /// A storage buffer the size of `bytes`.
    private static GpuBuffer storage(GpuDevice device, ByteBuffer bytes) {
        return device.createBuffer(EnumSet.of(BufferUsage.COMPUTE_STORAGE_READ), bytes.remaining());
    }

    /// Uploads each of `data` into the buffer of `buffers` beside it, in one
    /// frame, submitted.
    private static void upload(GpuDevice device, List<GpuBuffer> buffers, List<ByteBuffer> data) {
        try (var frame = device.beginFrame()) {
            frame.copyPass(pass -> {
                for (var i = 0; i < buffers.size(); i++) {
                    pass.upload(buffers.get(i), 0, data.get(i));
                }
            });
            frame.submit();
        }
    }

    /// Places `geometry`'s instances instead.
    ///
    /// @throws IllegalArgumentException when it places a mesh this was not
    ///                                  built with
    void update(SceneGeometry geometry) {
        requireOpen();
        var next = InstanceLevel.of(meshes, geometry);
        var nodes = next.nodeBytes();
        var placed = next.instanceBytes();
        if (nodes.remaining() != topNodes.size()) {
            var made = storage(device, nodes);
            topNodes.close();
            topNodes = made;
        }
        if (placed.remaining() != instances.size()) {
            var made = storage(device, placed);
            instances.close();
            instances = made;
        }
        upload(device, List.of(topNodes, instances), List.of(nodes, placed));
        top = next;
    }

    /// The five buffers, in the shaders' order: the top level's nodes, the
    /// instances, the meshes' nodes, the triangles, the normals.
    GpuBuffer[] buffers() {
        requireOpen();
        return new GpuBuffer[] {topNodes, instances, bottomNodes, triangles, normals};
    }

    /// The device it was built on.
    GpuDevice device() {
        return device;
    }

    /// Its bottom level.
    MeshLevels meshes() {
        return meshes;
    }

    /// Its top level, as last built.
    InstanceLevel top() {
        return top;
    }

    @Override
    public SceneGeometry geometry() {
        return top.geometry();
    }

    @Override
    public long structureBytes() {
        return meshes.bytes() + top.bytes();
    }

    @Override
    public boolean isClosed() {
        return closed || device.isClosed();
    }

    private void requireOpen() {
        if (isClosed()) {
            throw new IllegalStateException("this scene is closed");
        }
    }

    /// Releases its buffers, on a device that is still open; a closed one
    /// released them itself. Idempotent.
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (!device.isClosed()) {
            for (var buffer : List.of(topNodes, instances, bottomNodes, triangles, normals)) {
                buffer.close();
            }
        }
    }

    @Override
    public String toString() {
        return "ComputeScene[" + meshes.triangleCount() + " triangles, " + top.instanceCount() + " instances]";
    }
}
