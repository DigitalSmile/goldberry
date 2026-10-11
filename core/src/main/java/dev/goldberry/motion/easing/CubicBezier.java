package dev.goldberry.motion.easing;

import dev.goldberry.motion.Easing;

/// `cubic-bezier(x1, y1, x2, y2)`: a curve from `(0, 0)` to `(1, 1)` through two
/// control points.
///
/// ## Why the solver is here
///
/// A cubic Bézier easing is not a function of `t` directly. CSS defines it as a
/// parametric curve where the *input* progress is the x coordinate and the eased
/// output is y. Getting from x to y means solving `bezierX(s) = x` for the
/// parameter `s` first, which has no closed form.
///
/// @param x1 the first control point's x, in `0..1` as CSS requires, so that the
///           curve is a function of x
/// @param y1 the first control point's y, any value: outside `0..1` overshoots
/// @param x2 the second control point's x, in `0..1`
/// @param y2 the second control point's y, any value
public record CubicBezier(double x1, double y1, double x2, double y2) implements Easing {

    public CubicBezier {
        requireUnit(x1, "x1");
        requireUnit(x2, "x2");
        requireFinite(y1, "y1");
        requireFinite(y2, "y2");
    }

    @Override
    public double at(double t) {
        if (t <= 0) {
            return 0;
        }
        if (t >= 1) {
            return 1;
        }
        return bezier(solveForX(t), y1, y2);
    }

    @Override
    public String cssName() {
        if (equals(Easing.EASE_ENTER)) {
            return "ease-enter";
        }
        if (equals(Easing.EASE_EXIT)) {
            return "ease-exit";
        }
        return "cubic-bezier(" + Numbers.css(x1) + ", " + Numbers.css(y1) + ", " + Numbers.css(x2) + ", "
                + Numbers.css(y2) + ")";
    }

    @Override
    public String toString() {
        return cssName();
    }

    /// One coordinate of a cubic Bézier from 0 to 1 through two controls.
    ///
    /// The standard basis, with the first and last points fixed at 0 and 1 so
    /// only the two control coordinates vary.
    private static double bezier(double s, double c1, double c2) {
        var inverse = 1 - s;
        return 3 * inverse * inverse * s * c1 + 3 * inverse * s * s * c2 + s * s * s;
    }

    /// The derivative of [#bezier], for Newton's method.
    private static double slope(double s, double c1, double c2) {
        var inverse = 1 - s;
        return 3 * inverse * inverse * c1 + 6 * inverse * s * (c2 - c1) + 3 * s * s * (1 - c2);
    }

    /// The curve parameter whose x coordinate is `x`.
    ///
    /// Newton–Raphson, falling back to bisection where the slope is too flat for
    /// it to converge — which is exactly what happens at the ends of
    /// `ease-enter`, whose second control x is 0 and whose tangent there is
    /// therefore horizontal. Newton alone would step off the interval and return
    /// a progress outside `0..1`, which reads as a value that jumps.
    ///
    /// Eight Newton iterations then bisection is far more than either needs for
    /// a curve this shallow; it costs about 40 ns and runs once per animating
    /// property per frame.
    private double solveForX(double x) {
        var s = x;
        for (var i = 0; i < 8; i++) {
            var error = bezier(s, x1, x2) - x;
            if (Math.abs(error) < 1e-7) {
                return s;
            }
            var derivative = slope(s, x1, x2);
            if (Math.abs(derivative) < 1e-6) {
                break;
            }
            s -= error / derivative;
        }

        var low = 0.0;
        var high = 1.0;
        s = x;
        for (var i = 0; i < 32 && high - low > 1e-7; i++) {
            if (bezier(s, x1, x2) < x) {
                low = s;
            } else {
                high = s;
            }
            s = (low + high) / 2;
        }
        return s;
    }

    private static void requireUnit(double value, String name) {
        if (!(value >= 0 && value <= 1)) {
            throw new IllegalArgumentException(name + " is " + value + "; a control point's x is in 0..1");
        }
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " is " + value);
        }
    }
}
