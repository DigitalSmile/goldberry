package io.github.digitalsmile.goldberry.css;

/// A 2D affine transform — the six numbers CSS, SVG and Blend2D all spell the
/// same way.
///
/// ```
///   x' = a·x + c·y + e
///   y' = b·x + d·y + f
/// ```
///
/// ## Why this type exists rather than a call into Blend2D
///
/// Blend2D has a matrix type and the arithmetic to go with it — `BLMatrix2D`,
/// `bl_matrix2d_apply_op`, `bl_matrix2d_invert`. None of it is on the export
/// list, and putting it there would add symbols to a boundary that has caught the
/// same class of local-symbol bug three times
/// ([ADR-0064](../../../../../../book/src/adr/0064-a-rounded-rectangle-is-four-cubics.md)).
/// It would also be the wrong place for the arithmetic to live: **hit testing
/// needs the inverse**, and hit testing runs against a snapshot of the last
/// painted frame, on the input path, with no rendering context anywhere near it
/// ([ADR-0054](../../../../../../book/src/adr/0054-hit-testing-runs-against-the-painted-frame.md)).
/// A matrix that could only be inverted by a context would have to be inverted
/// during paint and carried, or inverted by a second implementation — and two
/// implementations of an inverse that must agree exactly is how a pointer starts
/// landing somewhere the ink is not.
///
/// So: one matrix type, in Java, used by the painter and by hit testing. The
/// painter hands its six numbers to Blend2D through the *already exported*
/// `bl_context_apply_transform_op`, and no new symbol crosses the boundary.
///
/// ## The field order is Blend2D's, and CSS's, and SVG's
///
/// `BLMatrix2D` is `{m00, m01, m10, m11, m20, m21}` — six consecutive doubles in
/// exactly the order `matrix(a, b, c, d, e, f)` writes them. That is not a
/// coincidence to rely on silently, so it is checked: `BL_MATRIX2D` is in the
/// layout registry and the probe compares it against what the C compiler
/// computed for the library actually loaded.
///
/// Immutable, like every value in the cascade.
///
/// @param a horizontal scale
/// @param b vertical skew
/// @param c horizontal skew
/// @param d vertical scale
/// @param e horizontal translation
/// @param f vertical translation
public record Affine(double a, double b, double c, double d, double e, double f) {

    /// The transform that changes nothing.
    public static final Affine IDENTITY = new Affine(1, 0, 0, 1, 0, 0);

    public Affine {
        requireFinite(a, "a");
        requireFinite(b, "b");
        requireFinite(c, "c");
        requireFinite(d, "d");
        requireFinite(e, "e");
        requireFinite(f, "f");
    }

    /// Offset by `(x, y)`.
    public static Affine translate(double x, double y) {
        return new Affine(1, 0, 0, 1, x, y);
    }

    /// Scale about the origin.
    public static Affine scale(double x, double y) {
        return new Affine(x, 0, 0, y, 0, 0);
    }

    /// Rotate clockwise about the origin.
    ///
    /// Clockwise because y grows downward here, as it does in every screen
    /// coordinate system and in CSS: `rotate(90deg)` turns the top edge toward
    /// the right, which is what an author who has used CSS expects to see.
    ///
    /// @param radians the angle, in radians
    public static Affine rotate(double radians) {
        var cos = Math.cos(radians);
        var sin = Math.sin(radians);
        return new Affine(cos, sin, -sin, cos, 0, 0);
    }

    /// Skew, as CSS's `skew()` — the angles the axes are tilted by.
    public static Affine skew(double xRadians, double yRadians) {
        return new Affine(1, Math.tan(yRadians), Math.tan(xRadians), 1, 0, 0);
    }

    /// This transform followed by `next`.
    ///
    /// Reading order, deliberately: `scale(2).then(translate(10, 0))` scales and
    /// *then* moves, which is what the sentence says. CSS's `transform` list runs
    /// the other way round — in `transform: translate(10px) scale(2)` the scale
    /// happens first — and [Transform] is what turns the list into this order, in
    /// one place, so nothing else has to hold the reversal in its head.
    public Affine then(Affine next) {
        return new Affine(
                next.a * a + next.c * b,
                next.b * a + next.d * b,
                next.a * c + next.c * d,
                next.b * c + next.d * d,
                next.a * e + next.c * f + next.e,
                next.b * e + next.d * f + next.f);
    }

    /// This transform applied about `(cx, cy)` instead of about the origin.
    ///
    /// Move the origin to the point, transform, move it back — which is the whole
    /// of what `transform-origin` means, and why a rotation with the CSS default
    /// of `50% 50%` spins a control about its middle rather than swinging it
    /// around its top-left corner.
    public Affine about(double cx, double cy) {
        return translate(-cx, -cy).then(this).then(translate(cx, cy));
    }

    /// How much this transform multiplies area by. Negative when it flips.
    public double determinant() {
        return a * d - b * c;
    }

    /// Whether this transform can be undone.
    ///
    /// `scale(0)` cannot: it collapses the plane to a point, and every point on
    /// screen came from everywhere at once. A box scaled to nothing is invisible,
    /// so nothing is lost by refusing to route a pointer into it.
    public boolean isInvertible() {
        var determinant = determinant();
        return Double.isFinite(determinant) && Math.abs(determinant) > 1e-12;
    }

    /// The transform that undoes this one, or null when there is none.
    ///
    /// Null rather than an exception or an `Optional`: this is called once per
    /// box per frame while capturing the hit-test snapshot, and the caller's
    /// answer to "not invertible" is to drop the region rather than to handle a
    /// failure.
    public Affine invert() {
        var determinant = determinant();
        if (!Double.isFinite(determinant) || Math.abs(determinant) <= 1e-12) {
            return null;
        }
        return new Affine(
                d / determinant,
                -b / determinant,
                -c / determinant,
                a / determinant,
                (c * f - d * e) / determinant,
                (b * e - a * f) / determinant);
    }

    /// Where `(x, y)` lands.
    public double mapX(double x, double y) {
        return a * x + c * y + e;
    }

    /// Where `(x, y)` lands.
    public double mapY(double x, double y) {
        return b * x + d * y + f;
    }

    /// Whether this is the identity, to within the tolerance an interpolated
    /// transform lands at.
    ///
    /// Asked on the hot path: the painter skips the whole transform machinery for
    /// a box that has none, which is every box in an ordinary frame.
    public boolean isIdentity() {
        return near(a, 1) && near(b, 0) && near(c, 0) && near(d, 1) && near(e, 0) && near(f, 0);
    }

    /// This transform separated into the four things CSS interpolates.
    ///
    /// Interpolating the six matrix entries directly is the obvious thing and is
    /// wrong for exactly one case, which is the case that matters: halfway between
    /// `rotate(0)` and `rotate(180deg)` the entries pass through **zero** — a
    /// matrix that collapses the box to a point — instead of through
    /// `rotate(90deg)`. The CSS Transforms specification says to decompose,
    /// interpolate the parts, and recompose, and this is that.
    ///
    /// @param translateX horizontal offset
    /// @param translateY vertical offset
    /// @param scaleX     horizontal scale, negative when the transform flips
    /// @param scaleY     vertical scale
    /// @param skew       the x-skew left after the rotation is taken out, in
    ///                   radians
    /// @param rotation   in radians
    public record Decomposed(
            double translateX, double translateY,
            double scaleX, double scaleY,
            double skew, double rotation) {

        /// The matrix these parts describe.
        ///
        /// Translate, rotate, skew, scale — the order the specification
        /// recomposes in, and the inverse of the order [Affine#decompose()]
        /// takes them out.
        public Affine recompose() {
            return Affine.scale(scaleX, scaleY)
                    .then(new Affine(1, 0, Math.tan(skew), 1, 0, 0))
                    .then(Affine.rotate(rotation))
                    .then(Affine.translate(translateX, translateY));
        }

        /// This decomposition `t` of the way to `to`.
        public Decomposed mix(Decomposed to, double t) {
            return new Decomposed(
                    lerp(translateX, to.translateX, t),
                    lerp(translateY, to.translateY, t),
                    lerp(scaleX, to.scaleX, t),
                    lerp(scaleY, to.scaleY, t),
                    lerp(skew, to.skew, t),
                    // The short way round, so a transition from 350° to 10°
                    // travels 20° rather than 340°.
                    lerp(rotation, rotation + shortestTurn(to.rotation - rotation), t));
        }

        private static double lerp(double from, double to, double t) {
            return from + (to - from) * t;
        }

        private static double shortestTurn(double delta) {
            var turn = 2 * Math.PI;
            var wrapped = delta % turn;
            if (wrapped > Math.PI) {
                return wrapped - turn;
            }
            if (wrapped < -Math.PI) {
                return wrapped + turn;
            }
            return wrapped;
        }
    }

    /// This transform split into a translation, a rotation, a skew and a scale.
    ///
    /// The 2D "unmatrix" from the CSS Transforms specification: Gram–Schmidt over
    /// the two basis vectors, with the reflection folded into the x scale so that
    /// a flipped transform round-trips instead of coming back as a rotation by π
    /// it never had.
    ///
    /// A singular matrix has no meaningful decomposition and returns null, for the
    /// same reason [#invert()] does.
    public Decomposed decompose() {
        var determinant = determinant();
        if (!Double.isFinite(determinant) || Math.abs(determinant) <= 1e-12) {
            return null;
        }

        var m00 = a;
        var m01 = b;
        var m10 = c;
        var m11 = d;

        var scaleX = Math.hypot(m00, m01);
        m00 /= scaleX;
        m01 /= scaleX;

        // How far the second axis leans onto the first, removed from it so the
        // two are perpendicular and the remaining length is the y scale.
        var shear = m00 * m10 + m01 * m11;
        m10 -= m00 * shear;
        m11 -= m01 * shear;

        var scaleY = Math.hypot(m10, m11);
        m10 /= scaleY;
        m11 /= scaleY;
        var skew = shear / scaleY;

        if (m00 * m11 - m01 * m10 < 0) {
            // A reflection. Attributed to the x axis by convention — the choice is
            // arbitrary, and making it consistently is what matters, because a
            // decomposition that picked a different axis on the way back would
            // animate through a shape that is in neither end state.
            scaleX = -scaleX;
            m00 = -m00;
            m01 = -m01;
        }

        return new Decomposed(e, f, scaleX, scaleY, Math.atan(skew), Math.atan2(m01, m00));
    }

    /// This transform `t` of the way to `to`, decomposed.
    ///
    /// Falls back to the end state when either matrix is singular, because there
    /// is nothing between a collapsed box and anything else to show.
    public Affine mix(Affine to, double t) {
        if (t <= 0) {
            return this;
        }
        if (t >= 1) {
            return to;
        }
        var from = decompose();
        var target = to.decompose();
        if (from == null || target == null) {
            return t < 0.5 ? this : to;
        }
        return from.mix(target, t).recompose();
    }

    @Override
    public String toString() {
        return isIdentity()
                ? "none"
                : String.format("matrix(%s, %s, %s, %s, %s, %s)", a, b, c, d, e, f);
    }

    private static boolean near(double value, double target) {
        return Math.abs(value - target) < 1e-9;
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(
                    "a transform's " + name + " must be a finite number, not " + value);
        }
    }
}
