/// The ray tracer in software: compute shaders on the window's own device
/// that walk a two-level hierarchy of boxes built on the CPU
/// ([dev.goldberry.gpu.raytrace.bvh]), writing what they find in the layouts
/// the hardware tier writes.
///
/// Not exported. [dev.goldberry.gpu.raytrace.RayTracer] is its one caller.
///
/// Read more: [The GPU canvas](https://goldberry.dev/docs/components/gpu.html).
@org.jspecify.annotations.NullMarked
package dev.goldberry.gpu.raytrace.compute;
