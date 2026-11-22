/// The memory uploads pass through on their way to a texture or buffer: one
/// transfer buffer per device, and how the regions of an upload are laid out
/// in it so that the driver copies them as they are.
///
/// Not exported. The composited window's UI upload and the public API's copy
/// pass are its callers.
///
/// Read more: [Shaders](https://goldberry.dev/docs/components/gpu.html#shaders).
@org.jspecify.annotations.NullMarked
package dev.goldberry.gpu.render.staging;
