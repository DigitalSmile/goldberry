package io.github.digitalsmile.goldberry.gpu;

import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuFilter;

/// How a sampler reads between texels.
public enum Filter {
    /// The nearest texel: exact when a texture is drawn 1:1, as the UI is.
    NEAREST,
    /// Interpolated between the nearest texels: scaled images, video.
    LINEAR;

    SdlGpuFilter sdl() {
        return switch (this) {
            case NEAREST -> SdlGpuFilter.NEAREST;
            case LINEAR -> SdlGpuFilter.LINEAR;
        };
    }
}
