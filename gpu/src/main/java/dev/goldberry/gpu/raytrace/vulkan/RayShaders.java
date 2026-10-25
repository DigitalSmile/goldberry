package dev.goldberry.gpu.raytrace.vulkan;

import java.io.IOException;
import java.io.UncheckedIOException;

/// The ray tracer's shader library, `trace.lib.hlsl` compiled to SPIR-V by
/// `:gpu:compileRayShaders`, and the names of its entry points.
final class RayShaders {

    /// Where `:gpu:compileRayShaders` writes, as a resource of this module.
    static final String DIRECTORY = "/dev/goldberry/gpu/raytrace/shaders/";

    /// The library's entry points, in the order the pipeline's stages are
    /// made: ray generation, the primary miss, the shadow miss, the closest
    /// hit.
    static final String RAYGEN = "RayGen";

    static final String MISS = "Miss";
    static final String SHADOW_MISS = "ShadowMiss";
    static final String CLOSEST_HIT = "ClosestHit";

    /// SPIR-V's magic number, its first word.
    static final int SPIRV_MAGIC = 0x0723_0203;

    private RayShaders() {}

    /// The library's SPIR-V.
    ///
    /// @throws IllegalStateException when the resource is missing or is not
    ///                               SPIR-V: a build that skipped the shaders
    static byte[] trace() {
        return load("trace.spv");
    }

    static byte[] load(String name) {
        try (var in = RayShaders.class.getResourceAsStream(DIRECTORY + name)) {
            if (in == null) {
                throw new IllegalStateException(DIRECTORY + name + " is missing: run ./gradlew :gpu:compileRayShaders");
            }
            var code = in.readAllBytes();
            if (code.length < 20 || code.length % 4 != 0 || magic(code) != SPIRV_MAGIC) {
                throw new IllegalStateException(DIRECTORY + name + " is not SPIR-V");
            }
            return code;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static int magic(byte[] code) {
        return (code[0] & 0xFF) | (code[1] & 0xFF) << 8 | (code[2] & 0xFF) << 16 | (code[3] & 0xFF) << 24;
    }
}
