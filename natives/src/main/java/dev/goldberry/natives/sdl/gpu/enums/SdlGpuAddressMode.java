package dev.goldberry.natives.sdl.gpu.enums;

/// What a sampler reads outside a texture's 0 to 1, as SDL's
/// `SDL_GPUSamplerAddressMode`.
public enum SdlGpuAddressMode {
    /// Wraps around: a tiled texture.
    REPEAT(0),
    /// Wraps around, mirrored every other time.
    MIRRORED_REPEAT(1),
    /// The edge texel: what the toolkit's own quads read with.
    CLAMP_TO_EDGE(2);

    private final int value;

    SdlGpuAddressMode(int value) {
        this.value = value;
    }

    /// SDL's value.
    public int value() {
        return value;
    }

    /// The name the C shim reports this constant under, for the layout probe.
    public String nativeName() {
        return "SDL_GPU_SAMPLERADDRESSMODE_" + name();
    }
}
