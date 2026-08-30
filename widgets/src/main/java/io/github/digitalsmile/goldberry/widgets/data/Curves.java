package io.github.digitalsmile.goldberry.widgets.data;

/// The arithmetic behind [Curve#SMOOTH] — Fritsch and Carlson's monotone cubic.
///
/// ## The whole point is what it refuses to do
///
/// Fit a natural cubic spline or a Catmull-Rom through `0, 0, 100, 100` and the
/// curve dips **below zero** before it climbs, and overshoots **above 100** at
/// the top. That is what a spline is for — it is minimising curvature, and
/// swinging past the endpoints is how it does that — and it is wrong for data.
/// On a percentage, a queue depth or a byte count the overshoot is not merely
/// inaccurate, it is impossible, and it lands exactly where a reader is looking
/// because it lands where the interesting thing happened.
///
/// Fritsch–Carlson (*Monotone Piecewise Cubic Interpolation*, SIAM J. Numer.
/// Anal. 17(2), 1980) takes the obvious tangents and then **limits** them: where
/// the data is monotone the curve is monotone, so it can never leave the interval
/// its own endpoints define. That is the property, and it is one line of
/// arithmetic — the `α² + β² > 9` test below — that is easy to leave out and
/// impossible to notice missing until a chart of a percentage shows −4%.
///
/// ## In pixels, not in values
///
/// The tangents are computed on the coordinates the painter is about to draw. A
/// value becomes a position through an affine scale ([Scale]) and monotonicity
/// survives an affine map, so the answer is the same either way — and doing it in
/// pixels means an unevenly sampled series on a time axis gets the right shape
/// for free, because the x spacing is already whatever it really was.
public final class Curves {

    private Curves() {}

    /// The tangent at each point of the monotone cubic through `(x, y)`.
    ///
    /// `x` must be strictly increasing, which is what a chart's own x always is.
    ///
    /// @return one tangent per point, in `y` units per `x` unit
    public static double[] tangents(double[] x, double[] y) {
        var n = Math.min(x.length, y.length);
        var m = new double[n];
        if (n < 2) {
            return m;
        }

        // The secant of each interval — the slope a straight line would have.
        var secant = new double[n - 1];
        for (var i = 0; i < n - 1; i++) {
            var run = x[i + 1] - x[i];
            secant[i] = run == 0 ? 0 : (y[i + 1] - y[i]) / run;
        }

        // The obvious first guess: the average of the two secants meeting at each
        // interior point, and the secant itself at the two ends.
        m[0] = secant[0];
        m[n - 1] = secant[n - 2];
        for (var i = 1; i < n - 1; i++) {
            // **A peak and a trough are flat.** Where the two secants meeting at
            // a point have opposite signs the point is a local extremum, and any
            // tangent through it climbs past the value on one side or the other:
            // averaging the secants at the top of `1, 9, 2` gives +0.5, and the
            // curve reaches 9.0013 on a series whose maximum is 9. Small, and the
            // wrong kind of small — it is a chart drawing a number nobody
            // recorded, at the one point a reader is looking at.
            //
            // The circle limiter below does not catch it: it scales tangents
            // back, and this one needs to be zero rather than smaller.
            m[i] = secant[i - 1] * secant[i] <= 0 ? 0 : (secant[i - 1] + secant[i]) / 2;
        }

        for (var i = 0; i < n - 1; i++) {
            if (secant[i] == 0) {
                // A flat interval. Both tangents must be flat or the curve leaves
                // the line and comes back, which on a series that held steady
                // draws a bump nobody measured.
                m[i] = 0;
                m[i + 1] = 0;
                continue;
            }
            var alpha = m[i] / secant[i];
            var beta = m[i + 1] / secant[i];
            // **The limiter.** Outside the circle of radius 3 the cubic is no
            // longer monotone on this interval, so both tangents are scaled back
            // onto it. This is the line the whole class exists for.
            var square = alpha * alpha + beta * beta;
            if (square > 9) {
                var tau = 3 / Math.sqrt(square);
                m[i] = tau * alpha * secant[i];
                m[i + 1] = tau * beta * secant[i];
            }
        }
        return m;
    }

    /// The first Bézier control point of the segment from `i` to `i + 1`.
    ///
    /// A cubic Hermite is a cubic Bézier whose controls sit a third of the way
    /// along each tangent, which is the form a path takes.
    public static double[] controlFrom(double[] x, double[] y, double[] m, int i) {
        var third = (x[i + 1] - x[i]) / 3;
        return new double[] {x[i] + third, y[i] + m[i] * third};
    }

    /// The second control point of the segment from `i` to `i + 1`.
    public static double[] controlTo(double[] x, double[] y, double[] m, int i) {
        var third = (x[i + 1] - x[i]) / 3;
        return new double[] {x[i + 1] - third, y[i + 1] - m[i + 1] * third};
    }

    /// The curve's own value at `t` along the segment from `i` to `i + 1`.
    ///
    /// Not used by the painter, which hands its control points to Blend2D. It is
    /// here so that "this curve never leaves the interval its endpoints define"
    /// can be **sampled and asserted** rather than argued from the algorithm —
    /// which is the only kind of proof worth having about a property that is one
    /// missing `if` away from being false.
    public static double at(double[] x, double[] y, double[] m, int i, double t) {
        var h = x[i + 1] - x[i];
        var t2 = t * t;
        var t3 = t2 * t;
        // Hermite basis.
        return (2 * t3 - 3 * t2 + 1) * y[i]
                + (t3 - 2 * t2 + t) * h * m[i]
                + (-2 * t3 + 3 * t2) * y[i + 1]
                + (t3 - t2) * h * m[i + 1];
    }
}
