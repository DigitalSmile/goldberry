package io.github.digitalsmile.goldberry.natives.yoga;

/// Whether a line wraps — `YGWrap`, CSS's `flex-wrap`.
public enum Wrap implements YogaEnum {

    /// One line, however much it overflows. Yoga's default, and CSS's.
    NO_WRAP(0, "YGWrapNoWrap"),

    WRAP(1, "YGWrapWrap"),

    WRAP_REVERSE(2, "YGWrapWrapReverse");

    private final int nativeValue;
    private final String nativeName;

    Wrap(int nativeValue, String nativeName) {
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
