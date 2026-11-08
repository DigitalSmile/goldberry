/// The software tier's scene: a two-level bounding volume hierarchy built on
/// the CPU, laid out for the compute shaders that walk it, and a walk of the
/// same layout in Java that finds what they find.
///
/// Not exported. The software tier is its one caller, and its tests.
///
/// Read more: [The GPU canvas](https://goldberry.dev/docs/components/gpu.html).
@org.jspecify.annotations.NullMarked
package dev.goldberry.gpu.raytrace.bvh;
