package io.github.digitalsmile.goldberry.paint.geom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.css.value.Affine;
import io.github.digitalsmile.goldberry.paint.Path;
import io.github.digitalsmile.goldberry.paint.Path.Segment;

/// What an affine transform does to each kind of segment.
///
/// The points are asserted exactly, because an affine map of a point has one
/// right answer. The **arc** is asserted twice over: once on the ellipse it
/// comes back as, and once on where the ink actually goes — the two radii, the
/// rotation and the sweep flag are four ways of writing the same curve, and a
/// mistake in one of them can be hidden by a matching mistake in another
/// (ADR-0390, `docs/gaps.md` G46).
class TransformerTest {

    @Nested
    @DisplayName("what is left alone")
    class Untouched {

        @Test
        @DisplayName("the identity gives back the same path, not a copy")
        void identity() {
            var path = Path.roundRect(0, 0, 10, 10, 3);

            assertSame(path, path.transformed(Affine.IDENTITY));
            assertSame(path, path.rotated(0, 5, 5));
            assertSame(path, path.translated(0, 0));
        }

        @Test
        @DisplayName("an empty path stays empty")
        void empty() {
            assertSame(Path.EMPTY, Path.EMPTY.transformed(Affine.rotate(1)));
        }

        @Test
        @DisplayName("a close stays where it is in the sequence")
        void structureSurvives() {
            var two = Path.builder()
                    .moveTo(0, 0)
                    .lineTo(4, 0)
                    .close()
                    .moveTo(10, 10)
                    .lineTo(14, 10)
                    .build();

            var moved = two.translated(1, 2).segments();

            assertEquals(
                    List.of(
                            new Segment.MoveTo(1, 2),
                            new Segment.LineTo(5, 2),
                            new Segment.Close(),
                            new Segment.MoveTo(11, 12),
                            new Segment.LineTo(15, 12)),
                    moved);
        }
    }

    @Nested
    @DisplayName("points and curves")
    class Points {

        @Test
        @DisplayName("a quarter turn about the centre moves each corner of a square to the next")
        void quarterTurn() {
            var square = Path.rect(0, 0, 10, 10);

            var first = (Segment.MoveTo)
                    square.rotated(Math.PI / 2, 5, 5).segments().getFirst();

            assertEquals(10, first.x(), 1e-9);
            assertEquals(0, first.y(), 1e-9);
        }

        @Test
        @DisplayName("a rotation and then a move is one matrix, in that order")
        void composed() {
            // The tile floor's own case: turned about its middle, then dropped
            // into place. Composing the other way round would turn the offset
            // with the shape and land the tile somewhere else.
            var line = Path.line(0, 0, 4, 0);
            var turnThenDrop = Affine.rotate(Math.PI / 2).about(0, 0).then(Affine.translate(0, -20));

            var segments = line.transformed(turnThenDrop).segments();

            var end = (Segment.LineTo) segments.get(1);
            assertEquals(0, end.x(), 1e-9);
            assertEquals(-16, end.y(), 1e-9);
        }

        @Test
        @DisplayName("a curve's control points travel with its ends")
        void curves() {
            var curved = Path.builder()
                    .moveTo(0, 0)
                    .quadTo(1, 2, 3, 4)
                    .cubicTo(5, 6, 7, 8, 9, 10)
                    .build();

            var scaled = curved.scaled(2, 3).segments();

            assertEquals(new Segment.QuadTo(2, 6, 6, 12), scaled.get(1));
            assertEquals(new Segment.CubicTo(10, 18, 14, 24, 18, 30), scaled.get(2));
        }

        @Test
        @DisplayName("a shear leans the shape and keeps its area")
        void shear() {
            // x' = x + y, which is an affine nothing but a general matrix
            // expresses -- and the kind of thing a rotate-only helper cannot do.
            var skew = new Affine(1, 0, 1, 1, 0, 0);

            var segments = Path.line(0, 0, 0, 10).transformed(skew).segments();

            assertEquals(new Segment.LineTo(10, 10), segments.get(1));
            assertEquals(1, skew.determinant(), 1e-12);
        }
    }

    @Nested
    @DisplayName("the elliptic arc")
    class Arcs {

        private static final Path ARC =
                Path.builder().moveTo(0, 0).arcTo(3, 2, 0.1, false, true, 5, 0).build();

        private static Segment.ArcTo arcOf(Path path) {
            return (Segment.ArcTo) path.segments().get(1);
        }

        @Test
        @DisplayName("a turn adds itself to the ellipse's own rotation")
        void turns() {
            var turned = arcOf(ARC.rotated(0.5, 0, 0));

            assertEquals(0.6, turned.rotation(), 1e-12);
            assertEquals(3, turned.rx(), 1e-12);
            assertEquals(2, turned.ry(), 1e-12);
            assertTrue(turned.sweep());
            assertFalse(turned.largeArc());
        }

        @Test
        @DisplayName("a scale stretches the ellipse rather than the arc's endpoints alone")
        void stretches() {
            var arc = Path.builder()
                    .moveTo(0, 0)
                    .arcTo(3, 3, 0, false, true, 6, 0)
                    .build();

            var wide = arcOf(arc.scaled(2, 1));

            assertEquals(6, wide.rx(), 1e-12);
            assertEquals(3, wide.ry(), 1e-12);
            assertEquals(12, wide.x(), 1e-12);
        }

        @Test
        @DisplayName("a mirror flips the sweep, because the arc now runs the other way")
        void mirrors() {
            var flipped = arcOf(ARC.scaled(-1, 1));

            assertFalse(flipped.sweep());
            assertEquals(3, flipped.rx(), 1e-9);
            assertEquals(2, flipped.ry(), 1e-9);
            // The ellipse leans the other way. An ellipse is unchanged by a half
            // turn, so -0.1 is written as pi - 0.1.
            assertEquals(Math.PI - 0.1, flipped.rotation(), 1e-9);
            assertEquals(-5, flipped.x(), 1e-12);
        }

        @Test
        @DisplayName("the large-arc flag says the same thing after the transform")
        void largeArcSurvives() {
            var large = Path.builder()
                    .moveTo(0, 0)
                    .arcTo(3, 2, 0.1, true, false, 5, 0)
                    .build();

            var turned = arcOf(large.rotated(0.7, 1, 1));

            assertTrue(turned.largeArc());
            assertFalse(turned.sweep());
        }

        @Test
        @DisplayName("the transformed arc draws where the transformed points of the original are")
        void inkLandsWhereThePointsDo() {
            // The assertion that does not trust the four numbers: flatten the
            // arc, map the points through the matrix, and ask whether the arc
            // that came back passes through them. A wrong radius or a wrong
            // sweep moves the curve off those points while leaving a plausible
            // shape behind.
            var sheared = Affine.rotate(0.9).then(new Affine(1.4, 0, 0.35, 0.8, 7, -3));
            var mirroring = Affine.scale(-1, 1).then(Affine.rotate(0.3));
            var large = Path.builder()
                    .moveTo(0, 0)
                    .arcTo(3, 2, 0.1, true, false, 5, 0)
                    .build();

            for (var matrix : List.of(sheared, mirroring)) {
                assertFollows(ARC, matrix);
                assertFollows(large, matrix);
                assertFollows(Path.circle(2, 3, 4), matrix);
            }
        }

        /// Asserts that transforming `path` and then flattening it lands on the
        /// same curve as flattening it and then transforming the points.
        ///
        /// Flattened a thousand times finer than a frame ever is, so the two
        /// approximations are far closer to the curve than to each other and the
        /// tolerance below measures the arithmetic rather than the flattener.
        private static void assertFollows(Path path, Affine matrix) {
            var expected = points(Flattener.flatten(path, 1e-4).transformed(matrix));
            var actual = points(Flattener.flatten(path.transformed(matrix), 1e-4));

            for (var point : actual) {
                var distance = distanceTo(expected, point);
                assertTrue(
                        distance < 0.01,
                        () -> "the transformed arc strays " + distance + " from where the transformed points are");
            }
        }

        /// The vertices of a flattened path. A close adds no vertex of its own —
        /// every shape here ends where it started.
        private static List<double[]> points(Path flattened) {
            return flattened.segments().stream()
                    .<double[]>mapMulti((segment, accept) -> {
                        switch (segment) {
                            case Segment.MoveTo(var x, var y) -> accept.accept(new double[] {x, y});
                            case Segment.LineTo(var x, var y) -> accept.accept(new double[] {x, y});
                            case Segment.Close _ -> {}
                            default -> throw new IllegalStateException("a flattened path holds moves and lines only");
                        }
                    })
                    .toList();
        }

        /// How far `point` is from the polyline through `polyline`.
        private static double distanceTo(List<double[]> polyline, double[] point) {
            var best = Double.MAX_VALUE;
            for (var i = 1; i < polyline.size(); i++) {
                best = Math.min(best, distanceToSegment(polyline.get(i - 1), polyline.get(i), point));
            }
            return best;
        }

        private static double distanceToSegment(double[] from, double[] to, double[] point) {
            var dx = to[0] - from[0];
            var dy = to[1] - from[1];
            var length = dx * dx + dy * dy;
            if (length == 0) {
                return Math.hypot(point[0] - from[0], point[1] - from[1]);
            }
            var t = Math.clamp(((point[0] - from[0]) * dx + (point[1] - from[1]) * dy) / length, 0, 1);
            return Math.hypot(point[0] - (from[0] + t * dx), point[1] - (from[1] + t * dy));
        }
    }
}
