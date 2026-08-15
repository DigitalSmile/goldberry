package io.github.digitalsmile.goldberry.natives.yoga;

/// Cross-axis alignment — `YGAlign`.
///
/// One C enum serves three CSS properties: `align-items`, `align-self` and
/// `align-content`. Not every constant is meaningful in every position —
/// `AUTO` only means anything for `align-self`, and the `SPACE_*` values only
/// for `align-content` — and Yoga does not reject the combinations it ignores.
/// The CSS layer is what refuses them; this enum is the full C surface.
public enum Align implements YogaEnum {

    /// Defer to the parent's `align-items`. Only meaningful as `align-self`.
    AUTO(0, "YGAlignAuto"),

    FLEX_START(1, "YGAlignFlexStart"),

    CENTER(2, "YGAlignCenter"),

    FLEX_END(3, "YGAlignFlexEnd"),

    /// Fill the cross axis. Yoga's own default for `align-items`, and CSS's.
    STRETCH(4, "YGAlignStretch"),

    /// Align on the children's baselines, which requires a baseline function —
    /// so until the text stack supplies one this behaves as [#FLEX_START].
    BASELINE(5, "YGAlignBaseline"),

    SPACE_BETWEEN(6, "YGAlignSpaceBetween"),

    SPACE_AROUND(7, "YGAlignSpaceAround"),

    SPACE_EVENLY(8, "YGAlignSpaceEvenly");

    private final int nativeValue;
    private final String nativeName;

    Align(int nativeValue, String nativeName) {
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
