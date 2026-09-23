package io.github.digitalsmile.goldberry.natives.blend2d;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.ValueLayout;

import io.github.digitalsmile.goldberry.natives.layout.Layouts;

/// An affine matrix as Blend2D spells one — `BLMatrix2D`, as a value.
///
/// ```
///   x' = m00·x + m10·y + m20
///   y' = m01·x + m11·y + m21
/// ```
///
/// Blend2D's own field names and Blend2D's own order, deliberately: this crosses
/// into a `const BLMatrix2D*` and a second naming scheme on this side of the
/// boundary would be a second place for the order to be wrong. The toolkit's
/// `Affine(a, b, c, d, e, f)` is the same six numbers in the same order —
/// `a = m00`, `b = m01`, `c = m10`, `d = m11`, `e = m20`, `f = m21`.
///
/// Today it places a gradient: a COLRv1 colour glyph may turn the fill inside a
/// glyph without turning the glyph, which is a matrix on the *gradient* rather
/// than on the context (ADR-0456).
public record BlendMatrix(double m00, double m01, double m10, double m11, double m20, double m21) {

    /// The matrix that moves nothing, which is also what "no matrix" means to
    /// every call that takes one.
    public static final BlendMatrix IDENTITY = new BlendMatrix(1, 0, 0, 1, 0, 0);

    private static final long M00 = Layouts.BL_MATRIX2D.offsetOf("m00");
    private static final long M01 = Layouts.BL_MATRIX2D.offsetOf("m01");
    private static final long M10 = Layouts.BL_MATRIX2D.offsetOf("m10");
    private static final long M11 = Layouts.BL_MATRIX2D.offsetOf("m11");
    private static final long M20 = Layouts.BL_MATRIX2D.offsetOf("m20");
    private static final long M21 = Layouts.BL_MATRIX2D.offsetOf("m21");

    /// @throws IllegalArgumentException if any of the six is not finite. Blend2D
    ///         accepts a NaN in a matrix and draws nothing through it, which is
    ///         indistinguishable from a shape that was never there.
    public BlendMatrix {
        if (!Double.isFinite(m00)
                || !Double.isFinite(m01)
                || !Double.isFinite(m10)
                || !Double.isFinite(m11)
                || !Double.isFinite(m20)
                || !Double.isFinite(m21)) {
            throw new IllegalArgumentException("a matrix is six finite numbers, and [" + m00 + " " + m01 + " " + m10
                    + " " + m11 + " " + m20 + " " + m21 + "] is not");
        }
    }

    /// Whether this is [#IDENTITY], which crosses as `NULL` rather than as six
    /// numbers that change nothing.
    public boolean isIdentity() {
        return equals(IDENTITY);
    }

    /// This matrix as a `BLMatrix2D`, or `NULL` when it is the identity.
    MemorySegment toNative(SegmentAllocator allocator) {
        if (isIdentity()) {
            return MemorySegment.NULL;
        }
        var segment = allocator.allocate(Layouts.BL_MATRIX2D.layout());
        segment.set(ValueLayout.JAVA_DOUBLE, M00, m00);
        segment.set(ValueLayout.JAVA_DOUBLE, M01, m01);
        segment.set(ValueLayout.JAVA_DOUBLE, M10, m10);
        segment.set(ValueLayout.JAVA_DOUBLE, M11, m11);
        segment.set(ValueLayout.JAVA_DOUBLE, M20, m20);
        segment.set(ValueLayout.JAVA_DOUBLE, M21, m21);
        return segment;
    }
}
