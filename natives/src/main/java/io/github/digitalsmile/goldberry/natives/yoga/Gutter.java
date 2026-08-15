package io.github.digitalsmile.goldberry.natives.yoga;

/// Which gap a `gap` value sets — `YGGutter`.
///
/// CSS's `row-gap`, `column-gap` and the `gap` shorthand. The names are CSS's
/// and read backwards at first: [#ROW] is the gap *between rows*, so it is
/// vertical space, and [#COLUMN] is horizontal.
public enum Gutter implements YogaEnum {

    /// The gap between columns — horizontal space. CSS's `column-gap`.
    COLUMN(0, "YGGutterColumn"),

    /// The gap between rows — vertical space. CSS's `row-gap`.
    ROW(1, "YGGutterRow"),

    /// Both, CSS's one-value `gap`.
    ALL(2, "YGGutterAll");

    private final int nativeValue;
    private final String nativeName;

    Gutter(int nativeValue, String nativeName) {
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
