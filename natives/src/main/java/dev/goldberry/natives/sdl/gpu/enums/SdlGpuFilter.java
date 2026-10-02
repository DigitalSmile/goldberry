package dev.goldberry.natives.sdl.gpu.enums;

import dev.goldberry.natives.sdl.calls.SdlGpuCommandCalls;

/// How a blit samples when the regions differ in size, as SDL's
/// `SDL_GPUFilter`.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
public enum SdlGpuFilter {
    /// The nearest texel: exact when the sizes are equal.
    NEAREST(SdlGpuCommandCalls.FILTER_NEAREST),
    /// Interpolated between texels.
    LINEAR(SdlGpuCommandCalls.FILTER_LINEAR);

    private final int value;

    SdlGpuFilter(int value) {
        this.value = value;
    }

    /// SDL's value.
    public int value() {
        return value;
    }
}
