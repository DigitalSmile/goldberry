package io.github.digitalsmile.goldberry.natives.blend2d;

/// How a fill combines with what is already there — `BLCompOp`.
///
/// Blend2D defines the full Porter-Duff set and then some. Two are bound,
/// because two is what a frame needs and the rest would be constants nothing
/// checks against a use.
public enum BlendCompOp implements BlendEnum {

    /// Blend over the destination, honouring the source's alpha. Blend2D's
    /// default and the one every ordinary fill wants.
    SRC_OVER(0, "BL_COMP_OP_SRC_OVER"),

    /// Overwrite the destination, alpha included.
    ///
    /// This is what makes a background fill a *background*: with [#SRC_OVER] a
    /// translucent colour composites onto whatever the last frame left behind,
    /// so a half-transparent window slowly turns opaque as frames accumulate.
    SRC_COPY(1, "BL_COMP_OP_SRC_COPY");

    private final int nativeValue;
    private final String nativeName;

    BlendCompOp(int nativeValue, String nativeName) {
        this.nativeValue = nativeValue;
        this.nativeName = nativeName;
    }

    @Override
    public int nativeValue() {
        return nativeValue;
    }

    @Override
    public String nativeName() {
        return nativeName;
    }
}
