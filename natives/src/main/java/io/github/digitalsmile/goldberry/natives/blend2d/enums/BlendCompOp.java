package io.github.digitalsmile.goldberry.natives.blend2d.enums;

/// How a fill combines with what is already there — `BLCompOp`.
///
/// Blend2D defines the full Porter-Duff set and then some. A frame needs two;
/// the rest are here because a COLRv1 colour glyph may name any of them in a
/// `PaintComposite`, and a font is data the toolkit does not choose — so every
/// operator the font format can ask for and Blend2D can answer is bound, and
/// every one is checked against the compiled library (ADR-0456).
///
/// The four HSL modes (`hue`, `saturation`, `color`, `luminosity`) have no
/// Blend2D operator at all and are absent for that reason.
public enum BlendCompOp implements BlendEnum {

    /// Blend over the destination, honouring the source's alpha. Blend2D's
    /// default and the one every ordinary fill wants.
    SRC_OVER(0, "BL_COMP_OP_SRC_OVER"),

    /// Overwrite the destination, alpha included.
    ///
    /// This is what makes a background fill a *background*: with [#SRC_OVER] a
    /// translucent colour composites onto whatever the last frame left behind,
    /// so a half-transparent window slowly turns opaque as frames accumulate.
    SRC_COPY(1, "BL_COMP_OP_SRC_COPY"),

    /// Only where the destination is: the source, cut to the destination's coverage. A
    /// colour glyph's mask.
    SRC_IN(2, "BL_COMP_OP_SRC_IN"),

    /// Only where the destination is not.
    SRC_OUT(3, "BL_COMP_OP_SRC_OUT"),

    /// The source over the destination, kept inside the destination.
    SRC_ATOP(4, "BL_COMP_OP_SRC_ATOP"),

    /// The destination over the source — drawing *behind*.
    DST_OVER(5, "BL_COMP_OP_DST_OVER"),

    /// The destination unchanged: the source is ignored.
    DST_COPY(6, "BL_COMP_OP_DST_COPY"),

    /// The destination, cut to the source's coverage.
    DST_IN(7, "BL_COMP_OP_DST_IN"),

    /// The destination, with the source's coverage cut out of it.
    DST_OUT(8, "BL_COMP_OP_DST_OUT"),

    /// The destination over the source, kept inside the source.
    DST_ATOP(9, "BL_COMP_OP_DST_ATOP"),

    /// Whichever of the two is there alone.
    XOR(10, "BL_COMP_OP_XOR"),

    /// Nothing, in the area drawn.
    CLEAR(11, "BL_COMP_OP_CLEAR"),

    /// The sum, clamped.
    PLUS(12, "BL_COMP_OP_PLUS"),

    /// Multiply: darkens, and white is neutral.
    MULTIPLY(15, "BL_COMP_OP_MULTIPLY"),

    /// Screen: lightens, and black is neutral.
    SCREEN(16, "BL_COMP_OP_SCREEN"),

    /// Overlay: multiply or screen by the destination's value.
    OVERLAY(17, "BL_COMP_OP_OVERLAY"),

    /// The darker of the two, per channel.
    DARKEN(18, "BL_COMP_OP_DARKEN"),

    /// The lighter of the two, per channel.
    LIGHTEN(19, "BL_COMP_OP_LIGHTEN"),

    /// Brightens the destination by the source.
    COLOR_DODGE(20, "BL_COMP_OP_COLOR_DODGE"),

    /// Darkens the destination by the source.
    COLOR_BURN(21, "BL_COMP_OP_COLOR_BURN"),

    /// Overlay with the operands swapped.
    HARD_LIGHT(25, "BL_COMP_OP_HARD_LIGHT"),

    /// A gentler hard light — how a waving flag's shading lies over its colours.
    SOFT_LIGHT(26, "BL_COMP_OP_SOFT_LIGHT"),

    /// The absolute difference, per channel.
    DIFFERENCE(27, "BL_COMP_OP_DIFFERENCE"),

    /// Difference with less contrast.
    EXCLUSION(28, "BL_COMP_OP_EXCLUSION");

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
