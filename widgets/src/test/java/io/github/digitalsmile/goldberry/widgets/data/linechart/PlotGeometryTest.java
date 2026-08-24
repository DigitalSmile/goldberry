package io.github.digitalsmile.goldberry.widgets.data.linechart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.widgets.data.Scale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The plot's own arithmetic, in both directions.
///
/// A crosshair is [PlotGeometry#xOf] and a pointer is [PlotGeometry#indexAt],
/// and the only thing that matters about them is that they are each other's
/// inverse: a chart whose crosshair lands two pixels left of the point the
/// pointer chose is a chart that looks broken at one window size and fine at
/// every other. So the tests here are round trips rather than expected values.
///
/// No renderer and no font: this is the half of the geometry that does not need
/// a text stack, which is why it could be lifted out of the painter at all.
class PlotGeometryTest {

    /// A plot 200 wide with a 30px gutter — the numbers do not matter, but they
    /// are deliberately not round, so an off-by-one in a division shows up.
    private static PlotGeometry geometry() {
        return new PlotGeometry(36, 7, 203, 111, 30, 14,
                Scale.linear(0, 40, 118, 7));
    }

    /// The same plot, with four points at **one minute, one minute and ten
    /// minutes** apart — a series that missed a scrape.
    private static PlotGeometry timed() {
        var minute = 60_000d;
        return new PlotGeometry(36, 7, 203, 111, 30, 14,
                Scale.linear(0, 40, 118, 7),
                new double[] {0, minute, 2 * minute, 12 * minute});
    }

    @Test
    @DisplayName("a timed point sits at its own instant, not at its index")
    void unevenSamplingIsDrawnUnevenly() {
        var geometry = timed();

        // The first three points are in the first sixth of the axis, because
        // that is when they happened. Spacing them evenly would be a picture of
        // a schedule nobody kept.
        assertEquals(geometry.left(), geometry.xOf(0, 4, false), 1e-6);
        assertEquals(geometry.right(), geometry.xOf(3, 4, false), 1e-6);
        assertEquals(geometry.left() + geometry.plotWidth() / 12,
                geometry.xOf(1, 4, false), 1e-6);
        assertEquals(geometry.left() + geometry.plotWidth() / 6,
                geometry.xOf(2, 4, false), 1e-6);
    }

    @Test
    @DisplayName("a pointer picks the nearest point in time, not the nearest index")
    void theNearestReadingWins() {
        var geometry = timed();

        // Two thirds across is a long way from anything, and the reading on the
        // right is nearer: an index-based search would answer 2 because 2 is the
        // second of four.
        assertEquals(3, geometry.indexAt(
                geometry.left() + geometry.plotWidth() * 2 / 3, 4, false));
        // And just past the cluster, the last of the cluster is still nearest.
        assertEquals(2, geometry.indexAt(
                geometry.left() + geometry.plotWidth() * 0.2, 4, false));
    }

    @Test
    @DisplayName("the round trip holds on a time axis too")
    void timedPointsFindThemselves() {
        var geometry = timed();
        for (var index = 0; index < 4; index++) {
            assertEquals(index, geometry.indexAt(geometry.xOf(index, 4, false), 4, false),
                    "point " + index + " of a timed chart");
        }
    }

    @Test
    @DisplayName("a pointer on a point picks that point, for every point")
    void theRoundTripHoldsOnPoints() {
        var geometry = geometry();
        for (var points = 1; points <= 40; points++) {
            for (var index = 0; index < points; index++) {
                assertEquals(index, geometry.indexAt(geometry.xOf(index, points, false),
                                points, false),
                        "a line chart of " + points + " points, at index " + index);
                assertEquals(index, geometry.indexAt(geometry.xOf(index, points, true),
                                points, true),
                        "a bar chart of " + points + " points, at index " + index);
            }
        }
    }

    @Test
    @DisplayName("a pointer between two points picks the nearer one")
    void itSnapsToTheNearest() {
        var geometry = geometry();
        var first = geometry.xOf(0, 5, false);
        var second = geometry.xOf(1, 5, false);

        assertEquals(0, geometry.indexAt(first + (second - first) * 0.49, 5, false));
        assertEquals(1, geometry.indexAt(first + (second - first) * 0.51, 5, false));
    }

    @Test
    @DisplayName("a bar's whole band belongs to its bar, edge to edge")
    void aBandIsWideAndNotAPoint() {
        var geometry = geometry();
        var band = geometry.plotWidth() / 4;

        // The difference between the two modes, and the thing that puts every bar
        // chart's crosshair half a band out when it is got wrong: a pointer just
        // inside the left edge of the plot is over bar 0, and one just inside the
        // *second* band is over bar 1 -- not bar 0's right half.
        assertEquals(0, geometry.indexAt(geometry.left() + 0.5, 4, true));
        assertEquals(0, geometry.indexAt(geometry.left() + band - 0.5, 4, true));
        assertEquals(1, geometry.indexAt(geometry.left() + band + 0.5, 4, true));
        assertEquals(3, geometry.indexAt(geometry.right() - 0.5, 4, true));
    }

    @Test
    @DisplayName("a pointer past either end still asks about the end point")
    void theEndsClampRatherThanVanish() {
        var geometry = geometry();

        assertEquals(0, geometry.indexAt(geometry.left() - 40, 6, false),
                "clamped, not -1: a crosshair that vanished in the last few pixels of a"
                        + " plot would look like a bug in the chart");
        assertEquals(5, geometry.indexAt(geometry.right() + 40, 6, false));
        assertEquals(0, geometry.indexAt(geometry.left() - 40, 6, true));
        assertEquals(5, geometry.indexAt(geometry.right() + 40, 6, true));
    }

    @Test
    @DisplayName("a chart with one point has one answer, and no division by zero")
    void onePointIsNotOneOverZero() {
        var geometry = geometry();

        assertEquals(geometry.left(), geometry.xOf(0, 1, false));
        assertEquals(0, geometry.indexAt(geometry.left() + 50, 1, false));
        assertEquals(-1, geometry.indexAt(100, 0, false), "no points: nothing to hover");
    }

    @Test
    @DisplayName("the gutter is not the plot")
    void theLabelsAreNotHoverable() {
        var geometry = geometry();

        // A crosshair that snapped to the first point whenever the pointer
        // crossed the axis numbers would be a chart reacting to being read.
        assertFalse(geometry.holds(geometry.left() - 1, 50), "over the y labels");
        assertFalse(geometry.holds(geometry.right() + 1, 50), "past the right edge");
        assertFalse(geometry.holds(100, geometry.bottom() + 1), "down among the x labels");
        assertTrue(geometry.holds(geometry.left(), geometry.top()), "the top-left corner");
        assertTrue(geometry.holds(geometry.right(), geometry.bottom()),
                "and the bottom-right one");
    }
}
