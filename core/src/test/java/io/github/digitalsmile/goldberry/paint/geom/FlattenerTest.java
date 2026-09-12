package io.github.digitalsmile.goldberry.paint.geom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.paint.Path;

/// How close a flattened curve stays to the curve it replaces.
///
/// The assertions are mostly **distance from the true curve**, sampled densely,
/// rather than point sequences: how many segments a cubic becomes is an
/// implementation's business and how far they stray from it is the contract
/// (ADR-0278). The one thing pinned exactly is that straight input comes back
/// untouched, because that is what says flattening is not quietly rewriting
/// every path in the toolkit.
class FlattenerTest {

    @Nested
    @DisplayName("what is left alone")
    class Untouched {

        @Test
        @DisplayName("a path with no curves comes back equal to itself")
        void straightIsUnchanged() {
            var square = Path.rect(0, 0, 10, 10);
            assertEquals(square, Flattener.flatten(square));
            assertEquals(Path.line(1, 2, 3, 4), Flattener.flatten(Path.line(1, 2, 3, 4)));
            assertEquals(Path.EMPTY, Flattener.flatten(Path.EMPTY));
        }

        @Test
        @DisplayName("moves and closes survive")
        void structureSurvives() {
            var two = Path.builder()
                    .moveTo(0, 0)
                    .lineTo(4, 0)
                    .close()
                    .moveTo(10, 10)
                    .lineTo(14, 10)
                    .build();

            assertEquals(two, Flattener.flatten(two));
        }

        @Test
        @DisplayName("a curve whose controls are collinear needs one segment")
        void collinearControlsAreALine() {
            // The second derivative is zero, so the bound says one piece draws
            // it exactly -- and it does.
            var straightCubic =
                    Path.builder().moveTo(0, 0).cubicTo(1, 0, 2, 0, 3, 0).build();

            assertEquals(2, Flattener.flatten(straightCubic).segmentCount());
        }
    }

    @Nested
    @DisplayName("what curves become")
    class Curves {

        @Test
        @DisplayName("nothing curved is left")
        void onlyMovesAndLines() {
            var mixed = Path.builder()
                    .moveTo(0, 0)
                    .quadTo(10, 20, 20, 0)
                    .cubicTo(30, -20, 40, 20, 50, 0)
                    .arcTo(10, 10, 0, false, true, 70, 0)
                    .close()
                    .build();

            for (var segment : Flattener.flatten(mixed).segments()) {
                assertTrue(
                        segment instanceof Path.Segment.MoveTo
                                || segment instanceof Path.Segment.LineTo
                                || segment instanceof Path.Segment.Close,
                        () -> "a flattened path holds only moves, lines and closes, and this is " + segment);
            }
        }

        @Test
        @DisplayName("a flattened cubic stays within the tolerance of the real one")
        void cubicStaysClose() {
            var cubic =
                    Path.builder().moveTo(0, 0).cubicTo(0, 60, 90, 60, 90, 0).build();
            var flattened = Flattener.flatten(cubic);

            // Sampled at a thousand points along the true curve: every one of
            // them is within the tolerance of some flattened segment.
            for (var step = 0; step <= 1000; step++) {
                var t = step / 1000d;
                var u = 1 - t;
                var x = 3 * u * t * t * 90 + t * t * t * 90;
                var y = 3 * u * u * t * 60 + 3 * u * t * t * 60;
                var distance = distanceToPath(flattened, x, y);
                assertTrue(
                        distance <= Flattener.TOLERANCE,
                        () -> "the curve at t=" + t + " is " + distance + " from the flattening");
            }
        }

        @Test
        @DisplayName("a flattened circle stays within the tolerance of the real one")
        void circleStaysClose() {
            // The arc path, which is its own conversion and the one most likely
            // to be subtly wrong: an endpoint-to-centre slip puts the circle in
            // the right place with the wrong radius.
            var flattened = Flattener.flatten(Path.circle(50, 50, 40));

            for (var step = 0; step < 720; step++) {
                var angle = step * Math.PI / 360;
                var distance = distanceToPath(flattened, 50 + 40 * Math.cos(angle), 50 + 40 * Math.sin(angle));
                assertTrue(
                        distance <= Flattener.TOLERANCE,
                        () -> "the circle at " + angle + " rad is " + distance + " from the flattening");
            }
        }

        @Test
        @DisplayName("an ellipse keeps both of its radii")
        void ellipseKeepsItsShape() {
            var flattened = Flattener.flatten(Path.ellipse(0, 0, 40, 10));
            var maxX = 0d;
            var maxY = 0d;
            for (var segment : flattened.segments()) {
                if (segment instanceof Path.Segment.LineTo line) {
                    maxX = Math.max(maxX, Math.abs(line.x()));
                    maxY = Math.max(maxY, Math.abs(line.y()));
                }
            }
            // A conversion that lost one radius would give a circle of the other.
            assertEquals(40, maxX, Flattener.TOLERANCE);
            assertEquals(10, maxY, Flattener.TOLERANCE);
        }

        @Test
        @DisplayName("a tighter tolerance buys more segments")
        void toleranceBuysSegments() {
            var cubic =
                    Path.builder().moveTo(0, 0).cubicTo(0, 60, 90, 60, 90, 0).build();

            var coarse = Flattener.flatten(cubic, 1).segmentCount();
            var fine = Flattener.flatten(cubic, 0.01).segmentCount();
            assertTrue(fine > coarse, () -> "a tenth the tolerance is more than " + coarse + " segments, not " + fine);
        }

        @Test
        @DisplayName("an arc of zero radius is a straight line, not an error")
        void degenerateArcs() {
            // Both of SVG's degenerate cases. A radius of zero arrives from a
            // collapsed layout rather than from a mistake.
            var zeroRadius = Path.builder()
                    .moveTo(0, 0)
                    .arcTo(0, 10, 0, false, true, 10, 0)
                    .build();
            assertEquals(Path.builder().moveTo(0, 0).lineTo(10, 0).build(), Flattener.flatten(zeroRadius));

            var noDistance = Path.builder()
                    .moveTo(5, 5)
                    .arcTo(10, 10, 0, false, true, 5, 5)
                    .build();
            assertEquals(Path.builder().moveTo(5, 5).lineTo(5, 5).build(), Flattener.flatten(noDistance));
        }

        @Test
        @DisplayName("radii too small to span the endpoints are scaled up, as SVG says")
        void undersizedRadiiAreGrown() {
            // SVG F.6.6. A radius of 1 cannot reach across 20, and the answer is
            // a half-circle of radius 10 rather than a refusal.
            var arc = Path.builder()
                    .moveTo(0, 0)
                    .arcTo(1, 1, 0, false, true, 20, 0)
                    .build();
            var flattened = Flattener.flatten(arc);

            // Upward, because `sweep` is the positive-angle direction and y
            // grows downward -- the same convention `Path.arc` documents.
            var apex = flattened.segments().stream()
                    .filter(segment -> segment instanceof Path.Segment.LineTo)
                    .map(Path.Segment.LineTo.class::cast)
                    .mapToDouble(Path.Segment.LineTo::y)
                    .min()
                    .orElseThrow();
            assertEquals(-10, apex, 0.5, "the fitted arc bulges to the radius it was grown to");
        }
    }

    @Nested
    @DisplayName("the tolerance itself")
    class Tolerance {

        @Test
        @DisplayName("a tolerance of zero or less is refused")
        void mustBePositive() {
            var circle = Path.circle(0, 0, 10);
            assertThrows(IllegalArgumentException.class, () -> Flattener.flatten(circle, 0));
            assertThrows(IllegalArgumentException.class, () -> Flattener.flatten(circle, -1));
            assertThrows(IllegalArgumentException.class, () -> Flattener.flatten(circle, Double.NaN));
        }

        @Test
        @DisplayName("a huge tolerance still produces a usable path rather than nothing")
        void coarseIsStillAPath() {
            // The clamp at the other end: one segment per curve, not zero.
            var flattened = Flattener.flatten(Path.circle(0, 0, 10), 1000);
            assertTrue(flattened.segmentCount() >= 2, () -> "still a path: " + flattened);
        }
    }

    /// The shortest distance from `(x, y)` to any segment of a flattened path.
    private static double distanceToPath(Path path, double x, double y) {
        var best = Double.MAX_VALUE;
        var fromX = 0d;
        var fromY = 0d;
        var startX = 0d;
        var startY = 0d;
        for (var segment : path.segments()) {
            switch (segment) {
                case Path.Segment.MoveTo move -> {
                    fromX = move.x();
                    fromY = move.y();
                    startX = fromX;
                    startY = fromY;
                }
                case Path.Segment.LineTo line -> {
                    best = Math.min(best, distanceToSegment(x, y, fromX, fromY, line.x(), line.y()));
                    fromX = line.x();
                    fromY = line.y();
                }
                case Path.Segment.Close ignored -> {
                    best = Math.min(best, distanceToSegment(x, y, fromX, fromY, startX, startY));
                    fromX = startX;
                    fromY = startY;
                }
                default -> throw new AssertionError("not flattened: " + segment);
            }
        }
        return best;
    }

    private static double distanceToSegment(double px, double py, double x1, double y1, double x2, double y2) {
        var dx = x2 - x1;
        var dy = y2 - y1;
        var lengthSquared = dx * dx + dy * dy;
        if (lengthSquared == 0) {
            return Math.hypot(px - x1, py - y1);
        }
        var t = Math.clamp(((px - x1) * dx + (py - y1) * dy) / lengthSquared, 0d, 1d);
        return Math.hypot(px - (x1 + t * dx), py - (y1 + t * dy));
    }
}
