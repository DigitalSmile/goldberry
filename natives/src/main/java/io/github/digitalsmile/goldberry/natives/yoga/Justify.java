package io.github.digitalsmile.goldberry.natives.yoga;

/// Main-axis distribution — `YGJustify`, CSS's `justify-content`.
///
/// Distinct from [Align] in C even though CSS spells the values the same way,
/// and the numbering differs: `YGJustifyCenter` is 1 where `YGAlignCenter` is 2.
/// Sharing one Java enum between the two would put a plausible wrong value on
/// the wire, so they stay separate.
public enum Justify implements YogaEnum {

    FLEX_START(0, "YGJustifyFlexStart"),

    CENTER(1, "YGJustifyCenter"),

    FLEX_END(2, "YGJustifyFlexEnd"),

    SPACE_BETWEEN(3, "YGJustifySpaceBetween"),

    SPACE_AROUND(4, "YGJustifySpaceAround"),

    SPACE_EVENLY(5, "YGJustifySpaceEvenly");

    private final int nativeValue;
    private final String nativeName;

    Justify(int nativeValue, String nativeName) {
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
