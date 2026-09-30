package io.github.digitalsmile.goldberry.gpu;

import io.github.digitalsmile.goldberry.natives.sdl.gpu.enums.SdlGpuCompareOp;

/// How a depth test compares a fragment's depth with the depth target's.
public enum CompareOp {
    /// Never passes.
    NEVER,
    /// Passes when nearer: the usual test, with the target cleared to 1.
    LESS,
    /// Passes when equal.
    EQUAL,
    /// Passes when nearer or equal.
    LESS_OR_EQUAL,
    /// Passes when farther: a reversed depth buffer, cleared to 0.
    GREATER,
    /// Passes when not equal.
    NOT_EQUAL,
    /// Passes when farther or equal.
    GREATER_OR_EQUAL,
    /// Always passes.
    ALWAYS;

    SdlGpuCompareOp sdl() {
        return switch (this) {
            case NEVER -> SdlGpuCompareOp.NEVER;
            case LESS -> SdlGpuCompareOp.LESS;
            case EQUAL -> SdlGpuCompareOp.EQUAL;
            case LESS_OR_EQUAL -> SdlGpuCompareOp.LESS_OR_EQUAL;
            case GREATER -> SdlGpuCompareOp.GREATER;
            case NOT_EQUAL -> SdlGpuCompareOp.NOT_EQUAL;
            case GREATER_OR_EQUAL -> SdlGpuCompareOp.GREATER_OR_EQUAL;
            case ALWAYS -> SdlGpuCompareOp.ALWAYS;
        };
    }
}
