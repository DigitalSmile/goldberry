package dev.goldberry.css.background;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.css.background.GradientLayer.ColorStop;
import dev.goldberry.css.background.GradientLayer.Direction;
import dev.goldberry.layout.Length;
import dev.goldberry.paint.Gradient;

/// A gradient as written, resolved against a box: where its line runs, how
/// large its ellipse is, and where each stop lands.
class GradientLayerTest {

    private static final int RED = 0xFFFF0000;

    private static final int BLUE = 0xFF0000FF;

    private static final double EPSILON = 1e-9;

    private static List<ColorStop> redToBlue() {
        return List.of(new ColorStop(RED, null), new ColorStop(BLUE, null));
    }

    private static Gradient.Linear linear(Direction direction, List<ColorStop> stops, double width, double height) {
        var layer = new GradientLayer.Linear(direction, stops, false);
        return assertInstanceOf(Gradient.Linear.class, layer.resolve(10, 20, width, height, BackgroundPosition.ZERO));
    }

    private static List<Double> offsets(Gradient gradient) {
        return gradient.stops().stream().map(Gradient.Stop::offset).toList();
    }

    @Nested
    @DisplayName("a linear gradient's line")
    class Line {

        @Test
        @DisplayName("`to right` runs edge to edge across the middle, in the frame's coordinates")
        void toRight() {
            var gradient = linear(new Direction.Angle(Math.PI / 2), redToBlue(), 100, 50);

            assertEquals(10, gradient.x1(), EPSILON);
            assertEquals(45, gradient.y1(), EPSILON);
            assertEquals(110, gradient.x2(), EPSILON);
            assertEquals(45, gradient.y2(), EPSILON);
            assertEquals(Gradient.Extend.PAD, gradient.extend());
        }

        @Test
        @DisplayName("an angle's line is long enough that the far corners sit on 0% and 100%")
        void angled() {
            // 45° across a square: the line is the diagonal's length.
            var gradient = linear(new Direction.Angle(Math.PI / 4), redToBlue(), 100, 100);

            var length = Math.hypot(gradient.x2() - gradient.x1(), gradient.y2() - gradient.y1());
            assertEquals(100 * Math.sqrt(2), length, 1e-6);
            assertEquals(10, gradient.x1(), 1e-6, "from the bottom-left corner");
            assertEquals(120, gradient.y1(), 1e-6);
        }

        @Test
        @DisplayName("`to top right` on a wide box is perpendicular to the other diagonal")
        void corner() {
            var gradient = linear(new Direction.Corner(true, false), redToBlue(), 200, 100);

            var dx = gradient.x2() - gradient.x1();
            var dy = gradient.y2() - gradient.y1();
            // Perpendicular to the diagonal from top-left to bottom-right, (200, 100).
            assertEquals(0, dx * 200 + dy * 100, 1e-6);
            assertTrue(dx > 0 && dy < 0, "towards the top right");
        }

        @Test
        @DisplayName("`background-position` moves the line with the box's layer")
        void moved() {
            var layer = new GradientLayer.Linear(new Direction.Angle(Math.PI / 2), redToBlue(), false);

            var gradient =
                    assertInstanceOf(Gradient.Linear.class, layer.resolve(0, 0, 100, 50, new BackgroundPosition(7, 3)));

            assertEquals(7, gradient.x1(), EPSILON);
            assertEquals(28, gradient.y1(), EPSILON);
        }
    }

    @Nested
    @DisplayName("stops")
    class Stops {

        @Test
        @DisplayName("unplaced stops share the distance between placed ones")
        void distributed() {
            var stops = List.of(
                    new ColorStop(RED, null),
                    new ColorStop(BLUE, null),
                    new ColorStop(RED, null),
                    new ColorStop(BLUE, null));

            var resolved = offsets(linear(new Direction.Angle(Math.PI / 2), stops, 90, 10));

            assertEquals(4, resolved.size());
            assertEquals(0, resolved.getFirst(), EPSILON);
            assertEquals(1.0 / 3, resolved.get(1), EPSILON);
            assertEquals(2.0 / 3, resolved.get(2), EPSILON);
        }

        @Test
        @DisplayName("a stop placed before an earlier one is moved up to it: a hard edge")
        void clamped() {
            var stops = List.of(new ColorStop(RED, Length.percent(60)), new ColorStop(BLUE, Length.percent(40)));

            var gradient = linear(new Direction.Angle(Math.PI / 2), stops, 100, 10);

            // Both at 60%, so the ramp is a hard edge there.
            assertEquals(70, gradient.x1(), 1e-6);
            assertEquals(70, gradient.x2(), 0.1);
        }

        @Test
        @DisplayName("the ramp covers the first stop to the last, and the ends hold beyond")
        void spanIsTheStops() {
            var stops = List.of(new ColorStop(RED, Length.points(20)), new ColorStop(BLUE, Length.percent(50)));

            var gradient = linear(new Direction.Angle(Math.PI / 2), stops, 100, 10);

            assertEquals(30, gradient.x1(), EPSILON);
            assertEquals(60, gradient.x2(), EPSILON);
            assertEquals(List.of(0.0, 1.0), offsets(gradient));
        }

        @Test
        @DisplayName("a repeating gradient repeats the span of its stops")
        void repeating() {
            var stops = List.of(
                    new ColorStop(RED, Length.points(0)),
                    new ColorStop(RED, Length.points(6)),
                    new ColorStop(BLUE, Length.points(6)),
                    new ColorStop(BLUE, Length.points(12)));
            var layer = new GradientLayer.Linear(new Direction.Angle(Math.PI / 2), stops, true);

            var gradient =
                    assertInstanceOf(Gradient.Linear.class, layer.resolve(0, 0, 100, 10, BackgroundPosition.ZERO));

            assertEquals(Gradient.Extend.REPEAT, gradient.extend());
            assertEquals(12, gradient.x2() - gradient.x1(), EPSILON, "one period");
            assertEquals(List.of(0.0, 0.5, 0.5, 1.0), offsets(gradient));
        }

        @Test
        @DisplayName("a gradient has two stops at least")
        void twoStops() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new GradientLayer.Linear(new Direction.Angle(0), List.of(new ColorStop(RED, null)), false));
        }
    }

    @Nested
    @DisplayName("a radial gradient's ellipse")
    class Ellipse {

        private static Gradient.Radial radial(GradientLayer.Radial layer, double width, double height) {
            return assertInstanceOf(Gradient.Radial.class, layer.resolve(0, 0, width, height, BackgroundPosition.ZERO));
        }

        private static GradientLayer.Radial sized(boolean circle, GradientLayer.Extent extent, Length x, Length y) {
            return new GradientLayer.Radial(circle, extent, null, null, x, y, redToBlue(), false);
        }

        @Test
        @DisplayName("the default ellipse passes through the corners, √2 the half-sides")
        void farthestCorner() {
            var gradient = radial(
                    sized(false, GradientLayer.Extent.FARTHEST_CORNER, Length.percent(50), Length.percent(50)),
                    100,
                    40);

            assertEquals(50, gradient.cx(), EPSILON);
            assertEquals(20, gradient.cy(), EPSILON);
            assertEquals(50 * Math.sqrt(2), gradient.radiusX(), 1e-6);
            assertEquals(20 * Math.sqrt(2), gradient.radiusY(), 1e-6);
            assertEquals(0, gradient.start(), EPSILON);
        }

        @Test
        @DisplayName("a circle to its closest side, from a corner centre, is as wide as the near side allows")
        void closestSide() {
            var gradient = radial(
                    sized(true, GradientLayer.Extent.CLOSEST_SIDE, Length.points(30), Length.points(10)), 100, 40);

            assertEquals(10, gradient.radiusX(), EPSILON);
            assertEquals(10, gradient.radiusY(), EPSILON);
        }

        @Test
        @DisplayName("a circle to its farthest corner reaches it")
        void circleFarthestCorner() {
            var gradient = radial(
                    sized(true, GradientLayer.Extent.FARTHEST_CORNER, Length.percent(0), Length.percent(0)), 30, 40);

            assertEquals(50, gradient.radiusX(), EPSILON);
        }

        @Test
        @DisplayName("explicit radii are the radii, a percentage of the box's own side")
        void explicit() {
            var layer = new GradientLayer.Radial(
                    false,
                    null,
                    Length.points(20),
                    Length.percent(50),
                    Length.percent(50),
                    Length.percent(50),
                    redToBlue(),
                    false);

            var gradient = radial(layer, 100, 60);

            assertEquals(20, gradient.radiusX(), EPSILON);
            assertEquals(30, gradient.radiusY(), EPSILON);
        }

        @Test
        @DisplayName("a first stop out from the centre starts the ramp there")
        void startsOut() {
            var layer = new GradientLayer.Radial(
                    true,
                    null,
                    Length.points(40),
                    Length.points(40),
                    Length.percent(50),
                    Length.percent(50),
                    List.of(new ColorStop(RED, Length.points(10)), new ColorStop(BLUE, Length.points(30))),
                    false);

            var gradient = radial(layer, 100, 100);

            assertEquals(30, gradient.radiusX(), EPSILON, "the last stop is the ellipse");
            assertEquals(1.0 / 3, gradient.start(), EPSILON, "and the first is a third of the way out");
        }

        @Test
        @DisplayName("a repeating ramp with a stop inside the centre is moved out by whole periods")
        void repeatingFromInside() {
            var layer = new GradientLayer.Radial(
                    true,
                    null,
                    Length.points(40),
                    Length.points(40),
                    Length.percent(50),
                    Length.percent(50),
                    List.of(new ColorStop(RED, Length.points(-5)), new ColorStop(BLUE, Length.points(5))),
                    true);

            var gradient = radial(layer, 100, 100);

            assertEquals(Gradient.Extend.REPEAT, gradient.extend());
            assertEquals(15, gradient.radiusX(), EPSILON);
            assertEquals(1.0 / 3, gradient.start(), EPSILON);
        }

        @Test
        @DisplayName("a plain ramp with a stop inside the centre is cut there, at the colour it had")
        void plainFromInside() {
            var layer = new GradientLayer.Radial(
                    true,
                    null,
                    Length.points(40),
                    Length.points(40),
                    Length.percent(50),
                    Length.percent(50),
                    List.of(
                            new ColorStop(0xFF000000, Length.points(-10)),
                            new ColorStop(0xFFFFFFFF, Length.points(10))),
                    false);

            var gradient = radial(layer, 100, 100);

            assertEquals(0, gradient.start(), EPSILON);
            assertEquals(10, gradient.radiusX(), EPSILON);
            var middle = gradient.stops().getFirst().argb();
            assertEquals(0x80, (middle >> 16) & 0xFF, 1, "half-way between black and white");
        }
    }
}
