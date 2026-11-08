package dev.goldberry.gpu.raytrace.bvh;

import java.util.Objects;
import java.util.Optional;

import dev.goldberry.gpu.raytrace.Instance;
import dev.goldberry.gpu.raytrace.Vector3;

/// A walk of a scene's two levels in Java, step for step the one the software
/// tier's shaders take (`raytrace.hlsli`): the same box test, the same order,
/// the same triangle test, in single precision. What the unit tests check a
/// tree with, against every triangle tried in turn.
///
/// A ray goes from `origin` along `direction` and hits what lies more than
/// `tMin` and less than `tMax` along it; with a unit direction those are
/// distances. Both faces of a triangle are hit.
///
/// Each level is walked the same way: from its root, where the ray enters its
/// box, a branch's two children are both tested, the nearer walked next and
/// the farther kept on a stack with where the ray enters it, and a kept child
/// the ray now enters past the closest hit is dropped.
public final class BvhTraversal {

    /// What a direction's zero component becomes before it is inverted: small
    /// enough to change no answer, and large enough that its inverse, and
    /// every product with it, stays finite.
    public static final float TINY = 1e-20f;

    /// How far outside its triangle a hit's barycentrics may be: what keeps a
    /// ray along the edge two triangles share from passing between them.
    public static final float EDGE_TOLERANCE = 1e-6f;

    /// The stack a walk needs: one entry a level of the deeper tree.
    public static final int STACK = BvhBuilder.MAX_DEPTH;

    private final MeshLevels meshes;
    private final InstanceLevel instances;
    private final int[] topNodes;
    private final int[] bottomNodes;
    private final int[] triangles;
    private final int[] placed;
    private final float[] normals;

    /// A walk of `meshes` placed by `instances`.
    public BvhTraversal(MeshLevels meshes, InstanceLevel instances) {
        this.meshes = Objects.requireNonNull(meshes, "meshes");
        this.instances = Objects.requireNonNull(instances, "instances");
        this.topNodes = instances.nodeWords();
        this.bottomNodes = meshes.nodeWords();
        this.triangles = meshes.triangleWords();
        this.placed = instances.instanceWords();
        this.normals = meshes.normals();
    }

    /// What it walks: the meshes.
    public MeshLevels meshes() {
        return meshes;
    }

    /// What it walks: the instances.
    public InstanceLevel instances() {
        return instances;
    }

    /// The closest hit along the ray, or empty.
    public Optional<Found> closest(Vector3 origin, Vector3 direction, float tMin, float tMax) {
        var walk = new Walk(origin, direction, tMin, tMax, false);
        walk.run();
        return walk.found ? Optional.of(walk.result()) : Optional.empty();
    }

    /// Whether anything is hit along the ray.
    public boolean anyHit(Vector3 origin, Vector3 direction, float tMin, float tMax) {
        var walk = new Walk(origin, direction, tMin, tMax, true);
        walk.run();
        return walk.found;
    }

    /// A ray's hit.
    ///
    /// @param instanceId  the instance's id
    /// @param primitiveId the triangle's index in its mesh
    /// @param distance    how far along the ray
    /// @param u           the weight of the triangle's second corner
    /// @param v           the weight of its third
    /// @param normal      the triangle's normal in the world, unit length,
    ///                    whichever way it faces
    public record Found(int instanceId, int primitiveId, float distance, float u, float v, Vector3 normal) {}

    /// The inverse of a direction, its zero components [#TINY] first.
    static float[] safeInverse(float x, float y, float z) {
        return new float[] {1f / nonZero(x), 1f / nonZero(y), 1f / nonZero(z)};
    }

    private static float nonZero(float value) {
        return Math.abs(value) < TINY ? Math.copySign(TINY, value) : value;
    }

    /// Where the ray enters the box of node `node` of `words`, clipped to
    /// `[tMin, tMax]`, or infinity where it misses it.
    static float enter(int[] words, int node, float[] origin, float[] inverse, float tMin, float tMax) {
        var at = node * Nodes.WORDS;
        var t1x = (Float.intBitsToFloat(words[at]) - origin[0]) * inverse[0];
        var t1y = (Float.intBitsToFloat(words[at + 1]) - origin[1]) * inverse[1];
        var t1z = (Float.intBitsToFloat(words[at + 2]) - origin[2]) * inverse[2];
        var t2x = (Float.intBitsToFloat(words[at + 4]) - origin[0]) * inverse[0];
        var t2y = (Float.intBitsToFloat(words[at + 5]) - origin[1]) * inverse[1];
        var t2z = (Float.intBitsToFloat(words[at + 6]) - origin[2]) * inverse[2];
        var near = Math.max(Math.max(Math.min(t1x, t2x), Math.min(t1y, t2y)), Math.min(t1z, t2z));
        var far = Math.min(Math.min(Math.max(t1x, t2x), Math.max(t1y, t2y)), Math.max(t1z, t2z));
        near = Math.max(near, tMin);
        far = Math.min(far, tMax);
        return near <= far ? near : Float.POSITIVE_INFINITY;
    }

    /// What a walk does at a leaf: tries its `count` items from `first`, and
    /// says whether the walk is over.
    @FunctionalInterface
    private interface Leaf {
        boolean visit(int first, int count);
    }

    /// One ray's walk, and what it found.
    private final class Walk {

        private final float[] origin;
        private final float[] direction;
        private final float tMin;
        private final boolean any;
        private float best;
        private boolean found;
        private int instance;
        private int hitInstance;
        private int hitPrimitive;
        private float hitU;
        private float hitV;

        Walk(Vector3 origin, Vector3 direction, float tMin, float tMax, boolean any) {
            this.origin = new float[] {origin.x(), origin.y(), origin.z()};
            this.direction = new float[] {direction.x(), direction.y(), direction.z()};
            this.tMin = tMin;
            this.best = tMax;
            this.any = any;
        }

        void run() {
            var inverse = safeInverse(direction[0], direction[1], direction[2]);
            walk(topNodes, 0, origin, inverse, (first, count) -> {
                for (var k = 0; k < count; k++) {
                    if (instance(first + k)) {
                        return true;
                    }
                }
                return false;
            });
        }

        /// Walks the tree in `words` from `root` with a ray from `from` whose
        /// inverse direction is `inverse`.
        private void walk(int[] words, int root, float[] from, float[] inverse, Leaf leaf) {
            if (enter(words, root, from, inverse, tMin, best) == Float.POSITIVE_INFINITY) {
                return;
            }
            var stack = new int[STACK];
            var entries = new float[STACK];
            var depth = 0;
            var node = root;
            while (true) {
                var at = node * Nodes.WORDS;
                var link = words[at + 3];
                var tail = words[at + 7];
                var next = -1;
                if ((tail & Nodes.LEAF) != 0) {
                    if (leaf.visit(link, tail & ~Nodes.LEAF)) {
                        return;
                    }
                } else {
                    var left = node + 1;
                    var tLeft = enter(words, left, from, inverse, tMin, best);
                    var tRight = enter(words, link, from, inverse, tMin, best);
                    if (tLeft != Float.POSITIVE_INFINITY && tRight != Float.POSITIVE_INFINITY) {
                        var leftFirst = tLeft <= tRight;
                        stack[depth] = leftFirst ? link : left;
                        entries[depth] = leftFirst ? tRight : tLeft;
                        depth++;
                        next = leftFirst ? left : link;
                    } else if (tLeft != Float.POSITIVE_INFINITY) {
                        next = left;
                    } else if (tRight != Float.POSITIVE_INFINITY) {
                        next = link;
                    }
                }
                while (next < 0 && depth > 0) {
                    depth--;
                    if (entries[depth] < best) {
                        next = stack[depth];
                    }
                }
                if (next < 0) {
                    return;
                }
                node = next;
            }
        }

        /// Tries instance `index` of the top level's order; whether the walk
        /// is over.
        private boolean instance(int index) {
            var at = index * InstanceLevel.INSTANCE_WORDS;
            if (((placed[at + 14] >>> 24) & Instance.VISIBLE) == 0) {
                return false;
            }
            var r = rows(at);
            // The ray in the mesh's space. Its direction is not normalised, so
            // a distance along it is a distance along the world's ray.
            var from = new float[] {
                r[0] * origin[0] + r[1] * origin[1] + r[2] * origin[2] + r[3],
                r[4] * origin[0] + r[5] * origin[1] + r[6] * origin[2] + r[7],
                r[8] * origin[0] + r[9] * origin[1] + r[10] * origin[2] + r[11]
            };
            var along = new float[] {
                r[0] * direction[0] + r[1] * direction[1] + r[2] * direction[2],
                r[4] * direction[0] + r[5] * direction[1] + r[6] * direction[2],
                r[8] * direction[0] + r[9] * direction[1] + r[10] * direction[2]
            };
            instance = index;
            var inverse = safeInverse(along[0], along[1], along[2]);
            walk(bottomNodes, placed[at + 12], from, inverse, (first, count) -> {
                for (var k = 0; k < count; k++) {
                    if (triangle(first + k, from, along) && any) {
                        return true;
                    }
                }
                return false;
            });
            return any && found;
        }

        private float[] rows(int at) {
            var r = new float[12];
            for (var w = 0; w < 12; w++) {
                r[w] = Float.intBitsToFloat(placed[at + w]);
            }
            return r;
        }

        /// Möller–Trumbore, both faces: whether triangle `index` is hit closer
        /// than the best so far, which it then becomes.
        private boolean triangle(int index, float[] from, float[] along) {
            var at = index * MeshLevels.TRIANGLE_WORDS;
            var v0x = Float.intBitsToFloat(triangles[at]);
            var v0y = Float.intBitsToFloat(triangles[at + 1]);
            var v0z = Float.intBitsToFloat(triangles[at + 2]);
            var e1x = Float.intBitsToFloat(triangles[at + 4]);
            var e1y = Float.intBitsToFloat(triangles[at + 5]);
            var e1z = Float.intBitsToFloat(triangles[at + 6]);
            var e2x = Float.intBitsToFloat(triangles[at + 8]);
            var e2y = Float.intBitsToFloat(triangles[at + 9]);
            var e2z = Float.intBitsToFloat(triangles[at + 10]);
            var qx = along[0];
            var qy = along[1];
            var qz = along[2];
            // p = direction × e2
            var pvx = qy * e2z - qz * e2y;
            var pvy = qz * e2x - qx * e2z;
            var pvz = qx * e2y - qy * e2x;
            var det = e1x * pvx + e1y * pvy + e1z * pvz;
            if (det == 0) {
                return false;
            }
            var inverseDet = 1f / det;
            var tvx = from[0] - v0x;
            var tvy = from[1] - v0y;
            var tvz = from[2] - v0z;
            var u = (tvx * pvx + tvy * pvy + tvz * pvz) * inverseDet;
            if (u < -EDGE_TOLERANCE || u > 1 + EDGE_TOLERANCE) {
                return false;
            }
            // q = (origin - first corner) × e1
            var qvx = tvy * e1z - tvz * e1y;
            var qvy = tvz * e1x - tvx * e1z;
            var qvz = tvx * e1y - tvy * e1x;
            var v = (qx * qvx + qy * qvy + qz * qvz) * inverseDet;
            if (v < -EDGE_TOLERANCE || u + v > 1 + EDGE_TOLERANCE) {
                return false;
            }
            var t = (e2x * qvx + e2y * qvy + e2z * qvz) * inverseDet;
            if (!(t > tMin && t < best)) {
                return false;
            }
            best = t;
            found = true;
            hitInstance = instance;
            hitPrimitive = triangles[at + 3];
            hitU = u;
            hitV = v;
            return true;
        }

        Found result() {
            var at = hitInstance * InstanceLevel.INSTANCE_WORDS;
            var r = rows(at);
            var n = 4 * (placed[at + 13] + hitPrimitive);
            // The mesh's normal through the transpose of the world-to-object
            // rows: the inverse transpose of the instance's transform, which
            // keeps a scaled instance's normals perpendicular.
            var nx = normals[n] * r[0] + normals[n + 1] * r[4] + normals[n + 2] * r[8];
            var ny = normals[n] * r[1] + normals[n + 1] * r[5] + normals[n + 2] * r[9];
            var nz = normals[n] * r[2] + normals[n + 1] * r[6] + normals[n + 2] * r[10];
            return new Found(
                    placed[at + 14] & Instance.MAX_ID,
                    hitPrimitive,
                    best,
                    hitU,
                    hitV,
                    new Vector3(nx, ny, nz).normalized());
        }
    }
}
