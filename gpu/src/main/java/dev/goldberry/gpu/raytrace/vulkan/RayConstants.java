package dev.goldberry.gpu.raytrace.vulkan;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED;

import java.lang.foreign.MemorySegment;

import dev.goldberry.gpu.raytrace.LightingRequest;
import dev.goldberry.gpu.raytrace.TraceRequest;
import dev.goldberry.gpu.raytrace.Vector3;

/// The push constants of `trace.lib.hlsl`, written from a [TraceRequest]: the
/// camera's frame, the light, the picture's size, the two distances, what the
/// dispatch writes and, for the lighting, its occlusion rays and reflections,
/// in the shader's `Constants` order.
final class RayConstants {

    /// Their size, in bytes: five `float4`s and eight 32-bit values.
    static final int SIZE = 112;

    static final long ORIGIN = 0;
    static final long FORWARD = 16;
    static final long RIGHT = 32;
    static final long UP = 48;
    static final long TOWARD_LIGHT = 64;
    static final long WIDTH = 80;
    static final long HEIGHT = 84;
    static final long MAX_DISTANCE = 88;
    static final long SHADOW_BIAS = 92;
    static final long OUTPUT = 96;
    static final long OCCLUSION_RAYS = 100;
    static final long OCCLUSION_RADIUS = 104;
    static final long REFLECTIONS = 108;

    private RayConstants() {}

    /// Writes `request`'s constants into `into`, which is at least [#SIZE]
    /// bytes, for a dispatch that writes `output`.
    static void write(MemorySegment into, TraceRequest request, TraceOutput output) {
        write(into, request, output, 0, 1, false);
    }

    /// Writes `lighting`'s constants into `into`, which is at least [#SIZE]
    /// bytes, for a dispatch that writes the lighting.
    static void write(MemorySegment into, LightingRequest lighting) {
        write(
                into,
                lighting.rays(),
                TraceOutput.LIGHTING,
                lighting.occlusionRays(),
                lighting.occlusionRadius(),
                lighting.reflections());
    }

    private static void write(
            MemorySegment into,
            TraceRequest request,
            TraceOutput output,
            int occlusionRays,
            float occlusionRadius,
            boolean reflections) {
        if (into.byteSize() < SIZE) {
            throw new IllegalArgumentException(
                    "the constants are " + SIZE + " bytes, and there are " + into.byteSize());
        }
        var basis = request.camera().basis(request.width(), request.height());
        vector(into, ORIGIN, basis.origin(), 1);
        vector(into, FORWARD, basis.forward(), 0);
        vector(into, RIGHT, basis.right(), 0);
        vector(into, UP, basis.up(), 0);
        var light = request.towardLight();
        vector(into, TOWARD_LIGHT, light.orElse(Vector3.ZERO), light.isPresent() ? 1 : 0);
        into.set(JAVA_INT_UNALIGNED, WIDTH, request.width());
        into.set(JAVA_INT_UNALIGNED, HEIGHT, request.height());
        into.set(JAVA_FLOAT_UNALIGNED, MAX_DISTANCE, request.maxDistance());
        into.set(JAVA_FLOAT_UNALIGNED, SHADOW_BIAS, request.shadowBias());
        into.set(JAVA_INT_UNALIGNED, OUTPUT, output.code());
        into.set(JAVA_INT_UNALIGNED, OCCLUSION_RAYS, occlusionRays);
        into.set(JAVA_FLOAT_UNALIGNED, OCCLUSION_RADIUS, occlusionRadius);
        into.set(JAVA_INT_UNALIGNED, REFLECTIONS, reflections ? 1 : 0);
    }

    private static void vector(MemorySegment into, long at, Vector3 vector, float w) {
        into.set(JAVA_FLOAT_UNALIGNED, at, vector.x());
        into.set(JAVA_FLOAT_UNALIGNED, at + 4, vector.y());
        into.set(JAVA_FLOAT_UNALIGNED, at + 8, vector.z());
        into.set(JAVA_FLOAT_UNALIGNED, at + 12, w);
    }
}
