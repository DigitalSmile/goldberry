package io.github.digitalsmile.goldberry.paint.shadow;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.css.Corners;
import io.github.digitalsmile.goldberry.css.value.Shadow;
import io.github.digitalsmile.goldberry.paint.Path;

/// Where a band of a shadow is — ADR-0310.
class ShadowGeometryTest {

    private static final int BLACK = 0xFF000000;

    /// The bounding box of a path, which is what a band's placement amounts to.
    ///
    /// Read off the points rather than asserted against a hand-written point
    /// sequence: the question here is where the shape *is*, and the four cubics
    /// that round its corners are [io.github.digitalsmile.goldberry.paint.Path]'s
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
            // nothing rather than an error: §8's rule for a value that goes
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
    @DisplayName("the hole cut out of every band — ADR-0427")
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
}
