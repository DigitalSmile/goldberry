package dev.goldberry.gpu.raytrace;

import java.util.OptionalInt;

/// What `trace.lib.hlsl`'s lighting output should hold at a pixel, worked out
/// with the [ReferenceTracer] in double precision: its shadow, its occlusion
/// rays drawn from the shader's own sequence, and its reflection.
///
/// A pixel is answered only where no ray of its own came within
/// [#EDGE] of deciding the other way; elsewhere single precision may
/// legitimately differ, and the answer is empty.
final class LightingReference {

    /// The barycentric margin under which a ray's answer is not trusted.
    static final double EDGE = 2e-3;

    private final ReferenceTracer tracer;
    private final LightingRequest request;
    private final double[] light;

    LightingReference(SceneGeometry geometry, LightingRequest request) {
        this.tracer = new ReferenceTracer(geometry);
        this.request = request;
        this.light = ReferenceTracer.of(request.rays().towardLight().orElseThrow());
    }

    /// What one pixel holds.
    ///
    /// @param hit            whether the primary ray hit anything
    /// @param lit            whether the light reaches it
    /// @param open           the shading byte for its openness, 0 to 255
    /// @param reflectedId    what its reflection sees
    /// @param reflectedLight how lit that is, 0 to 1
    record Pixel(boolean hit, boolean lit, int open, OptionalInt reflectedId, double reflectedLight) {}

    /// What pixel `x, y` holds, or null where a ray of it is too near an edge.
    Pixel at(int x, int y) {
        var rays = request.rays();
        var ray = rays.camera().ray(x, y, rays.width(), rays.height());
        var origin = ReferenceTracer.of(ray.origin());
        var direction = ReferenceTracer.of(ray.direction());
        var primary = tracer.closest(origin, direction, 0, rays.maxDistance());
        if (primary.margin() < EDGE) {
            return null;
        }
        if (primary.hit().isEmpty()) {
            return new Pixel(false, true, 255, OptionalInt.empty(), 0);
        }
        var hit = primary.hit().get();
        var t = hit.distance();
        var at = along(origin, direction, t);
        var n = facing(tracer.normal(hit.instanceId(), hit.primitiveId()), direction);
        var tMin = rays.shadowBias() * Math.max(1, t);

        var shadow = tracer.anyHit(at, light, tMin, rays.maxDistance());
        if (shadow.margin() < EDGE) {
            return null;
        }
        var open = 255;
        if (request.occlusionRays() > 0) {
            var escaped = 0;
            var index = y * rays.width() + x;
            for (var k = 0; k < request.occlusionRays(); k++) {
                var occlusion = tracer.anyHit(at, occlusionDirection(n, index, k), tMin, request.occlusionRadius());
                if (occlusion.margin() < EDGE) {
                    return null;
                }
                if (occlusion.hit().isEmpty()) {
                    escaped++;
                }
            }
            open = (escaped * 255 + request.occlusionRays() / 2) / request.occlusionRays();
        }
        var reflectedId = OptionalInt.empty();
        var reflectedLight = 0.0;
        if (request.reflections()) {
            var d = dot(direction, n);
            var mirror =
                    new double[] {direction[0] - 2 * d * n[0], direction[1] - 2 * d * n[1], direction[2] - 2 * d * n[2]
                    };
            var seen = tracer.closest(at, mirror, tMin, rays.maxDistance());
            if (seen.margin() < EDGE) {
                return null;
            }
            if (seen.hit().isPresent()) {
                var other = seen.hit().get();
                reflectedId = OptionalInt.of(other.instanceId());
                var seenAt = along(at, mirror, other.distance());
                var seenNormal = facing(tracer.normal(other.instanceId(), other.primitiveId()), mirror);
                var lambert = Math.clamp(dot(seenNormal, light), 0, 1);
                if (lambert > 0) {
                    var seenShadow = tracer.anyHit(
                            seenAt, light, rays.shadowBias() * Math.max(1, t + other.distance()), rays.maxDistance());
                    if (seenShadow.margin() < EDGE) {
                        return null;
                    }
                    reflectedLight = seenShadow.hit().isEmpty() ? lambert : 0;
                }
            }
        }
        return new Pixel(true, shadow.hit().isEmpty(), open, reflectedId, reflectedLight);
    }

    /// `trace.lib.hlsl`'s `mix`: Chris Wellons' lowbias32.
    static int mix(int x) {
        x ^= x >>> 16;
        x *= 0x7feb352d;
        x ^= x >>> 15;
        x *= 0x846ca68b;
        x ^= x >>> 16;
        return x;
    }

    /// `trace.lib.hlsl`'s `unit`: the top 24 bits as a float in 0 to 1.
    static float unit(int hash) {
        return (hash >>> 8) * (1f / 16777216f);
    }

    /// `trace.lib.hlsl`'s `occlusionDirection`, in floats as the shader works.
    static double[] occlusionDirection(double[] normal, int index, int k) {
        var seed = index * 64 + k;
        var u1 = unit(mix(seed));
        var u2 = unit(mix(seed ^ 0x9e3779b9));
        var r = (float) Math.sqrt(u1);
        var phi = 6.28318530718f * u2;
        var lx = r * (float) Math.cos(phi);
        var ly = r * (float) Math.sin(phi);
        var lz = (float) Math.sqrt(1 - u1);
        var nx = (float) normal[0];
        var ny = (float) normal[1];
        var nz = (float) normal[2];
        var s = nz >= 0 ? 1f : -1f;
        var a = -1f / (s + nz);
        var b = nx * ny * a;
        var x = (1 + s * nx * nx * a) * lx + b * ly + nx * lz;
        var y = s * b * lx + (s + ny * ny * a) * ly + ny * lz;
        var z = -s * nx * lx - ny * ly + nz * lz;
        var length = Math.sqrt(x * x + y * y + z * z);
        return new double[] {x / length, y / length, z / length};
    }

    private static double[] facing(double[] normal, double[] direction) {
        return dot(normal, direction) > 0 ? new double[] {-normal[0], -normal[1], -normal[2]} : normal;
    }

    private static double[] along(double[] origin, double[] direction, double t) {
        return new double[] {origin[0] + direction[0] * t, origin[1] + direction[1] * t, origin[2] + direction[2] * t};
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }
}
