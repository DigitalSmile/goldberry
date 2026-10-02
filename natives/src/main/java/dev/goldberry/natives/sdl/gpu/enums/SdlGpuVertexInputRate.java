package dev.goldberry.natives.sdl.gpu.enums;

/// Whether a vertex buffer advances per vertex or per instance, as SDL's
/// `SDL_GPUVertexInputRate`.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
public enum SdlGpuVertexInputRate {
    /// One element per vertex.
    VERTEX(0),
    /// One element per instance: instanced drawing.
    INSTANCE(1);

    private final int value;

    SdlGpuVertexInputRate(int value) {
        this.value = value;
    }

    /// SDL's value.
    public int value() {
        return value;
    }

    /// The name the C shim reports this constant under, for the layout probe.
    public String nativeName() {
        return "SDL_GPU_VERTEXINPUTRATE_" + name();
    }
}
