package dev.goldberry.gpu;

import dev.goldberry.natives.sdl.gpu.enums.SdlGpuStencilOp;

/// What a [StencilTest] does to a sample's stencil value.
public enum StencilOp {
    /// Keeps the value.
    KEEP,
    /// Sets it to 0.
    ZERO,
    /// Sets it to the pass's [RenderPass#setStencilReference]: marking where a
    /// portal, a mirror or a selected object was drawn.
    REPLACE,
    /// Adds one, stopping at 255: counting overdraw.
    INCREMENT_AND_CLAMP,
    /// Takes one away, stopping at 0.
    DECREMENT_AND_CLAMP,
    /// Inverts every bit.
    INVERT,
    /// Adds one, 255 wrapping to 0: a shadow volume's front faces.
    INCREMENT_AND_WRAP,
    /// Takes one away, 0 wrapping to 255: a shadow volume's back faces.
    DECREMENT_AND_WRAP;

    SdlGpuStencilOp sdl() {
        return switch (this) {
            case KEEP -> SdlGpuStencilOp.KEEP;
            case ZERO -> SdlGpuStencilOp.ZERO;
            case REPLACE -> SdlGpuStencilOp.REPLACE;
            case INCREMENT_AND_CLAMP -> SdlGpuStencilOp.INCREMENT_AND_CLAMP;
            case DECREMENT_AND_CLAMP -> SdlGpuStencilOp.DECREMENT_AND_CLAMP;
            case INVERT -> SdlGpuStencilOp.INVERT;
            case INCREMENT_AND_WRAP -> SdlGpuStencilOp.INCREMENT_AND_WRAP;
            case DECREMENT_AND_WRAP -> SdlGpuStencilOp.DECREMENT_AND_WRAP;
        };
    }
}
