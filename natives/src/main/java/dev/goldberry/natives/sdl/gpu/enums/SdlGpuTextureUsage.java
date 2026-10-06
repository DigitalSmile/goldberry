package dev.goldberry.natives.sdl.gpu.enums;

import java.util.Collection;

/// What a texture may be used for, as SDL's `SDL_GPU_TEXTUREUSAGE_*` bits.
///
/// The uses a draw and a compute dispatch have. SDL's simultaneous
/// read-write bit joins when a caller for it does.
///
/// Read more: [The GPU canvas](https://goldberry.dev/docs/components/gpu.html#canvas3d) and
/// [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
public enum SdlGpuTextureUsage {
    /// Sampled by a shader: the UI texture, a video plane, a `canvas3d` result.
    SAMPLER(1),
    /// Rendered into, or cleared.
    COLOR_TARGET(1 << 1),
    /// A depth or stencil target.
    DEPTH_STENCIL_TARGET(1 << 2),
    /// Read as a storage texture by a vertex or fragment shader.
    GRAPHICS_STORAGE_READ(1 << 3),
    /// Read as a storage texture by a compute shader.
    COMPUTE_STORAGE_READ(1 << 4),
    /// Written as a storage texture by a compute shader.
    COMPUTE_STORAGE_WRITE(1 << 5);

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
