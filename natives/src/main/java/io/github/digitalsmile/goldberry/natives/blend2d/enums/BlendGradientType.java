package io.github.digitalsmile.goldberry.natives.blend2d.enums;


/// Which shape a gradient's stops are laid along — `BLGradientType`.
///
/// All three are named although only [#LINEAR] is constructed, and that is not
/// completeness for its own sake: the type is what says how many doubles
/// `bl_gradient_init_as` reads out of its `const void* values`, and the three
/// disagree — four for a linear, six for a radial, four in a different meaning
/// for a conic. Naming the other two here is what makes
/// [io.github.digitalsmile.goldberry.natives.blend2d.BlendGradient]'s refusal to
/// take them a decision rather than an omission (ADR-0207).
///
/// Every value is checked against the compiled library, and `LINEAR` at zero is
/// exactly why: a zero that is right by accident reads the same as a field
/// nobody wrote.
public enum BlendGradientType implements BlendEnum {

    /// Stops along the segment from `(x0, y0)` to `(x1, y1)`.
    LINEAR(0, "BL_GRADIENT_TYPE_LINEAR"),

    /// Stops along a circle's radius. Six values, and not built.
    RADIAL(1, "BL_GRADIENT_TYPE_RADIAL"),

    /// Stops swept round an angle. Not built.
    CONIC(2, "BL_GRADIENT_TYPE_CONIC");

    private final int nativeValue;
    private final String nativeName;

    BlendGradientType(int nativeValue, String nativeName) {
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
