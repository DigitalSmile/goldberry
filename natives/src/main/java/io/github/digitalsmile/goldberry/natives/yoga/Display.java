package io.github.digitalsmile.goldberry.natives.yoga;

/// Whether a node takes part in layout — `YGDisplay`.
///
/// Yoga's two values are CSS's `display: flex` and `display: none`. There is no
/// `block` or `inline`: every Goldberry widget is a flex container, which is
/// what makes the CSS subset in `docs/ARCHITECTURE.md` §8 compile to Yoga
/// directly.
public enum Display implements YogaEnum {

    /// Laid out, and a flex container for its children.
    FLEX(0, "YGDisplayFlex"),

    /// Skipped entirely: no size, no position, and its children are not measured.
    /// A hidden node still costs a tree entry, which is what makes toggling
    /// visibility cheaper than rebuilding.
    NONE(1, "YGDisplayNone");

    private final int nativeValue;
    private final String nativeName;

    Display(int nativeValue, String nativeName) {
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
