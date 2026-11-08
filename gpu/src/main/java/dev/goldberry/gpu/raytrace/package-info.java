/// Ray tracing: rays through a scene of triangle meshes, and what each
/// pixel's ray hit and whether the light reaches it, in software or in
/// hardware behind one [dev.goldberry.gpu.raytrace.RayTracer].
///
/// In software it traces on the window's own device, in compute shaders that
/// walk a hierarchy of boxes built on the CPU. SDL's GPU has no ray tracing, so
/// in hardware it traces on a Vulkan device of its own beside SDL's, and its
/// [dev.goldberry.gpu.raytrace.Hits] come back through this process's memory
/// for a renderer to upload into SDL's textures and buffers. Where a tier
/// cannot trace, [dev.goldberry.gpu.raytrace.RayTracer#availability(dev.goldberry.gpu.GpuDevice, RayTracingMode)]
/// says why, in a sentence a settings screen can show.
///
/// Exported.
///
/// Read more: [The GPU canvas](https://goldberry.dev/docs/components/gpu.html).
@org.jspecify.annotations.NullMarked
package dev.goldberry.gpu.raytrace;
