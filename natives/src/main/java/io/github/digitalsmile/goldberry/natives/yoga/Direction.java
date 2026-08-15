package io.github.digitalsmile.goldberry.natives.yoga;

/// Writing direction — `YGDirection`.
///
/// This is the one Yoga enum that also travels *back* across the boundary:
/// `YGNodeLayoutGetDirection` reports the direction a node resolved to, which is
/// what turns [Edge#START] and [Edge#END] into left and right.
public enum Direction implements YogaEnum {

    /// Take the owner's direction. The root's owner is the layout call itself,
    /// so a tree that is entirely `INHERIT` resolves to whatever
    /// [YogaNode#calculateLayout] was passed.
    INHERIT(0, "YGDirectionInherit"),

    LTR(1, "YGDirectionLTR"),

    RTL(2, "YGDirectionRTL");

    private final int nativeValue;
    private final String nativeName;

    Direction(int nativeValue, String nativeName) {
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

    /// Maps a `YGDirection` back to Java.
    ///
    /// Unknown values are rejected rather than defaulted, for the reason
    /// [MeasureMode#of] gives: a value Yoga does not define is evidence the
    /// binding is wrong, and guessing turns that into a mirrored layout instead
    /// of an error.
    ///
    /// @throws IllegalArgumentException if no direction has this value
    public static Direction of(int nativeValue) {
        return switch (nativeValue) {
            case 0 -> INHERIT;
            case 1 -> LTR;
            case 2 -> RTL;
            default -> throw new IllegalArgumentException(
                    "YGDirection " + nativeValue + " is not one Yoga defines."
                            + " The layout binding's return type is probably wrong.");
        };
    }
}
