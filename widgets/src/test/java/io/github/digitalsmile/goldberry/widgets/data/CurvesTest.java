package io.github.digitalsmile.goldberry.widgets.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// That a smooth line cannot draw a value the data never had.
///
/// Every test here **samples the curve** rather than inspecting its tangents. The
/// property `charts.md` §3.1 asks for — "monotone-cubic, which cannot overshoot
/// into impossible values" — is about the shape on screen, and it is one missing
/// `if` away from being false in a way no assertion about coefficients would
/// catch.
class CurvesTest {

    /// The smallest and largest y the curve actually reaches, sampled densely.
    private static double[] extremes(double[] x, double[] y) {
        var m = Curves.tangents(x, y);
        var low = Double.POSITIVE_INFINITY;
        var high = Double.NEGATIVE_INFINITY;
        for (var i = 0; i < x.length - 1; i++) {
            for (var step = 0; step <= 100; step++) {
                var at = Curves.at(x, y, m, i, step / 100.0);
                low = Math.min(low, at);
                high = Math.max(high, at);
            }
        }
        return new double[] {low, high};
    }

    @Test
    @DisplayName("a step up does not dip below the floor on its way")
    void theCanonicalOvershoot() {
        // The shape every non-monotone spline gets wrong: two readings at zero,
        // then two at a hundred. A natural cubic or a Catmull-Rom dips below zero
        // before the climb and overshoots above a hundred after it.
        var x = new double[] {0, 1, 2, 3};
        var y = new double[] {0, 0, 100, 100};

        var extremes = extremes(x, y);

        assertEquals(0, extremes[0], 1e-9, "it went below zero, which never happened");
        assertEquals(100, extremes[1], 1e-9, "and above the maximum, which also never happened");
    }

    @Test
    @DisplayName("a percentage stays a percentage")
    void impossibleValues() {
        // The reason this matters rather than being a nicety: on a chart of a
        // percentage an overshoot is not inaccurate, it is impossible -- and it
        // lands where the reader is looking, because it lands where the
        // interesting thing happened.
        var x = new double[] {0, 1, 2, 3, 4, 5};
        var y = new double[] {100, 100, 0, 0, 100, 100};

        var extremes = extremes(x, y);

        assertTrue(extremes[0] >= -1e-9, "dipped to " + extremes[0] + "%");
        assertTrue(extremes[1] <= 100 + 1e-9, "climbed to " + extremes[1] + "%");
    }

    @Test
    @DisplayName("a peak is a peak, and the curve does not climb past it")
    void localExtremaAreFlat() {
        // Found by the interval test below: averaging the secants at the top of
        // `1, 9, 2` gives a tangent of +0.5, and the curve reaches 9.0013 on a
        // series whose maximum is 9. The circle limiter does not catch it —
        // it scales tangents back, and this one has to be zero.
        var x = new double[] {0, 1, 2};
        var y = new double[] {1, 9, 2};
        var m = Curves.tangents(x, y);

        assertEquals(0, m[1], 1e-12, "the tangent at a peak is flat");
        assertEquals(9, extremes(x, y)[1], 1e-9, "and nothing goes above it");
    }

    @Test
    @DisplayName("the curve never leaves the interval its own endpoints define")
    void monotoneOnEveryMonotoneInterval() {
        var x = new double[] {0, 1, 2, 3, 4, 5, 6};
        var y = new double[] {3, 1, 1, 9, 2, 2, 8};
        var m = Curves.tangents(x, y);

        for (var i = 0; i < x.length - 1; i++) {
            var low = Math.min(y[i], y[i + 1]);
            var high = Math.max(y[i], y[i + 1]);
            for (var step = 0; step <= 50; step++) {
                var at = Curves.at(x, y, m, i, step / 50.0);
                assertTrue(
                        at >= low - 1e-9 && at <= high + 1e-9,
                        "segment " + i + " reached " + at + " outside " + low + "…" + high);
            }
        }
    }

    @Test
    @DisplayName("a flat stretch is flat, not a bump")
    void flatIsFlat() {
        // Both tangents of a flat interval are forced to zero. Without that the
        // curve leaves the line and comes back, drawing a rise on a series that
        // held steady.
        var x = new double[] {0, 1, 2, 3};
        var y = new double[] {5, 5, 5, 9};
        var m = Curves.tangents(x, y);

        for (var step = 0; step <= 20; step++) {
            assertEquals(5, Curves.at(x, y, m, 0, step / 20.0), 1e-9);
            assertEquals(5, Curves.at(x, y, m, 1, step / 20.0), 1e-9);
        }
    }

    @Test
    @DisplayName("it passes through every point it was given")
    void interpolationRatherThanApproximation() {
        var x = new double[] {0, 1.5, 4, 4.5, 9};
        var y = new double[] {2, -3, 7, 7.5, 0};
        var m = Curves.tangents(x, y);

        for (var i = 0; i < x.length - 1; i++) {
            assertEquals(y[i], Curves.at(x, y, m, i, 0), 1e-9, "the start of segment " + i);
            assertEquals(y[i + 1], Curves.at(x, y, m, i, 1), 1e-9, "the end of segment " + i);
        }
    }

    @Test
    @DisplayName("uneven x spacing is handled, which is what a time axis produces")
    void unevenSpacing() {
        // A series scraped every minute that missed ten. The curve still passes
        // through the readings and still cannot overshoot; the tangents are per
        // unit of x, so a long interval is a shallow slope rather than a spike.
        var x = new double[] {0, 60, 120, 720, 780};
        var y = new double[] {10, 10, 90, 90, 10};

        var extremes = extremes(x, y);

        assertTrue(extremes[0] >= 10 - 1e-9, "dipped to " + extremes[0]);
        assertTrue(extremes[1] <= 90 + 1e-9, "climbed to " + extremes[1]);
    }

    @Test
    @DisplayName("the Bézier controls are the tangents, a third of the way along")
    void controlsMatchTheHermite() {
        var x = new double[] {0, 3};
        var y = new double[] {1, 7};
        var m = Curves.tangents(x, y);

        // A cubic Hermite is a cubic Bézier whose controls sit a third of the way
        // along each tangent, which is the form `BlendPath.cubicTo` takes. If
        // these two disagreed, every smooth chart would be drawn with a curve
        // nothing in this file describes.
        var from = Curves.controlFrom(x, y, m, 0);
        var to = Curves.controlTo(x, y, m, 0);
        assertEquals(1.0, from[0], 1e-9);
        assertEquals(y[0] + m[0], from[1], 1e-9);
        assertEquals(2.0, to[0], 1e-9);
        assertEquals(y[1] - m[1], to[1], 1e-9);
    }

    @Test
    @DisplayName("two points and one point are not errors")
    void degenerateInputs() {
        assertEquals(0, Curves.tangents(new double[] {}, new double[] {}).length);
        assertEquals(1, Curves.tangents(new double[] {1}, new double[] {2}).length);

        var m = Curves.tangents(new double[] {0, 1}, new double[] {0, 4});
        assertEquals(4, m[0], 1e-9, "two points are a straight line, and its slope is the secant");
        assertEquals(4, m[1], 1e-9);
    }
}
