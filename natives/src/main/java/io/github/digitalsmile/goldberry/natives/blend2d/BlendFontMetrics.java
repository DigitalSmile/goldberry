package io.github.digitalsmile.goldberry.natives.blend2d;

/// What a font measures at the size it was created with.
///
/// Every number is in the rendering context's own units — logical pixels, once
/// the context has been scaled — so nothing here needs converting before it is
/// used to place a baseline.
///
/// Ten of `BLFontMetrics`' sixteen fields are carried. The other six (the
/// vertical-orientation ascent and descent, and the bounding box) are verified in
/// the layout table but not surfaced: an accessor for a number nothing uses is a
/// promise to keep it working.
///
/// **The four decoration fields are the newest four**, and they are here because a
/// rectangle under a line of text is a *type design* decision rather than a layout
/// one: where the rule sits and how thick it is come from the face, and a painter
/// that guessed would be wrong at every size and at every family
/// (`docs/gaps.md` G27, ADR-0321). No new native symbol was needed for them —
/// `bl_font_get_metrics` was already filling the whole struct and this end was
/// reading six of it; both positions are scaled by the font's size on the way
/// out, like everything else here.
///
/// @param size                    the size the font was created at
/// @param ascent                  how far above the baseline the font reaches, as
///                                a positive number — the opposite sign to the y
///                                axis it is measured on, which is Blend2D's
///                                convention and everyone else's
/// @param descent                 how far below the baseline it reaches, also
///                                positive
/// @param lineGap                 the extra leading the designer asked for between
///                                lines, and commonly zero
/// @param xHeight                 the height of a lower-case `x`
/// @param capHeight               the height of a capital letter
/// @param underlinePosition       where the **top** of an underline goes, as a
///                                y-down offset from the baseline — so a positive
///                                number, an underline being below the text.
///                                Unlike [#ascent] and [#descent] this keeps the
///                                sign the rasterizer uses, because it is added to
///                                a baseline rather than compared with a box
/// @param underlineThickness      how thick that rule is, and `0` from a face that
///                                does not say — a `post` table too short to hold
///                                the pair is legal, and a caller has to have an
///                                answer for it
/// @param strikethroughPosition   the same for a rule through the text, and
///                                therefore **negative**: it is above the baseline
/// @param strikethroughThickness  how thick that one is, `0` when the face is
///                                silent
public record BlendFontMetrics(
        float size,
        float ascent,
        float descent,
        float lineGap,
        float xHeight,
        float capHeight,
        float underlinePosition,
        float underlineThickness,
        float strikethroughPosition,
        float strikethroughThickness) {

    /// The six metrics that were here before the decorations were, with the four
    /// new ones zero.
    ///
    /// Kept because "this face says nothing about its own underline" is a real
    /// answer a caller has to handle anyway — see [#underlineThickness] — so a
    /// test or a fallback that has only the six is not obliged to invent four.
    public BlendFontMetrics(float size, float ascent, float descent, float lineGap, float xHeight, float capHeight) {
        this(size, ascent, descent, lineGap, xHeight, capHeight, 0, 0, 0, 0);
    }

    /// The distance from one baseline to the next.
    ///
    /// `ascent + descent + lineGap`, which is the font's own idea of a line —
    /// not the CSS `line-height` a style might impose on top of it. The two are
    /// different numbers and the style layer will need both.
    public float lineHeight() {
        return ascent + descent + lineGap;
    }
}
