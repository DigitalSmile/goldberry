package dev.goldberry.natives.sdl.gpu.enums;

/// How wide each index of an index buffer is, as SDL's
/// `SDL_GPUIndexElementSize`.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
public enum SdlGpuIndexSize {
    /// 16-bit indices: meshes of up to 65,536 vertices.
    UINT16(0, 2, "16BIT"),
    /// 32-bit indices.
    UINT32(1, 4, "32BIT");

    private final int value;
    private final int bytes;
    private final String suffix;

    SdlGpuIndexSize(int value, int bytes, String suffix) {
        this.value = value;
        this.bytes = bytes;
        this.suffix = suffix;
    }

    /// SDL's value.
    public int value() {
        return value;
    }

    /// How many bytes one index takes.
    public int bytes() {
        return bytes;
    }

    /// The name the C shim reports this constant under, for the layout probe.
    public String nativeName() {
        return "SDL_GPU_INDEXELEMENTSIZE_" + suffix;
    }
}
