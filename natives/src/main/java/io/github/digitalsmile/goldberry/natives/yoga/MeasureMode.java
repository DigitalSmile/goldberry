package io.github.digitalsmile.goldberry.natives.yoga;

/// The constraint Yoga puts on a measured node.
///
/// Mirrors `YGMeasureMode` in `yoga/YGEnums.h`. The ordinals are Yoga's, not
/// Java's: they are declared explicitly so reordering this enum cannot silently
/// change what crosses the boundary.
public enum MeasureMode implements YogaEnum {

    /// No constraint. The node may be whatever size it wants.
    UNDEFINED(0, "YGMeasureModeUndefined"),

    /// The parent has fixed the size. The node gets those bounds regardless of
    /// what it asks for.
    EXACTLY(1, "YGMeasureModeExactly"),

    /// An upper bound. The node may be anything up to the given size.
    AT_MOST(2, "YGMeasureModeAtMost");

    private final int nativeValue;
    private final String nativeName;

    MeasureMode(int nativeValue, String nativeName) {
        this.nativeValue = nativeValue;
        this.nativeName = nativeName;
    }

    /// The `YGMeasureMode` value this maps to.
    @Override
    public int nativeValue() {
        return nativeValue;
    }

    @Override
    public String nativeName() {
        return nativeName;
    }

    /// Maps a `YGMeasureMode` back to Java.
    ///
    /// Unknown values are rejected rather than defaulted. A value Yoga did not
    /// document is evidence that the callback signature is wrong — most likely
    /// that the enum crossed at the wrong width — and quietly treating it as
    /// [#UNDEFINED] would turn that into a subtly wrong layout instead of an
    /// error.
    ///
    /// @throws IllegalArgumentException if no mode has this value
    public static MeasureMode of(int nativeValue) {
        return switch (nativeValue) {
            case 0 -> UNDEFINED;
            case 1 -> EXACTLY;
            case 2 -> AT_MOST;
            default -> throw new IllegalArgumentException(
                    "YGMeasureMode " + nativeValue + " is not one Yoga defines."
                            + " The measure callback's signature is probably wrong.");
        };
    }
}
