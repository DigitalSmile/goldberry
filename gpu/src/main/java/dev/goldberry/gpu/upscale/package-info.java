/// A 3D view drawn at a scale of its box, and brought to the box's size.
///
/// - [RenderScale] is the size drawn at against the size shown, how it is
///   brought there and how much it is sharpened: what `canvas3d
///   render-scale=`, `upscale=` and `sharpness=` say.
/// - [UpscaleFilter] is bilinear or AMD FidelityFX Super Resolution 1.
/// - [Upscaler] runs the passes on the GPU, for a `canvas3d` and for a renderer
///   that upscales a texture of its own.
///
/// Read more: [`canvas3d`](https://goldberry.dev/docs/components/gpu.html#canvas3d).
@org.jspecify.annotations.NullMarked
package dev.goldberry.gpu.upscale;
