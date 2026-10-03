package dev.goldberry.paint.geom;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.css.Corners;
import dev.goldberry.paint.Path;

/// Two convex polygons intersected — what lets an inner shadow's hole be cut
/// out of a padding box it pokes out of.
class ConvexClipTest {

    private static final double[] SQUARE = {0, 0, 10, 0, 10, 10, 0, 10};

    @Test
    @DisplayName("a polygon inside the clip comes back as it was")
    void inside() {
        var inner = new double[] {2, 2, 8, 2, 8, 8, 2, 8};

        assertArrayEquals(inner, ConvexClip.intersect(inner, SQUARE), 1e-12);
    }

    @Test
    @DisplayName("a polygon over one edge is cut along it")
    void overAnEdge() {
        var shifted = new double[] {5, 2, 15, 2, 15, 8, 5, 8};

        var clipped = ConvexClip.intersect(shifted, SQUARE);

        assertEquals(30, area(clipped), 1e-9, "5 wide and 6 tall once the part past x = 10 is gone");
        for (var i = 0; i < clipped.length; i += 2) {
            assertTrue(clipped[i] <= 10 + 1e-9, "nothing is left past the edge");
        }
    }

    @Test
    @DisplayName("polygons that do not meet intersect in nothing")
    void apart() {
        var far = new double[] {20, 20, 30, 20, 30, 30, 20, 30};

        assertEquals(0, ConvexClip.intersect(far, SQUARE).length);
    }

    @Test
    @DisplayName("an odd number of values is refused by name, not read past its end")
    void oddLength() {
        var odd = new double[] {0, 0, 10, 0, 10, 10, 0};

        var path = assertThrows(IllegalArgumentException.class, () -> ConvexClip.toPath(odd));
        var subject = assertThrows(IllegalArgumentException.class, () -> ConvexClip.intersect(odd, SQUARE));
        var clip = assertThrows(IllegalArgumentException.class, () -> ConvexClip.intersect(SQUARE, odd));

        assertTrue(path.getMessage().startsWith("polygon is x, y pairs"), path.getMessage());
        assertTrue(subject.getMessage().startsWith("subject"), subject.getMessage());
        assertTrue(clip.getMessage().startsWith("clip"), clip.getMessage());
    }

    @Test
    @DisplayName("either winding of the clip is read the same")
    void winding() {
        var shifted = new double[] {5, 2, 15, 2, 15, 8, 5, 8};
        var reversed = new double[] {0, 10, 10, 10, 10, 0, 0, 0};

        assertEquals(area(ConvexClip.intersect(shifted, SQUARE)), area(ConvexClip.intersect(shifted, reversed)), 1e-9);
    }

    @Test
    @DisplayName("a rounded rectangle flattens to a closed polygon that does not repeat its first point")
    void polygonOfARoundRect() {
        var polygon = ConvexClip.polygon(Path.roundRect(0, 0, 40, 20, Corners.all(6)), 0.05);

        var n = polygon.length;
        assertTrue(n >= 16, "four straight edges and some points round each corner");
        assertTrue(polygon[0] != polygon[n - 2] || polygon[1] != polygon[n - 1]);
        // Shorter than the rectangle by the four corners a radius cuts off.
        var expected = 40 * 20 - (4 - Math.PI) * 36;
        assertEquals(expected, area(polygon), 1.5, "within what flattening each corner cuts off");
    }

    @Test
    @DisplayName("and a polygon becomes a closed path again")
    void toPath() {
        var path = ConvexClip.toPath(SQUARE);

        assertEquals(5, path.segmentCount(), "a move, three lines and a close");
        assertEquals(Path.EMPTY, ConvexClip.toPath(new double[] {0, 0, 1, 1}));
    }

    private static double area(double[] polygon) {
        return Math.abs(ConvexClip.signedArea(polygon)) / 2;
    }
}
