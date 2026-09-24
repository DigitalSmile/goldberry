package io.github.digitalsmile.goldberry.gpu;

import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuIndexSize;

/// How wide each index in an index buffer is.
public enum IndexFormat {
    /// 16 bits: a mesh of up to 65,536 vertices.
    UINT16,
    /// 32 bits.
    UINT32;

    /// How many bytes one index takes.
    public int bytes() {
        return sdl().bytes();
    }

    SdlGpuIndexSize sdl() {
        return switch (this) {
            case UINT16 -> SdlGpuIndexSize.UINT16;
            case UINT32 -> SdlGpuIndexSize.UINT32;
        };
    }
}
