package io.github.digitalsmile.goldberry.natives.blend2d;

/// What a font measures at the size it was created with.
///
/// Every number is in the rendering context's own units — logical pixels, once
/// the context has been scaled — so nothing here needs converting before it is
/// used to place a baseline.
///
/// Only the six fields something actually reads are carried. `BLFontMetrics` has
/// sixteen, and the other ten (the vertical-orientation ascent and descent, the
/// bounding box, the underline and strikethrough positions) are verified in the
/// layout table but not surfaced: an accessor for a number nothing uses is a
/// promise to keep it working.
///
/// @param size       the size the font was created at
/// @param ascent     how far above the baseline the font reaches, as a positive
///                   number — the opposite sign to the y axis it is measured on,
///                   which is Blend2D's convention and everyone else's
/// @param descent    how far below the baseline it reaches, also positive
/// @param lineGap    the extra leading the designer asked for between lines, and
///                   commonly zero
/// @param xHeight    the height of a lower-case `x`
/// @param capHeight  the height of a capital letter
public record BlendFontMetrics(
        float size, float ascent, float descent, float lineGap,
        float xHeight, float capHeight) {

    /// The distance from one baseline to the next.
    ///
    /// `ascent + descent + lineGap`, which is the font's own idea of a line —
    /// not the CSS `line-height` a style might impose on top of it. The two are
    /// different numbers and the style layer will need both.
    public float lineHeight() {
        return ascent + descent + lineGap;
    }
}
