package dev.goldberry.paint.shadow;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.css.Border;
import dev.goldberry.css.Corners;
import dev.goldberry.css.value.Shadow;
import dev.goldberry.paint.Path;

/// Where a band of a shadow is: the shape each rectangle of the stack takes.
class ShadowGeometryTest {

    private static final int BLACK = 0xFF000000;

    /// The bounding box of a path, which is what a band's placement amounts to.
    ///
    /// Read off the points rather than asserted against a hand-written point
    /// sequence: the question here is where the shape *is*, and the four cubics
    /// that round its corners are [dev.goldberry.paint.Path]'s
    /// business and already have their own tests.
    private static double[] boundsOf(Path path) {
        var bounds = new double[] {
            Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY
        };
        for (var segment : path.segments()) {
            switch (segment) {
                case Path.Segment.MoveTo move -> cover(bounds, move.x(), move.y());
                case Path.Segment.LineTo line -> cover(bounds, line.x(), line.y());
                case Path.Segment.CubicTo cubic -> cover(bounds, cubic.x(), cubic.y());
                default -> {
                    // A close has no point of its own, and a round rect has no
                    // arcs or quadratics in it.
                }
            }
        }
        return bounds;
    }

    private static void cover(double[] bounds, double x, double y) {
        bounds[0] = Math.min(bounds[0], x);
        bounds[1] = Math.min(bounds[1], y);
        bounds[2] = Math.max(bounds[2], x);
        bounds[3] = Math.max(bounds[3], y);
    }

    @Nested
    @DisplayName("where a band sits")
    class Placement {

        @Test
        @DisplayName("a band with no growth is the box itself, moved by the offset")
        void offset() {
            var band = ShadowGeometry.band(100, 40, Corners.SQUARE, new Shadow(3, 5, 0, 0, BLACK), 0);
            var bounds = boundsOf(band);

            assertEquals(3, bounds[0], 1e-9);
            assertEquals(5, bounds[1], 1e-9);
            assertEquals(103, bounds[2], 1e-9);
            assertEquals(45, bounds[3], 1e-9);
        }

        @Test
        @DisplayName("growth pushes all four edges out by the same amount")
        void grown() {
            var bounds = boundsOf(ShadowGeometry.band(100, 40, Corners.SQUARE, new Shadow(0, 0, 0, 0, BLACK), 6));

            assertEquals(-6, bounds[0], 1e-9);
            assertEquals(-6, bounds[1], 1e-9);
            assertEquals(106, bounds[2], 1e-9);
            assertEquals(46, bounds[3], 1e-9);
        }

        @Test
        @DisplayName("negative growth pulls them in, which is what the inner half of a blur is")
        void shrunk() {
            var bounds = boundsOf(ShadowGeometry.band(100, 40, Corners.SQUARE, new Shadow(0, 0, 0, 0, BLACK), -4));

            assertEquals(4, bounds[0], 1e-9);
            assertEquals(96, bounds[2], 1e-9);
        }

        @Test
        @DisplayName("a band shrunk past nothing has no shape rather than a negative one")
        void shrunkAway() {
            // A large negative spread does this, and it is a shadow that draws
            // nothing rather than an error: the rule for a style value that goes
            // nowhere is to carry on.
            assertSame(Path.EMPTY, ShadowGeometry.band(20, 20, Corners.SQUARE, Shadow.NONE, -10));
            assertSame(Path.EMPTY, ShadowGeometry.band(20, 10, Corners.SQUARE, Shadow.NONE, -6));
        }
    }

    @Nested
    @DisplayName("the corners")
    class Rounding {

        @Test
        @DisplayName("a growing band's radius grows with it, so the shapes stay concentric")
        void concentric() {
            // The rule the focus ring follows, for the same reason: a shadow that
            // kept the box's radius while growing would pull away from the
            // corners and leave four dark ears.
            assertEquals(Corners.all(12), ShadowGeometry.grown(Corners.all(8), 4));
        }

        @Test
        @DisplayName("and a shrinking band's radius shrinks, without going negative")
        void shrinking() {
            assertEquals(Corners.all(4), ShadowGeometry.grown(Corners.all(8), -4));
            assertEquals(Corners.SQUARE, ShadowGeometry.grown(Corners.all(2), -8));
        }

        @Test
        @DisplayName("a square corner stays square, however far the band grows")
        void squareStaysSquare() {
            // A box with sharp corners casts a shadow with sharp corners. This is
            // `Corners.grownBy`'s own rule and the shadow inherits it rather than
            // restating it.
            assertTrue(ShadowGeometry.grown(Corners.SQUARE, 16).isSquare());
        }

        @Test
        @DisplayName("a rounded band is a rounded path and a square one is four lines")
        void shape() {
            var square = ShadowGeometry.band(80, 80, Corners.SQUARE, Shadow.NONE, 0);
            var rounded = ShadowGeometry.band(80, 80, Corners.all(8), Shadow.NONE, 0);

            assertTrue(rounded.segmentCount() > square.segmentCount());
        }
    }

    @Nested
    @DisplayName("the hole cut out of every band")
    class Hole {

        @Test
        @DisplayName("it is the border box itself, at the origin and unmoved by the offset")
        void isTheBorderBox() {
            // The offset moves the band and not the hole. A hole that travelled
            // with the band would sit exactly on top of it, and an even-odd fill
            // of the two would paint nothing at all — the shadow would vanish
            // rather than gain a hole.
            var hole = ShadowGeometry.borderBox(60, 40, Corners.SQUARE);

            assertArrayEquals(new double[] {0, 0, 60, 40}, boundsOf(hole));
        }

        @Test
        @DisplayName("it carries the box's own radii, ungrown")
        void keepsTheBoxesCorners() {
            // The band at `grow` has radii grown by `grow`; the hole never does.
            // It is the shape the background will be painted with, and cutting a
            // hole any other shape would leave a rim of shadow around a rounded
            // box.
            var square = ShadowGeometry.borderBox(60, 40, Corners.SQUARE);
            var rounded = ShadowGeometry.borderBox(60, 40, Corners.all(12));

            assertTrue(rounded.segmentCount() > square.segmentCount());
            assertArrayEquals(boundsOf(square), boundsOf(rounded));
        }

        @Test
        @DisplayName("a square box's hole is the same point sequence its band is")
        void squareHoleIsARect() {
            // So the fill rule has nothing to disagree about: the shape being
            // cut and the shape cutting it are described the same way.
            var hole = ShadowGeometry.borderBox(60, 40, Corners.SQUARE);
            var band = ShadowGeometry.band(60, 40, Corners.SQUARE, Shadow.NONE, 0);

            assertEquals(band.segments(), hole.segments());
        }
    }

    @Nested
    @DisplayName("which bands the hole erases entirely")
    class Covered {

        @Test
        @DisplayName("a centred shadow's whole inner half is inside the hole")
        void centred() {
            // No offset: the band at grow = 0 is the border box, and everything
            // inside it is erased.
            assertEquals(0, ShadowGeometry.coveredAt(new Shadow(0, 0, 16, 0, BLACK)));
        }

        @Test
        @DisplayName("an offset shadow keeps the bands that reach past the edge it moved towards")
        void offset() {
            // Moved 4px down, so a band inset by less than 4 still sticks out of
            // the top — and one inset by 4 or more does not.
            assertEquals(-4, ShadowGeometry.coveredAt(new Shadow(0, 4, 16, 0, BLACK)));
        }

        @Test
        @DisplayName("it is the larger of the two offsets, not their sum")
        void twoAxes() {
            // A band has to escape the box on *some* side to be seen, so the
            // axis that moved furthest is the one that decides. Taking the sum,
            // or one axis alone, would erase bands that are still visible.
            assertEquals(-7, ShadowGeometry.coveredAt(new Shadow(7, 3, 16, 0, BLACK)));
            assertEquals(-7, ShadowGeometry.coveredAt(new Shadow(-7, 3, 16, 0, BLACK)));
            assertEquals(-7, ShadowGeometry.coveredAt(new Shadow(3, -7, 16, 0, BLACK)));
        }

        @Test
        @DisplayName("every band it erases really is inside the border box")
        void erasedBandsAreInside() {
            // The claim checked against the geometry rather than restated: a
            // covered band's bounding box must lie within the hole's.
            var shadow = new Shadow(0, 4, 16, 0, BLACK);
            var hole = boundsOf(ShadowGeometry.borderBox(60, 40, Corners.all(8)));

            for (var band : ShadowRamp.bands(shadow)) {
                if (band.grow() > ShadowGeometry.coveredAt(shadow)) {
                    continue;
                }
                var bounds = boundsOf(ShadowGeometry.band(60, 40, Corners.all(8), shadow, band.grow()));
                assertTrue(bounds[0] >= hole[0] - 1e-9, "band at " + band.grow() + " escapes on the left");
                assertTrue(bounds[1] >= hole[1] - 1e-9, "band at " + band.grow() + " escapes on the top");
                assertTrue(bounds[2] <= hole[2] + 1e-9, "band at " + band.grow() + " escapes on the right");
                assertTrue(bounds[3] <= hole[3] + 1e-9, "band at " + band.grow() + " escapes on the bottom");
            }
        }

        @Test
        @DisplayName("and the first band it keeps really does escape")
        void keptBandsEscape() {
            // The other side of it, which is what stops `coveredAt` from being
            // over-eager and quietly erasing a visible band.
            var shadow = new Shadow(0, 4, 16, 0, BLACK);
            var hole = boundsOf(ShadowGeometry.borderBox(60, 40, Corners.all(8)));
            var covered = ShadowGeometry.coveredAt(shadow);

            var last = ShadowRamp.bands(shadow).stream()
                    .filter(band -> band.grow() > covered)
                    .reduce((first, second) -> second)
                    .orElseThrow();
            var bounds = boundsOf(ShadowGeometry.band(60, 40, Corners.all(8), shadow, last.grow()));

            assertTrue(
                    bounds[0] < hole[0] || bounds[1] < hole[1] || bounds[2] > hole[2] || bounds[3] > hole[3],
                    "the innermost drawn band at " + last.grow() + " is entirely inside the hole");
        }
    }

    @Nested
    @DisplayName("an inner shadow's band")
    class Inset {

        /// The sub-paths of a path, each as its points.
        private static List<double[]> polygons(Path path) {
            var polygons = new ArrayList<double[]>();
            var current = new ArrayList<Double>();
            for (var segment : path.segments()) {
                switch (segment) {
                    case Path.Segment.MoveTo move -> {
                        if (!current.isEmpty()) {
                            polygons.add(current.stream()
                                    .mapToDouble(Double::doubleValue)
                                    .toArray());
                            current.clear();
                        }
                        current.add(move.x());
                        current.add(move.y());
                    }
                    case Path.Segment.LineTo line -> {
                        current.add(line.x());
                        current.add(line.y());
                    }
                    default -> {
                        // Polygons only: closes carry no point.
                    }
                }
            }
            if (!current.isEmpty()) {
                polygons.add(current.stream().mapToDouble(Double::doubleValue).toArray());
            }
            return polygons;
        }

        private static double[] boundsOfPolygon(double[] polygon) {
            var bounds = new double[] {
                Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY
            };
            for (var i = 0; i + 1 < polygon.length; i += 2) {
                cover(bounds, polygon[i], polygon[i + 1]);
            }
            return bounds;
        }

        @Test
        @DisplayName("is the padding box with a hole in it, the hole moved by the offset")
        void holeInThePaddingBox() {
            var shadow = new Shadow(3, 0, 0, 0, BLACK, true);

            var band = polygons(ShadowGeometry.insetBand(100, 40, Corners.SQUARE, Border.NONE, shadow, 0));

            assertEquals(2, band.size(), "the box and the hole");
            assertArrayEquals(new double[] {0, 0, 100, 40}, boundsOfPolygon(band.get(0)), 1e-9);
            // Moved 3 right, then cut back to the box: its left edge is 3 in,
            // which is the 3px stripe the shadow paints.
            assertArrayEquals(new double[] {3, 0, 100, 40}, boundsOfPolygon(band.get(1)), 1e-9);
        }

        @Test
        @DisplayName("the padding box is inside the border")
        void insideTheBorder() {
            var border = new Border(
                    new Border.Line(1, BLACK),
                    new Border.Line(2, BLACK),
                    new Border.Line(3, BLACK),
                    new Border.Line(4, BLACK));

            var band = polygons(
                    ShadowGeometry.insetBand(100, 40, Corners.SQUARE, border, new Shadow(0, 0, 0, 5, BLACK, true), 5));

            assertArrayEquals(new double[] {4, 1, 98, 37}, boundsOfPolygon(band.get(0)), 1e-9);
            assertArrayEquals(
                    new double[] {9, 6, 93, 32}, boundsOfPolygon(band.get(1)), 1e-9, "spread shrinks the hole");
        }

        @Test
        @DisplayName("a hole that has shrunk away leaves the whole padding box")
        void noHole() {
            var band = polygons(ShadowGeometry.insetBand(
                    20, 20, Corners.SQUARE, Border.NONE, new Shadow(0, 0, 0, 0, BLACK, true), 15));

            assertEquals(1, band.size());
        }

        @Test
        @DisplayName("a rounded hole pushed past a rounded box is cut to the box's curve")
        void roundedClip() {
            var corners = Corners.all(10);
            var band = polygons(
                    ShadowGeometry.insetBand(60, 40, corners, Border.NONE, new Shadow(-8, -8, 0, 0, BLACK, true), 0));

            var box = band.get(0);
            for (var i = 0; i < band.get(1).length; i += 2) {
                var x = band.get(1)[i];
                var y = band.get(1)[i + 1];
                assertTrue(
                        insideOrOn(box, x, y),
                        "every point of the clipped hole is inside the box: (" + x + ", " + y + ")");
            }
        }

        private static boolean insideOrOn(double[] polygon, double x, double y) {
            var n = polygon.length / 2;
            for (var i = 0; i < n; i++) {
                var j = (i + 1) % n;
                var cross = (polygon[j * 2] - polygon[i * 2]) * (y - polygon[i * 2 + 1])
                        - (polygon[j * 2 + 1] - polygon[i * 2 + 1]) * (x - polygon[i * 2]);
                if (cross < -1e-6) {
                    return false;
                }
            }
            return true;
        }

        @Test
        @DisplayName("a border that fills the box leaves no padding box and no band")
        void noPaddingBox() {
            assertSame(
                    Path.EMPTY,
                    ShadowGeometry.insetBand(
                            10, 10, Corners.SQUARE, Border.all(5, BLACK), new Shadow(0, 0, 4, 0, BLACK, true), 0));
        }
    }
}
