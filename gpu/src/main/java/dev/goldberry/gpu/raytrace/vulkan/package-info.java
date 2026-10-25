/// The ray tracer on Vulkan: the device it makes for itself with `:natives`'
/// Vulkan bindings, the acceleration structures, the ray-tracing pipeline of
/// `trace.lib.hlsl`, and the buffers the hits come back through.
///
/// Not exported. [dev.goldberry.gpu.raytrace.RayTracer] is its one caller.
///
/// Read more: [The GPU canvas](https://goldberry.dev/docs/components/gpu.html).
@org.jspecify.annotations.NullMarked
package dev.goldberry.gpu.raytrace.vulkan;
