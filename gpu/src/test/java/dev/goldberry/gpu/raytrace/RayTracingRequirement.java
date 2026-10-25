package dev.goldberry.gpu.raytrace;

import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/// A test that needs a ray-tracing device skips where there is none, and
/// fails instead where `goldberry.raytracing.required=true` says there must
/// be one: a lane with lavapipe, or this machine's GPU. The reason is the
/// probe's, which names each device and what it lacks.
final class RayTracingRequirement {

    static final String REQUIRED = "goldberry.raytracing.required";

    private RayTracingRequirement() {}

    /// A tracer, or the test skipped or failed.
    static RayTracer open() {
        var availability = RayTracer.availability();
        if (availability instanceof RayTracing.Unavailable(var reason)) {
            if (Boolean.getBoolean(REQUIRED)) {
                fail(REQUIRED + " is set, and there is no ray tracing: " + reason);
            }
            assumeTrue(false, "no ray tracing here: " + reason);
        }
        return RayTracer.open(RayTracingMode.HARDWARE).orElseThrow();
    }
}
