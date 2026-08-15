package io.github.digitalsmile.goldberry.natives.yoga;

/// What a measure callback reports back to Yoga: the `YGSize` it returns by
/// value.
///
/// Validated on construction, because this is one of the few values Java hands
/// to native code that native code will not check. Yoga takes a measured size as
/// fact and lays out around it; a NaN entering here does not fail, it spreads —
/// every arithmetic result downstream is NaN too, and the first visible symptom
/// is a blank window several layers away from the text that produced it.
public record MeasuredSize(float width, float height) {

    public MeasuredSize {
        requireUsable(width, "width");
        requireUsable(height, "height");
    }

    private static void requireUsable(float value, String name) {
        if (Float.isNaN(value)) {
            throw new IllegalArgumentException(
                    "measured " + name + " is NaN; Yoga would propagate it through the whole layout");
        }
        if (Float.isInfinite(value)) {
            throw new IllegalArgumentException("measured " + name + " is infinite");
        }
        if (value < 0f) {
            throw new IllegalArgumentException("measured " + name + " is negative: " + value);
        }
    }
}
