package io.github.digitalsmile.goldberry.natives.blend2d;

/// How Blend2D is to read a glyph run's placement array — `BLGlyphPlacementType`.
///
/// This enum decides the **units**, which makes it the single most consequential
/// constant in the text path. Blend2D transforms placements by whichever matrix
/// the type selects:
///
/// - [#ADVANCE_OFFSET] and [#DESIGN_UNITS] go through the **font matrix**, which
///   is `size / units-per-em`. The numbers must therefore be in font design
///   units.
/// - [#USER_UNITS] goes through the user transform only, so the numbers are
///   already in the context's coordinates.
///
/// Pick the wrong one and nothing fails: a run in design units read as user
/// units is drawn at roughly `units-per-em / size` times its proper spacing —
/// around 128&times; for a 16-point Inter — and a run in user units read as
/// design units collapses into a single illegible pile. Both render. See
/// ADR-0034.
public enum BlendGlyphPlacementType implements BlendEnum {

    /// No placement at all: every glyph is drawn at the origin, on top of the
    /// last. Blend2D's zero value, and never something to ask for.
    NONE(0, "BL_GLYPH_PLACEMENT_TYPE_NONE"),

    /// A [Layouts.BL_GLYPH_PLACEMENT][io.github.digitalsmile.goldberry.natives.layout.Layouts]
    /// per glyph — an offset and an advance, in **font design units**.
    ///
    /// The one Goldberry uses, because it is the shape HarfBuzz already reports:
    /// four `int`s per glyph, offset first, advance second.
    ADVANCE_OFFSET(1, "BL_GLYPH_PLACEMENT_TYPE_ADVANCE_OFFSET"),

    /// A `BLPoint` per glyph, in font design units. Absolute positions rather
    /// than advances, so the caller has already accumulated the pen.
    DESIGN_UNITS(2, "BL_GLYPH_PLACEMENT_TYPE_DESIGN_UNITS"),

    /// A `BLPoint` per glyph, in the context's own units.
    USER_UNITS(3, "BL_GLYPH_PLACEMENT_TYPE_USER_UNITS"),

    /// A `BLPoint` per glyph, untransformed.
    ABSOLUTE_UNITS(4, "BL_GLYPH_PLACEMENT_TYPE_ABSOLUTE_UNITS");

    private final int nativeValue;
    private final String nativeName;

    BlendGlyphPlacementType(int nativeValue, String nativeName) {
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
