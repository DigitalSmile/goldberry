package dev.goldberry.natives.sdl.gpu.enums;

/// What shape a texture is, as SDL's `SDL_GPUTextureType`: what a shader
/// declares it as, and what `layer_count_or_depth` counts.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
public enum SdlGpuTextureType {
    /// One 2D image: `Texture2D`.
    TWO_D(0, "2D"),
    /// Layers of 2D images of one size, sampled by index: `Texture2DArray`.
    TWO_D_ARRAY(1, "2D_ARRAY"),
    /// A volume, `layer_count_or_depth` slices deep and filtered between
    /// them: `Texture3D`.
    THREE_D(2, "3D"),
    /// Six square faces sampled by direction: `TextureCube`.
    CUBE(3, "CUBE");

    private final int value;
    private final String sdlName;

    SdlGpuTextureType(int value, String sdlName) {
        this.value = value;
        this.sdlName = sdlName;
    }

    /// SDL's value.
    public int value() {
        return value;
    }

    /// The name the C shim reports this constant under, for the layout probe.
    public String nativeName() {
        return "SDL_GPU_TEXTURETYPE_" + sdlName;
    }
}
