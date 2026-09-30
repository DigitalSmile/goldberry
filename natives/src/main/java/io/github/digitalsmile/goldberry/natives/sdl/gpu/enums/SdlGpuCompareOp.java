package io.github.digitalsmile.goldberry.natives.sdl.gpu.enums;

/// How a depth test compares a fragment with what the target holds, as SDL's
/// `SDL_GPUCompareOp`. SDL's `INVALID`, 0, is not modelled.
public enum SdlGpuCompareOp {
    /// Never passes.
    NEVER(1),
    /// Passes when nearer: the usual depth test.
    LESS(2),
    /// Passes when equal.
    EQUAL(3),
    /// Passes when nearer or equal.
    LESS_OR_EQUAL(4),
    /// Passes when farther: a reversed depth buffer.
    GREATER(5),
    /// Passes when not equal.
    NOT_EQUAL(6),
    /// Passes when farther or equal.
    GREATER_OR_EQUAL(7),
    /// Always passes.
    ALWAYS(8);

    private final int value;

    SdlGpuCompareOp(int value) {
        this.value = value;
    }

    /// SDL's value.
    public int value() {
        return value;
    }

    /// The name the C shim reports this constant under, for the layout probe.
    public String nativeName() {
        return "SDL_GPU_COMPAREOP_" + name();
    }
}
