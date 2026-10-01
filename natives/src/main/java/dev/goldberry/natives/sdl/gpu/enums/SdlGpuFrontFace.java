package dev.goldberry.natives.sdl.gpu.enums;

/// Which winding makes a triangle front-facing, as SDL's `SDL_GPUFrontFace`.
public enum SdlGpuFrontFace {
    /// Counter-clockwise, as seen in normalised device coordinates.
    COUNTER_CLOCKWISE(0),
    /// Clockwise.
    CLOCKWISE(1);

    private final int value;

    SdlGpuFrontFace(int value) {
        this.value = value;
    }

    /// SDL's value.
    public int value() {
        return value;
    }

    /// The name the C shim reports this constant under, for the layout probe.
    public String nativeName() {
        return "SDL_GPU_FRONTFACE_" + name();
    }
}
