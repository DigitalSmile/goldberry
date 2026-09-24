package io.github.digitalsmile.goldberry.gpu;

import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuBlend;

/// How a pipeline's output meets what its target already holds.
public enum BlendMode {
    /// The output replaces the target: an opaque layer, a video picture, a 3D
    /// scene.
    REPLACE,
    /// Premultiplied "over": `src + dst × (1 − src.a)` on every channel. What
    /// the UI is composited with, since it is painted premultiplied, and what
    /// any premultiplied output wants.
    PREMULTIPLIED_OVER;

    SdlGpuBlend sdl() {
        return switch (this) {
            case REPLACE -> SdlGpuBlend.REPLACE;
            case PREMULTIPLIED_OVER -> SdlGpuBlend.PREMULTIPLIED_OVER;
        };
    }
}
