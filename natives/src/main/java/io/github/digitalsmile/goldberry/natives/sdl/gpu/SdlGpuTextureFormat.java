package io.github.digitalsmile.goldberry.natives.sdl.gpu;

/// The texture formats the toolkit uses, with SDL's `SDL_GPUTextureFormat`
/// values.
///
/// Checked against the compiled library by the layout table, as every other
/// enumerator the bindings hard-code is: SDL's list is long, ordered, and has
/// been inserted into before.
public enum SdlGpuTextureFormat {
    /// One 8-bit channel: a luma plane, or I420's chroma planes.
    R8_UNORM(2, 1),
    /// Two 8-bit channels: NV12's interleaved chroma plane.
    R8G8_UNORM(3, 2),
    /// Four 8-bit channels in memory order R, G, B, A.
    R8G8B8A8_UNORM(4, 4),
    /// One 16-bit channel: a 10-bit luma plane (P010, I010).
    R16_UNORM(5, 2),
    /// Two 16-bit channels: P010's interleaved chroma plane.
    R16G16_UNORM(6, 4),
    /// Four 8-bit channels in memory order B, G, R, A: what Blend2D paints, so
    /// the UI uploads without a conversion.
    B8G8R8A8_UNORM(12, 4),
    /// A 16-bit depth target: enough for most scenes, and every device has it.
    D16_UNORM(58, 2),
    /// A 32-bit float depth target.
    D32_FLOAT(60, 4);

    private final int value;
    private final int bytesPerPixel;

    SdlGpuTextureFormat(int value, int bytesPerPixel) {
        this.value = value;
        this.bytesPerPixel = bytesPerPixel;
    }

    /// SDL's value.
    public int value() {
        return value;
    }

    /// How many bytes one pixel takes.
    public int bytesPerPixel() {
        return bytesPerPixel;
    }

    /// Whether this is a depth format: one a render pass tests depth against,
    /// never a colour target.
    public boolean isDepth() {
        return this == D16_UNORM || this == D32_FLOAT;
    }

    /// The name the C shim reports this constant under, for the layout probe.
    public String nativeName() {
        return "SDL_GPU_TEXTUREFORMAT_" + name();
    }
}
