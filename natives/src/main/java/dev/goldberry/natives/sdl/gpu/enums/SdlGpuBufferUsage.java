package dev.goldberry.natives.sdl.gpu.enums;

import java.util.Collection;

/// What a buffer may be used for, as SDL's `SDL_GPU_BUFFERUSAGE_*` bits.
///
/// The uses a draw and a compute dispatch have. Indirect buffers join when a
/// caller for them does.
///
/// Read more: [The GPU canvas](https://goldberry.dev/docs/components/gpu.html#canvas3d) and
/// [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
public enum SdlGpuBufferUsage {
    /// Vertices, bound to a pipeline's vertex-buffer slot.
    VERTEX(1),
    /// Indices, 16 or 32 bits each, for an indexed draw.
    INDEX(1 << 1),
    /// Read as a storage buffer by a vertex or fragment shader.
    GRAPHICS_STORAGE_READ(1 << 3),
    /// Read as a storage buffer by a compute shader.
    COMPUTE_STORAGE_READ(1 << 4),
    /// Written as a storage buffer by a compute shader.
    COMPUTE_STORAGE_WRITE(1 << 5);

    private final int bit;

    SdlGpuBufferUsage(int bit) {
        this.bit = bit;
    }

    /// SDL's bit.
    public int bit() {
        return bit;
    }

    /// The name the C shim reports this constant under, for the layout probe.
    public String nativeName() {
        return "SDL_GPU_BUFFERUSAGE_" + name();
    }

    /// The bits of `usages`, or'd together.
    public static int mask(Collection<SdlGpuBufferUsage> usages) {
        var mask = 0;
        for (var usage : usages) {
            mask |= usage.bit;
        }
        return mask;
    }
}
