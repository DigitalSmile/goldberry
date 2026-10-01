package dev.goldberry.natives.sdl.gpu.enums;

/// How a pipeline assembles vertices, as SDL's `SDL_GPUPrimitiveType`.
public enum SdlGpuPrimitiveType {
    /// Every three vertices a triangle.
    TRIANGLE_LIST(0, "TRIANGLELIST"),
    /// Each vertex after the second makes a triangle with the two before it.
    TRIANGLE_STRIP(1, "TRIANGLESTRIP"),
    /// Every two vertices a line.
    LINE_LIST(2, "LINELIST"),
    /// Each vertex after the first makes a line with the one before it.
    LINE_STRIP(3, "LINESTRIP"),
    /// Every vertex a point.
    POINT_LIST(4, "POINTLIST");

    private final int value;
    private final String suffix;

    SdlGpuPrimitiveType(int value, String suffix) {
        this.value = value;
        this.suffix = suffix;
    }

    /// SDL's value.
    public int value() {
        return value;
    }

    /// The name the C shim reports this constant under, for the layout probe.
    public String nativeName() {
        return "SDL_GPU_PRIMITIVETYPE_" + suffix;
    }
}
