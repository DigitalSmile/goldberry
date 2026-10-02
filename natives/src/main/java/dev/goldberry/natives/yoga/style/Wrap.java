package dev.goldberry.natives.yoga.style;

/// Whether a line wraps — `YGWrap`, CSS's `flex-wrap`.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
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
