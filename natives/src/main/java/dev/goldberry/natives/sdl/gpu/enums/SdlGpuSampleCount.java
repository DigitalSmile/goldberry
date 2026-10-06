package dev.goldberry.natives.sdl.gpu.enums;

/// How many samples a texel of a render target has, as SDL's
/// `SDL_GPUSampleCount`.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
public enum SdlGpuSampleCount {
    /// No multisampling: every texture that is sampled or uploaded.
    ONE(0, 1),
    /// 2x MSAA.
    TWO(1, 2),
    /// 4x MSAA.
    FOUR(2, 4),
    /// 8x MSAA.
    EIGHT(3, 8);

    private final int value;
    private final int samples;

    SdlGpuSampleCount(int value, int samples) {
        this.value = value;
        this.samples = samples;
    }

    /// SDL's value.
    public int value() {
        return value;
    }

    /// How many samples: 1, 2, 4 or 8.
    public int samples() {
        return samples;
    }

    /// The count for `samples`.
    ///
    /// @throws IllegalArgumentException when `samples` is not 1, 2, 4 or 8
    public static SdlGpuSampleCount of(int samples) {
        for (var count : values()) {
            if (count.samples == samples) {
                return count;
            }
        }
        throw new IllegalArgumentException(samples + " samples; a target has 1, 2, 4 or 8");
    }

    /// The name the C shim reports this constant under, for the layout probe.
    public String nativeName() {
        return "SDL_GPU_SAMPLECOUNT_" + samples;
    }
}
