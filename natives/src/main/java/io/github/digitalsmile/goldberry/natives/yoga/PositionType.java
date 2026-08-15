package io.github.digitalsmile.goldberry.natives.yoga;

/// How a node is positioned — `YGPositionType`, CSS's `position`.
///
/// The CSS subset admits `relative` and `absolute` (§8). [#STATIC] is bound
/// anyway because it is what makes [#ABSOLUTE] useful: an absolutely positioned
/// node is placed against its nearest non-static ancestor, so `static` is how a
/// container declines to be that ancestor.
public enum PositionType implements YogaEnum {

    /// Laid out in flow, and not a containing block for absolute descendants.
    STATIC(0, "YGPositionTypeStatic"),

    /// Laid out in flow, then offset by its inset. Yoga's default, and CSS's.
    RELATIVE(1, "YGPositionTypeRelative"),

    /// Taken out of flow and placed against the containing block.
    ABSOLUTE(2, "YGPositionTypeAbsolute");

    private final int nativeValue;
    private final String nativeName;

    PositionType(int nativeValue, String nativeName) {
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
