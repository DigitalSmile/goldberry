package io.github.digitalsmile.goldberry.natives.sdl.gpu.enums;

/// How one vertex attribute is stored, as SDL's `SDL_GPUVertexElementFormat`.
///
/// The formats a mesh is commonly made of; SDL has thirty, and the rest join
/// when something asks for one.
public enum SdlGpuVertexFormat {
    /// One 32-bit unsigned integer.
    UINT(5, 4),
    /// One 32-bit float.
    FLOAT(9, 4),
    /// Two 32-bit floats: a texture coordinate, a 2D position.
    FLOAT2(10, 8),
    /// Three 32-bit floats: a position, a normal.
    FLOAT3(11, 12),
    /// Four 32-bit floats: a colour, a tangent with its sign.
    FLOAT4(12, 16),
    /// Four unsigned bytes read as 0 to 1: a packed colour.
    UBYTE4_NORM(20, 4);

    private final int value;
    private final int bytes;

    SdlGpuVertexFormat(int value, int bytes) {
        this.value = value;
        this.bytes = bytes;
    }

    /// SDL's value.
    public int value() {
        return value;
    }

    /// How many bytes one attribute of this format takes.
    public int bytes() {
        return bytes;
    }

    /// The name the C shim reports this constant under, for the layout probe.
    public String nativeName() {
        return "SDL_GPU_VERTEXELEMENTFORMAT_" + name();
    }
}
