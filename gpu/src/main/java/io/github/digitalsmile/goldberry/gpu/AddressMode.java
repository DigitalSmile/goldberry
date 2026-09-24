package io.github.digitalsmile.goldberry.gpu;

import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuAddressMode;

/// What a sampler reads outside a texture's 0 to 1.
public enum AddressMode {
    /// The edge texel.
    CLAMP_TO_EDGE,
    /// Wraps around: a tiled texture.
    REPEAT,
    /// Wraps around, mirrored every other time.
    MIRRORED_REPEAT;

    SdlGpuAddressMode sdl() {
        return switch (this) {
            case CLAMP_TO_EDGE -> SdlGpuAddressMode.CLAMP_TO_EDGE;
            case REPEAT -> SdlGpuAddressMode.REPEAT;
            case MIRRORED_REPEAT -> SdlGpuAddressMode.MIRRORED_REPEAT;
        };
    }
}
