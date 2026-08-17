package io.github.digitalsmile.goldberry.natives.blend2d;

/// A transform Blend2D can apply to a context's user space — `BLTransformOp`.
///
/// `bl_context_apply_transform_op` takes the operation and a `const void*` whose
/// meaning depends on it: nothing for [#RESET], two doubles for [#TRANSLATE] and
/// [#SCALE]. That is a signature the compiler cannot check, so the operand shape
/// is documented per constant and applied in one place — [BlendContext].
public enum BlendTransformOp implements BlendEnum {

    /// Back to identity. Takes no operand.
    RESET(0, "BL_TRANSFORM_OP_RESET"),

    /// **Replace** the context's transform with the one given. A `BLMatrix2D` —
    /// six doubles, in the order `matrix(a, b, c, d, e, f)` writes them.
    ///
    /// Replace, not compose, and that is why this is the operation the painter
    /// uses. Blend2D's `bl_context_save` / `bl_context_restore` are not on the
    /// export list, so there is no way to push a transform for a subtree and pop
    /// it afterwards. Assigning an absolute matrix per box needs neither: the
    /// stack is accumulated in Java, where hit testing can invert it, and each
    /// box says where it is rather than where it moved to
    /// ([ADR-0068](../../../../../../book/src/adr/0068-the-transform-stack-is-java-side.md)).
    ASSIGN(1, "BL_TRANSFORM_OP_ASSIGN"),

    /// Offset by `[x, y]`. Two doubles.
    TRANSLATE(2, "BL_TRANSFORM_OP_TRANSLATE"),

    /// Scale by `[x, y]`. Two doubles.
    ///
    /// This is how the display scale reaches the rasterizer: scale the context
    /// once by the window's factor and everything afterwards is drawn in logical
    /// coordinates, with Blend2D deciding what a fractional edge does to the
    /// physical pixels underneath it.
    SCALE(3, "BL_TRANSFORM_OP_SCALE");

    private final int nativeValue;
    private final String nativeName;

    BlendTransformOp(int nativeValue, String nativeName) {
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
