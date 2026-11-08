package dev.goldberry.gpu.raytrace.bvh;

import java.util.Optional;

import dev.goldberry.gpu.raytrace.Transform;

/// The inverse of an instance's transform, which takes a ray from the world
/// into its mesh's own space, as a ray tracer's `WorldToObject` does.
final class Affine {

    private Affine() {}

    /// The inverse of `transform`, row-major as [Transform#toRowMajor], worked
    /// out in double precision; empty when it has none: a transform that
    /// flattens its mesh, which no ray can hit.
    static Optional<float[]> inverse(Transform transform) {
        var m = transform.toRowMajor();
        double a = m[0], b = m[1], c = m[2], d = m[4], e = m[5], f = m[6], g = m[8], h = m[9], i = m[10];
        var c00 = e * i - f * h;
        var c01 = c * h - b * i;
        var c02 = b * f - c * e;
        var c10 = f * g - d * i;
        var c11 = a * i - c * g;
        var c12 = c * d - a * f;
        var c20 = d * h - e * g;
        var c21 = b * g - a * h;
        var c22 = a * e - b * d;
        var det = a * c00 + b * c10 + c * c20;
        var scale = Math.max(Math.abs(a), Math.max(Math.abs(e), Math.abs(i)));
        if (!(Math.abs(det) > 1e-30 * Math.max(1, scale * scale * scale)) || !Double.isFinite(det)) {
            return Optional.empty();
        }
        var r00 = c00 / det;
        var r01 = c01 / det;
        var r02 = c02 / det;
        var r10 = c10 / det;
        var r11 = c11 / det;
        var r12 = c12 / det;
        var r20 = c20 / det;
        var r21 = c21 / det;
        var r22 = c22 / det;
        double tx = m[3], ty = m[7], tz = m[11];
        return Optional.of(new float[] {
            (float) r00,
            (float) r01,
            (float) r02,
            (float) -(r00 * tx + r01 * ty + r02 * tz),
            (float) r10,
            (float) r11,
            (float) r12,
            (float) -(r10 * tx + r11 * ty + r12 * tz),
            (float) r20,
            (float) r21,
            (float) r22,
            (float) -(r20 * tx + r21 * ty + r22 * tz)
        });
    }
}
