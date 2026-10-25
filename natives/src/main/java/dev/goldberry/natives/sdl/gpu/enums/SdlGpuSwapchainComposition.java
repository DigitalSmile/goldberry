package dev.goldberry.natives.sdl.gpu.enums;

/// What a claimed window's swapchain holds and how the display reads it, as
/// SDL's `SDL_GPUSwapchainComposition`. Every window has [#SDR]; the others are
/// the display's and the driver's to grant, and asked for first.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
public enum SdlGpuSwapchainComposition {
    /// 8-bit sRGB-encoded colour, written as is: what every window has.
    SDR(0),
    /// 8-bit sRGB, encoded as it is written: a shader writes linear colour.
    SDR_LINEAR(1),
    /// 16-bit float, linear, where 1.0 is SDR white and brighter is above it.
    HDR_EXTENDED_LINEAR(2),
    /// 10-bit HDR10: BT.2020 primaries, encoded with the ST 2084 (PQ) curve.
    HDR10_ST2084(3);

    private final int value;

    SdlGpuSwapchainComposition(int value) {
        this.value = value;
    }

    /// SDL's value.
    public int value() {
        return value;
    }

    /// The name the C shim reports this constant under, for the layout probe.
    public String nativeName() {
        return "SDL_GPU_SWAPCHAINCOMPOSITION_" + name();
    }
}
