package dev.goldberry.natives.sdl.gpu.enums;

/// What a stencil test does to the stencil value of a sample, as SDL's
/// `SDL_GPUStencilOp`. SDL's `INVALID`, 0, is not modelled.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
public enum SdlGpuStencilOp {
    /// Keeps the value.
    KEEP(1),
    /// Sets it to 0.
    ZERO(2),
    /// Sets it to the pass's stencil reference.
    REPLACE(3),
    /// Adds one, stopping at 255.
    INCREMENT_AND_CLAMP(4),
    /// Takes one away, stopping at 0.
    DECREMENT_AND_CLAMP(5),
    /// Inverts every bit.
    INVERT(6),
    /// Adds one, 255 wrapping to 0.
    INCREMENT_AND_WRAP(7),
    /// Takes one away, 0 wrapping to 255.
    DECREMENT_AND_WRAP(8);

    private final int value;

    SdlGpuStencilOp(int value) {
        this.value = value;
    }

    /// SDL's value.
    public int value() {
        return value;
    }

    /// The name the C shim reports this constant under, for the layout probe.
    public String nativeName() {
        return "SDL_GPU_STENCILOP_" + name();
    }
}
