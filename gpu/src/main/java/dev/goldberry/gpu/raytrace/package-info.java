/// Ray tracing: rays through a scene of triangle meshes, traced in hardware,
/// and what each pixel's ray hit and whether the light reaches it.
///
/// SDL's GPU has none, so a [dev.goldberry.gpu.raytrace.RayTracer] traces on a
/// Vulkan device of its own beside SDL's, and its
/// [dev.goldberry.gpu.raytrace.Hits] come back through this process's memory
/// for a renderer to upload into SDL's textures and buffers. Where there is no
/// such device, [dev.goldberry.gpu.raytrace.RayTracer#availability()] says why,
/// in a sentence a settings screen can show.
///
/// Exported.
///
/// Read more: [The GPU canvas](https://goldberry.dev/docs/components/gpu.html).
@org.jspecify.annotations.NullMarked
package dev.goldberry.gpu.raytrace;
