/// What the ray tracer's two tiers share: the [dev.goldberry.gpu.raytrace.tier.Tier]
/// each implements, the scenes they build, the constants their shaders read,
/// the face normals of the lighting and the layouts of what they write.
///
/// Not exported. [dev.goldberry.gpu.raytrace.RayTracer] is its one caller.
///
/// Read more: [The GPU canvas](https://goldberry.dev/docs/components/gpu.html).
@org.jspecify.annotations.NullMarked
package dev.goldberry.gpu.raytrace.tier;
