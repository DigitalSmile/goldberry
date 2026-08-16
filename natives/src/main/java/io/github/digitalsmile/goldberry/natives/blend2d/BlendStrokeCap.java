package io.github.digitalsmile.goldberry.natives.blend2d;

/// What a stroke does at the end of an open sub-path — `BLStrokeCap`.
///
/// Only the three SVG has. Blend2D also offers reversed-round and two triangle
/// caps, which no SVG document can ask for and no icon set uses; leaving them
/// out keeps every enumerator here one an icon can actually need.
///
/// The numbering is not alphabetical and not obvious — round is 2, with a
/// reversed round at 3 — which is why every value is checked against the
/// compiled library (ADR-0043).
public enum BlendStrokeCap implements BlendEnum {

    /// Stops flat at the endpoint. Blend2D's default, and SVG's.
    BUTT(0, "BL_STROKE_CAP_BUTT"),

    /// Extends half the stroke width past the endpoint, squared off.
    SQUARE(1, "BL_STROKE_CAP_SQUARE"),

    /// A half-disc at the endpoint. What Lucide's icons are drawn with, and the
    /// difference between an icon that looks like the design and one that looks
    /// like a chopped-off version of it.
    ROUND(2, "BL_STROKE_CAP_ROUND");

    private final int nativeValue;
    private final String nativeName;

    BlendStrokeCap(int nativeValue, String nativeName) {
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
