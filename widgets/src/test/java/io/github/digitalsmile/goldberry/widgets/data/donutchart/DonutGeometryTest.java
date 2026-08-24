package io.github.digitalsmile.goldberry.widgets.data.donutchart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The ring's arithmetic — which slice a point is on.
///
/// No renderer and no font, because a ring needs neither: unlike an axis chart,
/// whose gutter is measured from its shaped labels, a donut's geometry follows
/// from the box alone. That is the whole reason this can be a plain unit test and
/// [io.github.digitalsmile.goldberry.widgets.data.linechart.PlotGeometryTest]'s
/// subject had to be lifted out of a painter first.
class DonutGeometryTest {

    /// Three slices in a 200x200 box: 50%, 25%, 25%, starting at twelve o'clock
    /// and going clockwise.
    private static final List<Double> THIRDS = List.of(50.0, 25.0, 25.0);

    private static DonutGeometry geometry() {
        return DonutGeometry.of(200, 200, DonutSurface.HOLE);
    }

    /// A point at `radius` from the centre, `turns` of a full circle clockwise
    /// from twelve o'clock.
    private static double[] at(DonutGeometry geometry, double turns, double radius) {
        var angle = DonutGeometry.START + turns * Math.PI * 2;
        return new double[] {
            geometry.cx() + radius * Math.cos(angle),
            geometry.cy() + radius * Math.sin(angle),
        };
    }

    @Test
    @DisplayName("twelve o'clock is the first slice, and it goes clockwise from there")
    void theRingStartsAtTheTop() {
        var geometry = geometry();
        var middle = (geometry.inner() + geometry.outer()) / 2;

        // The maths starts at three o'clock if nobody intervenes, so this is the
        // assertion that says somebody did.
        assertEquals(0, geometry.sliceAt(
                at(geometry, 0.01, middle)[0], at(geometry, 0.01, middle)[1], THIRDS),
                "just clockwise of the top is the first slice");
        assertEquals(2, geometry.sliceAt(
                at(geometry, 0.99, middle)[0], at(geometry, 0.99, middle)[1], THIRDS),
                "just anticlockwise of it is the last");
        assertEquals(1, geometry.sliceAt(
                at(geometry, 0.6, middle)[0], at(geometry, 0.6, middle)[1], THIRDS),
                "three fifths round is the second, which spans a half to three quarters");
    }

    @Test
    @DisplayName("every angle on the ring belongs to a slice, gaps included")
    void thereAreNoDeadWedges() {
        var geometry = geometry();
        var middle = (geometry.inner() + geometry.outer()) / 2;

        // The painter trims a sliver off each end of every arc so the slices do
        // not touch. A hit test that respected those slivers would put a ring of
        // two-pixel dead wedges through the chart, and a pointer crossing one
        // would drop the readout and pick it up again.
        for (var step = 0; step < 720; step++) {
            var point = at(geometry, step / 720.0, middle);
            assertTrue(geometry.sliceAt(point[0], point[1], THIRDS) >= 0,
                    "nothing at " + step + "/720 of a turn");
        }
    }

    @Test
    @DisplayName("the hole is not the chart, and neither are the corners")
    void onlyTheRingIsHoverable() {
        var geometry = geometry();

        assertEquals(-1, geometry.sliceAt(geometry.cx(), geometry.cy(), THIRDS),
                "the middle of the hole");
        var justInside = at(geometry, 0.2, geometry.inner() - 2);
        assertEquals(-1, geometry.sliceAt(justInside[0], justInside[1], THIRDS),
                "just inside the hole's edge");
        assertEquals(-1, geometry.sliceAt(2, 2, THIRDS), "the corner of the box");
        var justOutside = at(geometry, 0.2, geometry.outer() + 2);
        assertEquals(-1, geometry.sliceAt(justOutside[0], justOutside[1], THIRDS),
                "just outside the ring");
    }

    @Test
    @DisplayName("a slice with no share cannot be pointed at")
    void zeroSharesAreNotThere() {
        var geometry = geometry();
        var middle = (geometry.inner() + geometry.outer()) / 2;
        var withZero = List.of(50.0, 0.0, 50.0);

        // It is not drawn, so there is nothing there to point at -- and the two
        // that are drawn take half the ring each.
        for (var step = 0; step < 360; step++) {
            var point = at(geometry, step / 360.0, middle);
            var slice = geometry.sliceAt(point[0], point[1], withZero);
            assertTrue(slice == 0 || slice == 2, "slice " + slice + " at " + step + " degrees");
        }
    }

    @Test
    @DisplayName("the middle of a slice is where a label for it would go")
    void middlesAreMiddles() {
        var geometry = geometry();

        // Half of the first slice is a quarter turn, so its middle is at three
        // o'clock: START + π/2.
        assertEquals(DonutGeometry.START + Math.PI / 2,
                geometry.middleOf(0, THIRDS), 1e-9);
        // And pointing at that middle finds the slice it is the middle of, for
        // every slice.
        var middle = (geometry.inner() + geometry.outer()) / 2;
        for (var i = 0; i < THIRDS.size(); i++) {
            var angle = geometry.middleOf(i, THIRDS);
            var x = geometry.cx() + middle * Math.cos(angle);
            var y = geometry.cy() + middle * Math.sin(angle);
            assertEquals(i, geometry.sliceAt(x, y, THIRDS), "the middle of slice " + i);
        }
    }

    @Test
    @DisplayName("a box with no room for a ring has no geometry rather than a broken one")
    void nothingToDrawIntoIsNull() {
        assertNull(DonutGeometry.of(0, 100, DonutSurface.HOLE));
        assertNull(DonutGeometry.of(100, 0, DonutSurface.HOLE));
        assertNull(DonutGeometry.of(2, 2, DonutSurface.HOLE),
                "a collapsed split pane produces this, and it is not an error");
    }

    @Test
    @DisplayName("a share is a percentage, and a sliver says so rather than saying zero")
    void sharesReadAsPercentages() {
        assertEquals("50%", DonutSurface.share(50, 100));
        assertEquals("1%", DonutSurface.share(1, 100));
        // A slice that rounds away to nothing is still there, and `0%` beside a
        // visible arc is a readout contradicting the picture.
        assertEquals("<1%", DonutSurface.share(0.2, 100));
        assertEquals("33%", DonutSurface.share(1, 3), "the root locale, so no comma");
    }
}
