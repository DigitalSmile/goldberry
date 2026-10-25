package dev.goldberry.gpu.raytrace;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/// The tests' oracle: every ray against every triangle of every instance, in
/// double precision, with Möller and Trumbore's intersection, no acceleration
/// structure at all. What the GPU finds has to be what this finds, wherever
/// the answer does not hang on a rounding.
///
/// Each answer carries a [Answer#margin]: how far the ray passes from the
/// nearest edge that could change it, in barycentric units. A pixel whose
/// margin is tiny sits on an edge between two triangles, or on a silhouette,
/// and single precision may legitimately go either way, so the tests compare
/// the pixels that do not.
final class ReferenceTracer {

    private final List<Triangle> triangles = new ArrayList<>();

    /// One triangle of the scene, in world space.
    private record Triangle(int instanceId, int primitiveId, double[] a, double[] b, double[] c) {}

    ReferenceTracer(SceneGeometry geometry) {
        for (var instance : geometry.instances()) {
            var mesh = instance.mesh();
            for (var t = 0; t < mesh.triangleCount(); t++) {
                triangles.add(new Triangle(
                        instance.id(),
                        t,
                        world(instance, mesh.corner(t, 0)),
                        world(instance, mesh.corner(t, 1)),
                        world(instance, mesh.corner(t, 2))));
            }
        }
    }

    private static double[] world(Instance instance, Vector3 point) {
        var m = instance.transform();
        double x = point.x();
        double y = point.y();
        double z = point.z();
        return new double[] {
            m.m00() * x + m.m01() * y + m.m02() * z + m.m03(),
            m.m10() * x + m.m11() * y + m.m12() * z + m.m13(),
            m.m20() * x + m.m21() * y + m.m22() * z + m.m23()
        };
    }

    /// What the closest hit was, if any, and how far from changing it.
    ///
    /// @param hit    the closest hit, or empty for a miss
    /// @param margin the smallest barycentric distance from an edge, of the hit
    ///               triangle and of every triangle the ray nearly hit nearer
    record Answer(Optional<Hit> hit, double margin) {}

    /// The closest hit along `ray` between `tMin` and `tMax`.
    Answer closest(double[] origin, double[] direction, double tMin, double tMax) {
        Triangle best = null;
        double bestT = tMax;
        double bestU = 0;
        double bestV = 0;
        var margin = Double.POSITIVE_INFINITY;
        var near = new ArrayList<double[]>();
        for (var triangle : triangles) {
            var r = intersect(triangle, origin, direction);
            if (r == null) {
                continue;
            }
            near.add(new double[] {r[0], r[4]});
            if (r[3] >= 0 && r[0] > tMin && r[0] < bestT) {
                best = triangle;
                bestT = r[0];
                bestU = r[1];
                bestV = r[2];
            }
        }
        for (var candidate : near) {
            // A triangle beyond the hit does not matter; one nearer (or about
            // as near) that the ray just missed or just hit does.
            if (candidate[0] > tMin && candidate[0] <= bestT * (1 + 1e-4) + 1e-6) {
                margin = Math.min(margin, Math.abs(candidate[1]));
            }
        }
        if (best == null) {
            return new Answer(Optional.empty(), margin);
        }
        return new Answer(
                Optional.of(new Hit(
                        best.instanceId(), best.primitiveId(), (float) bestT, (float) bestU, (float) bestV, true)),
                margin);
    }

    /// Whether anything is hit along `ray` between `tMin` and `tMax`, and how
    /// near a triangle came to deciding it the other way.
    Answer anyHit(double[] origin, double[] direction, double tMin, double tMax) {
        var margin = Double.POSITIVE_INFINITY;
        var hit = false;
        for (var triangle : triangles) {
            var r = intersect(triangle, origin, direction);
            if (r == null || r[0] <= tMin * 0.5 || r[0] >= tMax) {
                continue;
            }
            margin = Math.min(margin, Math.abs(r[4]));
            // A hit just past tMin is as uncertain as one just inside an edge.
            margin = Math.min(margin, Math.abs(r[0] - tMin) / Math.max(tMin, 1e-9));
            if (r[3] >= 0 && r[0] > tMin) {
                hit = true;
            }
        }
        return new Answer(hit ? Optional.of(new Hit(0, 0, 0, 0, 0, false)) : Optional.empty(), margin);
    }

    /// `{t, u, v, inside, signedEdgeDistance}` where the ray meets the
    /// triangle's plane in front of the origin, or null when it is parallel
    /// or behind. `inside` is non-negative when the point is in the triangle;
    /// `signedEdgeDistance` is the smallest barycentric, negative outside.
    private static double[] intersect(Triangle triangle, double[] origin, double[] direction) {
        var e1 = minus(triangle.b(), triangle.a());
        var e2 = minus(triangle.c(), triangle.a());
        var p = cross(direction, e2);
        var determinant = dot(e1, p);
        if (Math.abs(determinant) < 1e-12) {
            return null;
        }
        var inverse = 1 / determinant;
        var s = minus(origin, triangle.a());
        var u = dot(s, p) * inverse;
        var q = cross(s, e1);
        var v = dot(direction, q) * inverse;
        var t = dot(e2, q) * inverse;
        if (t <= 0) {
            return null;
        }
        var smallest = Math.min(Math.min(u, v), 1 - u - v);
        return new double[] {t, u, v, smallest, smallest};
    }

    private static double[] minus(double[] a, double[] b) {
        return new double[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    /// `vector` as doubles.
    static double[] of(Vector3 vector) {
        return new double[] {vector.x(), vector.y(), vector.z()};
    }
}
