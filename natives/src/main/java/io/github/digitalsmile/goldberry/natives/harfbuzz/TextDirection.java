package io.github.digitalsmile.goldberry.natives.harfbuzz;

/// Which way a run of text advances — `hb_direction_t`.
///
/// The numbering is not sequential and not accidental: HarfBuzz starts at 4 and
/// arranges the values so that the low bit distinguishes forward from backward
/// and the next bit horizontal from vertical. Assuming they counted from zero
/// would silently shape right-to-left text left-to-right.
public enum TextDirection implements HarfBuzzEnum {

    /// Not set. What a buffer reports before anything has decided, and what
    /// [ShapingBuffer#guessSegmentProperties()] exists to replace.
    INVALID(0, "HB_DIRECTION_INVALID"),

    LTR(4, "HB_DIRECTION_LTR"),

    RTL(5, "HB_DIRECTION_RTL"),

    /// Top to bottom — vertical text.
    TTB(6, "HB_DIRECTION_TTB"),

    /// Bottom to top.
    BTT(7, "HB_DIRECTION_BTT");

    private final int nativeValue;
    private final String nativeName;

    TextDirection(int nativeValue, String nativeName) {
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

    /// Whether text advances along the x axis.
    public boolean isHorizontal() {
        return this == LTR || this == RTL;
    }

    /// Maps an `hb_direction_t` back to Java.
    ///
    /// @throws IllegalArgumentException if no direction has this value
    public static TextDirection of(int nativeValue) {
        for (var direction : values()) {
            if (direction.nativeValue == nativeValue) {
                return direction;
            }
        }
        throw new IllegalArgumentException(
                "hb_direction_t " + nativeValue + " is not one HarfBuzz defines");
    }
}
