package io.github.digitalsmile.goldberry.natives.blend2d;

/// What a stroke does where two segments meet — `BLStrokeJoin`.
///
/// Three of Blend2D's five. The two omitted are miter-with-a-fallback variants
/// whose behaviour depends on the miter limit, and nothing binds that yet — an
/// enumerator whose meaning depends on a value Goldberry never sets would be a
/// constant that means something different from what its name says.
///
/// Round is 4, not 2, and the default is 0: [#MITER_CLIP] is what a context
/// starts with, so an icon that wants round corners has to say so (ADR-0043).
public enum BlendStrokeJoin implements BlendEnum {

    /// Extend both edges to their intersection, clipped at the miter limit.
    /// Blend2D's default.
    MITER_CLIP(0, "BL_STROKE_JOIN_MITER_CLIP"),

    /// Cut the corner off with a straight edge.
    BEVEL(3, "BL_STROKE_JOIN_BEVEL"),

    /// Round the corner with an arc of the stroke's radius. What Lucide is drawn
    /// with, and what makes a chevron look drawn rather than folded.
    ROUND(4, "BL_STROKE_JOIN_ROUND");

    private final int nativeValue;
    private final String nativeName;

    BlendStrokeJoin(int nativeValue, String nativeName) {
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
