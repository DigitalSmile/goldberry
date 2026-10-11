package dev.goldberry.motion.easing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import dev.goldberry.motion.Easing;
import dev.goldberry.motion.easing.PiecewiseLinear.Point;
import dev.goldberry.motion.easing.Steps.Position;

/// The three kinds of curve, against CSS's definitions.
///
/// The Bézier reference values were computed apart from the solver here, by
/// bisecting the curve to double precision.
class EasingCurvesTest {

    private static final double CLOSE = 1e-5;

    @Nested
    @DisplayName("a cubic Bézier")
    class Bezier {

        @ParameterizedTest(name = "{0} at {5}")
        @CsvSource({
            "ease,        0.25, 0.1, 0.25, 1,   0.1,  0.094796",
            "ease,        0.25, 0.1, 0.25, 1,   0.5,  0.802403",
            "ease,        0.25, 0.1, 0.25, 1,   0.9,  0.994316",
            "ease-in,     0.42, 0,   1,    1,   0.5,  0.315357",
            "ease-out,    0,    0,   0.58, 1,   0.5,  0.684643",
            "ease-in-out, 0.42, 0,   0.58, 1,   0.25, 0.129162",
            "ease-in-out, 0.42, 0,   0.58, 1,   0.5,  0.5",
            "ease-enter,  0.2,  0,   0,    1,   0.25, 0.60722",
            "overshoot,   0.3, -0.5, 0.7,  1.5, 0.1, -0.080792",
            "overshoot,   0.3, -0.5, 0.7,  1.5, 0.9,  1.080792",
        })
        @DisplayName("follows CSS's curve, overshoot included")
        void referencePoints(String name, double x1, double y1, double x2, double y2, double t, double expected) {
            assertEquals(expected, new CubicBezier(x1, y1, x2, y2).at(t), CLOSE, name);
        }

        @Test
        @DisplayName("starts at 0 and ends at 1, clamped outside")
        void ends() {
            var curve = new CubicBezier(0.3, -0.5, 0.7, 1.5);
            assertEquals(0, curve.at(0));
            assertEquals(0, curve.at(-2));
            assertEquals(1, curve.at(1));
            assertEquals(1, curve.at(7));
        }

        @Test
        @DisplayName("refuses a control point's x outside 0..1, and a y that is not finite")
        void refuses() {
            assertThrows(IllegalArgumentException.class, () -> new CubicBezier(-0.1, 0, 1, 1));
            assertThrows(IllegalArgumentException.class, () -> new CubicBezier(0, 0, 1.1, 1));
            assertThrows(IllegalArgumentException.class, () -> new CubicBezier(0, Double.NaN, 1, 1));
            assertThrows(IllegalArgumentException.class, () -> new CubicBezier(0, 0, 1, Double.POSITIVE_INFINITY));
        }

        @Test
        @DisplayName("writes itself as CSS, and the built-in curves by name")
        void names() {
            assertEquals("cubic-bezier(0.1, 0.7, 1, 0.1)", new CubicBezier(0.1, 0.7, 1, 0.1).cssName());
            assertEquals("ease-enter", new CubicBezier(0.2, 0, 0, 1).cssName());
            assertEquals("ease-exit", Easing.EASE_EXIT.cssName());
            assertEquals(Easing.EASE_ENTER, new CubicBezier(0.2, 0, 0, 1), "a curve is its control points");
        }
    }

    @Nested
    @DisplayName("steps")
    class Stepped {

        @ParameterizedTest(name = "steps({0}, {1}) at {2}")
        @CsvSource({
            "4, JUMP_END,   0,    0",
            "4, JUMP_END,   0.3,  0.25",
            "4, JUMP_END,   0.99, 0.75",
            "4, JUMP_END,   1,    1",
            "4, JUMP_START, 0,    0.25",
            "4, JUMP_START, 0.3,  0.5",
            "4, JUMP_START, 0.99, 1",
            "5, JUMP_NONE,  0,    0",
            "5, JUMP_NONE,  0.2,  0.25",
            "5, JUMP_NONE,  0.99, 1",
            "3, JUMP_BOTH,  0,    0.25",
            "3, JUMP_BOTH,  0.5,  0.5",
            "3, JUMP_BOTH,  1,    1",
            "1, JUMP_START, 0,    1",
            "1, JUMP_END,   0.99, 0",
        })
        @DisplayName("jumps where CSS says")
        void jumps(int count, Position position, double t, double expected) {
            assertEquals(expected, new Steps(count, position).at(t), 1e-12);
        }

        @Test
        @DisplayName("refuses too few steps for its position")
        void refuses() {
            assertThrows(IllegalArgumentException.class, () -> new Steps(0, Position.JUMP_END));
            assertThrows(IllegalArgumentException.class, () -> new Steps(1, Position.JUMP_NONE));
        }

        @Test
        @DisplayName("writes itself as CSS")
        void name() {
            assertEquals("steps(4, jump-end)", new Steps(4, Position.JUMP_END).cssName());
            assertEquals("steps(2, jump-none)", new Steps(2, Position.JUMP_NONE).toString());
        }
    }

    @Nested
    @DisplayName("a piecewise linear function")
    class Linear {

        private final PiecewiseLinear threePoints =
                new PiecewiseLinear(List.of(new Point(0, 0), new Point(0.25, 0.75), new Point(1, 1)));

        @Test
        @DisplayName("draws straight lines between its points")
        void lines() {
            assertEquals(0.125, threePoints.at(0.375), 1e-12);
            assertEquals(0.25, threePoints.at(0.75), 1e-12);
            assertEquals(0.625, threePoints.at(0.875), 1e-12);
            assertEquals(1, threePoints.at(1), 1e-12);
        }

        @Test
        @DisplayName("jumps where two points share an input, to the later one")
        void jumps() {
            var jump = new PiecewiseLinear(
                    List.of(new Point(0, 0), new Point(0, 0.5), new Point(1, 0.5), new Point(1, 1)));
            assertEquals(0, jump.at(0.25), 1e-12);
            assertEquals(0, jump.at(0.49), 1e-12);
            assertEquals(1, jump.at(0.5), 1e-12);
        }

        @Test
        @DisplayName("holds at its end points outside their inputs, and may overshoot between")
        void outside() {
            var late = new PiecewiseLinear(List.of(new Point(0.2, 0.25), new Point(1.2, 0.5), new Point(1, 0.75)));
            assertEquals(0.2, late.at(0.1), 1e-12);
            assertEquals(1.2, late.at(0.5), 1e-12);
            assertEquals(1, late.at(0.9), 1e-12);
        }

        @Test
        @DisplayName("refuses one point, inputs that go backwards, and a point that is not finite")
        void refuses() {
            assertThrows(IllegalArgumentException.class, () -> new PiecewiseLinear(List.of(new Point(0, 0))));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new PiecewiseLinear(List.of(new Point(0, 0.5), new Point(1, 0.4))));
            assertThrows(IllegalArgumentException.class, () -> new Point(Double.NaN, 0));
        }

        @Test
        @DisplayName("is the identity as linear, and writes anything else as CSS")
        void names() {
            assertSame(PiecewiseLinear.IDENTITY, Easing.LINEAR);
            assertEquals(0.37, Easing.LINEAR.at(0.37), 1e-15, "exactly t, with no solver in the way");
            assertEquals("linear", new PiecewiseLinear(List.of(new Point(0, 0), new Point(1, 1))).cssName());
            assertEquals("linear(0 0%, 0.25 75%, 1 100%)", threePoints.cssName());
        }
    }

    @Test
    @DisplayName("a switch over the kinds needs no default")
    void sealed() {
        var kinds = List.of(Easing.EASE_ENTER, new Steps(2, Position.JUMP_END), Easing.LINEAR).stream()
                .map(easing -> switch (easing) {
                    case CubicBezier _ -> "bezier";
                    case Steps _ -> "steps";
                    case PiecewiseLinear _ -> "linear";
                })
                .toList();
        assertEquals(List.of("bezier", "steps", "linear"), kinds);
        assertEquals(
                List.of("ease-enter", "ease-exit", "linear"),
                Easing.BUILT_IN.stream().map(Easing::cssName).toList());
    }
}
