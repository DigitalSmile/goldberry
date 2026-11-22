/// Rasters painted where the GPU copies from: a frame-sized upload buffer that
/// stays mapped for its life, its damaged regions copied into a texture with
/// no copy on the CPU, and the check at device creation that says whether the
/// memory is fit to paint in.
///
/// Not exported. The composited window is its caller, and the public API's
/// raster texture is built on it.
///
/// Read more: [What it costs](https://goldberry.dev/docs/components/gpu.html#what-it-costs).
@org.jspecify.annotations.NullMarked
package dev.goldberry.gpu.render.mapped;
