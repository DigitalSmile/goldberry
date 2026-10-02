package dev.goldberry.natives.sdl.gpu.enums;

import java.util.Collection;

/// What a texture may be used for, as SDL's `SDL_GPU_TEXTUREUSAGE_*` bits.
///
/// Only the uses the toolkit has a caller for. SDL's storage-read and
/// storage-write bits join when compute does.
///
/// Read more: [The GPU canvas](https://goldberry.dev/docs/components/gpu.html#canvas3d) and
/// [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
public enum SdlGpuTextureUsage {
    /// Sampled by a shader: the UI texture, a video plane, a `canvas3d` result.
    SAMPLER(1),
    /// Rendered into, or cleared.
    COLOR_TARGET(1 << 1),
    /// A depth or stencil target.
    DEPTH_STENCIL_TARGET(1 << 2);

    private final int bit;

    SdlGpuTextureUsage(int bit) {
        this.bit = bit;
    }

    /// SDL's bit.
    public int bit() {
        return bit;
    }

    /// The name the C shim reports this constant under, for the layout probe.
    public String nativeName() {
        return "SDL_GPU_TEXTUREUSAGE_" + name();
    }

    /// The bits of `usages`, or'd together.
    public static int mask(Collection<SdlGpuTextureUsage> usages) {
        var mask = 0;
        for (var usage : usages) {
            mask |= usage.bit;
        }
        return mask;
    }
}
