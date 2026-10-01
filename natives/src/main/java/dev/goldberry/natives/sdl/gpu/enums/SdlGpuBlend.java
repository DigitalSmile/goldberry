package dev.goldberry.natives.sdl.gpu.enums;

/// How a pipeline's output meets what the target holds.
public enum SdlGpuBlend {
    /// The output replaces the target: an opaque GPU layer, or a video picture.
    REPLACE,
    /// Premultiplied "over": `src + dst × (1 − src.a)` on every channel. What the
    /// UI quad is composited with, since Blend2D paints premultiplied
    /// (`docs/gpu-plan.md`, D4).
    PREMULTIPLIED_OVER
}
