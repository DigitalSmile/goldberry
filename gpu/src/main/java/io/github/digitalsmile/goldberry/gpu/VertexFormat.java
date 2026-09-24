package io.github.digitalsmile.goldberry.gpu;

import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuVertexFormat;

/// How one [VertexAttribute] is stored in its buffer.
public enum VertexFormat {
    /// One 32-bit unsigned integer.
    UINT,
    /// One 32-bit float.
    FLOAT,
    /// Two 32-bit floats: a texture coordinate.
    FLOAT2,
    /// Three 32-bit floats: a position, a normal.
    FLOAT3,
    /// Four 32-bit floats: a colour.
    FLOAT4,
    /// Four unsigned bytes read as 0 to 1: a packed colour.
    UBYTE4_NORM;

    /// How many bytes one attribute of this format takes.
    public int bytes() {
        return sdl().bytes();
    }

    SdlGpuVertexFormat sdl() {
        return switch (this) {
            case UINT -> SdlGpuVertexFormat.UINT;
            case FLOAT -> SdlGpuVertexFormat.FLOAT;
            case FLOAT2 -> SdlGpuVertexFormat.FLOAT2;
            case FLOAT3 -> SdlGpuVertexFormat.FLOAT3;
            case FLOAT4 -> SdlGpuVertexFormat.FLOAT4;
            case UBYTE4_NORM -> SdlGpuVertexFormat.UBYTE4_NORM;
        };
    }
}
