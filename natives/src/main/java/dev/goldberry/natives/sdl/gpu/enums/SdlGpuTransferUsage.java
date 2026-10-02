package dev.goldberry.natives.sdl.gpu.enums;

/// Which way a transfer buffer carries pixels, as SDL's
/// `SDL_GPUTransferBufferUsage`.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
public enum SdlGpuTransferUsage {
    /// CPU to GPU: written while mapped, then uploaded in a copy pass.
    UPLOAD(0),
    /// GPU to CPU: downloaded in a copy pass, then read while mapped once the
    /// command buffer's fence has signalled.
    DOWNLOAD(1);

    private final int value;

    SdlGpuTransferUsage(int value) {
        this.value = value;
    }

    /// SDL's value.
    public int value() {
        return value;
    }

    /// The name the C shim reports this constant under, for the layout probe.
    public String nativeName() {
        return "SDL_GPU_TRANSFERBUFFERUSAGE_" + name();
    }
}
