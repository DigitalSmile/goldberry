package dev.goldberry.natives.sdl.gpu.enums;

/// How a pipeline's output meets what the target holds.
///
/// Read more: [The GPU canvas](https://goldberry.dev/docs/components/gpu.html#what-the-module-does-to-a-window) and
/// [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
public enum SdlGpuBlend {
    /// The output replaces the target: an opaque GPU layer, or a video picture.
    REPLACE,
    /// Premultiplied "over": `src + dst × (1 − src.a)` on every channel. What the
    /// UI quad is composited over the GPU layers with, since Blend2D paints
    /// premultiplied.
    PREMULTIPLIED_OVER
}
