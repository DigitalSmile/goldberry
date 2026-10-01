package dev.goldberry.gpu;

import dev.goldberry.natives.sdl.gpu.enums.SdlGpuFrontFace;

/// Which winding, in normalised device coordinates (y up), makes a triangle
/// face front.
public enum FrontFace {
    /// Counter-clockwise: OpenGL's and glTF's convention.
    COUNTER_CLOCKWISE,
    /// Clockwise.
    CLOCKWISE;

    SdlGpuFrontFace sdl() {
        return switch (this) {
            case COUNTER_CLOCKWISE -> SdlGpuFrontFace.COUNTER_CLOCKWISE;
            case CLOCKWISE -> SdlGpuFrontFace.CLOCKWISE;
        };
    }
}
