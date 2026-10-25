package dev.goldberry.gpu.raytrace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;

/// Everything rays can hit: instances of meshes. A [RayTracer] builds one
/// into a [RayScene].
///
/// @param instances in no particular order; an instance's [Instance#id], not
///                  its place here, is what a hit reports
public record SceneGeometry(List<Instance> instances) {

    /// @throws IllegalArgumentException when there are no instances
    public SceneGeometry {
        instances = List.copyOf(instances);
        if (instances.isEmpty()) {
            throw new IllegalArgumentException("a scene has at least one instance");
        }
    }

    /// A scene of `instances`.
    ///
    /// @throws IllegalArgumentException when there are none
    public static SceneGeometry of(Instance... instances) {
        return new SceneGeometry(List.of(instances));
    }

    /// The distinct meshes, each once however many instances place it, in the
    /// order they first appear. Distinct by identity: two meshes of equal
    /// triangles are two builds.
    public List<TriangleMesh> meshes() {
        var seen = Collections.newSetFromMap(new IdentityHashMap<TriangleMesh, Boolean>());
        var meshes = new ArrayList<TriangleMesh>();
        for (var instance : instances) {
            if (seen.add(instance.mesh())) {
                meshes.add(instance.mesh());
            }
        }
        return List.copyOf(meshes);
    }

    /// The index in [#meshes] of each instance's mesh, in the instances' order.
    public int[] meshIndices() {
        var meshes = meshes();
        var byMesh = new IdentityHashMap<TriangleMesh, Integer>();
        for (var i = 0; i < meshes.size(); i++) {
            byMesh.put(meshes.get(i), i);
        }
        var indices = new int[instances.size()];
        for (var i = 0; i < indices.length; i++) {
            indices[i] = Objects.requireNonNull(byMesh.get(instances.get(i).mesh()));
        }
        return indices;
    }

    /// How many triangles every instance together places.
    public long triangleCount() {
        return instances.stream().mapToLong(i -> i.mesh().triangleCount()).sum();
    }
}
