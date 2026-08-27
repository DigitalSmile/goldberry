package io.github.digitalsmile.goldberry.natives.blend2d.enums;


/// What a gradient does outside its own two ends — `BLExtendMode`.
///
/// Only the three simple modes. Blend2D also has the six pad/repeat/reflect
/// combinations that apply a different rule at each end, which is a thing no CSS
/// or SVG gradient can ask for; leaving them out keeps every enumerator here one
/// a document could actually name (ADR-0207).
public enum BlendExtendMode implements BlendEnum {

    /// The end colours hold. What CSS specifies, and what a fade under a band
    /// wants: the gradient covers the shape's own extent and everything the
    /// clip lets past beyond it is the last stop, rather than a second copy of
    /// the ramp.
    PAD(0, "BL_EXTEND_MODE_PAD"),

    /// The ramp starts again.
    REPEAT(1, "BL_EXTEND_MODE_REPEAT"),

    /// The ramp runs backwards, then forwards again.
    REFLECT(2, "BL_EXTEND_MODE_REFLECT");

    private final int nativeValue;
    private final String nativeName;

    BlendExtendMode(int nativeValue, String nativeName) {
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
