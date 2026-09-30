package io.github.digitalsmile.goldberry.gpu;

import io.github.digitalsmile.goldberry.natives.sdl.gpu.enums.SdlGpuCullMode;

/// Which triangles a pipeline discards by the way they face.
public enum CullMode {
    /// None.
    NONE,
    /// Those facing front.
    FRONT,
    /// Those facing away: the usual choice for a closed mesh.
    BACK;

    SdlGpuCullMode sdl() {
        return switch (this) {
            case NONE -> SdlGpuCullMode.NONE;
            case FRONT -> SdlGpuCullMode.FRONT;
            case BACK -> SdlGpuCullMode.BACK;
        };
    }
}
