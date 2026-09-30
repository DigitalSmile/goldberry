package io.github.digitalsmile.goldberry.natives.sdl.gpu.enums;

/// Which triangles a pipeline discards by their facing, as SDL's
/// `SDL_GPUCullMode`.
public enum SdlGpuCullMode {
    /// None: what the toolkit's quads are drawn with.
    NONE(0),
    /// Front-facing triangles.
    FRONT(1),
    /// Back-facing triangles: the usual choice for closed meshes.
    BACK(2);

    private final int value;

    SdlGpuCullMode(int value) {
        this.value = value;
    }

    /// SDL's value.
    public int value() {
        return value;
    }

    /// The name the C shim reports this constant under, for the layout probe.
    public String nativeName() {
        return "SDL_GPU_CULLMODE_" + name();
    }
}
