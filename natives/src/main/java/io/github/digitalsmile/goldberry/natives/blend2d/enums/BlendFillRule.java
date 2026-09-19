package io.github.digitalsmile.goldberry.natives.blend2d.enums;

/// Which points a path encloses — `BLFillRule`.
///
/// Both of Blend2D's two, because the whole point of binding this is that the
/// two disagree. A path with one sub-path is filled identically by either; a
/// path with a sub-path *inside* another is where they part company, and that is
/// the only case anything here uses.
///
/// [#NON_ZERO] is what a context starts with and what every other fill in the
/// toolkit wants — a shape is its outline and its inside, and a caller who put
/// two rings in one path meant two rings. [#EVEN_ODD] is asked for by exactly
/// one drawing: a drop shadow with the box's own rectangle cut out of it
/// (ADR-0427).
///
/// **It is context state**, like the stroke options and unlike a colour. A fill
/// that sets it must put it back, or the next thing drawn inherits a rule it
/// did not ask for — and the symptom is a hole in an unrelated shape.
public enum BlendFillRule implements BlendEnum {

    /// Count the windings, signed; non-zero is inside. Blend2D's default, and a
    /// reversed sub-path under it still *fills* the part of itself the outer
    /// shape does not cover — which is why it cannot cut a hole.
    NON_ZERO(0, "BL_FILL_RULE_NON_ZERO"),

    /// Count the crossings; odd is inside. A point inside two sub-paths is
    /// crossed twice and left empty, whichever direction either was wound in —
    /// so the hole does not depend on getting a winding order right.
    EVEN_ODD(1, "BL_FILL_RULE_EVEN_ODD");

    private final int nativeValue;
    private final String nativeName;

    BlendFillRule(int nativeValue, String nativeName) {
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
