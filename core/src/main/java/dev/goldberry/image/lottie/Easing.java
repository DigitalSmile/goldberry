package dev.goldberry.image.lottie;

/// A keyframe's timing curve: a cubic Bézier from `(0, 0)` to `(1, 1)` through
/// two control points, which is CSS's `cubic-bezier()` and After Effects'
/// temporal ease.
///
/// The curve is a function of `x`, and the parameter `t` that reaches a given
/// `x` is not `x` itself: [#apply] solves for it, by Newton's method with a
/// bisection to fall back on, and then reads `y` at it. Reading `y(x)` as if
/// `t` were `x` is the classic wrong ease, and it is visibly wrong — a curve
/// that is meant to start slowly starts at the wrong speed.
///
/// @param x1 the first control point's time, clamped to 0..1
/// @param y1 the first control point's progress, which may overshoot
/// @param x2 the second control point's time, clamped to 0..1
/// @param y2 the second control point's progress, which may overshoot
record Easing(double x1, double y1, double x2, double y2) {

    /// Straight: progress is time.
    static final Easing LINEAR = new Easing(0, 0, 1, 1);

    private static final int NEWTON_STEPS = 8;
    private static final int BISECTION_STEPS = 40;
    private static final double EPSILON = 1e-7;

    Easing {
        // A control point outside 0..1 in time would make the curve run
        // backwards, which is not a function of x and not something any
        // exporter means. After Effects clamps it too.
        x1 = Math.clamp(x1, 0, 1);
        x2 = Math.clamp(x2, 0, 1);
    }

    /// Progress at `x`, the fraction of the keyframe's time gone by.
    double apply(double x) {
        if (x <= 0) {
            return 0;
        }
        if (x >= 1) {
            return 1;
        }
        if (x1 == y1 && x2 == y2) {
            return x;
        }
        return sample(y1, y2, solve(x));
    }

    /// The parameter at which the curve's `x` is `x`.
    double solve(double x) {
        var t = x;
        for (var i = 0; i < NEWTON_STEPS; i++) {
            var error = sample(x1, x2, t) - x;
            if (Math.abs(error) < EPSILON) {
                return t;
            }
            var slope = slope(x1, x2, t);
            if (Math.abs(slope) < 1e-9) {
                break;
            }
            t -= error / slope;
        }
        // Newton strays where the curve is flat; bisection cannot.
        var low = 0.0;
        var high = 1.0;
        t = x;
        for (var i = 0; i < BISECTION_STEPS; i++) {
            var value = sample(x1, x2, t);
            if (Math.abs(value - x) < EPSILON) {
                return t;
            }
            if (value < x) {
                low = t;
            } else {
                high = t;
            }
            t = (low + high) / 2;
        }
        return t;
    }

    /// One coordinate of the curve at `t`, the end points being 0 and 1.
    private static double sample(double c1, double c2, double t) {
        var u = 1 - t;
        return 3 * u * u * t * c1 + 3 * u * t * t * c2 + t * t * t;
    }

    private static double slope(double c1, double c2, double t) {
        var u = 1 - t;
        return 3 * u * u * c1 + 6 * u * t * (c2 - c1) + 3 * t * t * (1 - c2);
    }
}
