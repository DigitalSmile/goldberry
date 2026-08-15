package io.github.digitalsmile.goldberry.natives.yoga;

/// The main axis — `YGFlexDirection`.
///
/// Yoga's default is [#COLUMN], not CSS's [#ROW]. That difference is one of the
/// two things [YogaConfig#useWebDefaults] exists to correct, and it is why
/// Goldberry sets web defaults rather than leaving Yoga's own: a stylesheet that
/// says nothing about direction should behave the way the CSS subset in
/// `docs/ARCHITECTURE.md` §8 promises.
public enum FlexDirection implements YogaEnum {

    COLUMN(0, "YGFlexDirectionColumn"),

    COLUMN_REVERSE(1, "YGFlexDirectionColumnReverse"),

    ROW(2, "YGFlexDirectionRow"),

    ROW_REVERSE(3, "YGFlexDirectionRowReverse");

    private final int nativeValue;
    private final String nativeName;

    FlexDirection(int nativeValue, String nativeName) {
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
